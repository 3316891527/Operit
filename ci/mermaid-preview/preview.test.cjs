const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { before, after, test } = require('node:test');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '../..');
const assets = path.join(root, 'app/src/main/assets/markdown/mermaid');
const artifacts = path.join(root, 'artifacts/mermaid-preview');
const fixtures = {
    flowchart: 'flowchart TD\nA[人工智能 AI] --> B{分析 Brain}\nB -->|思考 Think| C[生成代码 Code]',
    architecture: 'flowchart TD\nEditor --> Graph\nGraph --> Data\nGraph --> Reflection\nGraph --> Serializer\nGraph --> Generator\nReflection --> Members\nSerializer --> Storage\nGenerator --> Editor',
    sequence: 'sequenceDiagram\nAlice->>Bob: Hello\nBob-->>Alice: Ready',
    class: 'classDiagram\nAnimal <|-- Duck\nAnimal : +String name',
    state: 'stateDiagram-v2\n[*] --> Ready\nReady --> [*]',
    er: 'erDiagram\nCUSTOMER ||--o{ ORDER : places',
    gantt: 'gantt\ntitle Plan\ndateFormat YYYY-MM-DD\nsection Work\nTask :2026-01-01, 1d',
    pie: 'pie\ntitle Plan\n"Done" : 60\n"Remaining" : 40',
    mindmap: 'mindmap\n  root((Plan))\n    Done\n    Remaining',
};
let server;
let browser;
let url;

