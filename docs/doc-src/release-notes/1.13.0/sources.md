# 1.13.0 来源与归并说明

[返回更新日志](index.md) · [补丁时间线](patches.md) · [全部提交](commits.md)

## 固定快照与统计口径

- 起点：[上游 v1.12.2](https://github.com/AAswordman/Operit/releases/tag/v1.12.2)，源码 `dbf71916fae9750cfdc9f9a774f5a0fee56633fb`
- 终点：[个人发行 v1.12.2+19](https://github.com/3316891527/Operit-Kong-patch/releases/tag/v1.12.2%2B19)，源码 `890e532070f14eee8b21084bbedbe5c9ab6cb7e1`
- 正文按终点的最终行为合并叙述；同一能力的试验、修复、重构与再次发布不重复累计
- 提交集合为 `git rev-list dbf71916fae9750cfdc9f9a774f5a0fee56633fb..890e532070f14eee8b21084bbedbe5c9ab6cb7e1`，共 488 条；其中 130 条合并提交、358 条非合并提交，第一父链 138 条
- 原默认重命名检测得到 4028 个变更文件、396262 行新增和 137617 行删除，合计 533879 行增删。该口径触发重命名数量限制，且含目录移动、文档和依赖；它不是逐提交增删量之和，也不能解释为 4028 个新文件或 53 万行净新增业务代码

起点是上游发布同步提交，并非终点祖先；双方共同祖先 `0f11d96231d3ea50818e79342e90be4391d3ea7a` 与起点的文件树完全相同。对照使用固定快照与可达集合，不按作者日期切割，因此较早编写、后来才整合的功能也会保留。

资料包括 52 项早期固定来源、112 条维护者提交给上游的 PR、个人仓库 104 条 PR、18 个公开补丁版说明，以及直接提交。PR 的当前开闭状态不能替代合入证据；没有使用当前 HEAD 作为来源，也不表示相关功能被删除。

## 功能归并索引

### 聊天界面与对话管理

- 相关 PR：[上游 #1372](https://github.com/AAswordman/Operit/pull/1372)、[本仓库 #7](https://github.com/3316891527/Operit/pull/7)、[本仓库 #8](https://github.com/3316891527/Operit/pull/8)、[本仓库 #12](https://github.com/3316891527/Operit/pull/12)、[本仓库 #13](https://github.com/3316891527/Operit/pull/13)、[本仓库 #14](https://github.com/3316891527/Operit/pull/14)、[本仓库 #16](https://github.com/3316891527/Operit/pull/16)、[本仓库 #18](https://github.com/3316891527/Operit/pull/18)、[本仓库 #21](https://github.com/3316891527/Operit/pull/21)、[本仓库 #79](https://github.com/3316891527/Operit/pull/79)、[本仓库 #83](https://github.com/3316891527/Operit/pull/83)
- 直接提交：[3e79e54c](https://github.com/3316891527/Operit/commit/3e79e54c2b2a552163bbe75df0143c0673332ed6)

### 消息渲染与生成控制

- 相关 PR：[上游 #1157](https://github.com/AAswordman/Operit/pull/1157)、[上游 #1168](https://github.com/AAswordman/Operit/pull/1168)、[上游 #1231](https://github.com/AAswordman/Operit/pull/1231)、[上游 #1256](https://github.com/AAswordman/Operit/pull/1256)、[上游 #1260](https://github.com/AAswordman/Operit/pull/1260)、[上游 #1270](https://github.com/AAswordman/Operit/pull/1270)、[上游 #1278](https://github.com/AAswordman/Operit/pull/1278)、[上游 #1299](https://github.com/AAswordman/Operit/pull/1299)、[上游 #1334](https://github.com/AAswordman/Operit/pull/1334)、[上游 #1338](https://github.com/AAswordman/Operit/pull/1338)、[上游 #1362](https://github.com/AAswordman/Operit/pull/1362)、[上游 #1365](https://github.com/AAswordman/Operit/pull/1365)、[本仓库 #28](https://github.com/3316891527/Operit/pull/28)、[本仓库 #39](https://github.com/3316891527/Operit/pull/39)、[本仓库 #49](https://github.com/3316891527/Operit/pull/49)、[本仓库 #52](https://github.com/3316891527/Operit/pull/52)、[本仓库 #55](https://github.com/3316891527/Operit/pull/55)、[本仓库 #66](https://github.com/3316891527/Operit/pull/66)、[本仓库 #81](https://github.com/3316891527/Operit/pull/81)、[本仓库 #83](https://github.com/3316891527/Operit/pull/83)、[本仓库 #87](https://github.com/3316891527/Operit/pull/87)
- 直接提交：[721688b2](https://github.com/3316891527/Operit/commit/721688b22b0a383a8864fae802d3f0ae1e2df425)

### 主题、角色与外观

- 相关 PR：[上游 #1156](https://github.com/AAswordman/Operit/pull/1156)、[上游 #1321](https://github.com/AAswordman/Operit/pull/1321)、[上游 #1356](https://github.com/AAswordman/Operit/pull/1356)、[上游 #1372](https://github.com/AAswordman/Operit/pull/1372)、[本仓库 #18](https://github.com/3316891527/Operit/pull/18)、[本仓库 #21](https://github.com/3316891527/Operit/pull/21)、[本仓库 #24](https://github.com/3316891527/Operit/pull/24)、[本仓库 #37](https://github.com/3316891527/Operit/pull/37)、[本仓库 #43](https://github.com/3316891527/Operit/pull/43)、[本仓库 #56](https://github.com/3316891527/Operit/pull/56)、[本仓库 #87](https://github.com/3316891527/Operit/pull/87)、[本仓库 #96](https://github.com/3316891527/Operit/pull/96)、[本仓库 #99](https://github.com/3316891527/Operit/pull/99)
- 直接提交：[285c628f](https://github.com/3316891527/Operit/commit/285c628f6dab448de4fd3cc95bf80b1fb9490a10)

### 模型配置与密钥池

- 相关 PR：[上游 #1113](https://github.com/AAswordman/Operit/pull/1113)、[上游 #1121](https://github.com/AAswordman/Operit/pull/1121)、[上游 #1189](https://github.com/AAswordman/Operit/pull/1189)、[上游 #1211](https://github.com/AAswordman/Operit/pull/1211)、[上游 #1255](https://github.com/AAswordman/Operit/pull/1255)、[上游 #1257](https://github.com/AAswordman/Operit/pull/1257)、[上游 #1269](https://github.com/AAswordman/Operit/pull/1269)、[上游 #1290](https://github.com/AAswordman/Operit/pull/1290)、[本仓库 #9](https://github.com/3316891527/Operit/pull/9)、[本仓库 #18](https://github.com/3316891527/Operit/pull/18)、[本仓库 #21](https://github.com/3316891527/Operit/pull/21)、[本仓库 #31](https://github.com/3316891527/Operit/pull/31)、[本仓库 #33](https://github.com/3316891527/Operit/pull/33)、[本仓库 #98](https://github.com/3316891527/Operit/pull/98)、[本仓库 #100](https://github.com/3316891527/Operit/pull/100)
- 直接提交：[8542070e](https://github.com/3316891527/Operit/commit/8542070e1878419533dc7eb7c8dbdf079c4229bd)

### 上下文总结与用量统计

- 相关 PR：[上游 #1110](https://github.com/AAswordman/Operit/pull/1110)、[上游 #1152](https://github.com/AAswordman/Operit/pull/1152)、[上游 #1236](https://github.com/AAswordman/Operit/pull/1236)、[上游 #1261](https://github.com/AAswordman/Operit/pull/1261)、[上游 #1262](https://github.com/AAswordman/Operit/pull/1262)、[上游 #1267](https://github.com/AAswordman/Operit/pull/1267)、[上游 #1284](https://github.com/AAswordman/Operit/pull/1284)、[上游 #1360](https://github.com/AAswordman/Operit/pull/1360)、[本仓库 #31](https://github.com/3316891527/Operit/pull/31)、[本仓库 #33](https://github.com/3316891527/Operit/pull/33)、[本仓库 #43](https://github.com/3316891527/Operit/pull/43)、[本仓库 #101](https://github.com/3316891527/Operit/pull/101)

### 语音、图片与媒体

- 相关 PR：[上游 #1233](https://github.com/AAswordman/Operit/pull/1233)、[上游 #1235](https://github.com/AAswordman/Operit/pull/1235)、[上游 #1298](https://github.com/AAswordman/Operit/pull/1298)、[本仓库 #10](https://github.com/3316891527/Operit/pull/10)、[本仓库 #12](https://github.com/3316891527/Operit/pull/12)、[本仓库 #33](https://github.com/3316891527/Operit/pull/33)、[本仓库 #37](https://github.com/3316891527/Operit/pull/37)、[本仓库 #41](https://github.com/3316891527/Operit/pull/41)、[本仓库 #42](https://github.com/3316891527/Operit/pull/42)、[本仓库 #43](https://github.com/3316891527/Operit/pull/43)、[本仓库 #45](https://github.com/3316891527/Operit/pull/45)、[本仓库 #53](https://github.com/3316891527/Operit/pull/53)、[本仓库 #86](https://github.com/3316891527/Operit/pull/86)、[本仓库 #87](https://github.com/3316891527/Operit/pull/87)、[本仓库 #98](https://github.com/3316891527/Operit/pull/98)
- 直接提交：[14c8afdc](https://github.com/3316891527/Operit/commit/14c8afdc3bfb3bb22caa824d164bb49b57d7bff1)、[ef1bf4b6](https://github.com/3316891527/Operit/commit/ef1bf4b6c307f9d64e16912fc8ac256d14d57f15)、[29d67812](https://github.com/3316891527/Operit/commit/29d678120994ab535ddb80bda171a4fa77b4f1bc)

### 记忆空间与检索

- 相关 PR：[上游 #1361](https://github.com/AAswordman/Operit/pull/1361)、[上游 #1372](https://github.com/AAswordman/Operit/pull/1372)、[本仓库 #24](https://github.com/3316891527/Operit/pull/24)、[本仓库 #33](https://github.com/3316891527/Operit/pull/33)、[本仓库 #34](https://github.com/3316891527/Operit/pull/34)、[本仓库 #37](https://github.com/3316891527/Operit/pull/37)
- 直接提交：[916345f0](https://github.com/3316891527/Operit/commit/916345f0620b412836c673935ad49884c154a5cd)

### 文件工作区与终端

- 相关 PR：[上游 #1178](https://github.com/AAswordman/Operit/pull/1178)、[上游 #1218](https://github.com/AAswordman/Operit/pull/1218)、[上游 #1296](https://github.com/AAswordman/Operit/pull/1296)、[上游 #1308](https://github.com/AAswordman/Operit/pull/1308)、[上游 #1344](https://github.com/AAswordman/Operit/pull/1344)、[本仓库 #32](https://github.com/3316891527/Operit/pull/32)、[本仓库 #33](https://github.com/3316891527/Operit/pull/33)、[本仓库 #37](https://github.com/3316891527/Operit/pull/37)、[本仓库 #44](https://github.com/3316891527/Operit/pull/44)、[本仓库 #63](https://github.com/3316891527/Operit/pull/63)、[本仓库 #87](https://github.com/3316891527/Operit/pull/87)、[本仓库 #93](https://github.com/3316891527/Operit/pull/93)、[本仓库 #95](https://github.com/3316891527/Operit/pull/95)、[本仓库 #97](https://github.com/3316891527/Operit/pull/97)
- 直接提交：[c8f4ca9e](https://github.com/3316891527/Operit/commit/c8f4ca9e7309e7c403d970ba17b972bf2bf6d16f)、[94a5313f](https://github.com/3316891527/Operit/commit/94a5313f739c652edd631e48c3a70e2a89877bad)、[ff0b0f73](https://github.com/3316891527/Operit/commit/ff0b0f73a038f591be905e8e549bfc436168b3cc)

### 工具、插件与权限

- 相关 PR：[上游 #1111](https://github.com/AAswordman/Operit/pull/1111)、[上游 #1126](https://github.com/AAswordman/Operit/pull/1126)、[上游 #1134](https://github.com/AAswordman/Operit/pull/1134)、[上游 #1142](https://github.com/AAswordman/Operit/pull/1142)、[上游 #1247](https://github.com/AAswordman/Operit/pull/1247)、[上游 #1311](https://github.com/AAswordman/Operit/pull/1311)、[上游 #1313](https://github.com/AAswordman/Operit/pull/1313)、[上游 #1340](https://github.com/AAswordman/Operit/pull/1340)、[上游 #1372](https://github.com/AAswordman/Operit/pull/1372)、[本仓库 #21](https://github.com/3316891527/Operit/pull/21)、[本仓库 #24](https://github.com/3316891527/Operit/pull/24)、[本仓库 #25](https://github.com/3316891527/Operit/pull/25)、[本仓库 #27](https://github.com/3316891527/Operit/pull/27)、[本仓库 #28](https://github.com/3316891527/Operit/pull/28)、[本仓库 #29](https://github.com/3316891527/Operit/pull/29)、[本仓库 #44](https://github.com/3316891527/Operit/pull/44)、[本仓库 #46](https://github.com/3316891527/Operit/pull/46)、[本仓库 #50](https://github.com/3316891527/Operit/pull/50)、[本仓库 #52](https://github.com/3316891527/Operit/pull/52)、[本仓库 #62](https://github.com/3316891527/Operit/pull/62)、[本仓库 #65](https://github.com/3316891527/Operit/pull/65)、[本仓库 #72](https://github.com/3316891527/Operit/pull/72)、[本仓库 #77](https://github.com/3316891527/Operit/pull/77)、[本仓库 #80](https://github.com/3316891527/Operit/pull/80)、[本仓库 #82](https://github.com/3316891527/Operit/pull/82)、[本仓库 #83](https://github.com/3316891527/Operit/pull/83)、[本仓库 #84](https://github.com/3316891527/Operit/pull/84)、[本仓库 #87](https://github.com/3316891527/Operit/pull/87)、[本仓库 #92](https://github.com/3316891527/Operit/pull/92)、[本仓库 #95](https://github.com/3316891527/Operit/pull/95)
- 直接提交：[2c9212ae](https://github.com/3316891527/Operit/commit/2c9212ae3bfb6122fdaa342375ba214929f5c744)、[b2d7e307](https://github.com/3316891527/Operit/commit/b2d7e30741ad40e59c252d8678aa949c929401b9)、[d8a7d2a6](https://github.com/3316891527/Operit/commit/d8a7d2a6b04a9fb2343abc003dd4ee2a2fcce4c5)、[9d053ef0](https://github.com/3316891527/Operit/commit/9d053ef08171192f76280444e6c7e292b0b21f74)

### 工作流

- 相关 PR：[上游 #1372](https://github.com/AAswordman/Operit/pull/1372)、[本仓库 #21](https://github.com/3316891527/Operit/pull/21)、[本仓库 #24](https://github.com/3316891527/Operit/pull/24)、[本仓库 #64](https://github.com/3316891527/Operit/pull/64)
- 直接提交：[8f317107](https://github.com/3316891527/Operit/commit/8f317107dc073ba64c6309f236920f30bf192caa)

### 数据、备份与诊断

- 相关 PR：[上游 #1104](https://github.com/AAswordman/Operit/pull/1104)、[上游 #1130](https://github.com/AAswordman/Operit/pull/1130)、[上游 #1193](https://github.com/AAswordman/Operit/pull/1193)、[上游 #1205](https://github.com/AAswordman/Operit/pull/1205)、[上游 #1254](https://github.com/AAswordman/Operit/pull/1254)、[上游 #1314](https://github.com/AAswordman/Operit/pull/1314)、[本仓库 #11](https://github.com/3316891527/Operit/pull/11)、[本仓库 #16](https://github.com/3316891527/Operit/pull/16)、[本仓库 #24](https://github.com/3316891527/Operit/pull/24)、[本仓库 #30](https://github.com/3316891527/Operit/pull/30)、[本仓库 #37](https://github.com/3316891527/Operit/pull/37)、[本仓库 #56](https://github.com/3316891527/Operit/pull/56)、[本仓库 #66](https://github.com/3316891527/Operit/pull/66)、[本仓库 #87](https://github.com/3316891527/Operit/pull/87)、[本仓库 #102](https://github.com/3316891527/Operit/pull/102)
- 直接提交：[6dc1dbfa](https://github.com/3316891527/Operit/commit/6dc1dbfae6e96c2d3f48a4b29f916180125a02e3)、[c2baad1e](https://github.com/3316891527/Operit/commit/c2baad1eb473f07bfbe8f68199226b3335edfd75)、[eacd0c08](https://github.com/3316891527/Operit/commit/eacd0c084281ebe16004892ef1211788c1dba7cb)

### 更新与离线资源

- 相关 PR：[本仓库 #5](https://github.com/3316891527/Operit/pull/5)、[本仓库 #19](https://github.com/3316891527/Operit/pull/19)、[本仓库 #22](https://github.com/3316891527/Operit/pull/22)、[本仓库 #26](https://github.com/3316891527/Operit/pull/26)、[本仓库 #35](https://github.com/3316891527/Operit/pull/35)、[本仓库 #37](https://github.com/3316891527/Operit/pull/37)、[本仓库 #40](https://github.com/3316891527/Operit/pull/40)、[本仓库 #47](https://github.com/3316891527/Operit/pull/47)、[本仓库 #54](https://github.com/3316891527/Operit/pull/54)、[本仓库 #57](https://github.com/3316891527/Operit/pull/57)、[本仓库 #58](https://github.com/3316891527/Operit/pull/58)、[本仓库 #59](https://github.com/3316891527/Operit/pull/59)、[本仓库 #60](https://github.com/3316891527/Operit/pull/60)、[本仓库 #61](https://github.com/3316891527/Operit/pull/61)、[本仓库 #68](https://github.com/3316891527/Operit/pull/68)、[本仓库 #70](https://github.com/3316891527/Operit/pull/70)、[本仓库 #71](https://github.com/3316891527/Operit/pull/71)、[本仓库 #73](https://github.com/3316891527/Operit/pull/73)、[本仓库 #74](https://github.com/3316891527/Operit/pull/74)、[本仓库 #78](https://github.com/3316891527/Operit/pull/78)、[本仓库 #83](https://github.com/3316891527/Operit/pull/83)、[本仓库 #85](https://github.com/3316891527/Operit/pull/85)、[本仓库 #88](https://github.com/3316891527/Operit/pull/88)、[本仓库 #89](https://github.com/3316891527/Operit/pull/89)、[本仓库 #90](https://github.com/3316891527/Operit/pull/90)、[本仓库 #91](https://github.com/3316891527/Operit/pull/91)、[本仓库 #103](https://github.com/3316891527/Operit/pull/103)、[本仓库 #104](https://github.com/3316891527/Operit/pull/104)
- 直接提交：[eed0b0bb](https://github.com/3316891527/Operit/commit/eed0b0bb688658db60ab6b9c215d68686fdf9c0b)、[29244342](https://github.com/3316891527/Operit/commit/292443425feaa62b5d558c0645c0d13f14ee2a2c)、[6dc1dbfa](https://github.com/3316891527/Operit/commit/6dc1dbfae6e96c2d3f48a4b29f916180125a02e3)、[7dc8bf3d](https://github.com/3316891527/Operit/commit/7dc8bf3d9cd386cca20ffcea8a0485c54ab0f272)、[0dbd1c8a](https://github.com/3316891527/Operit/commit/0dbd1c8afd546c0d9dff5b413bd3c2f03bb26176)

### 文档、语言与维护

- 相关 PR：[上游 #1282](https://github.com/AAswordman/Operit/pull/1282)、[上游 #1326](https://github.com/AAswordman/Operit/pull/1326)、[上游 #1330](https://github.com/AAswordman/Operit/pull/1330)、[本仓库 #21](https://github.com/3316891527/Operit/pull/21)、[本仓库 #34](https://github.com/3316891527/Operit/pull/34)、[本仓库 #35](https://github.com/3316891527/Operit/pull/35)、[本仓库 #37](https://github.com/3316891527/Operit/pull/37)、[本仓库 #67](https://github.com/3316891527/Operit/pull/67)、[本仓库 #70](https://github.com/3316891527/Operit/pull/70)、[本仓库 #76](https://github.com/3316891527/Operit/pull/76)、[本仓库 #83](https://github.com/3316891527/Operit/pull/83)、[本仓库 #90](https://github.com/3316891527/Operit/pull/90)、[本仓库 #92](https://github.com/3316891527/Operit/pull/92)、[本仓库 #94](https://github.com/3316891527/Operit/pull/94)、[本仓库 #95](https://github.com/3316891527/Operit/pull/95)
- 直接提交：[73ed1570](https://github.com/3316891527/Operit/commit/73ed157025234bea5e3632122d456a5129add8bd)、[5fa6de67](https://github.com/3316891527/Operit/commit/5fa6de67925f4b726be186838d53bb4a252c1070)、[c86235ea](https://github.com/3316891527/Operit/commit/c86235ea9bf783303f25c0a88c9438665d51e770)、[832c8abd](https://github.com/3316891527/Operit/commit/832c8abd316ec34c33e1ebcf7613434d0ddc5222)

## 早期上游来源

52 项旧集成清单全部可在终点追溯。#1338 的来源分支兼作过界面集成分支，因此按标题只理解为一项 Waifu 修复会遗漏 #1372 等界面工作；本次结合固定来源和实际提交归并。

<details><summary>58 项有范围内来源的上游 PR</summary>

- [上游 #1104](https://github.com/AAswordman/Operit/pull/1104) feat(backup): 导入快照自动应用主题媒体并整理导出资源；[来源 dd8365848fed](https://github.com/3316891527/Operit/commit/dd8365848fed2601d5cbe14d3e3ed424fe32ebdd)；固定来源已在终点历史；数据、备份与诊断
- [上游 #1110](https://github.com/AAswordman/Operit/pull/1110) feat(tokenstats): 增加月度与年度统计并按周期刷新图表；[来源 6db9aa252336](https://github.com/3316891527/Operit/commit/6db9aa2523365f818bdcb6d50255504da11946b4)；固定来源已在终点历史；上下文总结与用量统计
- [上游 #1111](https://github.com/AAswordman/Operit/pull/1111) fix(shell): 按命令位置识别危险操作，避免误拦正常参数；[来源 3c5c76e83b6c](https://github.com/3316891527/Operit/commit/3c5c76e83b6c0790196a9789abd73ef649417601)；固定来源已在终点历史；工具、插件与权限
- [上游 #1113](https://github.com/AAswordman/Operit/pull/1113) feat(deepseek): 接入 Files API 并复用图片上传结果；[来源 62b787398764](https://github.com/3316891527/Operit/commit/62b78739876408fab595af35a55674aa1fce1421)；固定来源已在终点历史；模型配置与密钥池
- [上游 #1121](https://github.com/AAswordman/Operit/pull/1121) fix(settings): 改善模型列表获取反馈与超时取消处理；[来源 02588055b7bf](https://github.com/3316891527/Operit/commit/02588055b7bf2c463f4b30a4e276c5dc989dc062)；固定来源已在终点历史；模型配置与密钥池
- [上游 #1126](https://github.com/AAswordman/Operit/pull/1126) fix(tools): 修复 MCP 与插件参数转换并减少输入框卡顿；[来源 f0bffa78e3f2](https://github.com/3316891527/Operit/commit/f0bffa78e3f280cc3c3b170ab4d8897acc025c48)；固定来源已在终点历史；工具、插件与权限
- [上游 #1130](https://github.com/AAswordman/Operit/pull/1130) feat(logcat): 支持日志导出过滤与敏感内容隐藏；[来源 59129ab26310](https://github.com/3316891527/Operit/commit/59129ab263100262e0bf890141a1a6199b6ab0d2)；固定来源已在终点历史；数据、备份与诊断
- [上游 #1134](https://github.com/AAswordman/Operit/pull/1134) fix(extended_chat): 修复会话查找、角色校验与错误处理；[来源 5ff8c9ea1e28](https://github.com/3316891527/Operit/commit/5ff8c9ea1e28f2a875911d7c9a7e9accb459307e)；固定来源已在终点历史；工具、插件与权限
- [上游 #1142](https://github.com/AAswordman/Operit/pull/1142) fix(qqbot): 修复监听开关超时与停机状态无法保存；[来源 a3eb31a3de5e](https://github.com/3316891527/Operit/commit/a3eb31a3de5e1d22d4f9e6ef797084581dbbd61d)；固定来源已在终点历史；工具、插件与权限
- [上游 #1152](https://github.com/AAswordman/Operit/pull/1152) feat(summary): 为总结功能提供独立的思考开关与档位；[来源 58a8aa22360c](https://github.com/3316891527/Operit/commit/58a8aa22360ccb21cc5e43b87e7f17381d46c4c3)；固定来源已在终点历史；上下文总结与用量统计
- [上游 #1156](https://github.com/AAswordman/Operit/pull/1156) feat(settings): 支持将当前角色卡主题应用到全部角色卡；[来源 a8040fcaec57](https://github.com/3316891527/Operit/commit/a8040fcaec57e6555a87af312ab9a40206cf5993)；固定来源已在终点历史；主题、角色与外观
- [上游 #1157](https://github.com/AAswordman/Operit/pull/1157) fix(chat): 异步处理长历史上下文，减少消息操作卡顿；[来源 d0fdd163cbd6](https://github.com/3316891527/Operit/commit/d0fdd163cbd6cf7b5fa5f25c7c1a425f99e6ecf2)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1168](https://github.com/AAswordman/Operit/pull/1168) fix(markdown): 修复表格宽度测量与横向拖动失效；[来源 67e992a3e516](https://github.com/3316891527/Operit/commit/67e992a3e51660dfbbeaf31ba0b3e3bdfb070249)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1178](https://github.com/AAswordman/Operit/pull/1178) fix(shell): 修复 Shizuku 命令执行中的管道阻塞与资源清理；[来源 d47cb7c9d79d](https://github.com/3316891527/Operit/commit/d47cb7c9d79dfb8567be3124e27d19115c3e2b07)；固定来源已在终点历史；文件工作区与终端
- [上游 #1189](https://github.com/AAswordman/Operit/pull/1189) feat(settings): 增加模型配置分组、排序与分组筛选；[来源 0234da6f1dc3](https://github.com/3316891527/Operit/commit/0234da6f1dc30344e6bf498fc0fd4e9d6c08e3ba)；固定来源已在终点历史；模型配置与密钥池
- [上游 #1193](https://github.com/AAswordman/Operit/pull/1193) Add data and storage management；[来源 91e83aa0b53d](https://github.com/3316891527/Operit/commit/91e83aa0b53db34c86b9adc7e131d3bad0be958a)；来源已在终点历史；数据、备份与诊断
- [上游 #1205](https://github.com/AAswordman/Operit/pull/1205) feat(settings): 增加数据占用总览与分类清理入口；[来源 d4c8a9368b1a](https://github.com/3316891527/Operit/commit/d4c8a9368b1af0dd05dc0651236630587d35cc7b)；固定来源已在终点历史；数据、备份与诊断
- [上游 #1211](https://github.com/AAswordman/Operit/pull/1211) refactor(key-pool): 重构密钥池调度与管理界面；[来源 3d936738c48b](https://github.com/3316891527/Operit/commit/3d936738c48b8e0db5b64cedb576db4e990788a2)；固定来源已在终点历史；模型配置与密钥池
- [上游 #1218](https://github.com/AAswordman/Operit/pull/1218) fix(repo): 保留附加仓库子目录路径并完善文件操作；[来源 92dd0fa40bba](https://github.com/3316891527/Operit/commit/92dd0fa40bba278a1f8a12c592080d6d8107d27e)；固定来源已在终点历史；文件工作区与终端
- [上游 #1231](https://github.com/AAswordman/Operit/pull/1231) fix(deepseek): 保留纯思考续写历史与未完成工具上下文；[来源 51fe8f1f1270](https://github.com/3316891527/Operit/commit/51fe8f1f12703f5d648d1469a01e560478a4db76)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1233](https://github.com/AAswordman/Operit/pull/1233) feat(draw): add image-to-image and include SpaceXAI prompt updates；[来源 29d678120994](https://github.com/3316891527/Operit/commit/29d678120994ab535ddb80bda171a4fa77b4f1bc)；来源已在终点历史；语音、图片与媒体
- [上游 #1235](https://github.com/AAswordman/Operit/pull/1235) fix(attachment): 将屏幕内容作为图片附件发送；[来源 ab0efc7b1aba](https://github.com/3316891527/Operit/commit/ab0efc7b1abac5b7f5acf47346a21fc6419cc696)；固定来源已在终点历史；语音、图片与媒体
- [上游 #1236](https://github.com/AAswordman/Operit/pull/1236) feat(tokenstats): 支持删除配置统计与清理失效配置记录；[来源 dc522c71f31d](https://github.com/3316891527/Operit/commit/dc522c71f31d46c582e1bfa5c6dd3531518cd947)；固定来源已在终点历史；上下文总结与用量统计
- [上游 #1247](https://github.com/AAswordman/Operit/pull/1247) feat(operit_editor): 支持查询和设置全局工具权限模式；[来源 41a2af8c0d52](https://github.com/3316891527/Operit/commit/41a2af8c0d52841f281e0dedb475660fcdbd6615)；固定来源已在终点历史；工具、插件与权限
- [上游 #1254](https://github.com/AAswordman/Operit/pull/1254) fix(storage): 排除开发目录媒体扫描，保护 TypeScript 源文件；[来源 1f1c4be3585b](https://github.com/3316891527/Operit/commit/1f1c4be3585bd76e6053862b08b9c23d17048416)；固定来源已在终点历史；数据、备份与诊断
- [上游 #1255](https://github.com/AAswordman/Operit/pull/1255) fix(codex): 复用会话标识与缓存键以保持请求缓存亲和；[来源 a80f9ab99367](https://github.com/3316891527/Operit/commit/a80f9ab99367a8b8edb41a62ad6204f9fbbc198f)；固定来源已在终点历史；模型配置与密钥池
- [上游 #1256](https://github.com/AAswordman/Operit/pull/1256) fix(stream): 在重试回滚时清除总结、翻译和工具描述的重复内容；[来源 bd04cc967812](https://github.com/3316891527/Operit/commit/bd04cc9678129529a994c22da898f0cbf719ae3c)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1257](https://github.com/AAswordman/Operit/pull/1257) feat(provider): add Antigravity OAuth login and fingerprint spoofing；[来源 a7676fa356dd](https://github.com/3316891527/Operit/commit/a7676fa356dd7a0562080b165733307bf51710cb)；来源已在终点历史；模型配置与密钥池
- [上游 #1260](https://github.com/AAswordman/Operit/pull/1260) fix(chat): 避免超长思考引导线触发尺寸约束崩溃；[来源 89d85c75131f](https://github.com/3316891527/Operit/commit/89d85c75131f20bfddd86e1ca6b6d64e2a92f655)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1261](https://github.com/AAswordman/Operit/pull/1261) feat(summary): 支持在新摘要中单独压缩上一轮摘要；[来源 86f771e08216](https://github.com/3316891527/Operit/commit/86f771e08216fcacc6290a4a2127f94c61c12a81)；固定来源已在终点历史；上下文总结与用量统计
- [上游 #1262](https://github.com/AAswordman/Operit/pull/1262) feat(config): 将默认日常与最大上下文调整为 200K 和 1000K；[来源 d9350eca466b](https://github.com/3316891527/Operit/commit/d9350eca466b6d615b8be5fb9ca99c1880162935)；固定来源已在终点历史；上下文总结与用量统计
- [上游 #1267](https://github.com/AAswordman/Operit/pull/1267) fix(tokenstats): 恢复用量重置入口并增加延时确认；[来源 55d052c8368d](https://github.com/3316891527/Operit/commit/55d052c8368d12cf489f0a2ecd144ce7a767cfaf)；固定来源已在终点历史；上下文总结与用量统计
- [上游 #1269](https://github.com/AAswordman/Operit/pull/1269) fix(deepseek): 移除已停用的内置联网搜索配置与请求字段；[来源 c41ca2a3f98e](https://github.com/3316891527/Operit/commit/c41ca2a3f98efae196e02cb343276726229c0e15)；固定来源已在终点历史；模型配置与密钥池
- [上游 #1270](https://github.com/AAswordman/Operit/pull/1270) fix(chat): 提高默认生成上限并区分正文缺失与输出截断；[来源 294889b22c13](https://github.com/3316891527/Operit/commit/294889b22c13b41e15ff1e8ca306e61960e53a00)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1278](https://github.com/AAswordman/Operit/pull/1278) feat(chat): 持久化结构化消息并完善工具与思考渲染；[来源 ff6d83b4cd0a](https://github.com/3316891527/Operit/commit/ff6d83b4cd0a5054e18e2de40ebedbf3580df933)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1282](https://github.com/AAswordman/Operit/pull/1282) 修复 DeepSeek/XAI 单元测试工作流失败；[来源 08b34ad93162](https://github.com/3316891527/Operit/commit/08b34ad93162711af58f153865018f089ea0ab84)；来源已在终点历史；文档、语言与维护
- [上游 #1284](https://github.com/AAswordman/Operit/pull/1284) feat(tokenstats): 支持峰谷定价与长上下文差异化计费；[来源 29bc3cc146e8](https://github.com/3316891527/Operit/commit/29bc3cc146e8e891e5829177177a1205897c1f68)；固定来源已在终点历史；上下文总结与用量统计
- [上游 #1290](https://github.com/AAswordman/Operit/pull/1290) fix(deepseek): 兼容两种思考分片字段并保留续写上下文；[来源 4067212991a5](https://github.com/3316891527/Operit/commit/4067212991a549a6f265bafc16d2e24e3c081883)；固定来源已在终点历史；模型配置与密钥池
- [上游 #1296](https://github.com/AAswordman/Operit/pull/1296) fix(files): 修复 Linux 文本搜索的路径映射；[来源 94b57556eb63](https://github.com/3316891527/Operit/commit/94b57556eb638f8244918a5980f608139779f7d7)；固定来源已在终点历史；文件工作区与终端
- [上游 #1298](https://github.com/AAswordman/Operit/pull/1298) fix(attachment): 修复厂商图片选择器临时 URI 导致的附件读取失败；[来源 c848f2c4ffdd](https://github.com/3316891527/Operit/commit/c848f2c4ffdd4d51bd3e5972fa2cc4490bcd1b99)；固定来源已在终点历史；语音、图片与媒体
- [上游 #1299](https://github.com/AAswordman/Operit/pull/1299) feat(chat): 将思考控制改为常驻动态滑块；[来源 b8d9383eeb8c](https://github.com/3316891527/Operit/commit/b8d9383eeb8cd53c667b1873765c667806cb0478)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1308](https://github.com/AAswordman/Operit/pull/1308) fix(code-runner): 限制隐藏执行时长并回收异常 shell；[来源 f308ecd7825e](https://github.com/3316891527/Operit/commit/f308ecd7825e21171ef1265f19f373b4125e5bcf)；固定来源已在终点历史；文件工作区与终端
- [上游 #1311](https://github.com/AAswordman/Operit/pull/1311) feat(operit_editor): 支持查询应用与插件性能指标；[来源 56e01b03d00c](https://github.com/3316891527/Operit/commit/56e01b03d00cf43c0d582b41f2cc59d5ece18155)；固定来源已在终点历史；工具、插件与权限
- [上游 #1313](https://github.com/AAswordman/Operit/pull/1313) refactor(permissions): 简化权限模式并按实际授权降级工具后端；[来源 512f02c07de0](https://github.com/3316891527/Operit/commit/512f02c07de0eefccd6ca4c78705b19fa7184cdf)；固定来源已在终点历史；工具、插件与权限
- [上游 #1314](https://github.com/AAswordman/Operit/pull/1314) feat(chat): 流式导入导出聊天备份，降低大记录内存占用；[来源 5f8cef5c857d](https://github.com/3316891527/Operit/commit/5f8cef5c857d5285ecbcb5c654723725cbeee09a)；固定来源已在终点历史；数据、备份与诊断
- [上游 #1321](https://github.com/AAswordman/Operit/pull/1321) feat(display): 支持配置横竖屏切换后的页面重载策略；[来源 a29314371a74](https://github.com/3316891527/Operit/commit/a29314371a74b0bc9c54950d1e46d46c59660b0a)；固定来源已在终点历史；主题、角色与外观
- [上游 #1326](https://github.com/AAswordman/Operit/pull/1326) fix(i18n): 按应用语言显示聊天处理状态并补齐日语文案；[来源 3df6c1ca061c](https://github.com/3316891527/Operit/commit/3df6c1ca061ca74d8d60e8a9e04fd3b90215d33f)；固定来源已在终点历史；文档、语言与维护
- [上游 #1330](https://github.com/AAswordman/Operit/pull/1330) docs: 完善 ToolPkg 开发者文档并更新 SandboxPackage_DEV；[来源 f1bbcd80b927](https://github.com/3316891527/Operit/commit/f1bbcd80b927bdbda50d07483322a805b1bfe432)；来源已在终点历史；文档、语言与维护
- [上游 #1334](https://github.com/AAswordman/Operit/pull/1334) fix(markdown): 修复行内反引号误触发代码围栏；[来源 59defadfd81a](https://github.com/3316891527/Operit/commit/59defadfd81a97849152a8fb26170bce5b3ffaa1)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1338](https://github.com/AAswordman/Operit/pull/1338) fix(waifu): 修复流式分句截断 Markdown 与后续内容；[来源 39a0abaaf234](https://github.com/3316891527/Operit/commit/39a0abaaf23446e5122f7951b5350f4f40b9d52d)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1340](https://github.com/AAswordman/Operit/pull/1340) fix(pc-agent): 修复配置接口凭据泄露与未授权修改 (#1322)；[来源 fd85521e8963](https://github.com/3316891527/Operit/commit/fd85521e89631d51dae39c14b8a1fb9f1ad30869)；固定来源已在终点历史；工具、插件与权限
- [上游 #1344](https://github.com/AAswordman/Operit/pull/1344) refactor(workspace): 统一文件管理入口并重构浏览与编辑界面；[来源 122925d487bc](https://github.com/3316891527/Operit/commit/122925d487bcae86f10b359de9efca350f87c15f)；固定来源已在终点历史；文件工作区与终端
- [上游 #1356](https://github.com/AAswordman/Operit/pull/1356) feat(settings): 自动清理替换后的图片与字体资源；[来源 39fb3dbc7508](https://github.com/3316891527/Operit/commit/39fb3dbc750857c1ba957d167a7e665a8237c257)；固定来源已在终点历史；主题、角色与外观
- [上游 #1360](https://github.com/AAswordman/Operit/pull/1360) fix(chat): 修复旧会话累计 Token 统计归零与覆盖；[来源 03a5ecc62ece](https://github.com/3316891527/Operit/commit/03a5ecc62ece45fa5ee9316140a0b62d8189b616)；固定来源已在终点历史；上下文总结与用量统计
- [上游 #1361](https://github.com/AAswordman/Operit/pull/1361) fix(memory): 收紧语义检索并补齐查询评分诊断；[来源 15369feca98b](https://github.com/3316891527/Operit/commit/15369feca98b59f73cf7f7fe9cf41ada02c5a8d3)；固定来源已在终点历史；记忆空间与检索
- [上游 #1362](https://github.com/AAswordman/Operit/pull/1362) fix(chat): 修复长回复末尾工具卡片点击并统一聊天滚动；[来源 eb90bfdb634b](https://github.com/3316891527/Operit/commit/eb90bfdb634b285b25f36e7b91a958d47ebd2c58)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1365](https://github.com/AAswordman/Operit/pull/1365) fix(chat): 降低长历史发送前的重复读取和请求构建开销；[来源 4740da86fe5d](https://github.com/3316891527/Operit/commit/4740da86fe5d0c4a5aa581d69ee3ac21cca441a4)；固定来源已在终点历史；消息渲染与生成控制
- [上游 #1372](https://github.com/AAswordman/Operit/pull/1372) feat(ui): 重构聊天主界面并整理包管理、工作流与主题交互；[来源 287fa9c669ab](https://github.com/3316891527/Operit/commit/287fa9c669abf6087ea650af96548fe7a9f06c5e)；来源已在终点历史；聊天界面与对话管理、主题、角色与外观、记忆空间与检索、工具、插件与权限、工作流

</details>

<details><summary>16 项基线已包含的上游 PR</summary>

这些 HEAD 或上游合并提交已在正式 1.12.2 的祖先集合中，不再计作 1.13.0 新增。原有 Grok、OpenCode、缓存命中率等能力不重复写成首次接入。

- [上游 #888](https://github.com/AAswordman/Operit/pull/888) 修复市场评论与发布页的主题文字颜色
- [上游 #938](https://github.com/AAswordman/Operit/pull/938) 更新思考模式的功能说明
- [上游 #988](https://github.com/AAswordman/Operit/pull/988) 功能：接入 OpenCode Zen 与 Go 提供商
- [上游 #1009](https://github.com/AAswordman/Operit/pull/1009) 修复：细化模型提供商地域提醒
- [上游 #1010](https://github.com/AAswordman/Operit/pull/1010) 新增：接入 xAI Grok 提供商
- [上游 #1022](https://github.com/AAswordman/Operit/pull/1022) fix: strip media links from assistant history
- [上游 #1095](https://github.com/AAswordman/Operit/pull/1095) fix(ui): adapt AutoGLM execution text colors to theme
- [上游 #1115](https://github.com/AAswordman/Operit/pull/1115) feat(chat): show cache hit rate in token stats brief and message info
- [上游 #1122](https://github.com/AAswordman/Operit/pull/1122) fix(chat): dispatch message_persisted event on message variant addition (#1085)
- [上游 #1131](https://github.com/AAswordman/Operit/pull/1131) fix(chat): stop reporting unpaired tool calls as user cancellation
- [上游 #1139](https://github.com/AAswordman/Operit/pull/1139) feat(settings): verify media capability probes with closed-form codes and three-state feedback
- [上游 #1144](https://github.com/AAswordman/Operit/pull/1144) fix(chat): fix copy preview jitter and support selection auto-scroll
- [上游 #1145](https://github.com/AAswordman/Operit/pull/1145) fix(character-card): keep cleared default voice prompt empty
- [上游 #1149](https://github.com/AAswordman/Operit/pull/1149) fix(provider): keep video_url content in OpenAI Responses conversion
- [上游 #1190](https://github.com/AAswordman/Operit/pull/1190) feat: support custom xAI draw base URL
- [上游 #1214](https://github.com/AAswordman/Operit/pull/1214) fix(web): serve JS modules with a javascript MIME type

</details>

<details><summary>38 项排查关联与重复来源</summary>

这些 PR 的当前 HEAD／上游合并提交未直接落入所选集合，未据此增加发布功能条目。可能涉及旧方案、重复提交或后续整合；相同能力只按本页已列的范围内提交、固定来源和最终实现记录。

- [上游 #826](https://github.com/AAswordman/Operit/pull/826) build: prepare locked native sources for F-Droid；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1018](https://github.com/AAswordman/Operit/pull/1018) fix GitHub terminal hangs, stale sessions, and command results；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1036](https://github.com/AAswordman/Operit/pull/1036) feat: add persistent message translation；不把原已有消息翻译重复计为新增；正文只记录后续原生接入和缓存完善
- [上游 #1037](https://github.com/AAswordman/Operit/pull/1037) fix: restore GitHub terminal execution and session handling；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1038](https://github.com/AAswordman/Operit/pull/1038) fix(apktool): reduce runtime memory pressure；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1043](https://github.com/AAswordman/Operit/pull/1043) feat(chat): expose current action state API；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1051](https://github.com/AAswordman/Operit/pull/1051) fix(chat): improve provider failure feedback；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1055](https://github.com/AAswordman/Operit/pull/1055) feat: integrate chat runtime, translation, and terminal reliability changes；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1066](https://github.com/AAswordman/Operit/pull/1066) feat: integrate translation, runtime state, terminal, and thinking improvements；1.12.2 发行说明已列入相关整合能力，不作为新接入的独立依据
- [上游 #1072](https://github.com/AAswordman/Operit/pull/1072) fix(chat): prevent OpenAI Responses metadata leaking into message content；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1083](https://github.com/AAswordman/Operit/pull/1083) feat(chat): 新增重试控制并修复工具调用渲染；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1086](https://github.com/AAswordman/Operit/pull/1086) feat: #1066与#1083；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1088](https://github.com/AAswordman/Operit/pull/1088) fix: improve chat export and localization；不以此早期 PR 单列新增；聊天导入导出的实际新增来源为 #1314 和后续个人分支
- [上游 #1089](https://github.com/AAswordman/Operit/pull/1089) fix(toolpkg): 修复 #1084 打包与 manifest 解析；feat(summary): 修复 #1039 自定义总结规则覆盖；perf(autoglm): AutoGLM 执行优化；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1090](https://github.com/AAswordman/Operit/pull/1090) fix: resolve toolpkg packaging/manifest issues (#1084), make summary rules overridable (#1039), and improve AutoGLM automation；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1092](https://github.com/AAswordman/Operit/pull/1092) feat(apk_reverse): upgrade APK reverse toolkit to 1.1.0；1.12.2 发行说明已有 APK 反编译工具 1.1.0，不重复计升级
- [上游 #1093](https://github.com/AAswordman/Operit/pull/1093) feat(apk_reverse): upgrade APK reverse toolkit to 1.1.0；1.12.2 发行说明已有 APK 反编译工具 1.1.0，不重复计升级
- [上游 #1094](https://github.com/AAswordman/Operit/pull/1094) fix(ui): adapt AutoGLM execution text colors to theme；对应主题修复已有 #1095 基线来源，不重复计项
- [上游 #1112](https://github.com/AAswordman/Operit/pull/1112) feat(packages): 为市场插件、技能和 MCP 增加更新检查；不因 PR 标题推定本期新接入市场更新检查，按最终管理与资源变化归并
- [上游 #1120](https://github.com/AAswordman/Operit/pull/1120) feat(chat): add scroll speed limit for streaming output and top positioning for non-streaming output (#1116)；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1123](https://github.com/AAswordman/Operit/pull/1123) feat(settings): add ClaudeCode/CodeX preset tags with 8-language i18n and P1 tag_prompts injection；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1143](https://github.com/AAswordman/Operit/pull/1143) fix(skills): 修复 GitHub 技能安装复用过期缓存；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1147](https://github.com/AAswordman/Operit/pull/1147) feat(github): expand built-in github package from 19 to 86 REST tools；不将现有 GitHub REST 工具数量宣称为本期首次新增
- [上游 #1153](https://github.com/AAswordman/Operit/pull/1153) fix(ffmpeg): 串行执行 FFmpeg 与 FFprobe，避免并发崩溃；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1167](https://github.com/AAswordman/Operit/pull/1167) fix(provider): sort Claude tool results by tool use order to preserve prompt cache；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1179](https://github.com/AAswordman/Operit/pull/1179) feat(toolpkg): 增加通用悬浮窗接口；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1191](https://github.com/AAswordman/Operit/pull/1191) feat(settings): 按工具包分组展示环境变量与配置状态；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1192](https://github.com/AAswordman/Operit/pull/1192) feat(draw): modernize SpaceXAI and prompt inputs；SpaceXAI 更新已随 #1233 对应的 14c8afdc 等代码归并，不重复计一项
- [上游 #1215](https://github.com/AAswordman/Operit/pull/1215) fix(extended_chat): 解除聊天工具对悬浮服务的依赖；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1216](https://github.com/AAswordman/Operit/pull/1216) fix(http): 将请求超时参数传递到宿主并统一单位处理；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1217](https://github.com/AAswordman/Operit/pull/1217) fix(chat): 删除会话前取消发送流，避免外键异常；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1219](https://github.com/AAswordman/Operit/pull/1219) fix(terminal): 串行化同名会话创建并清理残留进程；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1244](https://github.com/AAswordman/Operit/pull/1244) feat(packages): 增加插件创建入口与外部分享导入；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1249](https://github.com/AAswordman/Operit/pull/1249) feat(chat): 展示当前上下文窗口的分类占用明细；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1294](https://github.com/AAswordman/Operit/pull/1294) feat(workflow): 重构工作流界面并支持 JSON 导入导出；工作流重构由 #1372 及 8f317107 的实际实现归并，不以此 PR 当前 HEAD 单列新增
- [上游 #1309](https://github.com/AAswordman/Operit/pull/1309) fix(terminal): 在 chroot 退出后恢复 APT 目录属主；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1310](https://github.com/AAswordman/Operit/pull/1310) fix(terminal): 校验 chroot 绑定挂载并输出失败诊断；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对
- [上游 #1325](https://github.com/AAswordman/Operit/pull/1325) fix(waifu): 调整表情标签提示规则并放宽插入位置；未以该 PR 当前 HEAD 单列新增，最终行为按正文与已列来源核对

</details>

## 个人仓库 PR

共 104 条：91 条的实际合并提交在范围内；13 条未直接合并。发布准备和 main 同步 PR 属于发行记录，不额外计算功能。

未直接合并的 #25 已在 #24 的工具生命周期优化中归并；#32 的源 HEAD 已在终点，经整合带入。#76—#82 的行为由 #83 统一集成，不能因为它们单独关闭就推断修复丢失。#4 为明确标注的 smoke PR，不作为发布变化。

<details><summary>全部 104 条个人 PR 与归属</summary>

- [本仓库 #1](https://github.com/3316891527/Operit/pull/1) fix: improve chat export and localization；未直接合并；不以该 PR 当前 HEAD 独立增加功能项；来源排查与归并
- [本仓库 #2](https://github.com/3316891527/Operit/pull/2) feat(apk_reverse): upgrade APK reverse toolkit to 1.1.0；未直接合并；APK 反编译工具 1.1.0 已在基线说明，不重复计项；来源排查与归并
- [本仓库 #3](https://github.com/3316891527/Operit/pull/3) fix(ui): adapt AutoGLM execution text colors to theme；未直接合并；对应 AutoGLM 颜色修复已有上游 #1095 基线来源；来源排查与归并
- [本仓库 #4](https://github.com/3316891527/Operit/pull/4) [DO NOT MERGE] github_dev smoke (updated)；smoke 占位，明确不计入发布功能；来源排查与归并
- [本仓库 #5](https://github.com/3316891527/Operit/pull/5) release: 合入 1.12.2+5 修复与验证补录；已合入；[提交 19247194c8e8](https://github.com/3316891527/Operit/commit/19247194c8e8fddbbe2901123ff5d64932279809)；更新与离线资源
- [本仓库 #6](https://github.com/3316891527/Operit/pull/6) release: 同步 1.12.2+5 到 main；已合入；[提交 e7ceaed8d0f6](https://github.com/3316891527/Operit/commit/e7ceaed8d0f6ea865f795bc825bcf58f7172adda)；来源排查与归并
- [本仓库 #7](https://github.com/3316891527/Operit/pull/7) feat: 增加聊天归档并整理工作区、市场账号与角色资料入口；已合入；[提交 f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4)；聊天界面与对话管理
- [本仓库 #8](https://github.com/3316891527/Operit/pull/8) fix(chat): 修复当前对话无法删除及锁定误报；已合入；[提交 f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4)；聊天界面与对话管理
- [本仓库 #9](https://github.com/3316891527/Operit/pull/9) fix: 修复请求错误静默结束并区分重试策略；已合入；[提交 f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4)；模型配置与密钥池
- [本仓库 #10](https://github.com/3316891527/Operit/pull/10) feat: 增加聊天图片全屏预览与应用内相册选择器；已合入；[提交 f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4)；语音、图片与媒体
- [本仓库 #11](https://github.com/3316891527/Operit/pull/11) feat(logging): 增加每日重置的日志写入开关；已合入；[提交 f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4)；数据、备份与诊断
- [本仓库 #12](https://github.com/3316891527/Operit/pull/12) feat(chat): 将语音通话迁至附件菜单并禁用空白发送；已合入；[提交 f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4)；聊天界面与对话管理、语音、图片与媒体
- [本仓库 #13](https://github.com/3316891527/Operit/pull/13) feat(chat): 在附件菜单增加无用户消息的继续生成；已合入；[提交 f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4)；聊天界面与对话管理
- [本仓库 #14](https://github.com/3316891527/Operit/pull/14) feat: 合入已验收的聊天体验并准备 1.12.2+6；已合入；[提交 f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4)；聊天界面与对话管理
- [本仓库 #15](https://github.com/3316891527/Operit/pull/15) release: 同步 1.12.2+6 到 main；已合入；[提交 99d2edeafb89](https://github.com/3316891527/Operit/commit/99d2edeafb89cab1743b94be1b21c7f205fb65c2)；来源排查与归并
- [本仓库 #16](https://github.com/3316891527/Operit/pull/16) feat: 精简对话列表、加入短动画并统一四态 OKToast；已合入；[提交 2162097a5e66](https://github.com/3316891527/Operit/commit/2162097a5e66933408aa388b7682244932afe99c)；聊天界面与对话管理、数据、备份与诊断
- [本仓库 #17](https://github.com/3316891527/Operit/pull/17) release: 同步 1.12.2+7 到 main；已合入；[提交 9300be6442fe](https://github.com/3316891527/Operit/commit/9300be6442fe66353bd857a8ca226aa2f3c19d1f)；来源排查与归并
- [本仓库 #18](https://github.com/3316891527/Operit/pull/18) feat: 完善对话管理、模型工作区与主题交互；已合入；[提交 49db947cf3a5](https://github.com/3316891527/Operit/commit/49db947cf3a521867fe4c4b19966acd7b58bd8d9)；聊天界面与对话管理、主题、角色与外观、模型配置与密钥池
- [本仓库 #19](https://github.com/3316891527/Operit/pull/19) release: 准备 1.12.2+8 版本与发布说明；已合入；[提交 462e05202f03](https://github.com/3316891527/Operit/commit/462e05202f038781036df93693ff2dbd3206fa1e)；更新与离线资源
- [本仓库 #20](https://github.com/3316891527/Operit/pull/20) release: 将 dev 同步到 main，发布 1.12.2+8；已合入；[提交 4820c82b528a](https://github.com/3316891527/Operit/commit/4820c82b528a6dc304ebfb44541466878ee5cb16)；来源排查与归并
- [本仓库 #21](https://github.com/3316891527/Operit/pull/21) 完善模型与主题工作区、工作流和聊天体验，并降低插件内存开销；已合入；[提交 6a02595b3fd0](https://github.com/3316891527/Operit/commit/6a02595b3fd06265458a5da8ae564e4d1c24bcde)；聊天界面与对话管理、主题、角色与外观、模型配置与密钥池、工具、插件与权限、工作流、文档、语言与维护
- [本仓库 #22](https://github.com/3316891527/Operit/pull/22) release: 准备 1.12.2+9 版本与发行说明；已合入；[提交 aac8a2eca557](https://github.com/3316891527/Operit/commit/aac8a2eca5578b434d633a9eec3e220558e1010a)；更新与离线资源
- [本仓库 #23](https://github.com/3316891527/Operit/pull/23) release: 同步 dev 并发布 1.12.2+9；已合入；[提交 8eacc9f4788a](https://github.com/3316891527/Operit/commit/8eacc9f4788ad84ad034bbb7c26d16e2bc6682c2)；来源排查与归并
- [本仓库 #24](https://github.com/3316891527/Operit/pull/24) feat(settings): 整合数据备份、主题与角色配置；已合入；[提交 083b1d1bce13](https://github.com/3316891527/Operit/commit/083b1d1bce137c74b55003f1f6f96f8e6a496a01)；主题、角色与外观、记忆空间与检索、工具、插件与权限、工作流、数据、备份与诊断
- [本仓库 #25](https://github.com/3316891527/Operit/pull/25) fix(toolpkg): 降低工具生命周期大结果的内存开销；未直接合并；实现经 #24 归并，不重复计项；工具、插件与权限
- [本仓库 #26](https://github.com/3316891527/Operit/pull/26) release: 将 dev 同步到 main 并发布 1.12.2+10；已合入；[提交 56bb1493113d](https://github.com/3316891527/Operit/commit/56bb1493113dce5f70bb7c5bccbfc936378c0784)；更新与离线资源
- [本仓库 #27](https://github.com/3316891527/Operit/pull/27) fix(tools): 细化包工具 JSON 参数错误及位置提示；已合入；[提交 fe8ff0e9292d](https://github.com/3316891527/Operit/commit/fe8ff0e9292d26486b20e1a96c680d1cac4ae202)；工具、插件与权限
- [本仓库 #28](https://github.com/3316891527/Operit/pull/28) fix(llm): 修复多工具交错输出和 Responses 最终参数遗漏；已合入；[提交 fe8ff0e9292d](https://github.com/3316891527/Operit/commit/fe8ff0e9292d26486b20e1a96c680d1cac4ae202)；消息渲染与生成控制、工具、插件与权限
- [本仓库 #29](https://github.com/3316891527/Operit/pull/29) fix(a2ui): 修复编译成功未展示并统一工具参数说明；已合入；[提交 fe8ff0e9292d](https://github.com/3316891527/Operit/commit/fe8ff0e9292d26486b20e1a96c680d1cac4ae202)；工具、插件与权限
- [本仓库 #30](https://github.com/3316891527/Operit/pull/30) fix(ui): 移除崩溃报告自动换行开关的灰色反馈；已合入；[提交 fe8ff0e9292d](https://github.com/3316891527/Operit/commit/fe8ff0e9292d26486b20e1a96c680d1cac4ae202)；数据、备份与诊断
- [本仓库 #31](https://github.com/3316891527/Operit/pull/31) feat(settings): 模型工作区加入完整上下文总结并整理功能分配；已合入；[提交 fe8ff0e9292d](https://github.com/3316891527/Operit/commit/fe8ff0e9292d26486b20e1a96c680d1cac4ae202)；模型配置与密钥池、上下文总结与用量统计
- [本仓库 #32](https://github.com/3316891527/Operit/pull/32) fix(terminal): 非零退出码统一返回失败并保留原始结果；未直接合并；源 HEAD 已在终点祖先集合；文件工作区与终端
- [本仓库 #33](https://github.com/3316891527/Operit/pull/33) chore: 整合记忆工作台等联调分支到 dev；已合入；[提交 fe8ff0e9292d](https://github.com/3316891527/Operit/commit/fe8ff0e9292d26486b20e1a96c680d1cac4ae202)；模型配置与密钥池、上下文总结与用量统计、语音、图片与媒体、记忆空间与检索、文件工作区与终端
- [本仓库 #34](https://github.com/3316891527/Operit/pull/34) fix: 修正记忆选择器并补齐八语种翻译；已合入；[提交 00014395f7ac](https://github.com/3316891527/Operit/commit/00014395f7ac2497b7934103997236a468cecdf7)；记忆空间与检索、文档、语言与维护
- [本仓库 #35](https://github.com/3316891527/Operit/pull/35) chore: 清理86项废弃资源并准备1.12.2+11；已合入；[提交 0c0293314e29](https://github.com/3316891527/Operit/commit/0c0293314e295a8e44f4c7c35075750e8a1ed8a8)；更新与离线资源、文档、语言与维护
- [本仓库 #36](https://github.com/3316891527/Operit/pull/36) release: 同步dev并发布1.12.2+11；已合入；[提交 cfa62ff1031b](https://github.com/3316891527/Operit/commit/cfa62ff1031b76fed7dc123ff1027e8761200d34)；来源排查与归并
- [本仓库 #37](https://github.com/3316891527/Operit/pull/37) fix(ui): 修复界面媒体交互并准备 1.12.2+12；已合入；[提交 5d234fb6e39c](https://github.com/3316891527/Operit/commit/5d234fb6e39c090623858eea1afa90c73d5f497e)；主题、角色与外观、语音、图片与媒体、记忆空间与检索、文件工作区与终端、数据、备份与诊断、更新与离线资源、文档、语言与维护
- [本仓库 #38](https://github.com/3316891527/Operit/pull/38) ci(release): 同步dev到main并准备1.12.2+12；已合入；[提交 4f88f59780e2](https://github.com/3316891527/Operit/commit/4f88f59780e2054d336e1bedf67672929219b5d2)；来源排查与归并
- [本仓库 #39](https://github.com/3316891527/Operit/pull/39) fix(chat): 拆分发送协程并完善取消收尾；已合入；[提交 e203baddd621](https://github.com/3316891527/Operit/commit/e203baddd62145cbeac5806b5c04fd6127e83fd5)；消息渲染与生成控制
- [本仓库 #40](https://github.com/3316891527/Operit/pull/40) fix(update): 按实际字节范围筛选镜像并校验补丁分段；已合入；[提交 e0f8f17105be](https://github.com/3316891527/Operit/commit/e0f8f17105beed8aa869e5738aeef802b50cd117)；更新与离线资源
- [本仓库 #41](https://github.com/3316891527/Operit/pull/41) fix(voice): 将音频理解限定为原话转写；已合入；[提交 4d6578070e13](https://github.com/3316891527/Operit/commit/4d6578070e1344d920edaea804c7b1e75bacfee5)；语音、图片与媒体
- [本仓库 #42](https://github.com/3316891527/Operit/pull/42) fix(chat): 缩小图片过期角标并将过期语音移出气泡；已合入；[提交 96b44e58e05f](https://github.com/3316891527/Operit/commit/96b44e58e05f7539f253cfa77d16787cb7a31f66)；语音、图片与媒体
- [本仓库 #43](https://github.com/3316891527/Operit/pull/43) feat: 语音原话与声音观察分离，累计用量按九类展示；已合入；[提交 3a232219da63](https://github.com/3316891527/Operit/commit/3a232219da63454491ef79394613378cbac23465)；主题、角色与外观、上下文总结与用量统计、语音、图片与媒体
- [本仓库 #44](https://github.com/3316891527/Operit/pull/44) fix: 保留终端失败输出并去除重复错误；已合入；[提交 76336d49b1c8](https://github.com/3316891527/Operit/commit/76336d49b1c8766e7cada4a34b5e88ac2014ddcf)；文件工作区与终端、工具、插件与权限
- [本仓库 #45](https://github.com/3316891527/Operit/pull/45) fix(chat): 对齐过期媒体标签并保留音频消息头像；已合入；[提交 4385d4aaef12](https://github.com/3316891527/Operit/commit/4385d4aaef129390169ec2c36236a3bddf7b0b0e)；语音、图片与媒体
- [本仓库 #46](https://github.com/3316891527/Operit/pull/46) fix(mcp): 改为手动启动并修复作者空值崩溃；已合入；[提交 fc74dbf78a93](https://github.com/3316891527/Operit/commit/fc74dbf78a939f1c77a858d2abefe2c79e4b44d5)；工具、插件与权限
- [本仓库 #47](https://github.com/3316891527/Operit/pull/47) ci(release): 准备 1.12.2+13 正式发行；已合入；[提交 f91ad450373b](https://github.com/3316891527/Operit/commit/f91ad450373b58e7d6bd2ef7891677fda26b93a6)；更新与离线资源
- [本仓库 #48](https://github.com/3316891527/Operit/pull/48) release: 同步 dev 到 main，发布 1.12.2+13；已合入；[提交 2bd3c8e41acb](https://github.com/3316891527/Operit/commit/2bd3c8e41acb4115134296679b5191b55598b481)；来源排查与归并
- [本仓库 #49](https://github.com/3316891527/Operit/pull/49) fix(chat): 防止流式输出露出隐藏协议 meta；已合入；[提交 cbd04afdb66d](https://github.com/3316891527/Operit/commit/cbd04afdb66d62e3123230382659c9740ab82110)；消息渲染与生成控制
- [本仓库 #50](https://github.com/3316891527/Operit/pull/50) fix(packages): 保留完整名称并固定四标签等宽布局；已合入；[提交 0a1860feb714](https://github.com/3316891527/Operit/commit/0a1860feb7146da45a5b8a38491e3b63f27f7bfe)；工具、插件与权限
- [本仓库 #51](https://github.com/3316891527/Operit/pull/51) docs(speech): 语音服务配置 HTML 交互设计稿；已合入；[提交 f80db2da93fd](https://github.com/3316891527/Operit/commit/f80db2da93fd51b816ac05f6efff482926760768)；来源排查与归并
- [本仓库 #52](https://github.com/3316891527/Operit/pull/52) fix(toolpkg): 避免 AI 语音条挂载时自动抢焦点；已合入；[提交 2c48f62f8f56](https://github.com/3316891527/Operit/commit/2c48f62f8f563617d82a628e90471917b7484294)；消息渲染与生成控制、工具、插件与权限
- [本仓库 #53](https://github.com/3316891527/Operit/pull/53) feat(settings): 落地语音服务原生工作区；已合入；[提交 f80db2da93fd](https://github.com/3316891527/Operit/commit/f80db2da93fd51b816ac05f6efff482926760768)；语音、图片与媒体
- [本仓库 #54](https://github.com/3316891527/Operit/pull/54) feat(resources): 离线资源工作区、模型选择与资源独立分发；已合入；[提交 541a1a144c6f](https://github.com/3316891527/Operit/commit/541a1a144c6fcaf807c15e6ccce228250e4d5295)；更新与离线资源
- [本仓库 #55](https://github.com/3316891527/Operit/pull/55) fix(chat): 修复流式重试后的工具卡片累积与回放保留；已合入；[提交 558632b41901](https://github.com/3316891527/Operit/commit/558632b41901d0c04c3e0a048f20dcd753c5b309)；消息渲染与生成控制
- [本仓库 #56](https://github.com/3316891527/Operit/pull/56) fix(storage): 修复数据管理角色卡与聊天列表头像显示；已合入；[提交 3de3d7567600](https://github.com/3316891527/Operit/commit/3de3d7567600036221892016358098719a0899d8)；主题、角色与外观、数据、备份与诊断
- [本仓库 #57](https://github.com/3316891527/Operit/pull/57) release: 同步 dev 并准备 1.12.2+14；已合入；[提交 42bbdb65e861](https://github.com/3316891527/Operit/commit/42bbdb65e861fbc6160ac570a0bc33c268c17fcc)；更新与离线资源
- [本仓库 #58](https://github.com/3316891527/Operit/pull/58) feat(distribution): 切换 +15 分仓发行并接入迁移与清理；已合入；[提交 93503038d45b](https://github.com/3316891527/Operit/commit/93503038d45ba89bb61c32cbb65b9ef801b9a038)；更新与离线资源
- [本仓库 #59](https://github.com/3316891527/Operit/pull/59) chore(release): 同步 1.12.2+15 并完成补丁与资源分仓；已合入；[提交 17e22f65f9fa](https://github.com/3316891527/Operit/commit/17e22f65f9fafbebe53db8dbadf493f5ec2b70fd)；更新与离线资源
- [本仓库 #60](https://github.com/3316891527/Operit/pull/60) fix(release): 修复迁移草稿按标签查询导致的 404；已合入；[提交 6383d701ff9d](https://github.com/3316891527/Operit/commit/6383d701ff9ddcbad1697dadcc72c90457e79422)；更新与离线资源
- [本仓库 #61](https://github.com/3316891527/Operit/pull/61) chore(release): 同步 +15 迁移草稿修复到 main；已合入；[提交 21a31bd14ad9](https://github.com/3316891527/Operit/commit/21a31bd14ad9532e81b99d9218669a23896ce2ee)；更新与离线资源
- [本仓库 #62](https://github.com/3316891527/Operit/pull/62) fix(packages): 按当前应用真实来源删除扩展并核实结果；已合入；[提交 3dd12589cae8](https://github.com/3316891527/Operit/commit/3dd12589cae8374379179f4eafb5a77c7daabe4f)；工具、插件与权限
- [本仓库 #63](https://github.com/3316891527/Operit/pull/63) fix(ui): 美化终端选项菜单并保持原导航；已合入；[提交 74c8010cf44e](https://github.com/3316891527/Operit/commit/74c8010cf44e4294a146b3f443425809d10f532c)；文件工作区与终端
- [本仓库 #64](https://github.com/3316891527/Operit/pull/64) fix(workflow): 展示失败节点名称、执行动作和具体原因；已合入；[提交 b1d7bc628cfb](https://github.com/3316891527/Operit/commit/b1d7bc628cfbe685f318ca31d1cfea8a3aeadf4b)；工作流
- [本仓库 #65](https://github.com/3316891527/Operit/pull/65) feat(market): 显示真实下载进度、速度和剩余时间；已合入；[提交 7c72bf2a3d5f](https://github.com/3316891527/Operit/commit/7c72bf2a3d5f327139e9f4db976b16b5ebb81815)；工具、插件与权限
- [本仓库 #66](https://github.com/3316891527/Operit/pull/66) fix(chat): 按故障位置细分请求错误并保留后台提示；已合入；[提交 6a4b9bfb399c](https://github.com/3316891527/Operit/commit/6a4b9bfb399c670321518089592ae3e02e38897e)；消息渲染与生成控制、数据、备份与诊断
- [本仓库 #67](https://github.com/3316891527/Operit/pull/67) refactor(layout): 整理职责目录、删除废弃界面并修复验收问题；已合入；[提交 3ff9a2a75755](https://github.com/3316891527/Operit/commit/3ff9a2a757554b9cd8e30c745656422016aaf1df)；文档、语言与维护
- [本仓库 #68](https://github.com/3316891527/Operit/pull/68) ci(release): 准备 1.12.2+16 与递增版本号 72；已合入；[提交 90981cefc2e1](https://github.com/3316891527/Operit/commit/90981cefc2e1e09fad482d256545b63d52dbeec7)；更新与离线资源
- [本仓库 #69](https://github.com/3316891527/Operit/pull/69) release: 同步 dev 并发布 1.12.2+16；已合入；[提交 dd0ea02a900f](https://github.com/3316891527/Operit/commit/dd0ea02a900f831a7b94ab3834539db8ae857bb2)；来源排查与归并
- [本仓库 #70](https://github.com/3316891527/Operit/pull/70) ci: 恢复完整发布工作流并启用 dev PR 检查；已合入；[提交 368c4efa7d1e](https://github.com/3316891527/Operit/commit/368c4efa7d1efbc580e1f0addd4b9121a613716e)；更新与离线资源、文档、语言与维护
- [本仓库 #71](https://github.com/3316891527/Operit/pull/71) ci: 同步完整工作流修复到主分支；已合入；[提交 998a9b00fa00](https://github.com/3316891527/Operit/commit/998a9b00fa0053e719b84c0bf4f5d431f83f5483)；更新与离线资源
- [本仓库 #72](https://github.com/3316891527/Operit/pull/72) fix(tools): 正确返回包激活失败与不存在错误；已合入；[提交 b60e94e33f1b](https://github.com/3316891527/Operit/commit/b60e94e33f1bf084866c630a998924aedf56fa6e)；工具、插件与权限
- [本仓库 #73](https://github.com/3316891527/Operit/pull/73) fix(resources): 升级保留已下载扩展及安装状态；已合入；[提交 5d0c465657b6](https://github.com/3316891527/Operit/commit/5d0c465657b634782c27646f150784a2142cfcd8)；更新与离线资源
- [本仓库 #74](https://github.com/3316891527/Operit/pull/74) ci(release): 准备 1.12.2+17 紧急修复发行；已合入；[提交 f6bc900a053c](https://github.com/3316891527/Operit/commit/f6bc900a053cb1e6ca3e42c99b8b23d2b17a3bb6)；更新与离线资源
- [本仓库 #75](https://github.com/3316891527/Operit/pull/75) release: 同步 dev 并发布 1.12.2+17 紧急修复；已合入；[提交 4179c6ac9810](https://github.com/3316891527/Operit/commit/4179c6ac9810b3b0ae32a40689a4787a893c0e64)；来源排查与归并
- [本仓库 #76](https://github.com/3316891527/Operit/pull/76) ci(android): 修复 PR Check 的 SDK 初始化；未直接合并；经 #83 集成，包含于 +17 最终来源；文档、语言与维护
- [本仓库 #77](https://github.com/3316891527/Operit/pull/77) fix(github): 保留终端非零退出的完整结果；未直接合并；经 #83 集成，包含于 +17 最终来源；工具、插件与权限
- [本仓库 #78](https://github.com/3316891527/Operit/pull/78) ci(resources): 复用固定资源并识别未公开草稿；未直接合并；经 #83 集成，包含于 +17 最终来源；更新与离线资源
- [本仓库 #79](https://github.com/3316891527/Operit/pull/79) fix(chat): 修正历史列表的整行动画绘制路径；未直接合并；经 #83 集成，包含于 +17 最终来源；聊天界面与对话管理
- [本仓库 #80](https://github.com/3316891527/Operit/pull/80) fix(ui): 适配上游 DSL 与设置页滚动状态修复；未直接合并；经 #83 集成，包含于 +17 最终来源；工具、插件与权限
- [本仓库 #81](https://github.com/3316891527/Operit/pull/81) fix(ui): 保留消息复制预览的选区和滚动位置；未直接合并；经 #83 集成，包含于 +17 最终来源；消息渲染与生成控制
- [本仓库 #82](https://github.com/3316891527/Operit/pull/82) fix(browser): 在主线程读取 ref 点击的 WebView 尺寸；未直接合并；经 #83 集成，包含于 +17 最终来源；工具、插件与权限
- [本仓库 #83](https://github.com/3316891527/Operit/pull/83) ci(debug): 汇总 +17 后续修复与真实扩展资源供统一测试；已合入；[提交 901471c562ce](https://github.com/3316891527/Operit/commit/901471c562ced9aea321526a49fb01e13f5955df)；聊天界面与对话管理、消息渲染与生成控制、工具、插件与权限、更新与离线资源、文档、语言与维护
- [本仓库 #84](https://github.com/3316891527/Operit/pull/84) fix(tools): 相同工具失败说明只输出一次；已合入；[提交 780c5849a315](https://github.com/3316891527/Operit/commit/780c5849a315d1c5ffc6e5a266e83a9827bf50a0)；工具、插件与权限
- [本仓库 #85](https://github.com/3316891527/Operit/pull/85) ci(release): 同步 +17 已验收修复并准备正式发行；已合入；[提交 2c5c43f16c27](https://github.com/3316891527/Operit/commit/2c5c43f16c27d574c7457bc2fb08e962e0eb1f12)；更新与离线资源
- [本仓库 #86](https://github.com/3316891527/Operit/pull/86) fix(speech): 修复离线语音崩溃与设置卡顿并完善试听配置；已合入；[提交 92a321bdfd7a](https://github.com/3316891527/Operit/commit/92a321bdfd7a74d46b763b2c87dd8af586757151)；语音、图片与媒体
- [本仓库 #87](https://github.com/3316891527/Operit/pull/87) feat(patch18): 统一工作区、消息、语音、工具计时、终端与资源管理改进；已合入；[提交 1962d3afbbba](https://github.com/3316891527/Operit/commit/1962d3afbbba332bbfcf42be6410d663d88c5b48)；消息渲染与生成控制、主题、角色与外观、语音、图片与媒体、文件工作区与终端、工具、插件与权限、数据、备份与诊断
- [本仓库 #88](https://github.com/3316891527/Operit/pull/88) chore(release): 准备 1.12.2+18 并固定补丁仓库发布范围；已合入；[提交 8df3befaf6dd](https://github.com/3316891527/Operit/commit/8df3befaf6ddf00282034c2153213f2dfdce12e2)；更新与离线资源
- [本仓库 #89](https://github.com/3316891527/Operit/pull/89) ci(release): 同步 1.12.2+18 到 main 并准备补丁仓库发行；已合入；[提交 01cb48ce4768](https://github.com/3316891527/Operit/commit/01cb48ce476835ceacbaf5d23c9d4b2244f4270d)；更新与离线资源
- [本仓库 #90](https://github.com/3316891527/Operit/pull/90) ci: 将构建工作流改名为 build 并更新入口；已合入；[提交 ffb78d3017df](https://github.com/3316891527/Operit/commit/ffb78d3017df07329b8de37af45a4f17e6800d6d)；更新与离线资源、文档、语言与维护
- [本仓库 #91](https://github.com/3316891527/Operit/pull/91) ci: 同步 build 工作流改名到 main；已合入；[提交 cb30c94469e1](https://github.com/3316891527/Operit/commit/cb30c94469e1740b1be609905c12bd639555d168)；更新与离线资源
- [本仓库 #92](https://github.com/3316891527/Operit/pull/92) refactor(tools): 收拢三档权限并按清单整理工具目录；已合入；[提交 6517f5159330](https://github.com/3316891527/Operit/commit/6517f5159330d1a6c352f1eebb360f169354b363)；工具、插件与权限、文档、语言与维护
- [本仓库 #93](https://github.com/3316891527/Operit/pull/93) fix(workspace): 优化文件操作内存并显示进度；已合入；[提交 6517f5159330](https://github.com/3316891527/Operit/commit/6517f5159330d1a6c352f1eebb360f169354b363)；文件工作区与终端
- [本仓库 #94](https://github.com/3316891527/Operit/pull/94) refactor(chat): 按职责整理聊天和工作区目录；已合入；[提交 4bbea330b19d](https://github.com/3316891527/Operit/commit/4bbea330b19df411a93d4704d8e8fe12c8a167b6)；文档、语言与维护
- [本仓库 #95](https://github.com/3316891527/Operit/pull/95) ci: 集成工具权限、文件操作与聊天目录整理；已合入；[提交 6517f5159330](https://github.com/3316891527/Operit/commit/6517f5159330d1a6c352f1eebb360f169354b363)；文件工作区与终端、工具、插件与权限、文档、语言与维护
- [本仓库 #96](https://github.com/3316891527/Operit/pull/96) fix(ui): 主题和外观标签使用“界面”名称；已合入；[提交 f42e8bc06e7e](https://github.com/3316891527/Operit/commit/f42e8bc06e7e9d93946dfedada977f2dbffffd9a)；主题、角色与外观
- [本仓库 #97](https://github.com/3316891527/Operit/pull/97) feat(terminal): 内置 apply_patch 并支持覆盖保护与差异预览；已合入；[提交 05811804cdee](https://github.com/3316891527/Operit/commit/05811804cdeec36a686c5b1ebd65977f5b938426)；文件工作区与终端
- [本仓库 #98](https://github.com/3316891527/Operit/pull/98) fix(voice): 修正硅基流动音频格式及识别重试通知；已合入；[提交 3e37884024c2](https://github.com/3316891527/Operit/commit/3e37884024c244a565893fbc017c214ac33b5a61)；模型配置与密钥池、语音、图片与媒体
- [本仓库 #99](https://github.com/3316891527/Operit/pull/99) feat(theme): 增加角色主题脚本接口与原生主题工作室；已合入；[提交 85f02f7a4c9f](https://github.com/3316891527/Operit/commit/85f02f7a4c9f630b0bcdfb128bde1d8e62e9b293)；主题、角色与外观
- [本仓库 #100](https://github.com/3316891527/Operit/pull/100) fix(chat): 修正单密钥与空池请求被误报为密钥池耗尽；已合入；[提交 5bf033a35da8](https://github.com/3316891527/Operit/commit/5bf033a35da8511a098bf233392c3037a518bb5b)；模型配置与密钥池
- [本仓库 #101](https://github.com/3316891527/Operit/pull/101) fix(chat): 实时提示总结重试并改用 OKToast；已合入；[提交 53833ba27bb6](https://github.com/3316891527/Operit/commit/53833ba27bb6caea1ac5e7ecae943e33fbce4b81)；上下文总结与用量统计
- [本仓库 #102](https://github.com/3316891527/Operit/pull/102) fix(chat): 简化附件成功提示并支持点击关闭；已合入；[提交 f562fc8efb8f](https://github.com/3316891527/Operit/commit/f562fc8efb8f215722b95cab118475fcebfa1b96)；数据、备份与诊断
- [本仓库 #103](https://github.com/3316891527/Operit/pull/103) chore(release): 准备 1.12.2+19 发行；已合入；[提交 e829418743de](https://github.com/3316891527/Operit/commit/e829418743de17a23e5cc9efe1caf8ef19735f0f)；更新与离线资源
- [本仓库 #104](https://github.com/3316891527/Operit/pull/104) chore(release): 同步 1.12.2+19 到 main；已合入；[提交 890e532070f1](https://github.com/3316891527/Operit/commit/890e532070f14eee8b21084bbedbe5c9ab6cb7e1)；更新与离线资源

</details>

## 上游引用与其他来源

#83 还按说明适配了[上游 #1411](https://github.com/AAswordman/Operit/pull/1411) 的 DSL 列表与设置页状态。此处记录人工适配来源，不声称上游整条提交历史都已合入。终端核心作为子模块独立维护，主仓库中固定引用和相关发布说明是相应版本的追溯入口。

没有 PR 或没有保留 PR HEAD 的直接变更同样收录在提交附录。原生 A2UI、首次启动、超长消息回填、固定签名与早期更新器等，均由实际提交和补丁说明归档，不要求先找到一条同名 PR 才纳入。

## 持续维护方法

1. 新补丁完成后先固定源码提交，追加补丁时间线，保留旧截止点和历史条目
2. 新能力补入正文对应模块；修复已有能力时直接调整最终描述，并在来源中补充提交或 PR
3. 将新增提交接到当前附录末尾，核对已覆盖集合与新快照可达集合，避免只抄 PR 标题
4. 设备验收、CI 结果和已知限制继续引用功能原记录，不能由“已合并”推导为“全量通过”
5. 1.13.0 发布前再确定品牌、应用 ID、版本号与安装迁移安排，检查正文是否仍与最终源码一致
