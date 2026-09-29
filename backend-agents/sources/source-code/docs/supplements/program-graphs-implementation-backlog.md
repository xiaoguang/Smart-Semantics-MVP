# 旧五图与Fact路线：退役说明

本页不再是活跃实施backlog。此前仍把JavaParser、五图builder、Fact/Proof和Flow/Capsule生成写成当前能力或待扩展算法，与已退役实现冲突；2026-09-26原位清除此类指令，不重建已经删除的算法。

当前Java路线为JDT LS导航＋Core语法/完整正文；持久化补全用MyBatis安全XML部件与JSqlParser；Step05组织完整技术材料。详细owner分别是[Step03](../analysis-steps/03-program-graphs.md)、[Step04](../analysis-steps/04-proven-code-facts.md)、[Step05](../analysis-steps/05-business-flows.md)。

当前目标是由用户或获授权的Agent使用官方Maven命令导出逐模块编译classpath，分析程序核验该外部输入并交给JDT；同时接通每模块环境、有限的Vue请求关联和三个技术操作，不是五图重做，见[技术设计](../modules/technical-analysis/README.md)。Java程序不负责Maven解析、下载或运行客户构建。依赖修正使用现有Step03能力做具名调用验收；是否另增调用一致性算法不在本轮。

剩余历史typed readers/DTO/专属测试按[清理设计](../plans/technical-analysis-cli-and-vue-cleanup-design.md)逐项核对消费者。没有实际用途的删除；历史Activity/材料/报告仍需要的最小读者保留。`ProgramGraphsExecution`等当前JDT代码不能因旧包名被误删。

旧图/Fact运行文件和Git历史保留原义，不改写成新技术或业务验收。本次仅纠正文档，未删除生产代码或历史结果。
