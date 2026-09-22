# Java代码读取模块

本组维护当前JDT工具Interface、声明/导航合同、共享索引及03→04→05接线。固定八步骤不变；`analysis.code`是内部模块，不是第九步。当前生产只启动JDT，JavaParser、严格图/Fact/Flow/Capsule/M10生成已退役。

## 1. 业务目标与模块分工

下游模型应能看见完整Controller/Service、所有调用候选、参数、条件和返回，以及相关Mapper/XML，不必重新扫描代码。程序不从方法名推断采购、销售或流程顺序。

| 模块 | 输入 | owned操作 | 输出/消费者 |
| --- | --- | --- | --- |
| Engine配置/Factory | 唯一YAML的工具字段 | 严格解码，新启动只接受JDT | 固定有效工具配置、JDT session |
| JdtProjectSession | 冻结project、静态模块画像、本地受控classpath | 隔离投影、启动/就绪、会话缓存 | 同一catalog/Core/LS读取能力 |
| JdtSyntaxReader | 完整冻结Java与source level | Core取声明、完整正文、语法范围与注解身份 | catalog、MethodCode、call/control/exit |
| JdtNavigationResolver | 精确调用navigationSite | hierarchy/definition/implementation、候选归一 | 目标与具体限制 |
| EntryCodeCollector | 精确EntrySeed | 按入口展开已导航候选、共享方法、保留调用归属 | EntryCodeContext与成员 |
| Step03 publisher/reader | 不可变索引 | 一次保存/严格重开 | java-code-index-v2，供04/05 |

详细算法见[JDT模块](jdt-engine.md)，字段/配置见[共同合同](contracts-and-configuration.md)，生产与历史路径见[接入与历史读取](integration-and-javaparser.md)。

## 2. 当前接力

```text
冻结源码与精确入口
 → JDT LS/Core → Step03 JavaCodeIndex
 → Step04 可选PersistenceMaterialIndex
 → Step05 CodeReadingMaterialSet保存/重开
 → Step06目标：模型投影/阅读/Activity
 → Step07：保存Activity与来源的过程发现
```

Step04拥有可选MyBatis/JSqlParser；Step05是唯一材料owner；Step06只重开固定材料并做模型投影。JDT不知道Provider、Prompt、retry或业务范围，不因模型失败再次启动。

## 3. 必须保留的正确性

- 精确methodKey + SourceRange定位入口；handler名称不能区分重载。
- JDT LS决定调用候选，Core给完整原文与语法；不自研通配import、继承或Spring分派。
- hierarchy已命中仍按声明属性查询必要implementation；多个实现全部保留。
- 方法全仓共享；CALL按entryId+物理callKey保存，入口展开状态不互相覆盖。
- 缓存同一冻结就绪会话、同操作/位置原始结果，不省略不同操作，不将错误当空目标。
- controls/exits是语法提示，不是路径Proof；完整条件/return/throw不可裁成几行。
- 不运行客户构建、插件、应用、数据库或动态表达式；缺依赖保留未知。
- 保存/重开核对身份、bytes/schema/ref，不重新运行producer。
- JDT index v2已有technicalEnhancements字段原义不变，但不再新装Fact accounting。

## 4. 当前实测与未验证范围

2026-09-18固定jshERP新01–05验收完成：326入口有处置，325有上下文，1个definition超时；9,189方法、51,675入口调用、28,033真实RPC、115,250缓存命中。插件启用取得61 XML/573 statements；关闭插件同索引读取不再次JDT。阅读材料325包/326覆盖；模型调用0。完整记录见[交付核验](../../supplements/jdt-persistence-reading-materials-delivery.md)。

旧326个已审Activity及原M10是保留的历史业务结果；它们不是本次新XML材料的模型验收。新Step06投影/大包阅读/retry尚未实现，见[Activity目标](../activity-explanation/README.md)。动态分派、反射、缺依赖、生成代码和外部实际效果仍按原边界未知；目录/Activity数量不能证明过程质量。

## 5. 开发与验收

Astra/ultra负责方向、文档、debug；Terra/xhigh写直接行为测试；Sol/xhigh实现。普通fixture无真实工具/模型；真实JDT仅单独授权入口，重型命令串行。只跑新增或直接覆盖测试；当前文档改动不授权源码扫描、工具运行、产品调用或提交。

现行步骤详细职责分别由[03](../../analysis-steps/03-program-graphs.md)、[04](../../analysis-steps/04-proven-code-facts.md)、[05](../../analysis-steps/05-business-flows.md)拥有。本组不复制另一套配置、材料或业务执行owner。
