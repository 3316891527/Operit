/* METADATA
{
  "name": "a2ui_guide",
  "display_name": {
    "zh": "A2UI 使用引导",
    "en": "A2UI Guide"
  },
  "description": {
    "zh": "可选的原生 A2UI 使用说明与 Express 示例，只返回文本，不生成或更新卡片。",
    "en": "Optional native A2UI instructions and Express examples. Returns text only; never generates or updates cards."
  },
  "enabledByDefault": false,
  "category": "Development",
  "tools": [
    {
      "name": "read_guide",
      "description": {
        "zh": "读取原生 A2UI 工具的可选使用说明。",
        "en": "Read optional usage instructions for native A2UI tools."
      },
      "parameters": []
    },
    {
      "name": "read_example",
      "description": {
        "zh": "读取 Express 示例源码，不编译、不生成卡片。",
        "en": "Read Express example source without compiling or creating a card."
      },
      "parameters": [
        {
          "name": "name",
          "type": "string",
          "required": true,
          "description": {
            "zh": "示例名称，例如 calculator、shopping_list、flowchart 或 form。",
            "en": "Example name such as calculator, shopping_list, flowchart or form."
          }
        }
      ]
    }
  ]
}
*/

// 仅返回使用说明和示例文本，生成与更新由应用的原生工具负责。
const guide = [
  "A2UI 原生工具使用说明",
  "这个包仅提供说明和示例。即使没有导入或启用它，应用内的原生 A2UI 工具仍然可用。是否使用卡片由当前任务决定，不要求每次回答先加载本包。",
  "原生工具：a2ui_catalog 查询组件、函数和参数；a2ui_icons 查询图标；a2ui_prepare(source, live=false) 编译 Express 并返回本轮卡片引用；a2ui_revise(reference, changes, remove=[]) 生成保留原卡片的新修订；a2ui_patch_data(reference, expected_version, data) 更新 live 卡片数据。",
  "这些工具直接以原生工具名调用，不经过 package_proxy，也不需要 use_package。它们沿用工具权限和角色卡可见性；生成、修订和更新必须处于真实的活动聊天请求中，不传入聊天 ID 或请求 ID。",
  "简例：root=Column(children=[Heading(text=\"标题\"),Text(text=\"内容\")])。Express 使用 name=value 参数；组件和函数的精确签名通过 a2ui_catalog 查询。模型可使用原生编译器返回的位置和错误信息修改源码。",
  "生成流程：调用 a2ui_prepare 编译并安排展示，宿主在本轮正常结束时自动把尚未展示的真实卡片加入正文，不必重写 JSON。也可在正文用 a2ui-ref 围栏输出 card-1 这样的本轮引用来指定位置，宿主展开后不会重复追加。不要把历史卡片摘要当作展示语法，引用不能跨请求复用。",
  "直接输出完整、有效的 a2ui v0.9 围栏也可由原生渲染器识别。网上的 A2UI 示例可能采用其他协议版本或组件目录；应遵循本应用的原生目录，不将通用示例视为已经兼容。",
  "界面中的本地输入、选择和试算不会自动发起模型请求。AI 提交和工具操作需要用户确认，并沿用当前聊天、候选回复和权限检查。编辑卡片不会消耗聊天输入框的草稿。",
  "live=true 必须在 source 中初始化 $/agent 对象，例如 $/agent={progress:10}，并把模型更新的数据绑定到 $/agent/progress；仅声明 $/progress=10 不符合 live 合同。更新时 data 直接传 {progress:50}，替换整个 $/agent 对象，不再包裹 agent 键。expected_version 初始为 0，每次成功后使用返回的递增版本。",
  "Progress.number 使用 0～1 的比例；数据为 0～100 时可写 number=divide(a=$/agent/progress,b=100)。live_progress 是当前请求内更新的专用示例，progress 是用户本地勾选的进度示例。",
  "模型数据更新不覆盖用户已经修改的输入状态；$/agent 属于模型，用户输入控件应绑定到其他路径。请求结束后保留最终快照，后续请求不能复用旧引用继续更新。修订返回新引用，原卡片和状态保留。",
  "HTML 卡片通过 Express 的 Html 组件描述并交给 a2ui_prepare；宿主只支持受限的离线 HTML/CSS，作者脚本和联网不受支持。",
  "示例包括 shopping_list、trip_budget、filtered_chart、calculator、cost_calculator、charts、form、info、stats、progress、live_progress、responsive、data、vector、repeat、curves、interactive_flow、p2_controls、p2_lists、flow_steps、flowchart、data_story、mixed_branch、profile、backgrounds、surfaces。read_example 只返回示例源码；示例数据不能当作已查询到的事实。"
].join("\n\n");

