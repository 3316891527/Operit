# 工作区 TextMate 第三方资源

工作区使用 Sora TextMate 0.23.6 提供的 TM4E Java 引擎，保留 Operit 自有编辑器控件。对应发布物、源码 URL、AAR SHA-256 和本地兼容修改记录在 `catalog.json` 的 `engine` 字段。

语法资源来自 [tm-grammars 1.32.22](https://www.npmjs.com/package/tm-grammars/v/1.32.22)，包含 242 个语言定义和 18 个注入定义。包下载校验值、原始来源 URL、提交 SHA、许可证元数据和导入后的 SHA-256 均记录在 `catalog.json`。包内组合规则 `ts-tags` 没有独立上游提交，使用精确 npm 包文件和包校验值追溯。Kotlin 块注释增加递归规则；XML 修正 ASP 注释属性层级，Racket 规范化 repository 和捕获对象，Stata 规范化命名捕获，Wikitext 保留空捕获组，Jinja 将捕获数组规范化为数字捕获字典；Markdown 删除线将终点的反向引用后视改为等价的前瞻与固定长度后视，保留捕获组和边界限制。以上本地修正均记录 `upstreamSha256`、当前 `sha256` 和 `localChanges`，其余语法保持包中内容。

包级 [LICENSE](LICENSE) 与逐来源 [NOTICE](NOTICE) 一并保留。NOTICE 和 EPL 文本仅规范化行尾空白，原始和当前校验值及变更记录见 `noticeFiles`。包级 MIT 不代表所有语法均采用 MIT；来源声明包含 Apache、GPL、MPL 等不同许可证，以及部分未明确声明的项目，具体见各项元数据和原始 NOTICE。

文件识别扩展名与特殊文件名参考 [GitHub Linguist 9.1.0](https://github.com/github-linguist/linguist/tree/v9.1.0)，保留其 [MIT 许可证](licenses/linguist-MIT.txt)。重复后缀在目录中登记明确选择，无法凭文件名确定的通用后缀不指向某种编程语言。

引擎相关许可证保留 [LGPL 2.1](licenses/sora-LGPL-2.1.txt) 和 [EPL 2.0](licenses/tm4e-EPL-2.0.txt)。兼容修正版位于应用源码 `org/eclipse/tm4e/core/internal/oniguruma/OnigString.java` 和 `org/eclipse/tm4e/core/internal/rule/RegExpSource.java`，分别修正 UTF-16 坐标和动态终点引用的重复转义。构建脚本只移除发布 AAR 中对应的四个类，再使用源码中的修正版，避免重复类。升级引擎需要同时审阅接口、校验值与修正是否仍有必要。

离线目录检查和 Kotlin 文件识别数据生成入口为仓库根目录的 `tools/workspace_textmate.py`。在项目 Python 环境运行 `python -B tools/workspace_textmate.py` 验证资源，修改 `catalog.json` 后运行 `python -B tools/workspace_textmate.py --generate` 生成目录。应用的规则加载和分词均在后台按需执行，启动时不加载全部语法。

TextMate 的外部 include 可以声明包外嵌入语法。已知的同语言历史作用域登记在 `scopeAliases`；当前包未提供的外部语法逐项登记在 `optionalScopes`，不宣称这些嵌入格式已经支持。加载一个未登记的缺失作用域会报告错误，便于发现升级后漏带的资源。

工作区默认文本打开规则直接复用此目录的后缀、特殊文件名和文件名前缀关联。已有 ActionScript 3 语法通过 `.as` 识别，同时支持 `as`、`as3` 和 `actionscript` 语言别名，无需重复导入规则。260 份语法与说明、目录及许可证合计为 267 个资源文件；注入规则和许可证不作为可打开的文件格式计数。源码后缀与媒体 MIME 冲突时使用有界内容样本分类，文本入口仍校验实际内容。

十一种原目录未关联的独立语言已补齐，当前登记 881 个后缀、285 个特殊文件名和三个前缀。九种补充声明的固定上游文件与 Git blob SHA 见 `associationsSource.supplements`，Logo 和文言的本地关联见 `localAssociations`。共用后缀保留原有明确选择，嵌入与共享语法变体通过所属语言使用。
