const fs=require('node:fs');
const assert=require('node:assert/strict');
const {JSDOM,VirtualConsole}=require('jsdom');
const cssTree=require('css-tree');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
const results=[];
const errors=[];
const intervals=new Map();
const blobs=[];
let serial=0;
let width=1440;
const output=new VirtualConsole();
output.on('jsdomError',error=>errors.push(error.message));
const dom=new JSDOM(html,{url:'file:///prototype/index.html',runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:output,beforeParse(window){
  // 只补充 DOM 模拟器缺少的浏览器接口，不把这些桩当作原生行为验证。
  window.matchMedia=query=>({matches:width<=Number(query.match(/max-width:(\d+)px/)[1]),media:query});
  window.setInterval=callback=>{const id=++serial;intervals.set(id,callback);return id;};
  window.clearInterval=id=>intervals.delete(id);
  window.HTMLElement.prototype.scrollIntoView=function(){};
  window.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};
  window.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');this.dispatchEvent(new window.Event('close'));};
  window.Blob=global.Blob;
  window.URL.createObjectURL=blob=>{blobs.push(blob);return 'blob:prototype-'+blobs.length;};
  window.URL.revokeObjectURL=function(){};
  window.HTMLAnchorElement.prototype.click=function(){window.generatedFilename=this.download;};
}});
const {window}=dom;
const doc=window.document;
const one=selector=>{const node=doc.querySelector(selector);assert(node,'缺少节点 '+selector);return node;};
const count=selector=>doc.querySelectorAll(selector).length;
const text=selector=>one(selector).textContent;
const click=selector=>one(selector).click();
const action=name=>click('[data-action="'+name+'"]');
function input(selector,value){const node=one(selector);node.value=value;node.dispatchEvent(new window.Event('input',{bubbles:true}));}
function change(selector,value){const node=one(selector);node.value=value;node.dispatchEvent(new window.Event('change',{bubbles:true}));}
function submit(selector){one(selector).dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));}
function navigate(page){click('#sidebar [data-page="'+page+'"]');}
function control(name){action('controls');action(name);}
function pause(){if(doc.querySelector('[data-action="toggle-play"][aria-label="暂停演示"]'))action('toggle-play');}
function check(name,fn){fn();assert.deepEqual(errors,[]);results.push(name);console.log('通过：'+name);}
(async()=>{
  try{
    check('页面脚本可执行、初始计划与唯一 DOM ID',()=>{
      assert.equal(count('.plan-item'),3);
      const ids=[...doc.querySelectorAll('[id]')].map(el=>el.id);
      assert.equal(new Set(ids).size,ids.length);
    });
    check('CSS 完整解析与离线资源声明',()=>{
      cssTree.parse(text('style'),{parseValue:true,onParseError:error=>{throw error;}});
      assert.equal(count('script[src],link[href],img[src]'),0);
      assert(one('meta[http-equiv="Content-Security-Policy"]').content.includes("connect-src 'none'"));
    });
    check('主聊天开始前补充进入三项任务简报',()=>{
      input('#mainInput','先关注手机交互');submit('#mainComposer');action('start');pause();
      click('#task-chat-research');click('[data-tab="conversation"]');
      assert(text('#inspectorContent').includes('先关注手机交互'));
    });
    check('两个运行名额与一项排队',()=>{
      assert.equal(count('#pageBody .agent-event.running'),2);
      assert.equal(count('#pageBody .agent-event.queued'),1);
    });
    check('自动推进时补充草稿和主输入保留',()=>{
      action('steer-open');input('#steerInput','重点 "手机" <Q> & 路径');input('#mainInput','还未发送的草稿');
      action('toggle-play');[...intervals.values()].forEach(callback=>callback());pause();
      assert.equal(one('#steerInput').value,'重点 "手机" <Q> & 路径');
      assert.equal(one('#mainInput').value,'还未发送的草稿');
    });
    check('补充先排队再在下一轮读取，特殊字符显示为文本',()=>{
      submit('#steerForm');assert(text('#inspectorContent').includes('等待下一轮读取'));action('next');
      assert(text('#inspectorContent').includes('已读取'));assert(text('#inspectorContent').includes('重点 "手机" <Q> & 路径'));
      assert.equal(count('#inspectorContent q'),0);
    });
    check('单项停止释放名额且不停止其它任务',()=>{
      action('stop');action('confirm-stop');
      assert(one('#task-chat-research').classList.contains('cancelled'));
      assert(one('#task-chat-review').classList.contains('running'));
      assert.equal(count('#pageBody .agent-event.running'),2);
    });
    check('停止后恢复为新的执行，名额不足时排队',()=>{
      action('resume');pause();assert(one('#task-chat-research').classList.contains('queued'));
      click('[data-tab="overview"]');assert(text('#inspectorContent').includes('第 2 次执行'));
    });
    check('单项失败保留原因、其它任务仍可执行',()=>{
      control('fail-demo');assert(one('#task-chat-design').classList.contains('failed'));
      assert(one('#task-chat-research').classList.contains('running'));
      click('#task-chat-design');assert(text('#inspectorContent').includes('模型请求未能完成'));
    });
    check('模拟中断保留未消费消息并显示中断状态',()=>{
      click('#task-chat-research');action('steer-open');input('#steerInput','中断后仍需处理');submit('#steerForm');control('interrupt-demo');
      assert.equal(count('#pageBody .agent-event.interrupted'),2);
      click('[data-tab="conversation"]');assert(text('#inspectorContent').includes('等待下一轮读取'));
    });
    check('中断后沿用历史、增加执行次数并读取排队消息',()=>{
      action('resume');pause();action('next');assert(text('#inspectorContent').includes('中断后仍需处理'));assert(text('#inspectorContent').includes('已读取'));
      click('[data-tab="overview"]');assert(text('#inspectorContent').includes('第 3 次执行'));
    });
    check('报告送达与主代理汇总区分',()=>{
      control('complete-demo');assert(text('#task-chat-research').includes('等待主代理汇总'));action('summarize');
      assert(text('.summary-answer').includes('已汇总 1 份报告'));assert(text('#task-chat-research').includes('已采用本次报告'));
    });
    check('后续执行保留旧汇总，使用同一子会话与新目标',()=>{
      click('#task-chat-research');action('resume');input('#followupInput','整理为五条 "验收" <重点>');submit('#followupForm');pause();
      assert(text('.summary-answer').includes('已汇总 1 份报告'));assert(text('#inspectorContent').includes('第 4 次执行'));
      control('complete-demo');assert(text('#inspectorContent').includes('整理为五条 "验收" <重点>'));action('summarize');
      assert(text('.summary-answer').includes('已汇总 2 份报告'));
    });
    action('report');action('download');action('close-modal');
    const report=await blobs.at(-1).text();
    check('Markdown 产物包含历史与后续报告、演示标记和文件名',()=>{
      assert(report.includes('第 3 次执行')&&report.includes('第 4 次执行'));assert(report.includes('预设演示报告'));assert(report.includes('整理为五条 "验收" <重点>'));
      assert.equal(window.generatedFilename,'Operit子代理-演示方案.md');
    });
    check('管理页归档、状态筛选与恢复记录',()=>{
      navigate('tasks');click('[data-action="archive"][data-task="research"]');assert.equal(count('#task-manager-research'),0);
      change('#taskFilter','archived');assert.equal(count('#task-manager-research'),1);
      click('[data-action="archive"][data-task="research"]');change('#taskFilter','all');assert.equal(count('#task-manager-research'),1);
    });
    check('搜索引号、中文与空结果，系统后台分组独立',()=>{
      input('#taskSearch','手机');assert.equal(count('.manager-list .agent-event'),1);
      input('#taskSearch','"<空结果>');assert(text('.empty-state').includes('没有匹配'));assert.equal(one('#taskSearch').value,'"<空结果>');
      action('clear-filter');click('[data-kind="system"]');assert(text('.empty-state').includes('系统后台'));
    });
    check('角色编辑转义与模型配置保存',()=>{
      navigate('profiles');click('[data-action="edit-role"][data-role="research"]');input('#roleName','研究员 "A" <Q>');input('#roleDescription','只读研究 & 核对');change('#roleModel','轻量模型 · 演示');submit('#roleForm');
      click('[data-action="edit-role"][data-role="research"]');assert.equal(one('#roleName').value,'研究员 "A" <Q>');action('close-modal');
    });
    check('新增任务使用新配置，既有任务保留原模型',()=>{
      navigate('tasks');action('new-task');input('#newTaskTitle','新任务 "标题" <Q>');input('#newTaskGoal','只读检查 & 整理');change('#newTaskRole','research');submit('#newTaskForm');
      click('#task-manager-custom-1');assert(text('#inspectorContent').includes('轻量模型 · 演示'));assert(text('#inspectorContent').includes('新任务 "标题" <Q>'));assert.equal(count('#inspectorContent q'),0);
      change('#taskSwitcher','research');assert(text('#inspectorContent').includes('继承主模型'));
    });
    check('页面内重置保留角色和主题、清空演示任务',()=>{
      action('theme');navigate('chat');action('reset');action('confirm-reset');
      assert.equal(doc.documentElement.dataset.theme,'light');assert.equal(count('.plan-item'),3);
      navigate('profiles');assert(text('.profiles-list').includes('研究员 "A" <Q>'));
    });
    check('手机详情状态、背景 inert 属性与兄弟任务切换',()=>{
      width=390;window.dispatchEvent(new window.Event('resize'));navigate('chat');action('start');pause();click('#task-chat-research');
      assert.equal(one('#inspector').getAttribute('aria-modal'),'true');assert.equal(one('#center').inert,true);
      change('#taskSwitcher','design');assert(text('#inspectorContent').includes('梳理手机上的交互路径'));action('close-detail');assert.equal(one('#center').inert,false);
    });
    check('手机抽屉开关及导航状态',()=>{
      action('menu');assert(one('#sidebar').classList.contains('open'));assert.equal(one('#center').inert,true);
      navigate('tasks');assert(!one('#sidebar').classList.contains('open'));assert.equal(one('#center').inert,false);
    });
    check('组合输入期间保留输入节点，提交后刷新搜索结果',()=>{
      navigate('chat');action('toggle-play');navigate('tasks');const node=one('#taskSearch');
      node.dispatchEvent(new window.CompositionEvent('compositionstart',{bubbles:true}));node.value='手机';node.dispatchEvent(new window.InputEvent('input',{bubbles:true,isComposing:true,data:'手机'}));
      [...intervals.values()].forEach(callback=>callback());assert.equal(one('#taskSearch'),node);
      node.dispatchEvent(new window.CompositionEvent('compositionend',{bubbles:true,data:'手机'}));assert.equal(one('#taskSearch').value,'手机');assert.equal(count('.manager-list .agent-event'),1);
    });
    check('界面全流程未产生 DOM 脚本异常',()=>assert.deepEqual(errors,[]));
    const data={passed:results.length,checks:results,errors,environment:'jsdom DOM 交互模拟；未验证浏览器布局、焦点、触摸和原生下载'};
    console.log(JSON.stringify(data));
  }catch(error){console.error(error.stack);process.exitCode=1;}
  finally{window.close();}
})();