function example(name) {
    if (name === "shopping_list") return String.raw`
$/form = {people:2}
$/ingredients = [{id:"rice",name:"大米",perPerson:150,unit:"克"},{id:"egg",name:"鸡蛋",perPerson:1,unit:"个"},{id:"greens",name:"青菜",perPerson:200,unit:"克"}]
root = Card(child=Column(children=[intro,people,portions,ingredients,note]))
intro = Header(text="备餐购物清单",label="演示份量 · 按人数调整")
people = Slider(label="用餐人数",min=1,max=8,steps=6,number=$/form/people)
portions = Text(text=concat(values=["当前准备 ",formatNumber(value=$/form/people,decimals=0)," 人份；勾选已备齐的食材。"]))
ingredients = Repeat(items=$/ingredients,key="id",child=ingredient)
ingredient = CheckBox(label=concat(values=[item(field="name")," · ",formatNumber(value=multiply(a=item(field="perPerson"),b=$/form/people),decimals=0)," ",item(field="unit")]),value=item_state(field="done",default=false))
note = Text(text="人数变化只调整数量，已勾选状态保留；增加人数后请重新核对是否备齐。",variant="caption")
`;
    if (name === "trip_budget") return String.raw`
$/form = {people:2,nights:2,rooms:1}
root = Card(child=Column(children=[intro,inputs,results,note]))
intro = Header(text="出行费用试算",label="演示价格 · 交通每人 ¥120，住宿每间每晚 ¥280")
inputs = Column(children=[people,nights,rooms])
people = Slider(label="出行人数",min=1,max=6,steps=4,number=$/form/people)
nights = Slider(label="住宿晚数",min=1,max=7,steps=5,number=$/form/nights)
rooms = Slider(label="房间数",min=1,max=3,steps=1,number=$/form/rooms)
results = Grid(minItemWidth=160,children=[total,perPerson])
total = Display(label="合计（元）",text=formatNumber(value=add(a=multiply(a=$/form/people,b=120),b=multiply(a=multiply(a=$/form/nights,b=$/form/rooms),b=280)),decimals=2))
perPerson = Display(label="人均（元）",text=formatNumber(value=divide(a=add(a=multiply(a=$/form/people,b=120),b=multiply(a=multiply(a=$/form/nights,b=$/form/rooms),b=280)),b=max(a=$/form/people,b=1)),decimals=2))
note = Text(text="只估算交通和住宿，不含餐饮等费用；这里不会预订或付款。",variant="caption")
`;
    if (name === "filtered_chart") return String.raw`
$/filter = {region:"北区"}
$/sales = [{region:"北区",month:"一月",amount:12},{region:"北区",month:"二月",amount:18},{region:"北区",month:"三月",amount:15},{region:"南区",month:"一月",amount:9},{region:"南区",month:"二月",amount:14},{region:"南区",month:"三月",amount:24}]
root = Card(child=Column(children=[intro,region,total,chart,note]))
intro = Header(text="地区销售趋势",label="演示数据 · 单位：万元")
region = Segmented(label="地区",options=["北区","南区"],value=$/filter/region)
total = Display(label=concat(values=[$/filter/region," · 三个月合计（万元）"]),text=formatNumber(value=arraySum(items=arrayFilter(items=$/sales,field="region",operator="eq",value=$/filter/region),field="amount"),decimals=0))
chart = Chart(label=concat(values=[$/filter/region,"月度销售额 · 演示数据"]),type="bar",labels=arrayMap(items=arrayFilter(items=$/sales,field="region",operator="eq",value=$/filter/region),field="month"),series=[{label:"销售额（万元）",values:arrayMap(items=arrayFilter(items=$/sales,field="region",operator="eq",value=$/filter/region),field="amount")}])
note = Text(text="切换地区会同步更新图表和合计，直接在本地完成。",variant="caption")
`;
    if (name === "surfaces") {
        const html = `<div class="demo">
<input class="choice" type="radio" name="scene" id="aurora" checked><input class="choice" type="radio" name="scene" id="nebula"><input class="choice" type="radio" name="scene" id="cyber"><input class="choice" type="radio" name="scene" id="soft">
<nav class="tabs"><label for="aurora">极光</label><label for="nebula">星云</label><label for="cyber">赛博</label><label for="soft">柔光</label></nav>
<section class="art"><div class="glow"></div><div class="texture"></div><div class="content">
<header><span class="dot"></span> OPERIT DESIGN LAB <span class="star">✦</span></header>
<p class="eyebrow">DYNAMIC SURFACES / 2026</p><h1>超越纯色的<br><span>动态背景艺术。</span></h1>
<p class="intro">光影、渐变与纹理交织，让每一张卡片拥有自己的空间感。</p>
<section class="glass"><div class="live">◇ LIVE PREVIEW <small>● ACTIVE</small></div><div class="stats"><div><strong>04</strong><span>背景风格</span></div><div><strong>CSS</strong><span>实时绘制</span></div><div><strong>0</strong><span>外部图片</span></div></div><div class="track"><i></i></div></section>
</div></section></div>`;
        const css = `.demo{width:100%}.choice{position:absolute;width:1px;height:1px;opacity:0}.tabs{display:flex;gap:6px;margin:0 0 14px}.tabs label{flex:1;text-align:center;padding:9px 2px;border-radius:24px;color:var(--operit-text);cursor:pointer}
#aurora:checked~.tabs [for=aurora],#nebula:checked~.tabs [for=nebula],#cyber:checked~.tabs [for=cyber],#soft:checked~.tabs [for=soft]{background:var(--operit-surface);box-shadow:inset 0 0 0 1px currentColor}
.choice:focus-visible~.tabs{outline:2px solid var(--operit-accent)}
.art{--fg:#f4f7ff;--muted:#d8e3f6;--accent:#aff8ef;--glass:#ffffff12;position:relative;overflow:hidden;isolation:isolate;border-radius:26px;background:radial-gradient(ellipse at 4% 0%,#2a838e,transparent 68%),radial-gradient(ellipse at 100% 15%,#4163d5,transparent 70%),linear-gradient(150deg,#12273e,#21173d 75%,#643f91);color:var(--fg)}
.content{position:relative;padding:28px;z-index:2}.glow{position:absolute;inset:-35%;background:conic-gradient(from 30deg,transparent,#44bccc45,transparent,#9e75ff55,transparent);filter:blur(28px);animation:drift 16s ease-in-out infinite alternate;pointer-events:none}.texture{position:absolute;inset:0;pointer-events:none}header{display:flex;align-items:center;gap:9px;font-size:11px;font-weight:700;letter-spacing:2px}.dot{width:7px;height:7px;border-radius:50%;background:var(--accent);box-shadow:0 0 18px var(--accent)}.star{margin-left:auto;font-size:28px;color:var(--accent)}
.eyebrow{margin:42px 0 16px;color:var(--muted);font-size:11px;letter-spacing:3px}h1{font-size:clamp(26px,7vw,40px);line-height:1.3;margin:0;font-weight:750}h1 span{color:var(--accent)}.intro{font-size:15px;color:var(--muted);margin:24px 0 30px;line-height:1.8}.glass{padding:20px;border-radius:22px;background:var(--glass);border:1px solid #ffffff40;backdrop-filter:blur(18px);box-shadow:0 10px 30px #00000010}.live{display:flex;justify-content:space-between;gap:8px;font-size:10px;letter-spacing:1.5px;color:var(--muted)}small{font-size:9px}.stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin:22px 0}.stats strong{display:block;font-size:clamp(23px,6vw,33px);font-weight:650}.stats span{font-size:11px;color:var(--muted)}.track{height:4px;background:#ffffff24;border-radius:8px;overflow:hidden}.track i{display:block;width:78%;height:100%;background:linear-gradient(90deg,var(--accent),#af90ed);border-radius:8px}
#nebula:checked~.art{--accent:#f2c6f7;background:radial-gradient(ellipse at 15% 30%,#5d316f,transparent 70%),radial-gradient(ellipse at 100% 80%,#383fa0,transparent 65%),#111329}
#nebula:checked~.art .texture{background-image:radial-gradient(#ffffff70 .7px,transparent 1px);background-size:23px 29px}
#cyber:checked~.art{--accent:#6effd4;--muted:#c4e4e7;background:radial-gradient(ellipse at 80% 10%,#263c70,transparent 75%),#091c25}
#cyber:checked~.art .texture{background-image:linear-gradient(#5ef9d518 1px,transparent 1px),linear-gradient(90deg,#5ef9d518 1px,transparent 1px);background-size:27px 27px;transform:perspective(300px) rotateX(16deg) scale(1.15);transform-origin:bottom}
#soft:checked~.art{--fg:#252c40;--muted:#47536b;--accent:#624487;--glass:#ffffff66;background:radial-gradient(ellipse at 0 0,#f1d8e5,transparent 65%),radial-gradient(ellipse at 100% 0,#cbd9fa,transparent 68%),radial-gradient(ellipse at 50% 100%,#c6e9df,transparent 75%),#f6f5ee}
#soft:checked~.art .texture{background:radial-gradient(#55556612 .7px,transparent 1px);background-size:7px 7px}#soft:checked~.art .glow{opacity:.18}
@keyframes drift{to{transform:translate(6%,4%) rotate(25deg)}}@media(prefers-reduced-motion:reduce){.glow{animation:none}}@media(max-width:320px){.content{padding:20px}.glass{padding:15px}.live{letter-spacing:0}.eyebrow{margin-top:28px}}`;
        return `root=Html(label="可切换的四种动态背景",html=${JSON.stringify(html)},css=${JSON.stringify(css)},maxHeight=900)`;
    }
    if (name === "backgrounds") return String.raw`
root=Grid(minItemWidth=150,children=[light,blue,green,dark])
light=Panel(background="light",radius="lg",child=Column(gap="md",children=[Icon(name="lucide:layers",size=32),Heading(text="浅色背景"),RichText(runs=[{text:"柔和的次级表面",tone:"muted"}])]))
blue=Panel(background="blue",radius="lg",child=Column(gap="md",children=[Icon(name="lucide:droplets",tone="info",size=32),Heading(text="蓝色背景"),RichText(runs=[{text:"淡蓝色卡片",tone:"muted"}])]))
green=Panel(background="green",radius="lg",child=Column(gap="md",children=[Icon(name="lucide:leaf",tone="success",size=32),Heading(text="绿色背景"),RichText(runs=[{text:"柔和的绿色",tone:"muted"}])]))
dark=Panel(background="dark",radius="lg",child=Column(gap="md",children=[Icon(name="lucide:moon",tone="info",size=32),Heading(text="深色背景"),RichText(runs=[{text:"深蓝灰色卡片",tone:"muted"}])]))`;
    if (name === "profile") {
        const features = [
            ["role", "person", "自由选择角色", "任何角色卡都可以成为伴侣", "选择适合自己的角色与交流方式。"],
            ["memory", "storage", "长期记忆", "持续理解经历与偏好", "在本地展开这一项的说明，再次点击同一项即可收起。"],
            ["companion", "chat", "主动陪伴", "让交流自然地延续", "这里是交互演示，不会启动后台任务或发送模型请求。"],
            ["tasks", "calendar", "自主任务", "计划、提醒与检查", "需要真实执行时，再设置明确的操作入口与确认流程。"],
        ];
        return String.raw`
$/selected=""
root=Panel(border=true,radius="lg",child=Column(gap="md",children=[header,Divider(),Text(text="一个长期陪伴你、了解你、主动帮助你的 AI 助理。"),features,details]))
header=Row(weights=[0,1],gap="md",align="center",children=[logo,Column(gap="xs",children=[Text(text="Orbit",variant="h1"),RichText(runs=[{text:"Your companion, your choice.",tone:"muted"}])])])
logo=Panel(padding="sm",radius="lg",background="inset",child=Vector(label="Orbit 环形标志",width=40,height=40,shapes=[{type:"circle",cx:20,cy:20,r:13,stroke:3},{type:"circle",cx:10,cy:30,r:4,filled:true}]))
features=Grid(minItemWidth=140,children=[${features.map(([id]) => id).join(",")}])
details=Column(children=[${features.map(([id, , title, , detail]) =>
    `Conditional(visible=equals(a=$/selected,b=${q(id)}),children=[Panel(background="inset",child=Column(children=[Heading(text=${q(title)}),Text(text=${q(detail)})]))])`).join(",")}])
${features.map(([id, icon, title, description]) =>
    `${id}=Pressable(label=${q(title)},selected=equals(a=$/selected,b=${q(id)}),action=Event(name="local_action",context={op:"set",path:"/selected",value:if(condition=equals(a=$/selected,b=${q(id)}),then="",else=${q(id)})}),child=Panel(background="inset",child=Column(gap="sm",children=[Icon(name=${q(icon)}),Heading(text=${q(title)}),Text(text=${q(description)})])))`).join("\n")}
`;
    }
    if (name === "flow_steps") return String.raw`
root = Flow(label="番茄炒蛋三个阶段",steps=[{title:"准备食材",caption:"2 分钟",icon:"list"},{title:"分别炒制",caption:"4 分钟",icon:"fire"},{title:"混合出锅",caption:"1 分钟",icon:"check",tone:"success"}])`;
    if (name === "flowchart") return String.raw`
root = Column(gap="xs",children=[main,question,split,branches])
main = Flow(label="请求处理：收到请求后校验参数",direction="vertical",steps=[{title:"收到请求",icon:"download"},{title:"校验参数",icon:"search"}])
question = Row(justify="center",children=[Badge(text="参数是否有效？",tone="info")])
split = Vector(label="分成两个分支",width=320,height=36,shapes=[{type:"arrow",x1:160,y1:2,x2:80,y2:34},{type:"arrow",x1:160,y1:2,x2:240,y2:34}])
branches = Grid(columnCount=2,children=[
 Flow(label="有效分支",direction="vertical",steps=[{title:"有效",icon:"check",tone:"success"},{title:"执行业务",icon:"build"},{title:"返回结果",icon:"upload"}]),
 Flow(label="无效分支",direction="vertical",steps=[{title:"无效",icon:"warning",tone:"error"},{title:"返回错误",caption:"附带原因"}])
])`;
    if (name === "data_story") return String.raw`
root = Column(gap="md",children=[
 Grid(minItemWidth=100,children=[Display(label="本周总量",text="9.0M"),Display(label="缓存命中",text="94%"),Display(label="较上周",text="+12%")]),
 Chart(label="每日用量（百万 Token，演示数据）",type="bar",labels=["周一","周二","周三","周四","周五"],series=[{label:"用量",values:[1.2,1.8,1.5,2.1,2.4]}]),
 Table(columns=["模型","占比","平均耗时"],rows=[["主对话","72%","14 秒"],["子代理","21%","38 秒"],["预测","7%","1.2 秒"]])
])`;
    if (name === "mixed_branch") return String.raw`
$/release = {plan:"稳妥"}
root = Column(gap="md",children=[
 Flow(label="发布流程",steps=[{title:"开发",icon:"code"},{title:"审计",icon:"search"},{title:"发布",icon:"upload",tone:"success"}]),
 Segmented(label="选择发布方式",options=["快速","稳妥"],value=$/release/plan),
 Conditional(visible=equals(a=$/release/plan,b="快速"),children=[InfoRow(label="快速发布",text="跳过真机验证，适合文案修改")]),
 Conditional(visible=equals(a=$/release/plan,b="稳妥"),children=[InfoRow(label="稳妥发布",text="真机验证通过后再合并，适合功能改动",tone="success")])
])`;
    if (name === "interactive_flow") return String.raw`
$/flow = {step:0,flavor:"原味"}
$/steps = [
 {id:"step0",order:0,title:"开始",detail:"点击任意步骤查看说明。准备好后，勾选完成并前往下一步。"},
 {id:"step1",order:1,title:"准备食材",detail:"准备番茄、鸡蛋、葱、食用油和适量盐。清洗番茄并切块。"},
 {id:"step2",order:2,title:"打散鸡蛋",detail:"鸡蛋打入碗中，加少量盐搅匀。生熟食材分开放置。"},
 {id:"step3",order:3,title:"炒鸡蛋",detail:"锅热后加油，倒入蛋液，轻推至凝固后盛出备用。"},
 {id:"step4",order:4,title:"炒番茄",detail:"锅中补少量油，放入番茄炒软，按需加一点水。"},
 {id:"step5",order:5,title:"口味选择",detail:"选择甜口或原味。可以随时返回更改，不影响其他步骤记录。"},
 {id:"step6",order:6,title:"混合出锅",detail:"倒回鸡蛋轻轻翻匀，按口味调盐，确认鸡蛋熟透后出锅。"},
 {id:"step7",order:7,title:"结束",detail:"完成！已经完成的步骤仍可点开查看，也可以返回上一步。"}
]
root = Column(children=[Header(text="番茄炒蛋",label="点步骤查看说明，勾选记录进度",status="本地交互"),steps])
steps = Repeat(items=$/steps,key="id",maxItems=8,child=stepRow)
stepRow = Column(gap="xs",children=[node,details,connector])
node = Pressable(label=concat(values=[item(field="title"),if(condition=item_state(field="done",default=false),then="，已完成",else="，未完成")]),selected=equals(a=$/flow/step,b=item(field="order")),child=Header(text=item(field="title"),status=if(condition=item_state(field="done",default=false),then="已完成",else="待完成"),tone=if(condition=item_state(field="done",default=false),then="success",else="neutral")),action=Event(name="local_action",context={op:"set",path:"/flow/step",value:item(field="order")}))
details = Conditional(visible=equals(a=$/flow/step,b=item(field="order")),children=[Text(text=item(field="detail")),branch,CheckBox(label="本步骤已完成",value=item_state(field="done",default=false)),navigation])
branch = Conditional(visible=equals(a=item(field="order"),b=5),children=[Frame(maxWidth=120,child=Vector(label="判断节点：选择口味",width=120,height=64,shapes=[{type:"polygon",points:[[60,2],[118,32],[60,62],[2,32]],tone:"info",filled:true}])),Segmented(label="选择口味",options=["甜口","原味"],value=$/flow/flavor),Text(text=if(condition=equals(a=$/flow/flavor,b="甜口"),then="甜口分支：加入少量糖，先少放，再尝味调整。",else="原味分支：不加糖，保留番茄的酸甜。"))])
connector = Conditional(visible=lessThan(a=item(field="order"),b=7),children=[Vector(label="前往下一步骤",width=320,height=28,shapes=[{type:"arrow",x1:160,y1:2,x2:160,y2:26,tone:if(condition=item_state(field="done",default=false),then="success",else="neutral")}])])
navigation = Row(wrap=true,children=[Button(child=Text(text="上一步"),checks=[{condition:greaterThan(a=$/flow/step,b=0),message:"当前是第一步"}],action=Event(name="local_action",context={op:"add",path:"/flow/step",value:-1})),Button(child=Text(text="下一步"),variant="primary",checks=[{condition:lessThan(a=$/flow/step,b=7),message:"已到最后一步"}],action=Event(name="local_action",context={op:"add",path:"/flow/step",value:1}))])
`;
    if (name === "repeat") return [
        '$/issues = [{id:"login",title:"修复登录",status:"处理中"},{id:"layout",title:"调整排版",status:"待处理"}]',
        '$/query = ""',
        'root = Column(children=[Header(text="任务清单",status="演示数据"),TextField(label="筛选标题",value=$/query),rows,empty])',
        'rows = Repeat(items=arrayFilter(items=$/issues,field="title",operator="contains",value=$/query),key="id",maxItems=20,child=Panel(child=InfoRow(label=item(field="title"),text=item(field="status")),background="inset"))',
        'empty = Conditional(visible=equals(a=arrayLength(items=arrayFilter(items=$/issues,field="title",operator="contains",value=$/query)),b=0),children=[Text(text="没有匹配的任务")])',
    ].join("\n");
    if (name === "curves") return [
        'root = Column(children=[Frame(maxWidth=480,maxHeight=240,aspectRatio=2,child=Vector(label="两条不同形状的曲线",width=320,height=160,shapes=[{type:"quadratic",x1:10,y1:80,cx:160,cy:0,x2:310,y2:80,tone:"accent"},{type:"cubic",x1:10,y1:100,cx1:100,cy1:20,cx2:220,cy2:160,x2:310,y2:100,tone:"success"}]))])',
    ].join("\n");
    if (name === "calculator") {
        const keys = [["AC","AC"],["backspace","⌫"],["sign","±"],["/","÷"],
            ["7","7"],["8","8"],["9","9"],["*","×"],["4","4"],["5","5"],["6","6"],["-","−"],
            ["1","1"],["2","2"],["3","3"],["+","+"],["0","0"],[".","."],["=","="]];
        return [
            '$/calculator = {display:"0",stored:"",op:"",replace:false,error:false}',
            'root = Card(child=Column(children=[Header(text="本地计算器",status="本地计算"),pending,Display(label="结果",text=$/calculator/display,align="end"),error,keys,Text(text="支持小数、退格和连续运算。",variant="caption")]))',
            'pending = Conditional(visible=not(value=equals(a=$/calculator/op,b="")),children=[Text(text=concat(values=[$/calculator/stored," ",$/calculator/op]))])',
            'error = Conditional(visible=$/calculator/error,children=[Text(text="无法计算，请检查除数或清除后重试。")])',
            'keys = Grid(columnCount=4,columnSpans=[1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1],children=[' +
                keys.map(([key,label]) => `Button(child=Text(text=${q(label)}),variant=${q(key === "=" ? "primary" : "default")},action=Event(name="local_action",context={op:"set",path:"/calculator",value:calculatorStep(state=$/calculator,key=${q(key)})}))`).join(",") + "])",
        ].join("\n");
    }
    if (name === "cost_calculator") return String.raw`
$/form = {years:4,discount:false}
root = Card(child=Column(children=[
 Header(text="费用试算",status="演示数据"),
 Display(label="预计费用",text=formatNumber(value=multiply(a=multiply(a=$/form/years,b=11.622222),b=if(condition=$/form/discount,then=0.9,else=1)))),
 Slider(label="购买年限",min=1,max=10,steps=8,number=$/form/years),
 CheckBox(label="模拟九折优惠",value=$/form/discount),
 Text(text="拖动滑块或勾选优惠即可本地重新计算，不会下单或付款。",variant="caption")
]))`;
    if (name === "charts") return 'root = Column(children=[' +
        ["line","bar","pie","scatter"].map((type, i) => 'Chart(label=' + q(["折线图","柱状图","饼图","散点图"][i] + " · 演示数据") +
            ',type=' + q(type) + (type === "scatter"
            ? ',series=[{label:"演示数据",points:[{x:1,y:7},{x:2,y:12},{x:4,y:26}]}])'
            : ',labels=["A","B","C"],series=[{label:"演示数据",values:[32,56,42]}])')).join(",") + "])";
    if (name === "form") return String.raw`
$/form = {name:"",notes:""}
root = Card(child=Form(label="偏好表单 · 演示",children=[
 TextField(label="称呼",value=$/form/name),
 TextArea(label="补充说明",value=$/form/notes),
 Button(child=Text(text="提交给 AI"),variant="primary",action=Event(name="ai_submit",context={form:$/form,required:["/form/name"]}))
]))`;
    if (name === "info") return String.raw`
root = Card(child=Column(children=[
 Header(text="example.com",label="域名状态 · 演示数据",status="活动中",tone="success"),
 Divider(),
 InfoRow(label="到期日期",text="2028/09/29"),
 InfoRow(label="自动续费",text="已开启",tone="success")
]))`;
    if (name === "stats") return String.raw`
root = Column(children=[
 Grid(children=[Display(label="总 Token",text="9.0M"),Display(label="缓存命中率",text="94.2%")]),
 Chart(label="每日 Token 用量",type="bar",labels=["周一","周二","周三","周四","周五"],
 series=[{label:"Token",values:[1200000,1800000,1500000,2100000,2400000]}])
])`;
    if (name === "progress") return String.raw`
$/tasks = {analysis:true,review:true,test:false}
root = Card(child=Column(children=[
 Header(text="代码审查任务",label="演示清单，仅修改当前卡片",status=if(condition=and(a=and(a=$/tasks/analysis,b=$/tasks/review),b=$/tasks/test),then="已完成",else="进行中"),tone=if(condition=and(a=and(a=$/tasks/analysis,b=$/tasks/review),b=$/tasks/test),then="success",else="info")),
 Progress(label="任务进度",number=divide(a=add(a=add(a=if(condition=$/tasks/analysis,then=1,else=0),b=if(condition=$/tasks/review,then=1,else=0)),b=if(condition=$/tasks/test,then=1,else=0)),b=3)),
 CheckBox(label="分析代码",value=$/tasks/analysis),
 CheckBox(label="检查问题",value=$/tasks/review),
 CheckBox(label="运行测试",value=$/tasks/test)
]))`;
    if (name === "live_progress") return String.raw`
$/agent = {progress:10}
root = Card(child=Column(children=[
 Display(label="progress",text=concat(values=[$/agent/progress])),
 Progress(label="实时进度",number=divide(a=$/agent/progress,b=100))
]))`;
    if (name === "responsive") return String.raw`
root = Column(gap="md",children=[
 Row(wrap=true,gap="sm",align="center",children=[Badge(text="Android",tone="info"),Badge(text="本地交互",tone="success"),Badge(text="响应式布局")]),
 Grid(minItemWidth=120,children=[Display(label="已完成",text="12"),Display(label="待处理",text="3"),Display(label="完成率",text="80%")]),
 Panel(background="inset",padding="md",radius="lg",child=Text(text="屏幕变窄或字体变大时，数据面板自动换行。",textAlign="center")),
 Popover(label="这些数字是什么意思？",child=Column(children=[Heading(text="示例说明"),Text(text="这些是演示数据。可以把面板替换为真实任务统计。")]))
])`;
    if (name === "data") return String.raw`
$/items = [{name:"设计",hours:8,row:["设计",8]},{name:"开发",hours:20,row:["开发",20]},{name:"测试",hours:12,row:["测试",12]}]
$/filter = {minimum:10}
$/date = "2026-10-12"
root = Column(gap="md",children=[
 Header(text="工时筛选",status="演示数据"),
 Slider(label="最低工时",min=0,max=24,steps=23,number=$/filter/minimum),
 Table(columns=["任务","工时"],rows=arrayMap(items=arrayFilter(items=$/items,field="hours",operator="gte",value=$/filter/minimum),field="row")),
 Display(label="全部任务工时",text=formatNumber(value=arraySum(items=$/items,field="hours"),decimals=0)),
 InfoRow(label="一周后",text=dateAddDays(date=$/date,days=7)),
 Text(text=arrayJoin(items=arrayMap(items=$/items,field="name"),separator=" · "),variant="caption")
])`;
    if (name === "vector") return String.raw`
root = Column(children=[
 Vector(label="输入经过本地处理，产生结果",width=320,height=150,shapes=[
 {type:"rect",x:5,y:40,width:85,height:65,tone:"info",filled:true},
 {type:"rect",x:120,y:40,width:85,height:65,tone:"success"},
 {type:"circle",cx:275,cy:72,r:34,tone:"accent"},
 {type:"line",x1:90,y1:72,x2:120,y2:72},
 {type:"polyline",points:[[110,65],[120,72],[110,79]]},
 {type:"line",x1:205,y1:72,x2:240,y2:72},
 {type:"text",x:18,y:58,text:"输入",size:18},
 {type:"text",x:129,y:58,text:"处理",size:18},
 {type:"text",x:254,y:58,text:"结果",size:18}
 ]),
 Text(text="矢量图用于解释关系；真实统计数据请使用图表。",variant="caption")
])`;
    if (name === "p2_controls") return String.raw`
$/form={date:"2026-10-12",notes:"",code:"",busy:false,step:0,formula:"\\frac{1}{2}"}
root=Column(children=[
 Header(text="原生控件演示",label="所有输入与切换均在本地"),
 Stepper(label="准备步骤",labels=["填写","检查","完成"],current=$/form/step),
 DatePicker(label="日期",value=$/form/date,minDate="2026-10-01",maxDate="2026-10-31",clearable=true),
 TextField(label="数字编号",value=$/form/code,mask="digits",error=if(condition=equals(a=$/form/code,b=""),then="请输入数字编号",else="")),
 TextArea(label="备注",value=$/form/notes,minLines=2,maxLines=5),
 Switch(label="预览按钮忙碌状态",checked=$/form/busy),
 Button(child=Text(text="保存"),loading=$/form/busy,action=Event(name="local_action",context={op:"save"})),
 Tooltip(label="为什么按钮不能点？",text="忙碌时会显示进度圈并禁止点击。关闭上方开关后即可使用。"),
 RichText(runs=[{text:"旧值 ",strike:true},{text:"重要信息 ",highlight:true},{text:"1234.56",tabular:true}]),
 TextField(label="公式源码",value=$/form/formula),
 Formula(label="公式预览",text=$/form/formula),
 Table(columns=["项目","数量"],rows=[["甲",12],["乙",24],["丙",36],["丁",48],["戊",60],["己",72]],columnWidths=[160,100],maxHeight=160)
])`;
    if (name === "p2_lists") return String.raw`
$/groups=[{id:"a",title:"厨房",rank:2,children:[{id:"one",title:"准备食材"},{id:"two",title:"整理台面"}]},{id:"b",title:"客厅",rank:1,children:[{id:"one",title:"整理书架"}]}]
root=Column(children=[
 Header(text="分组任务",label="相同子任务 ID 在不同分组中独立保存"),
 Row(wrap=true,children=[
  Button(child=Text(text="按顺序排列"),action=Event(name="local_action",context={op:"set",path:"/groups",value:arraySort(items=$/groups,field="rank")})),
  Button(child=Text(text="重命名厨房"),action=Event(name="local_action",context={op:"set",path:"/groups",value:arrayUpdateByKey(items=$/groups,key="id",value="a",patch={title:"厨房与餐厅"})}))
 ]),
 groups
])
groups=Repeat(items=$/groups,key="id",child=Panel(child=Column(children=[
 Heading(text=item(field="title")),
 tasks
]),background="inset"))
tasks=Repeat(items=item(field="children"),key="id",child=CheckBox(label=item(field="title"),value=item_state(field="done",default=false)))
`;
    throw new Error("Choose shopping_list, trip_budget, filtered_chart, backgrounds, surfaces, profile, flow_steps, flowchart, data_story, mixed_branch, stats, charts, info, vector, curves, responsive, interactive_flow, calculator, cost_calculator, form, progress, live_progress, data, repeat, p2_controls or p2_lists.");
}

exports.read_guide = async () => complete({success:true,message:guide});
exports.read_example = async ({name}) => complete({success:true,source:example(name),message:name === "live_progress" ? "以 live=true 调用 a2ui_prepare；更新时 data={\"progress\":50}，不包裹 agent 键，expected_version 初始为 0。" : "示例源码；按任务修改后可调用原生 a2ui_prepare。"});
