# 工作区实机反馈、标题主题修正与待测链接

本轮依据用户对提交 6f239395 文档的反馈更新结果；实际 APK 提交、设备型号、Android 版本和逐来源组合未单独提供，不填入推断值。

## 已报告结果

- [01-interaction-design.md](01-interaction-design.md)：其余检查通过；“设置工作区”标题仍为默认黑色，主题项保留部分通过，新增 W-TH01 记录标题修正后的待复测状态。
- [02-file-links-and-theme.md](02-file-links-and-theme.md)：用户明确报告 1–10 全部通过。
- [04-file-links-review.md](04-file-links-review.md)：L-R01、L-R02、L-R03、L-R06、L-R07 通过；L-R04、L-R05 未测，原因是还未构造链接。
- [03-review-fixes.md](03-review-fixes.md) 的 R01–R15 本轮未逐项报告，原结果保留。

WorkspaceSetup 只给 Column 设置 surface 背景，标题 Text 没有指定前景，因而继续继承聊天区域的 LocalContentColor。本轮只给标题显式设置 MaterialTheme.colorScheme.onSurface，让它与已有 surface 背景对应。此修正待新构建复测，既有通过结果继续保留。

## 链接样例的使用

以下为明确生成的测试文件，样例仅写入设备和 Linux 文件系统，不进入源码提交。把代码块内的 Markdown 内容复制到助手消息的纯文本编辑入口，保存后点击链接，再点击弹窗中的“访问”。不要把代码围栏一起贴入消息，否则会显示为代码而不是可点击链接。

路径是当前会话设备与 Linux 环境中的真实路径。复制到其他设备时先创建同名文件或替换为实际路径。

L-R04：Android 挂载根、挂载文件和类似目录名称。

```markdown
[Android 挂载根目录](/mnt)
[Android 挂载路径中的文本](/mnt/sdcard/Download/Operit/link-regression-1344/中文%20文件.txt)
[file 协议的 Android 挂载文本](file:///mnt/sdcard/Download/Operit/link-regression-1344/中文%20文件.txt)
[相似目录名仍应走 Linux](/mnt-other/operit-link-regression-1344/similar-root.txt)
```

- 前三条按 Android 处理。当前工具读取 /mnt/sdcard 返回 Permission denied；若同样报权限错误，应展示实际来源错误并能返回。挂载目录或文件成功读取需要该位置对应用可读，权限错误不作为成功打开文件的证据。
- 最后一条的 Linux 文件已创建并回读，应显示 LINUX_SIMILAR_ROOT_1344；/mnt-other 不应被归为 Android。
- 当前未提供实际 OTG 挂载卷，/mnt/media_rw/卷名 下真实文件的成功读取仍可在有设备条件时补测。

L-R05：三个写法全部访问同一 Linux /mnt 文件。

```markdown
[显式 Linux 协议](linux:///mnt/operit-link-regression-1344/linux-source.txt)
[file 的 Linux 来源](file://linux/mnt/operit-link-regression-1344/linux-source.txt)
[查询参数指定 Linux](/mnt/operit-link-regression-1344/linux-source.txt?environment=linux)
```

文件已创建并回读，三条均应显示 LINUX_MNT_LINK_1344。这里的 /mnt 在 Linux 环境中；虽然路径形状属于默认 Android 根，显式协议或环境参数仍要优先。

普通设备与 Linux 文本链接也可按下面构造，便于复测 02 表中的本地文本与第 3 项。

```markdown
[设备文本](/sdcard/Download/Operit/link-regression-1344/中文%20文件.txt)
[Linux 裸路径文本](/tmp/operit-link-regression-1344/linux.txt)
[Linux 显式文本](linux:///tmp/operit-link-regression-1344/linux.txt)
[Linux 第12行](linux:///tmp/operit-link-regression-1344/linux.txt#L12)
```

设备样例首行是 ANDROID_LINK_1344；Linux 文件有 40 行，第 12 行是“第12行：LINUX_TEXT_LINK_1344”。这些内容已回读确认，样例创建不代表应用链接点击已测试通过。

## 待复测结果表

| 编号 | 操作 | 预期 | 当前结果 | 新结果与证据 |
| --- | --- | --- | --- | --- |
| L-R04 | 依次点击 Android /mnt 链接与 /mnt-other 链接 | /mnt 走 Android，相似名称走 Linux；错误可返回，可读文件内容正确 | 未测 |  |
| L-R05 | 点击三个显式 Linux /mnt 链接 | 三条访问相同 Linux 内容，环境不被默认路径判断覆盖 | 未测 |  |
| W-TH01 | 新构建未绑定工作区时切换浅色、深色、背景图主题 | “设置工作区”文字使用主题前景色 | 原构建失败；修正待复测 |  |

[DONE] 本轮反馈和标题颜色修正已记录，可复制的链接及真实样例已准备；新增设备结果仍待填写。