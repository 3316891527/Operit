const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const scriptPath = process.argv[2] || path.resolve(__dirname, '../../plugins/examples/openai_draw.js');
const source = fs.readFileSync(scriptPath, 'utf8');

async function invoke(parameters) {
    const requests = [];
    const writes = [];
    let result;
    const responseContent = fields => JSON.stringify({
        data: [{ b64_json: Buffer.from(fields.output_format === 'gif' ? 'GIF89a mock animation' : 'mock still image').toString('base64') }]
    });
    const client = {
        newRequest() {
            let url;
            let fields;
            const request = {
                url(value) { url = value; return request; },
                method() { return request; },
                headers() { return request; },
                body(value) { fields = JSON.parse(value); return request; },
                build() {
                    return { execute: async () => {
                        requests.push({ transport: 'json', url, fields });
                        return { statusCode: 200, content: responseContent(fields), isSuccessful: () => true };
                    } };
                }
            };
            return request;
        }
    };
    const builder = {
        connectTimeout() { return builder; },
        readTimeout() { return builder; },
        writeTimeout() { return builder; },
        build() { return client; }
    };
    const context = {
        exports: {},
        console: { error() {}, warn() {}, log() {} },
        getEnv: name => name === 'OPENAI_API_KEY' ? 'offline-mock-key' : '',
        getPluginConfigDir: () => '/offline-mock/draw',
        complete: value => { result = value; },
        OkHttp: { newBuilder: () => builder },
        Tools: {
            Files: {
                mkdir: async () => ({ successful: true }),
                exists: async () => ({ exists: true }),
                writeBinary: async (filePath, content) => { writes.push({ filePath, content }); return { successful: true }; },
                download: async () => { throw new Error('Unexpected network download in offline verification'); }
            },
            Net: {
                uploadFile: async request => {
                    requests.push({ transport: 'multipart', url: request.url, fields: request.form_data });
                    return { statusCode: 200, content: responseContent(request.form_data) };
                }
            }
        }
    };
    vm.runInNewContext(source, context, { filename: scriptPath });
    await context.exports.draw_image({ prompt: 'offline regression', ...parameters });
    return { result, requests, writes };
}

function assertOmitted(fields, key) {
    assert.equal(Object.hasOwn(fields, key), false, `${key} must be absent from the outgoing request`);
}

for (const transport of ['json', 'multipart']) {
    const reference = transport === 'multipart' ? { image_paths: ['/offline-mock/reference.png'] } : {};
    for (const format of ['png', 'jpeg', 'webp', 'gif']) {
        for (const [label, frames] of [
            ['omitted', {}],
            ['host nulls', { frame_count: null, frame_duration_ms: null }],
            ['one host null', { frame_count: null }]
        ]) {
            test(`${transport} ${format}: ${label} sends no frame fields`, async () => {
                const actual = await invoke({ output_format: format, ...reference, ...frames });
                assert.equal(actual.result.success, true, actual.result.message);
                assert.equal(actual.requests.length, 1);
                assert.equal(actual.requests[0].transport, transport);
                assertOmitted(actual.requests[0].fields, 'frame_count');
                assertOmitted(actual.requests[0].fields, 'frame_duration_ms');
                assert.equal(actual.writes.length, 1);
                assert.ok(actual.writes[0].filePath.endsWith('.' + format));
            });
        }
    }
    for (const frames of [
        { frame_count: 2, frame_duration_ms: 20 },
        { frame_count: 16, frame_duration_ms: 2000 },
        { frame_count: 9, frame_duration_ms: null },
        { frame_count: null, frame_duration_ms: 200 }
    ]) {
        test(`${transport} GIF explicit fields ${JSON.stringify(frames)}`, async () => {
            const actual = await invoke({ output_format: 'gif', ...reference, ...frames });
            assert.equal(actual.result.success, true, actual.result.message);
            const fields = actual.requests[0].fields;
            for (const [key, value] of Object.entries(frames)) {
                if (value === null) assertOmitted(fields, key);
                else assert.equal(fields[key], transport === 'json' ? value : value.toString());
            }
        });
    }
    for (const [frames, message] of [
        [{ frame_count: 1 }, '2–16'],
        [{ frame_count: 17 }, '2–16'],
        [{ frame_count: 2.5 }, '2–16'],
        [{ frame_count: 0 }, '2–16'],
        [{ frame_duration_ms: 19 }, '至少 20'],
        [{ frame_duration_ms: 20.5 }, '至少 20'],
        [{ frame_duration_ms: Number.MAX_SAFE_INTEGER + 1 }, '至少 20']
    ]) {
        test(`${transport} GIF rejects ${JSON.stringify(frames)}`, async () => {
            const actual = await invoke({ output_format: 'gif', ...reference, ...frames });
            assert.equal(actual.result.success, false);
            assert.ok(actual.result.message.includes(message), actual.result.message);
            assert.equal(actual.requests.length, 0);
        });
    }
    for (const frames of [{ frame_count: 9 }, { frame_duration_ms: 200 }]) {
        test(`${transport} PNG rejects explicit GIF field ${JSON.stringify(frames)}`, async () => {
            const actual = await invoke({ output_format: 'png', ...reference, ...frames });
            assert.equal(actual.result.success, false);
            assert.ok(actual.result.message.includes('只能用于 output_format=gif'));
            assert.equal(actual.requests.length, 0);
        });
    }
    test(`${transport} blank execution form keeps the existing PNG default`, async () => {
        const actual = await invoke({
            model: '', size: '', quality: '', background: '', output_format: '', complexity: '',
            frame_count: null, frame_duration_ms: null, image_urls: null, image_paths: null,
            file_name: '', api_base_url: '', ...reference
        });
        assert.equal(actual.result.success, true, actual.result.message);
        assert.equal(actual.result.data.output_format, 'png');
        assertOmitted(actual.requests[0].fields, 'frame_count');
        assertOmitted(actual.requests[0].fields, 'frame_duration_ms');
    });
}

test('tool metadata keeps GIF frame fields optional without default values', () => {
    const metadata = JSON.parse(source.match(/\/\* METADATA\s*([\s\S]*?)\*\//)[1]);
    const tool = metadata.tools.find(item => item.name === 'draw_image');
    assert.equal(tool.parameters.length, 13);
    for (const name of ['frame_count', 'frame_duration_ms']) {
        const parameter = tool.parameters.find(item => item.name === name);
        assert.equal(parameter.type, 'number');
        assert.equal(parameter.required, false);
        assert.equal(Object.hasOwn(parameter, 'default'), false);
        assert.equal(Object.hasOwn(parameter, 'defaultValue'), false);
    }
});


test('bundled drawing script matches the maintained example', () => {
    const root = path.resolve(__dirname, '../..');
    assert.equal(
        fs.readFileSync(path.join(root, 'app/src/main/assets/packages/openai_draw.js'), 'utf8'),
        fs.readFileSync(path.join(root, 'plugins/examples/openai_draw.js'), 'utf8')
    );
});
