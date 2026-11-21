const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname,
  '../../app/src/main/java/com/ai/assistance/operit/core/tools/builtin/web/WebVisitBlocking.kt'), 'utf8');
const match = source.match(/WEB_VISIT_PAGE_CHECK_SCRIPT = """([\s\S]*?)"""\.trimIndent\(\)/);
assert.ok(match, '必须检查实际注入 WebView 的页面检查脚本');
const script = new vm.Script(match[1]);

function inspect({ title = 'Normal page', text = 'Readable article', options, selectors = [] } = {}) {
  const document = {
    title,
    body: { innerText: text },
    querySelector: query => query.split(',').some(selector => selectors.includes(selector.trim())) ? {} : null,
  };
  return JSON.parse(script.runInNewContext({ document, window: { _cf_chl_opt: options } }));
}

test('Cloudflare 的托管与交互验证在正文加载前即可识别', () => {
  for (const cType of ['managed', 'interactive', 'non-interactive']) {
    assert.equal(inspect({ text: '', options: { cType } }).cloudflareChallenge, true);
  }
});

test('带有挑战地址的主文档验证表单可以提前识别', () => {
  assert.equal(inspect({ selectors: ['form#challenge-form[action*="__cf_chl"]'] }).cloudflareChallenge, true);
});

test('旧版挑战页需要阶段节点、脚本和验证标题共同支持', () => {
  const selectors = ['#challenge-stage', 'script[src*="/cdn-cgi/challenge-platform/"]'];
  assert.equal(inspect({ title: 'Just a moment...', selectors }).cloudflareChallenge, true);
  assert.equal(inspect({ title: 'Cloudflare documentation', selectors }).cloudflareChallenge, false);
});

test('普通 JavaScript Detections 脚本不能让正常页面成为挑战页', () => {
  assert.equal(inspect({ selectors: ['script[src*="/cdn-cgi/challenge-platform/"]'] }).cloudflareChallenge, false);
});

test('登录表单嵌入 Turnstile 不等于整个页面被 Cloudflare 拦截', () => {
  const result = inspect({ title: 'Sign in', selectors: ['.cf-turnstile', 'iframe[src*="challenges.cloudflare.com"]'] });
  assert.equal(result.cloudflareChallenge, false);
  assert.equal(result.cloudflareBlocked, false);
});

test('Cloudflare 原生拒绝页使用错误结构和拒绝文字共同判断', () => {
  const result = inspect({ title: 'Attention Required! | Cloudflare',
    text: 'Sorry, you have been blocked. Cloudflare Ray ID: abc', selectors: ['#cf-error-details'] });
  assert.equal(result.cloudflareBlocked, true);
  assert.equal(result.cloudflareChallenge, false);
});

test('文章引用挑战变量和拒绝文字不能作为拦截证据', () => {
  const result = inspect({ title: 'How Cloudflare works', text: 'window._cf_chl_opt; Sorry, you have been blocked.' });
  assert.equal(result.cloudflareChallenge, false);
  assert.equal(result.cloudflareBlocked, false);
});

test('尚无正文的文档可以检查，中文验证标题也能识别', () => {
  const value = JSON.parse(script.runInNewContext({ document: { title: '', body: null, querySelector: () => null }, window: {} }));
  assert.equal(value.text, '');
  assert.equal(value.cloudflareChallenge, false);
  assert.equal(inspect({ title: '正在检查您的浏览器', selectors:
    ['#challenge-running', 'script[src*="/cdn-cgi/challenge-platform/"]'] }).cloudflareChallenge, true);
});