# JDT、MyBatis与SQL阅读材料实施计划

状态：2026-09-18，步骤0–7实现及验收完成。469项clean CI通过；真实01–05运行结束，325包/326条覆盖，1个导航失败明确保留。结论见[交付核验](../supplements/jdt-persistence-reading-materials-delivery.md)，不表示已执行第6步或业务质量验证。详细合同以[统一材料设计](../supplements/cross-object-process-reconstruction/jdt-persistence-reading-materials.md)为准。

## 目标与边界

第3步JDT导航 → 第4步可选MyBatis/JSqlParser补全 → 第5步一次组织、保存、重开和导出 → 停止。

不重做JDT/Core/cache，不执行第6–8步、Provider、客户构建或数据库。产品模型调用none。保护旧冻结源、326条已审Activity、全部模型结果及more-findings.md。前次三阶段业务解释的未提交实现单独保留，不能作为本轮修改或清理。

## 工作与验收

| 步骤 | 工作 | 完成条件 | 连续工时初估 |
| --- | --- | --- | --- |
| 0 | 保存基线、核对远端、正式目录实现分支 | 本轮设计独立可回退；其他改动有恢复副本 | 0.5–1h |
| 1 / A | MyBatis官方读取/include部件、JSqlParser最小实验 | 真实getFinishNumber、insertSelective、include/foreach及中性fixture；原文/动态条件/SQL AST和自动关联可读；明确通过或失败 | 2–4h |
| 2 / B | analysis.persistence与可选YAML插件 | 已保存Java索引→Mapper XML/依赖/参数/SQL结构；多候选保留，无重新导航 | 4–6h |
| 3 / B | analysis.material唯一组包模块 | 完整Java/接口/XML单元，引用持久化、覆盖、无重扫重开与文本投影 | 5–8h |
| 4 / B | store、state、output、CLI接线 | 正式plan-materials止于05；历史严格读取，模型未初始化 | 4–6h |
| 5 / B | 新路径通过后清理 | JavaParser/Fact/Proof/严格Flow/Capsule/M10生产路径退役；历史DTO/reader保留 | 3–5h |
| 6 / B | 直接回归与模块本地CI | 插件关闭、历史字节、内容/候选/参数不丢、零模型等检查通过 | 2–3h |
| 7 / C | 固定完整仓库1–5验收 | 新材料/可读入口样本/差异/缺口/耗时交付；停止讨论第6步 | 1–2h核验+扫描 |

工程准备初估21.5–35h；下载/扫描另计，产品模型0。A后校准；步骤3完成即展示完整材料。A未通过不进入B，不自动换工具或自研动态SQL运行器。

## 具体接线

- `PersistenceAnalyzer.analyze(PersistenceAnalysisRequest)`接已验证Java索引、冻结读取器、Mapper目录和配置；MyBatis是唯一首版Adapter。未配置/空plugins不解析。SQL不支持保留XML原文和原因；不执行OGNL/getBoundSql/客户类。
- `CodeReadingMaterialBuilder.build(CodeReadingMaterialRequest)`接入口、Java/持久化索引和显式profile；不接受Provider/parser/JDT。完整单元按稳定顺序选择；maxPacketUtf8Bytes按真实UTF-8投影计，maxEntriesPerPacket控制组包，不继承逐行截断。未放入的单元有具体原因。
- 保持Step03 module7/index-v2；Step04新module4 persistence-analysis/index-v1；Step05新module4 code-reading-materials/set-v1；state-v4、output-v5/READING_MATERIALS_ONLY。旧存储槽位仅作位置，不继续执行旧算法。
- 同步确切地址、文件集合、前驱、策略、身份、保存器和读取器；旧M10/output-v3/v4按旧合同读取，新材料不冒充M10，不接Activity。
- `source-analysis --config <absolute> plan-materials`完成01–05；inspect显示材料完成/业务未执行；artifact可按同一reader导出reading-materials.md，预览不是第二个canonical payload。
- Mapper目录重复namespace改为候选/局部诊断，不丢Spring入口；旧schema无法承载时明确新版本，HTTP入口合同不变。
- 先证明新路径可用，再按消费者清单删除旧代码/专属测试；有效的来源、保存、内容、候选和零重扫断言迁移。研究依赖不得进入生产。

## 验证与交付

直接RED→GREEN；测试Luna/xhigh、实现Terra/xhigh、调试Sol/xhigh。最多两个工作Agent；重型命令一个。保留每Agent当前plan progress，最终将结论收纳到设计/交付记录后仅清理本plan交接。

A记录实际固定依赖版本、API返回、原XML与解析投影、支持范围、安全测试和耗时。XML安全以本地拒绝/计数验证，不发外网测试请求。

C使用既有完整固定快照8c30ce7861570458920175e200bb2a6442713580。正式CLI执行一次完整链；插件开/关经正式模块Interface复用同一Java索引，不新增导航恢复CLI。实际入口逐项处置，不硬编码326。采购/销售/调拨仅作为查看样本，不预置业务阶段。工具断开后重开，06–08/模型/客户构建/数据库调用均0。

完整本模块最终命令（本计划授权范围）：

```bash
mvn -t .mvn/toolchains.local.xml -Pquality clean spotless:check verify
```

格式化/质量宿主JDK21+，应用Java17。默认不启用real-jdt-it；C具名工具验收另行串行执行，不运行外层工程。

最终一个实现PR，不等待远端CI、不强推。运行数据和本机配置不提交。交付A报告、新材料检查点/Markdown、内容/大小/耗时对照、清理清单与未解决范围；不宣称业务文档质量已通过。
