'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const root = path.resolve(__dirname, '../..');
const plugin = fs.readFileSync(path.join(root, 'plugins/examples/super_admin.js'), 'utf8');

function loadShell(handler) {
    const exports = {};
    vm.runInNewContext(plugin, {
        exports,
        Tools: { System: { shell: handler } },
        console: { log() {}, error() {} },
    });
    return exports.shell;
}

test('默认调用保留 command/output/exitCode，期限交给内置工具默认值', async () => {
    const calls = [];
    const shell = loadShell(async (...args) => {
        calls.push(args);
        return { output: 'OK', exitCode: 0 };
    });
    const result = await shell({ command: 'echo OK' });
    assert.deepEqual(calls, [['echo OK', undefined]]);
    assert.equal(JSON.stringify(result), JSON.stringify({ command: 'echo OK', output: 'OK', exitCode: 0 }));
});

test('插件将更长的 timeoutMs 传到内置 Shell', async () => {
    let received;
    await loadShell(async (...args) => {
        received = args;
        return { output: '', exitCode: 0 };
    })({ command: 'slow-command', timeoutMs: 120000 });
    assert.deepEqual(received, ['slow-command', 120000]);
});

test('内置工具超时失败仍为拒绝，不包装为成功结果', async () => {
    const failure = new Error('Shell command timed out');
    await assert.rejects(loadShell(async () => { throw failure; })({ command: 'sleep 60' }),
        error => error === failure);
});

test('空命令不能进入执行器', async () => {
    let called = false;
    await assert.rejects(loadShell(async () => { called = true; })({ command: '' }));
    assert.equal(called, false);
});

test('JS 桥接使用 timeout_ms，并兼容只传命令的已有调用', async () => {
    const source = fs.readFileSync(path.join(root,
        'app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt'), 'utf8');
    const implementation = source.match(/shell: (\(command, timeoutMs\) => \{[\s\S]*?\n                \}),/);
    assert.ok(implementation);
    const calls = [];
    const shell = vm.runInNewContext(`(${implementation[1]})`, {
        toolCall: (name, params) => { calls.push([name, JSON.stringify(params)]); },
    });
    shell('echo OK');
    shell('sleep 40', 60000);
    assert.deepEqual(calls, [
        ['execute_shell', '{"command":"echo OK"}'],
        ['execute_shell', '{"command":"sleep 40","timeout_ms":"60000"}'],
    ]);
});

test('打包资产与已编译插件保持一致，工具描述公开执行期限', () => {
    assert.equal(fs.readFileSync(path.join(root, 'app/src/main/assets/packages/super_admin.js'), 'utf8'), plugin);
    const metadata = JSON.parse(plugin.match(/METADATA\s*([\s\S]*?)\*\//)[1]);
    const tool = metadata.tools.find(tool => tool.name === 'shell');
    assert.ok(tool.parameters.some(parameter => parameter.name === 'timeoutMs' && parameter.required === false));
    assert.match(tool.description.zh, /30秒/);
});