before(async () => {
    server = http.createServer((request, response) => {
        const name = new URL(request.url, 'http://localhost').pathname.slice(1);
        if (!['preview.html', 'renderer.js', 'mermaid.min.js'].includes(name)) {
            response.writeHead(404).end();
            return;
        }
        response.setHeader('Content-Type', name.endsWith('.js') ? 'application/javascript' : 'text/html');
        response.end(fs.readFileSync(path.join(assets, name)));
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    url = `http://127.0.0.1:${server.address().port}/preview.html`;
    browser = await chromium.launch({ headless: true });
});
after(async () => {
    if (browser) await browser.close();
    if (server) await new Promise(resolve => server.close(resolve));
});

async function openPage(context, viewport) {
    const page = await browser.newPage({ viewport, hasTouch: true });
    context.after(async () => {
        try {
            fs.mkdirSync(artifacts, { recursive: true });
            const name = context.name.replace(/[^a-z0-9_-]/gi, '-');
            await page.screenshot({ path: path.join(artifacts, name + '.png') });
            fs.writeFileSync(path.join(artifacts, name + '.json'), JSON.stringify(await geometry(page), null, 2));
        } finally {
            await page.close();
        }
    });
    await page.goto(url);
    return page;
}

async function render(page, source) {
    await page.evaluate(async code => {
        await window.renderMermaid({ code, errorMessage: 'Diagram error', loadingMessage: 'Loading' });
        const status = document.getElementById('status');
        if (!status.hidden) throw new Error(status.textContent);
    }, source);
}

async function geometry(page) {
    return page.evaluate(() => {
        const rectangle = value => ({ x: value.x, y: value.y, width: value.width, height: value.height });
        const viewport = document.getElementById('viewport').getBoundingClientRect();
        const svg = document.querySelector('#diagram > svg');
        if (!svg) return { status: document.getElementById('status').textContent };
        const bounds = svg.getBBox();
        const matrix = svg.getScreenCTM();
        const first = new DOMPoint(bounds.x, bounds.y).matrixTransform(matrix);
        const last = new DOMPoint(bounds.x + bounds.width, bounds.y + bounds.height).matrixTransform(matrix);
        return {
            viewport: rectangle(viewport),
            graphic: { x: first.x, y: first.y, width: last.x - first.x, height: last.y - first.y },
            viewBox: rectangle(svg.viewBox.baseVal),
            svg: rectangle(svg.getBoundingClientRect()),
            text: svg.textContent,
            nodes: svg.querySelectorAll('.node').length,
        };
    });
}

function assertFits(value) {
    assert.ok(value.graphic, JSON.stringify(value));
    const graphic = value.graphic;
    const viewport = value.viewport;
    assert.ok(Object.values(graphic).every(Number.isFinite), JSON.stringify(value));
    assert.ok(graphic.width > 0 && graphic.height > 0, JSON.stringify(value));
    assert.ok(Math.max(graphic.width / viewport.width, graphic.height / viewport.height) > .6, JSON.stringify(value));
    assert.ok(graphic.x >= viewport.x - .5, JSON.stringify(value));
    assert.ok(graphic.y >= viewport.y - .5, JSON.stringify(value));
    assert.ok(graphic.x + graphic.width <= viewport.x + viewport.width + .5, JSON.stringify(value));
    assert.ok(graphic.y + graphic.height <= viewport.y + viewport.height + .5, JSON.stringify(value));
    assert.ok(Math.abs(graphic.x + graphic.width / 2 - viewport.x - viewport.width / 2) < 1, JSON.stringify(value));
    assert.ok(Math.abs(graphic.y + graphic.height / 2 - viewport.y - viewport.height / 2) < 1, JSON.stringify(value));
}

// 每一种图表都检查实际绘制范围，避免存在 SVG 却仅露出一个节点边框的情况。
for (const [name, source] of Object.entries(fixtures)) {
    for (const viewport of [{ width: 380, height: 300 }, { width: 380, height: 880 }]) {
        test(`${name} fits ${viewport.width}x${viewport.height}`, async context => {
            const page = await openPage(context, viewport);
            await render(page, source);
            assertFits(await geometry(page));
        });
    }
}

test('reset restores all content after zoom pan and viewport resize', async context => {
    const page = await openPage(context, { width: 380, height: 300 });
    await render(page, fixtures.architecture);
    const initial = await geometry(page);
    await page.locator('#zoom-in').click();
    const enlarged = await geometry(page);
    assert.ok(enlarged.viewBox.width < initial.viewBox.width);
    await page.mouse.move(180, 130);
    await page.mouse.down();
    await page.mouse.move(240, 170, { steps: 5 });
    await page.mouse.up();
    assert.notEqual((await geometry(page)).viewBox.x, enlarged.viewBox.x);
    await page.locator('#fit').click();
    assertFits(await geometry(page));
    await page.setViewportSize({ width: 800, height: 380 });
    assertFits(await geometry(page));
    await page.setViewportSize({ width: 380, height: 880 });
    assertFits(await geometry(page));
});

test('tall graph remains complete at initial fit', async context => {
    const page = await openPage(context, { width: 320, height: 300 });
    const source = 'flowchart TD\n' + Array.from({ length: 25 }, (_, index) => `N${index} --> N${index + 1}`).join('\n');
    await render(page, source);
    const value = await geometry(page);
    assert.equal(value.nodes, 26);
    assertFits(value);
});

test('real content bounds replace a clipped upstream viewBox', async context => {
    const page = await openPage(context, { width: 380, height: 300 });
    await page.evaluate(() => {
        const original = mermaid.render.bind(mermaid);
        mermaid.render = async (...args) => {
            const result = await original(...args);
            const document = new DOMParser().parseFromString(result.svg, 'image/svg+xml');
            document.documentElement.setAttribute('viewBox', '0 0 5000 1');
            result.svg = new XMLSerializer().serializeToString(document);
            return result;
        };
    });
    await render(page, fixtures.flowchart);
    const value = await geometry(page);
    assert.equal(value.nodes, 3);
    assertFits(value);
});

test('zero size viewport waits for actual layout', async context => {
    const page = await openPage(context, { width: 380, height: 300 });
    await page.evaluate(code => {
        document.getElementById('viewport').style.display = 'none';
        window.pendingMermaid = window.renderMermaid({ code, errorMessage: 'Diagram error', loadingMessage: 'Loading' });
    }, fixtures.flowchart);
    await page.evaluate(() => new Promise(requestAnimationFrame));
    assert.equal(await page.locator('#diagram > svg').count(), 0);
    await page.evaluate(() => { document.getElementById('viewport').style.display = ''; });
    await page.evaluate(() => window.pendingMermaid);
    assertFits(await geometry(page));
});

test('invalid source shows an error and edited source renders fully', async context => {
    const page = await openPage(context, { width: 380, height: 300 });
    await page.evaluate(async () => {
        await window.renderMermaid({ code: 'flowchart LR\nA -->', errorMessage: 'Diagram error', loadingMessage: 'Loading' });
    });
    assert.equal(await page.locator('#status').getAttribute('class'), 'error');
    await render(page, fixtures.flowchart);
    assertFits(await geometry(page));
});

test('wide graph remains complete at initial fit', async context => {
    const page = await openPage(context, { width: 320, height: 300 });
    const source = 'flowchart LR\n' + Array.from({ length: 25 }, (_, index) => `N${index} --> N${index + 1}`).join('\n');
    await render(page, source);
    const value = await geometry(page);
    assert.equal(value.nodes, 26);
    assertFits(value);
});

test('pinch zoom uses native touch input and reset restores the whole graph', async context => {
    const page = await openPage(context, { width: 380, height: 300 });
    await render(page, fixtures.flowchart);
    const initial = await geometry(page);
    const session = await page.context().newCDPSession(page);
    await session.send('Input.dispatchTouchEvent', {
        type: 'touchStart',
        touchPoints: [{ id: 1, x: 90, y: 110 }, { id: 2, x: 210, y: 110 }],
    });
    await session.send('Input.dispatchTouchEvent', {
        type: 'touchMove',
        touchPoints: [{ id: 1, x: 60, y: 130 }, { id: 2, x: 240, y: 130 }],
    });
    await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await page.waitForFunction(width => document.querySelector('#diagram > svg').viewBox.baseVal.width < width * .8, initial.viewBox.width);
    await session.detach();
    await page.locator('#fit').click();
    assertFits(await geometry(page));
});

test('wheel zoom preserves the point under the cursor', async context => {
    const page = await openPage(context, { width: 380, height: 300 });
    await render(page, fixtures.flowchart);
    const initial = await geometry(page);
    const anchor = () => page.evaluate(() => {
        const svg = document.querySelector('#diagram > svg');
        const point = new DOMPoint(140, 100).matrixTransform(svg.getScreenCTM().inverse());
        return { x: point.x, y: point.y };
    });
    const before = await anchor();
    await page.mouse.move(140, 100);
    await page.mouse.wheel(0, -120);
    await page.waitForFunction(width => document.querySelector('#diagram > svg').viewBox.baseVal.width < width, initial.viewBox.width);
    const after = await anchor();
    assert.ok(Math.abs(before.x - after.x) < .01);
    assert.ok(Math.abs(before.y - after.y) < .01);
    await page.locator('#fit').click();
    assertFits(await geometry(page));
});

test('a replacement source cancels an obsolete wait for viewport layout', async context => {
    const page = await openPage(context, { width: 380, height: 300 });
    await page.evaluate(async () => {
        document.getElementById('viewport').style.display = 'none';
        const first = window.renderMermaid({ code: 'flowchart LR\nOld --> Stale', errorMessage: 'Diagram error', loadingMessage: 'Loading' });
        window.pendingMermaid = window.renderMermaid({ code: 'flowchart LR\nNew --> Latest', errorMessage: 'Diagram error', loadingMessage: 'Loading' });
        await first;
    });
    assert.equal(await page.locator('#diagram > svg').count(), 0);
    await page.evaluate(async () => {
        document.getElementById('viewport').style.display = '';
        await window.pendingMermaid;
    });
    const value = await geometry(page);
    assert.ok(value.text.includes('Latest'));
    assert.ok(!value.text.includes('Stale'));
    assertFits(value);
});

test('new source supersedes an outstanding render request', async context => {
    const page = await openPage(context, { width: 380, height: 300 });
    await page.evaluate(() => {
        const original = mermaid.render.bind(mermaid);
        let release;
        const pending = new Promise(resolve => { release = resolve; });
        window.releaseOldRender = release;
        mermaid.render = async (id, code) => {
            const result = await original(id, code);
            if (code.includes('Stale')) {
                window.oldRenderReady = true;
                await pending;
            }
            return result;
        };
        window.oldRender = window.renderMermaid({ code: 'flowchart LR\nOld --> Stale', errorMessage: 'Diagram error', loadingMessage: 'Loading' });
    });
    await page.waitForFunction(() => window.oldRenderReady);
    await render(page, 'flowchart LR\nNew --> Latest');
    await page.evaluate(async () => {
        window.releaseOldRender();
        await window.oldRender;
    });
    const value = await geometry(page);
    assert.ok(value.text.includes('Latest'));
    assert.ok(!value.text.includes('Stale'));
    assertFits(value);
});
