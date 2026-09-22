# 历史九章文档读取与渲染

> 固定key：nine-section-document；目录：steps/08-nine-section-document/。保留八步注册与历史合同；当前无Step08模型生成器，本轮目标结束于Step07。

## 职责与范围

严格重开已有九章checkpoint、查询结构化报告和来源，按对应历史版本确定性生成Markdown。读取/重渲染不调用Provider、不读新Activity解释、不发现过程、不升级旧结论。

此前消费singleton Process/旧RepositoryBusinessKnowledge的生成器已退出生产。历史340个单Activity/单Stage过程及其报告不能证明新跨活动业务理解完成。未来九章生成需要独立需求/设计/实施范围，本页不规定待执行模型任务。

## 输入、输出与渲染

输入为完整历史BusinessReport checkpoint、来源映射、验证记录和精确receipt/producer/Schema/basis。不得从新Step05、旧Activity或新Step07静默补齐历史字段。

| 文件 | 作用 |
| --- | --- |
| business-report.json | 当时完整已审九章结构 |
| source-refs.jsonl | 短引用→冻结文件、行段和原文 |
| document.md | 对应版本确定性Markdown |
| report-validation.json | 当时章节、引用、覆盖与审阅状态 |

历史H2顺序固定：文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、指标口径、示例问题、待确认事项。不得增加第十章塞来源或改写已审正文、certainty、条件/规则/公式。

正文保留业务段落和短ref，源码在独立source-refs.jsonl，不恢复已删除的同文档源码锚点。Step07的business-processes.md/sources.md是另一产物；新Activity来源/schema不重写历史九章。

## 失败、复用与验证

来源/身份/hash/Schema不符、必需文件缺失、章节/引用非法时失败并保留旧记录，不自动补章、补来源、换输入或调用模型。旧报告的未知和未处理范围照实保存，结构正确不等于语义通过。

同一已验证报告和同版本renderer产生相同字节；只有修改直接读取/排版代码时才做定向验证。本轮仅文档，未测试、生成或重新保存历史报告。
