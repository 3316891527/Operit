'use strict';

const assert = require('node:assert/strict');
const { test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../..');
const sourceRoot = path.join(root, 'app/src/main/java/com/ai/assistance/operit/core/tools/javascript');

function kotlinString(file, functionName) {
  const source = fs.readFileSync(path.join(sourceRoot, file), 'utf8');
  const start = source.indexOf(`fun ${functionName}(`);
  assert.ok(start >= 0);
  const open = source.indexOf('return """', start) + 'return """'.length;
  const end = source.indexOf('""".trimIndent()', open);
  assert.ok(end > open);
  return source.slice(open, end);
}

function runtime() {
  const calls = [];
  const sandbox = { Date, Math, JSON, Promise };
  sandbox.__operitExpose = (key, value) => { sandbox[key] = value; };
  sandbox.NativeInterface = {
    callToolAsync(...args) { calls.push({ owner: null, streaming: false, args }); },
    callToolAsyncStreaming(...args) { calls.push({ owner: null, streaming: true, args }); },
    callToolAsyncForCall(owner, ...args) { calls.push({ owner, streaming: false, args }); },
    callToolAsyncStreamingForCall(owner, ...args) { calls.push({ owner, streaming: true, args }); },
  };
  const context = vm.createContext(sandbox);
  vm.runInContext(kotlinString('JsInitRuntimeScriptBuilder.kt', 'buildRuntimeToolCallScript'), context);
  const tools = kotlinString('JsTools.kt', 'getJsToolsDefinition');
  vm.runInContext(tools.slice(0, tools.indexOf('var Tools = {')), context);
  function forCall(owner) {
    return sandbox.toolCall.bind({
      __operitCallId: owner,
      __operitAsyncToolMethod: 'callToolAsyncForCall',
      __operitStreamingToolMethod: 'callToolAsyncStreamingForCall',
    });
  }
  function complete(call, data) {
    sandbox[call.args[0]]({ success: true, data }, false);
  }
  return { sandbox, calls, forCall, complete };
}

test('two conversations retain their own file operation owner across interleaved promises', async () => {
  const state = runtime();
  const filesA = state.sandbox.__operitCreateFileTools(state.forCall('chat-A'));
  const filesB = state.sandbox.__operitCreateFileTools(state.forCall('chat-B'));
  const operationA = (async () => {
    await filesA.create('/outside/a.as', 'first', 'linux');
    await filesA.edit('/outside/a.as', 'first', 'second', 'linux');
  })();
  const operationB = filesB.write('/workspace/b.as', 'other', false, 'android');
  assert.deepEqual(state.calls.map(call => call.owner), ['chat-A', 'chat-B']);
  state.complete(state.calls[1], { path: '/workspace/b.as' });
  await operationB;
  state.sandbox.__operitCurrentCallId = 'unrelated-chat';
  state.complete(state.calls[0], { path: '/outside/a.as' });
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(state.calls[2].owner, 'chat-A');
  assert.equal(state.calls[2].args[2], 'edit_file');
  assert.equal(JSON.parse(state.calls[2].args[3]).path, '/outside/a.as');
  state.complete(state.calls[2], { path: '/outside/a.as' });
  await operationA;
});

test('streaming calls retain the originating call and preserve intermediate results', async () => {
  const state = runtime();
  const received = [];
  const pending = state.forCall('chat-stream')('apply_file', { path: '/a.as' }, { onIntermediateResult: data => received.push(data) });
  const call = state.calls[0];
  assert.equal(call.owner, 'chat-stream');
  assert.equal(call.streaming, true);
  state.sandbox[call.args[1]]({ success: true, data: 'partial' }, false);
  state.complete(call, 'finished');
  assert.equal(await pending, 'finished');
  assert.deepEqual(received, ['partial']);
});

test('ordinary UI tools use the existing generic bridge without conversation ownership', async () => {
  const state = runtime();
  const pending = state.sandbox.toolCall('file_exists', { path: '/tmp/a.as', environment: 'linux' });
  assert.equal(state.calls[0].owner, null);
  assert.equal(state.calls[0].args[2], 'file_exists');
  state.complete(state.calls[0], { exists: true });
  assert.equal((await pending).exists, true);
});
