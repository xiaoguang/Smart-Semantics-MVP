# 管伊佳 MySQL 真实审阅节选生成记录

## 生成身份

- 任务／会话参考：`2026-08-20-guanyijia-curated-real-review/task-1`（本次 Codex ChatGPT session）
- 登录检查：`codex login status` 返回 `Logged in using ChatGPT`；未使用 API key、外部网络或计量 API。
- 模型：`gpt-5.6-luna`
- 推理强度：`xhigh`
- Prompt 版本：`curated-source-review-v1`
- 输入摘要：`sha256:5530d4c103e82ddac9907d7ccce9232e18bb392e55f880b77f6e22d9cce44b49`
- 输出摘要：`sha256:9ece6fb99c7933bcdcd012a0ee3f936b8714f3f336c3a07b492027d31c3ccd66`

## 冻结输入

本次内容 authoring 只读以下两个已冻结根目录：

- `../modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79`
- `../modeling-evidence/guanyijia/repositories/github/jishenghua/jshERP/snapshots/20260806193218Z-6f5194dc2836`

没有读取 `samples/`、`profiles/`、查询结果、绑定参数或任何 Tenant 153 业务行。运行时只消费提交到原型仓库的冻结 TypeScript 对象，不回读上述归档。

## 内容范围与计数

生成对象为 `generatedCuratedMysqlReview`，覆盖标签为 `30 / 95 张表`，Markdown 含九个固定章节，包含 48 条唯一 trace link 和 43 条证据：

- 39 条 `OBSERVED`：30 张选定表的 DDL、6 个选定存储过程、索引集合、显式外键集合和参数化 DML 摘要。
- 3 条 `FROZEN_RECORD`：负库存界面词汇、欠款字段 schema 记录、单据状态界面词汇。GitHub 只作为带精确文件行定位的冻结结构化记录，不宣称完整源码原文。
- 1 条 `GAP`：官方状态 9 原始资料缺口。

选定表为：`jsh_account`、`jsh_account_head`、`jsh_account_item`、`jsh_depot`、`jsh_depot_head`、`jsh_depot_item`、`jsh_function`、`jsh_in_out_item`、`jsh_material`、`jsh_material_attribute`、`jsh_material_category`、`jsh_material_current_stock`、`jsh_material_extend`、`jsh_material_initial_stock`、`jsh_material_property`、`jsh_msg`、`jsh_orga_user_rel`、`jsh_organization`、`jsh_person`、`jsh_platform_config`、`jsh_role`、`jsh_serial_number`、`jsh_supplier`、`jsh_sys_dict_data`、`jsh_sys_dict_type`、`jsh_system_config`、`jsh_tenant`、`jsh_unit`、`jsh_user`、`jsh_user_business`。

选定过程为：`sp_rebalance_below_low_stock_requisition`、`sp_rebalance_over_high_stock_sales`、`sp_run_inventory_stock_rebalance`、`sp_run_retail_return_rate_and_fact_sync`、`sp_run_store_retail_return_coverage_and_fact_sync`、`sp_sync_retail_out_fact_batch`。

## 内容质量复核

后续内容复核仍只重用已冻结对象中的字段行和存储过程定义行：30 条表 trace 改为简短的人类可读说明，保留实际关键字段；正文新增 6 个 `sql` 围栏摘录，覆盖财务子表、单据主表、单据明细、当前库存表以及两个真实库存／事实同步过程片段。Task 2 复审后仅修正 3 条 GitHub FROZEN_RECORD 的事实与精确 locator、4 条治理 Markdown 语句及其摘要；证据数量、trace 数量和 30 / 6 覆盖保持不变。

本次正文 hash 为 `sha256:bd812cc828597b687aaf163d5cc244f3900c254d74a3d28c27747225a8afb6df`；生成对象输出摘要按 `guanyijia-curated-mysql-review/output/v1` canonical payload（正文、Markdown 摘要、证据、trace 和不含自身的生成元数据）重算为 `sha256:9ece6fb99c7933bcdcd012a0ee3f936b8714f3f336c3a07b492027d31c3ccd66`。三条 GitHub 记录分别定位到 `SystemConfigList.vue.json#L35-L37`、`schema/tables/jsh_depot_head.sql#L27-L29` 和 `PurchaseOrderList.vue.json#L30-L35`；迁移历史 JSON 只含索引元数据，未被写成迁移语句。六个 SQL 围栏均是相应 OBSERVED 摘录中的连续原文：四个表范围为 `jsh_account_item#L3-L11`、`jsh_depot_head#L6-L32`、`jsh_depot_item#L3-L25`、`jsh_material_current_stock#L3-L7`，另有两个过程摘录。复合索引记录精确定位 `constraints/indexes.sql#L112-L113`，DML 记录精确定位 `dml/digests.sql#L3`；没有加入业务行、样本、profile、查询结果或绑定参数。

## 脱敏与真实性检查

- DDL 只删除 `AUTO_INCREMENT=<number>` 的环境计数，保留列定义和其余源行。
- 存储过程保留开头的真实定义片段；参数名和逻辑字节没有被改写为业务样例。
- DML 摘要移除 execution count 行；不携带结果行、绑定值、时间统计或原始业务行。
- `OBSERVED` 的 `excerptSha256` 对应已保存的冻结原始摘录字节；`FROZEN_RECORD` 的摘要只绑定保存的结构化记录载荷，不得被描述为完整源码、迁移原文或官方原文。Markdown 的 `markdownSha256` 与正文一致。
- 每一条证据（包括 GAP）都有唯一 trace 前缀和可定位 Markdown 锚点；正文明确排除业务行、样本、profile、查询结果和绑定参数。
- 三条固定治理议题均在第 9 节内联：负库存配置字段与“支持负库存”词汇的互补资料及目标制度、欠款字段结构差异、单据状态时间漂移风险与官方 GAP。

该资产是一次性内容 authoring 结果，不是运行时生成器，也不改变正式 V1 编译物、Pinned Bundle、workbench/compiler 或交付链。
