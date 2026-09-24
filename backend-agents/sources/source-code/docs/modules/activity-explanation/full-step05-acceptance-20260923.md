# 已保存Step05材料生成全量Activity：验收记录

## 输入与边界

- 固定输入：`jdt-persistence-acceptance-20260917/materials-state-v4.json`，SHA-256 `968fc339c5dbd876ce16e7ca9a26ce78e406ad37ae2c4135ca55a069c4a09bb5`。
- 输入含325个材料包、326个入口处置；其中1个入口原先因`JDT_QUERY_FAILED`没有材料包。本轮没有重跑JDT、Step01–05、Builder或旧Activity。
- 业务模型使用现有登录的`gpt-5.6-terra/xhigh`，全局与服务并发均为4；没有API-key回退，也没有启动真实Step07过程生成或九章生成。
- 旧326条Activity JSONL的SHA-256仍为`6b63f48276cd6b772ba7c46ece7399ac3a3aeb90f70c7d7a81efab22be698052`；`more-findings.md`仍为`59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e`。

## 两例与全量结果

真实`/user/logout`小包完成一次DRAFT及完整REVIEW，验证小包直接读取；真实`/depotHead/addDepotHeadAndDetail`大包经阅读选择后完成DRAFT及完整REVIEW，模型请求包含取得的Service实现和保存的XML/SQL材料。两例只用于决定是否扩大运行，不登记成全仓成功。

全量模型批次为`analysis-run:e00cf448733fc078fe460e84b2ea41f51aec85347a4240a49154087748dfa2f9`，M11回执为`module-receipt:5360f1f47cc4c7b4f5016d85d97978f08e4e999b9b4f923c5705e4dd458a8700`。正式产物在本机忽略的`.workspace/jdt-persistence-acceptance-20260917/stores/runs/`下，未纳入Git。

| 核对项 | 实测 |
| --- | ---: |
| 已处理的现有材料包 | 325/325 |
| 新版已审Activity | 418 |
| 有材料且已解释的入口 | 325，均记为`ANALYZED_WITH_GAPS` |
| 无材料入口 | 1，`NOT_ANALYZED`，原因是原有`JDT_QUERY_FAILED` |
| 入口处置记录 | 326/326 |
| `semanticDeliveryStatus` | `PARTIAL` |
| 最终模型批次生命周期 | `FINISHED` |

`FINISHED`表示这次选定的325包任务执行结束，不把`PARTIAL`改写成完整业务理解。`ANALYZED_WITH_GAPS`明确保留未读辅助调用、导航限制或局部范围限制；418条Activity也不等于418个完整业务过程。

### 后续离线范围核对（2026-09-24）

新增的零模型调用核对批次`analysis-run:cb1ec0d7063db8c527d10ff41ca98c2ed7ffe17b69757ce698aa8909282ed8e2`直接重开上述418条Activity及私有阅读、生成、审阅记录，没有改写原批次。按最终必需阅读范围核对，325包中311包`COMPLETE`，14包`INCOMPLETE`，合计24个范围尚不能认定完成。上表的“已处理325/325”仅指当时全部包均结束执行，并非325包的每个最终阅读范围都已解释。

14包缺口均记为`ACTIVITY_SLICE_RESULT_UNEXPLAINED`，不是本次离线核对发生网络超时。少数旧Activity与缺口使用相同切片名称，但其已保存生成请求对应较早版本的阅读范围；最后一次阅读决策改变了同名范围所需材料，因此不能仅按名称认领为最终范围的结果。定向补做须使用新的模型批次并保留原311包、旧418条Activity和所有私有记录；完成前Step07不能把该输入宣称为全范围完整。本节是对旧验收范围的补充核对，不重新解释或覆盖历史业务正文。

## 重试与耗时

最终批次从上一已结束批次显式复用317个完整已审包，只重新执行8个失败包；它们原先均在阅读决策中触发`ACTIVITY_READING_SLICE_DUPLICATE`。原因是模型在后续决策中使用稳定切片键修订范围，旧程序却把跨轮修订当作同轮重复；修复后同轮重复仍拒绝、跨轮修订替换旧范围，超容量修订保留先前可用范围。8包在最终批次全部完成，没有新失败。

最终批次实际保存了29次阅读决策、69次DRAFT、69次REVIEW，共167条已启动且结果为`SUCCESS`的模型阶段记录；重用的317包没有重新调用模型。从首次请求记录到批次结果发布约63分钟。按文件时间统计，阅读、DRAFT、REVIEW阶段各自的累计持续时间分别为1,866、4,508、5,255秒；这些请求并行执行，累计时间不能与63分钟墙钟时间相加。

先前失败批次、诊断小包及其原始请求/响应均保留在本机私有journal；最终批次的成功不覆盖那些历史记录。

## 离线验证

- 正式CLI `artifact --key ACTIVITY_COVERAGE`从已保存M11重新打开，返回326条入口处置，计数与磁盘产物一致；不初始化产品Provider。
- Step07新旧材料来源的Corpus与协议测试通过：新Activity按Step05来源读取，历史Activity仍按M10读取；本轮没有真实过程模型调用。
- 隔离构建目录执行`mvn -t .mvn/toolchains.local.xml -Pquality clean spotless:check verify`，在允许测试HTTP替身绑定本机回环端口的环境下退出码为0；Surefire为544个测试、0失败、0错误、0跳过，Spotless、SpotBugs与PMD正常完成。首次无回环权限运行的3个`SocketException`是沙箱环境错误，随后原命令在允许回环的执行边界通过，没有删除或跳过测试。
- `git diff --check`通过；固定材料、旧Activity和`more-findings.md`的哈希保持不变。

## 不在本轮完成的事

没有材料包的那个JDT入口仍未解释；要消除它，需要另行核对并可能重新取材。本轮Step07只验证读取适配，不用新版418条Activity重新生成业务过程或九章。后续过程模型能否提升业务文档质量，不能从本轮Activity数量或自动化测试通过直接推断。
