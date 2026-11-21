const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const root = path.resolve(__dirname, '../../..');
const generated = fs.readFileSync(path.join(root, 'plugins/examples/operit_editor.js'), 'utf8');
const bridgeFile = fs.readFileSync(path.join(root, 'app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt'), 'utf8');
const bridge = bridgeFile.match(/SoftwareSettings:\s*\{([\s\S]*?)\s*readEnvironmentVariable:/);
assert.ok(bridge, '未找到生产 SoftwareSettings 桥接');

function load(host, supported = true) {
    const calls = [];
    const logs = [];
    const softwareSettings = vm.runInNewContext(`({${bridge[1]}})`, {
        toolCall(name, params) {
            calls.push({ name, params });
            return host(JSON.parse(params.args));
        },
    });
    if (!supported) delete softwareSettings.exec;
    const context = { exports: {}, Tools: { SoftwareSettings: softwareSettings }, NativeInterface: { logError(message) { logs.push(message); } } };
    vm.runInNewContext(generated, context, { filename: 'operit_editor.js' });
    return { call: context.exports.operit_editor, sdk: softwareSettings.exec, calls, logs };
}

for (const encoded of [false, true]) {
    test(`脚本到真实 SDK 桥接逐项保留参数，JSON 编码=${encoded}`, async () => {
        const args = ['package', 'exec', 'sample:run', '{"text":" a b ","empty":"","line":"a\\nb"}', '', '--json', '中文\n第二行', '$(not-a-shell)'];
        const expected = { actual: true, value: ['业务结果'] };
        const runtime = load(received => { assert.deepEqual(received, args); return Promise.resolve(expected); });
        assert.equal(await runtime.call({ args: encoded ? JSON.stringify(args) : args }), expected);
        assert.equal(runtime.calls.length, 1);
        assert.equal(runtime.calls[0].name, 'execute_cli_command');
        assert.deepEqual(JSON.parse(runtime.calls[0].params.args), args);
    });
}

test('空数组直接请求宿主帮助', async () => {
    const runtime = load(args => { assert.deepEqual(args, []); return Promise.resolve('live help'); });
    assert.equal(await runtime.call({ args: [] }), 'live help');
});

test('缺失参数、query、无效元素和错误 JSON 均在调用宿主前拒绝', async () => {
    const runtime = load(() => assert.fail('不应调用宿主'));
    for (const value of [undefined, null, [], {}, { query: 'help' }, { args: [], query: '' }, { args: 'package list' }, { args: {} }, { args: [1] }, { args: [null] }, { args: new Array(1) }]) {
        await assert.rejects(runtime.call(value));
    }
    assert.equal(runtime.calls.length, 0);
    assert.equal(runtime.logs.length, 1);
});

test('SDK 直接调用也拒绝非字符串数组', () => {
    const runtime = load(() => assert.fail('不应调用宿主'));
    for (const value of [null, '[]', {}, [1], [null], new Array(1)]) assert.throws(() => runtime.sdk(value));
    assert.equal(runtime.calls.length, 0);
});

test('宿主失败对象原样传播且仅执行一次', async () => {
    const failure = new Error('permission denied with native diagnostics');
    const runtime = load(() => Promise.reject(failure));
    await assert.rejects(runtime.call({ args: ['package', 'enable', 'sample'] }), error => error === failure);
    assert.equal(runtime.calls.length, 1);
});

test('旧软件缺失接口时明确要求同步更新', async () => {
    const runtime = load(() => assert.fail('不应调用宿主'), false);
    await assert.rejects(runtime.call({ args: [] }), /1\.12\.2\+27/);
    assert.equal(runtime.calls.length, 0);
});

test('六层嵌套调用与十二个并发调用使用各自的参数', async () => {
    const nested = load(async args => {
        const depth = Number(args[0]);
        return depth === 0 ? args[1] : nested.call({ args: [String(depth - 1), args[1]] });
    });
    const values = await Promise.all(Array.from({ length: 12 }, (_, index) => nested.call({ args: ['6', `caller-${index}`] })));
    assert.deepEqual(values, Array.from({ length: 12 }, (_, index) => `caller-${index}`));
    assert.equal(nested.calls.length, 12 * 7);
});

test('内置资源与生成脚本一致，仅公开一个 args 工具', () => {
    assert.equal(fs.readFileSync(path.join(root, 'app/src/main/assets/packages/operit_editor.js'), 'utf8'), generated);
    const metadata = JSON.parse(generated.match(/\/\* METADATA\s*([\s\S]*?)\*\//)[1]);
    assert.deepEqual(metadata.tools.map(tool => tool.name), ['operit_editor']);
    assert.deepEqual(metadata.tools[0].parameters.map(parameter => parameter.name), ['args']);
    assert.match(metadata.description.zh, /1\.12\.2\+27/);
    assert.ok(generated.split('\n').length < 100);
});