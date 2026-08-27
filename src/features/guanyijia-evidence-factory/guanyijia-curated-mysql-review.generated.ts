const legacyCuratedMysqlReviewDraft = {
  "schemaVersion": 1,
  "sourceId": "guanyijia_mysql",
  "snapshotId": "20260813T032528Z-abb0502c7d79",
  "title": "数据库建模审阅（真实证据节选）",
  "coverageLabel": "30 / 95 张表",
  "markdown": "# 数据库建模审阅（真实证据节选）\n\n## 1. 文档说明\n\n<!-- curated-g1 -->\n- [TRACE:G1] 这是针对冻结 MySQL 快照的只读审阅投影；正文只呈现可定位结构片段。\n<!-- curated-g2 -->\n- [TRACE:G2] 本投影不改写正式 V1 编译物、版本、冲突决定或交付链。\n## 2. 业务目标\n\n<!-- curated-g3 -->\n- [TRACE:G3] 目标是把数据库结构、扩展过程和跨源差异放在同一份可复核文档中；没有业务行分布结论。\n## 3. 业务对象\n\n<!-- curated-e001 -->\n- [TRACE:E001] `jsh_account` 账户主数据；`name`、`current_amount`、`enabled`、`tenant_id`。\n<!-- curated-e002 -->\n- [TRACE:E002] `jsh_account_head` 财务主表；`bill_no`、`bill_time`、`change_amount`、`status`、`tenant_id`。\n<!-- curated-e003 -->\n- [TRACE:E003] `jsh_account_item` 财务子表；`header_id`、`bill_id`、`need_debt`、`finish_debt`、`each_amount`、`tenant_id`。\n\n```sql\n  `header_id` bigint NOT NULL COMMENT '表头Id',\n  `account_id` bigint DEFAULT NULL COMMENT '账户Id',\n  `in_out_item_id` bigint DEFAULT NULL COMMENT '收支项目Id',\n  `bill_id` bigint DEFAULT NULL COMMENT '单据id',\n  `need_debt` decimal(24,6) DEFAULT NULL COMMENT '应收欠款',\n  `finish_debt` decimal(24,6) DEFAULT NULL COMMENT '已收欠款',\n  `each_amount` decimal(24,6) DEFAULT NULL COMMENT '单项金额',\n  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单据备注',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n```\n<!-- curated-e004 -->\n- [TRACE:E004] `jsh_depot` 仓库主数据；`name`、`principal`、`enabled`、`tenant_id`。\n<!-- curated-e005 -->\n- [TRACE:E005] `jsh_depot_head` 单据主表；`number`、`create_time`、`oper_time`、`status`、`tenant_id`。\n\n```sql\n  `number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '票据号',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `oper_time` datetime DEFAULT NULL COMMENT '出入库时间',\n  `organ_id` bigint DEFAULT NULL COMMENT '供应商id',\n  `creator` bigint DEFAULT NULL COMMENT '操作员',\n  `account_id` bigint DEFAULT NULL COMMENT '账户id',\n  `change_amount` decimal(24,6) DEFAULT NULL COMMENT '变动金额(收款/付款)',\n  `back_amount` decimal(24,6) DEFAULT NULL COMMENT '找零金额',\n  `total_price` decimal(24,6) DEFAULT NULL COMMENT '合计金额',\n  `pay_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '付款类型(现金、记账等)',\n  `bill_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单据类型',\n  `remark` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `file_name` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '附件名称',\n  `sales_man` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '销售员（可以多个）',\n  `account_id_list` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多账户ID列表',\n  `account_money_list` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多账户金额列表',\n  `discount` decimal(24,6) DEFAULT NULL COMMENT '优惠率',\n  `discount_money` decimal(24,6) DEFAULT NULL COMMENT '优惠金额',\n  `discount_last_money` decimal(24,6) DEFAULT NULL COMMENT '优惠后金额',\n  `other_money` decimal(24,6) DEFAULT NULL COMMENT '销售或采购费用合计',\n  `deposit` decimal(24,6) DEFAULT NULL COMMENT '订金',\n  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，0未审核、1已审核、2完成采购|销售、3部分采购|销售、9审核中',\n  `purchase_status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '采购状态，0未采购、2完成采购、3部分采购',\n  `source` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '单据来源，0-pc，1-手机',\n  `link_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联订单号',\n  `link_apply` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联请购单',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n```\n<!-- curated-e006 -->\n- [TRACE:E006] `jsh_depot_item` 单据明细；`header_id`、`material_id`、`oper_number`、`depot_id`、`tenant_id`。\n\n```sql\n  `header_id` bigint NOT NULL COMMENT '表头Id',\n  `material_id` bigint NOT NULL COMMENT '商品Id',\n  `material_extend_id` bigint DEFAULT NULL COMMENT '商品扩展id',\n  `material_unit` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品单位',\n  `sku` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多属性',\n  `oper_number` decimal(24,6) DEFAULT NULL COMMENT '数量',\n  `basic_number` decimal(24,6) DEFAULT NULL COMMENT '基础数量，如kg、瓶',\n  `unit_price` decimal(24,6) DEFAULT NULL COMMENT '单价',\n  `purchase_unit_price` decimal(24,6) DEFAULT NULL COMMENT '采购单价',\n  `tax_unit_price` decimal(24,6) DEFAULT NULL COMMENT '含税单价',\n  `all_price` decimal(24,6) DEFAULT NULL COMMENT '金额',\n  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `depot_id` bigint DEFAULT NULL COMMENT '仓库ID',\n  `another_depot_id` bigint DEFAULT NULL COMMENT '调拨时，对方仓库Id',\n  `tax_rate` decimal(24,6) DEFAULT NULL COMMENT '税率',\n  `tax_money` decimal(24,6) DEFAULT NULL COMMENT '税额',\n  `tax_last_money` decimal(24,6) DEFAULT NULL COMMENT '价税合计',\n  `material_type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品类型',\n  `sn_list` varchar(2000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '序列号列表',\n  `batch_number` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '批号',\n  `expiration_date` datetime DEFAULT NULL COMMENT '有效日期',\n  `link_id` bigint DEFAULT NULL COMMENT '关联明细id',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n```\n<!-- curated-e007 -->\n- [TRACE:E007] `jsh_function` 功能权限结构；`url`、`component`、`enabled`、`type`。\n<!-- curated-e008 -->\n- [TRACE:E008] `jsh_in_out_item` 收支项目；`name`、`type`、`enabled`、`tenant_id`。\n<!-- curated-e009 -->\n- [TRACE:E009] `jsh_material` 商品主数据；`category_id`、`name`、`unit_id`、`enable_serial_number`、`enable_batch_number`。\n<!-- curated-e010 -->\n- [TRACE:E010] `jsh_material_attribute` 商品属性值；`attribute_name`、`attribute_value`、`tenant_id`。\n<!-- curated-e011 -->\n- [TRACE:E011] `jsh_material_category` 商品类型层级；`category_level`、`parent_id`、`tenant_id`。\n<!-- curated-e012 -->\n- [TRACE:E012] `jsh_material_current_stock` 当前库存结构；`material_id`、`depot_id`、`current_number`、`current_unit_price`、`tenant_id`。\n\n```sql\n  `material_id` bigint DEFAULT NULL COMMENT '产品id',\n  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',\n  `current_number` decimal(24,6) DEFAULT NULL COMMENT '当前库存数量',\n  `current_unit_price` decimal(24,6) DEFAULT NULL COMMENT '当前单价',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n```\n<!-- curated-e013 -->\n- [TRACE:E013] `jsh_material_extend` 商品价格扩展；`material_id`、`bar_code`、`sku`、`purchase_decimal`、`commodity_decimal`、`tenant_id`。\n<!-- curated-e014 -->\n- [TRACE:E014] `jsh_material_initial_stock` 产品初始库存；`material_id`、`depot_id`、`number`、`low_safe_stock`、`high_safe_stock`、`tenant_id`。\n<!-- curated-e015 -->\n- [TRACE:E015] `jsh_material_property` 产品扩展字段定义；`native_name`、`enabled`、`another_name`、`tenant_id`。\n<!-- curated-e016 -->\n- [TRACE:E016] `jsh_msg` 消息结构；`msg_title`、`msg_content`、`user_id`、`status`、`tenant_id`。\n<!-- curated-e017 -->\n- [TRACE:E017] `jsh_orga_user_rel` 机构用户关系；`orga_id`、`user_id`、`user_blng_orga_dspl_seq`、`tenant_id`。\n<!-- curated-e018 -->\n- [TRACE:E018] `jsh_organization` 组织主数据；`org_no`、`org_abr`、`parent_id`、`tenant_id`。\n<!-- curated-e019 -->\n- [TRACE:E019] `jsh_person` 往来人员结构；`type`、`name`、`enabled`、`tenant_id`。\n<!-- curated-e020 -->\n- [TRACE:E020] `jsh_platform_config` 平台配置键值；`platform_key`、`platform_key_info`、`platform_value`。\n<!-- curated-e021 -->\n- [TRACE:E021] `jsh_role` 角色权限结构；`name`、`type`、`price_limit`、`enabled`、`tenant_id`。\n<!-- curated-e022 -->\n- [TRACE:E022] `jsh_serial_number` 商品序列号台账；`material_id`、`depot_id`、`serial_number`、`is_sell`、`tenant_id`。\n<!-- curated-e023 -->\n- [TRACE:E023] `jsh_supplier` 供应商主数据；`supplier`、`contacts`、`type`、`advance_in`、`tenant_id`。\n<!-- curated-e024 -->\n- [TRACE:E024] `jsh_sys_dict_data` 字典数据；`dict_label`、`dict_value`、`dict_type`、`status`。\n<!-- curated-e025 -->\n- [TRACE:E025] `jsh_sys_dict_type` 字典类型；`dict_name`、`dict_type`、`status`。\n<!-- curated-e026 -->\n- [TRACE:E026] `jsh_system_config` 系统参数；`minus_stock_flag`、`multi_level_approval_flag`、`force_approval_flag`、`tenant_id`。\n<!-- curated-e027 -->\n- [TRACE:E027] `jsh_tenant` 租户配置；`tenant_id`、`login_name`、`user_num_limit`、`enabled`。\n<!-- curated-e028 -->\n- [TRACE:E028] `jsh_unit` 计量单位；`name`、`basic_unit`、`ratio`、`enabled`、`tenant_id`。\n<!-- curated-e029 -->\n- [TRACE:E029] `jsh_user` 用户结构；`username`、`login_name`、`status`、`tenant_id`。\n<!-- curated-e030 -->\n- [TRACE:E030] `jsh_user_business` 用户业务扩展；`type`、`key_id`、`btn_str`、`tenant_id`。\n## 4. 业务活动\n\n<!-- curated-e031 -->\n- [TRACE:E031] `sp_rebalance_below_low_stock_requisition` 低库存补货过程；输入租户、订单上限和单据行上限，并声明补货候选处理变量。\n<!-- curated-e032 -->\n- [TRACE:E032] `sp_rebalance_over_high_stock_sales` 高库存销售平衡过程；输入租户、订单上限和单据行上限，并声明库存候选处理变量。\n<!-- curated-e033 -->\n- [TRACE:E033] `sp_run_inventory_stock_rebalance` 库存平衡调度过程；`p_tenant_id`、`p_max_orders_each`、`p_max_lines_per_order` 控制批次，并含异常处理。\n\n```sql\nCREATE DEFINER=CURRENT_USER PROCEDURE `sp_run_inventory_stock_rebalance`(\n    IN p_tenant_id BIGINT,\n    IN p_max_orders_each INT,\n    IN p_max_lines_per_order INT\n)\nBEGIN\n    DECLARE v_replenish_limit INT DEFAULT 10000;\n    DECLARE v_replenish_loop INT DEFAULT 0;\n    DECLARE v_cycle_progress INT DEFAULT 0;\n    DECLARE v_success_count INT DEFAULT 0;\n```\n<!-- curated-e034 -->\n- [TRACE:E034] `sp_run_retail_return_rate_and_fact_sync` 零售退货率及事实同步；输入登录名、目标月份和同步批大小，并解析租户。\n<!-- curated-e035 -->\n- [TRACE:E035] `sp_run_store_retail_return_coverage_and_fact_sync` 门店零售退货覆盖与事实同步；输入登录名、目标月份和同步批大小，并维护租户与锁状态。\n<!-- curated-e036 -->\n- [TRACE:E036] `sp_sync_retail_out_fact_batch` 零售出库事实批同步；按租户和时间区间接收批次，并声明批量刷盘及单据游标变量。\n\n```sql\nCREATE DEFINER=CURRENT_USER PROCEDURE `sp_sync_retail_out_fact_batch`(\n    IN p_tenant_id BIGINT,\n    IN p_start_time DATETIME,\n    IN p_stop_time DATETIME\n)\nBEGIN\n    -- 1. 批次控制与状态标志变量\n    DECLARE v_batch_id VARCHAR(50);\n    DECLARE v_done INT DEFAULT FALSE;\n    DECLARE v_batch_size INT DEFAULT 1000; -- 核心控制: 每 1000 条刷盘一次\n    DECLARE v_current_count INT DEFAULT 0;  -- 内存缓存计数器\n    \n```\n## 5. 字段与维度\n\n<!-- curated-g4 -->\n- [TRACE:G4] 单据主表同时保留 create_time、oper_time、status 和 tenant_id；字段存在不等于已经确认业务时间或制度口径。\n## 6. 对象关系\n\n<!-- curated-g5 -->\n- [TRACE:G5] 单据明细通过 header_id、material_id 和 depot_id 形成结构关联；显式外键清单另行保留，不能把推断关系写成外键事实。\n## 7. 指标口径\n\n<!-- curated-g6 -->\n- [TRACE:G6] 负库存同时保留 MySQL 的 `minus_stock_flag` 字段和 GitHub 冻结词汇“支持负库存”；不从快照推算库存分布或租户效果。\n<!-- curated-g7 -->\n- [TRACE:G7] 参数化 DML 摘要只说明可审阅的查询形态，不提供执行次数、结果行、绑定参数或样本。\n## 8. 示例问题\n\n<!-- curated-g8 -->\n- [TRACE:G8] 可据此追问单据、库存和财务结构的字段与关联；任何数量、金额或趋势问题都必须先补充业务口径和业务行授权。\n## 9. 待确认事项\n\n<!-- curated-g9 -->\n- [TRACE:G9] 负库存治理关系：MySQL 的 `minus_stock_flag` 字段与 GitHub 冻结词汇“支持负库存”都被保留；统一禁止负库存仍是待确认目标，不是当前生产事实。\n<!-- curated-g10 -->\n- [TRACE:G10] 欠款字段治理关系：当前部署 `jsh_depot_head` 的字段结构与 GitHub 冻结 schema 记录存在差异；欠款指标保持阻断。\n<!-- curated-g11 -->\n- [TRACE:G11] 单据状态治理关系：MySQL `status` 字段明确包含 9=审核中，GitHub 冻结记录只包含“审核中”界面词汇；官方状态 9 的定义保留为资料缺口，存在时间漂移风险。\n<!-- curated-g12 -->\n- [TRACE:G12] 覆盖与排除：本审阅覆盖 30 / 95 张表和 6 个当前部署扩展过程；排除业务行、样本、profile、查询结果、绑定参数以及未选的其他表和过程。\n",
  "markdownSha256": "sha256:bd812cc828597b687aaf163d5cc244f3900c254d74a3d28c27747225a8afb6df",
  "evidence": [
    {
      "evidenceRef": "mysql:table:jsh_account",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_account",
      "excerpt": "CREATE TABLE `jsh_account` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',\n  `serial_no` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '编号',\n  `initial_amount` decimal(24,6) DEFAULT NULL COMMENT '期初金额',\n  `current_amount` decimal(24,6) DEFAULT NULL COMMENT '当前余额',\n  `remark` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用',\n  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',\n  `is_default` bit(1) DEFAULT NULL COMMENT '是否默认',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='账户信息';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_account.sql",
      "excerptSha256": "sha256:53a6093ebcf03aeed551aedda6174e72416d22a79d5514c0c51531814f60536d",
      "affectedObjectRefs": [
        "table:jsh_account"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_account_head",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_account_head",
      "excerpt": "CREATE TABLE `jsh_account_head` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型(支出/收入/收款/付款/转账)',\n  `organ_id` bigint DEFAULT NULL COMMENT '单位Id(收款/付款单位)',\n  `hands_person_id` bigint DEFAULT NULL COMMENT '经手人id',\n  `creator` bigint DEFAULT NULL COMMENT '操作员',\n  `change_amount` decimal(24,6) DEFAULT NULL COMMENT '变动金额(优惠/收款/付款/实付)',\n  `discount_money` decimal(24,6) DEFAULT NULL COMMENT '优惠金额',\n  `total_price` decimal(24,6) DEFAULT NULL COMMENT '合计金额',\n  `account_id` bigint DEFAULT NULL COMMENT '账户(收款/付款)',\n  `bill_no` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单据编号',\n  `bill_time` datetime DEFAULT NULL COMMENT '单据日期',\n  `remark` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `file_name` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '附件名称',\n  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，0未审核、1已审核、9审核中',\n  `source` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '单据来源，0-pc，1-手机',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `FK9F4C0D8DB610FC06` (`organ_id`) USING BTREE,\n  KEY `FK9F4C0D8DAAE50527` (`account_id`) USING BTREE,\n  KEY `FK9F4C0D8DC4170B37` (`hands_person_id`) USING BTREE,\n  KEY `bill_no` (`bill_no`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='财务主表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_account_head.sql",
      "excerptSha256": "sha256:2ab8bd771168ebc22e54ea3a3122b029f79c67ebd9dac4130be2e847403c792b",
      "affectedObjectRefs": [
        "table:jsh_account_head"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_account_item",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_account_item",
      "excerpt": "CREATE TABLE `jsh_account_item` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `header_id` bigint NOT NULL COMMENT '表头Id',\n  `account_id` bigint DEFAULT NULL COMMENT '账户Id',\n  `in_out_item_id` bigint DEFAULT NULL COMMENT '收支项目Id',\n  `bill_id` bigint DEFAULT NULL COMMENT '单据id',\n  `need_debt` decimal(24,6) DEFAULT NULL COMMENT '应收欠款',\n  `finish_debt` decimal(24,6) DEFAULT NULL COMMENT '已收欠款',\n  `each_amount` decimal(24,6) DEFAULT NULL COMMENT '单项金额',\n  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单据备注',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `FK9F4CBAC0AAE50527` (`account_id`) USING BTREE,\n  KEY `FK9F4CBAC0C5FE6007` (`header_id`) USING BTREE,\n  KEY `FK9F4CBAC0D203EDC5` (`in_out_item_id`) USING BTREE,\n  KEY `bill_id` (`bill_id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='财务子表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_account_item.sql",
      "excerptSha256": "sha256:7fcf604c6ec34d3a814a5b89419f5678ccb4eea837f607db8657c03663f42c70",
      "affectedObjectRefs": [
        "table:jsh_account_item"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_depot",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_depot",
      "excerpt": "CREATE TABLE `jsh_depot` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `name` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '仓库名称',\n  `address` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '仓库地址',\n  `warehousing` decimal(24,6) DEFAULT NULL COMMENT '仓储费',\n  `truckage` decimal(24,6) DEFAULT NULL COMMENT '搬运费',\n  `type` int DEFAULT NULL COMMENT '类型',\n  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',\n  `remark` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '描述',\n  `principal` bigint DEFAULT NULL COMMENT '负责人',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_Flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  `is_default` bit(1) DEFAULT NULL COMMENT '是否默认',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='仓库表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_depot.sql",
      "excerptSha256": "sha256:985af5e5b983c7e3fc5b450dc741c26028a98787c6e326ee8fa0cda2dd4058f3",
      "affectedObjectRefs": [
        "table:jsh_depot"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_depot_head",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_depot_head",
      "excerpt": "CREATE TABLE `jsh_depot_head` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型(出库/入库)',\n  `sub_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '出入库分类',\n  `default_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '初始票据号',\n  `number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '票据号',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `oper_time` datetime DEFAULT NULL COMMENT '出入库时间',\n  `organ_id` bigint DEFAULT NULL COMMENT '供应商id',\n  `creator` bigint DEFAULT NULL COMMENT '操作员',\n  `account_id` bigint DEFAULT NULL COMMENT '账户id',\n  `change_amount` decimal(24,6) DEFAULT NULL COMMENT '变动金额(收款/付款)',\n  `back_amount` decimal(24,6) DEFAULT NULL COMMENT '找零金额',\n  `total_price` decimal(24,6) DEFAULT NULL COMMENT '合计金额',\n  `pay_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '付款类型(现金、记账等)',\n  `bill_type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单据类型',\n  `remark` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `file_name` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '附件名称',\n  `sales_man` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '销售员（可以多个）',\n  `account_id_list` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多账户ID列表',\n  `account_money_list` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多账户金额列表',\n  `discount` decimal(24,6) DEFAULT NULL COMMENT '优惠率',\n  `discount_money` decimal(24,6) DEFAULT NULL COMMENT '优惠金额',\n  `discount_last_money` decimal(24,6) DEFAULT NULL COMMENT '优惠后金额',\n  `other_money` decimal(24,6) DEFAULT NULL COMMENT '销售或采购费用合计',\n  `deposit` decimal(24,6) DEFAULT NULL COMMENT '订金',\n  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，0未审核、1已审核、2完成采购|销售、3部分采购|销售、9审核中',\n  `purchase_status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '采购状态，0未采购、2完成采购、3部分采购',\n  `source` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '单据来源，0-pc，1-手机',\n  `link_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联订单号',\n  `link_apply` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关联请购单',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `FK2A80F214B610FC06` (`organ_id`) USING BTREE,\n  KEY `FK2A80F214AAE50527` (`account_id`) USING BTREE,\n  KEY `number` (`number`) USING BTREE,\n  KEY `link_number` (`link_number`) USING BTREE,\n  KEY `creator` (`creator`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE,\n  KEY `idx_tenant_subtype_status_id` (`tenant_id`,`sub_type`,`status`,`delete_flag`,`id` DESC),\n  KEY `idx_tenant_type_subtype_id` (`tenant_id`,`type`,`sub_type`,`id` DESC,`number` DESC)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='单据主表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_depot_head.sql",
      "excerptSha256": "sha256:147451e93546908cb2ced45c08309b1f21a80a9036a592feae2279a693e46725",
      "affectedObjectRefs": [
        "table:jsh_depot_head"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_depot_item",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_depot_item",
      "excerpt": "CREATE TABLE `jsh_depot_item` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `header_id` bigint NOT NULL COMMENT '表头Id',\n  `material_id` bigint NOT NULL COMMENT '商品Id',\n  `material_extend_id` bigint DEFAULT NULL COMMENT '商品扩展id',\n  `material_unit` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品单位',\n  `sku` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多属性',\n  `oper_number` decimal(24,6) DEFAULT NULL COMMENT '数量',\n  `basic_number` decimal(24,6) DEFAULT NULL COMMENT '基础数量，如kg、瓶',\n  `unit_price` decimal(24,6) DEFAULT NULL COMMENT '单价',\n  `purchase_unit_price` decimal(24,6) DEFAULT NULL COMMENT '采购单价',\n  `tax_unit_price` decimal(24,6) DEFAULT NULL COMMENT '含税单价',\n  `all_price` decimal(24,6) DEFAULT NULL COMMENT '金额',\n  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `depot_id` bigint DEFAULT NULL COMMENT '仓库ID',\n  `another_depot_id` bigint DEFAULT NULL COMMENT '调拨时，对方仓库Id',\n  `tax_rate` decimal(24,6) DEFAULT NULL COMMENT '税率',\n  `tax_money` decimal(24,6) DEFAULT NULL COMMENT '税额',\n  `tax_last_money` decimal(24,6) DEFAULT NULL COMMENT '价税合计',\n  `material_type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品类型',\n  `sn_list` varchar(2000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '序列号列表',\n  `batch_number` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '批号',\n  `expiration_date` datetime DEFAULT NULL COMMENT '有效日期',\n  `link_id` bigint DEFAULT NULL COMMENT '关联明细id',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `FK2A819F475D61CCF7` (`material_id`) USING BTREE,\n  KEY `FK2A819F474BB6190E` (`header_id`) USING BTREE,\n  KEY `FK2A819F479485B3F5` (`depot_id`) USING BTREE,\n  KEY `FK2A819F47729F5392` (`another_depot_id`) USING BTREE,\n  KEY `material_extend_id` (`material_extend_id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='单据子表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_depot_item.sql",
      "excerptSha256": "sha256:7569f84f3613ab306ce63e6f39509d09c0d4e3d5e18c365e9738328559ef996c",
      "affectedObjectRefs": [
        "table:jsh_depot_item"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_function",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_function",
      "excerpt": "CREATE TABLE `jsh_function` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '编号',\n  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',\n  `parent_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '上级编号',\n  `url` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '链接',\n  `component` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '组件',\n  `state` bit(1) DEFAULT NULL COMMENT '收缩',\n  `sort` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用',\n  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',\n  `push_btn` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '功能按钮',\n  `icon` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '图标',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  UNIQUE KEY `url` (`url`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='功能模块表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_function.sql",
      "excerptSha256": "sha256:6aa25922c5c4daf78e5b45883c8fc623ea281b257329888efa2cc448040617a0",
      "affectedObjectRefs": [
        "table:jsh_function"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_in_out_item",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_in_out_item",
      "excerpt": "CREATE TABLE `jsh_in_out_item` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',\n  `type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',\n  `remark` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用',\n  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='收支项目';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_in_out_item.sql",
      "excerptSha256": "sha256:29dba855cd9ead86fcec67c87e9b7e16ef0411621e5db72e61cb1e78a97a2f9e",
      "affectedObjectRefs": [
        "table:jsh_in_out_item"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_material",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_material",
      "excerpt": "CREATE TABLE `jsh_material` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `category_id` bigint DEFAULT NULL COMMENT '产品类型id',\n  `name` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',\n  `mfrs` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '制造商',\n  `model` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '型号',\n  `standard` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '规格',\n  `brand` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '品牌',\n  `mnemonic` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '助记码',\n  `color` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '颜色',\n  `unit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '单位-单个',\n  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `img_name` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '图片名称',\n  `unit_id` bigint DEFAULT NULL COMMENT '单位Id',\n  `expiry_num` int DEFAULT NULL COMMENT '保质期天数',\n  `weight` decimal(24,6) DEFAULT NULL COMMENT '基础重量(kg)',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用 0-禁用  1-启用',\n  `other_field1` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '自定义1',\n  `other_field2` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '自定义2',\n  `other_field3` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '自定义3',\n  `enable_serial_number` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '是否开启序列号，0否，1是',\n  `enable_batch_number` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '是否开启批号，0否，1是',\n  `position` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '仓位货架',\n  `attribute` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多属性信息',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `FK675951272AB6672C` (`category_id`) USING BTREE,\n  KEY `UnitId` (`unit_id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_material.sql",
      "excerptSha256": "sha256:fb6180a15a6e62cddfae5e4c470ad2b96e562a7b3c9a538b1dc03d9528cabbe6",
      "affectedObjectRefs": [
        "table:jsh_material"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_material_attribute",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_material_attribute",
      "excerpt": "CREATE TABLE `jsh_material_attribute` (\n  `id` bigint NOT NULL AUTO_INCREMENT,\n  `attribute_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '属性名',\n  `attribute_value` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '属性值',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品属性表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_material_attribute.sql",
      "excerptSha256": "sha256:32c6655de0e4e0c8cdb0e76423adf42c688b2d0df6a7f298452e7f5e4efa8a57",
      "affectedObjectRefs": [
        "table:jsh_material_attribute"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_material_category",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_material_category",
      "excerpt": "CREATE TABLE `jsh_material_category` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',\n  `category_level` smallint DEFAULT NULL COMMENT '等级',\n  `parent_id` bigint DEFAULT NULL COMMENT '上级id',\n  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '显示顺序',\n  `serial_no` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '编号',\n  `remark` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `update_time` datetime DEFAULT NULL COMMENT '更新时间',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `FK3EE7F725237A77D8` (`parent_id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品类型表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_material_category.sql",
      "excerptSha256": "sha256:3cc2fc7d9c9359c6f891d79860f1544b07cac26da491670f51f8a759ab7f19af",
      "affectedObjectRefs": [
        "table:jsh_material_category"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_material_current_stock",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_material_current_stock",
      "excerpt": "CREATE TABLE `jsh_material_current_stock` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `material_id` bigint DEFAULT NULL COMMENT '产品id',\n  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',\n  `current_number` decimal(24,6) DEFAULT NULL COMMENT '当前库存数量',\n  `current_unit_price` decimal(24,6) DEFAULT NULL COMMENT '当前单价',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `material_id` (`material_id`) USING BTREE,\n  KEY `depot_id` (`depot_id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='产品当前库存';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_material_current_stock.sql",
      "excerptSha256": "sha256:e797cb490a67d525e8787d83d16717d9d99e848cc52d8c6d6fd3aa95dd8ea0c4",
      "affectedObjectRefs": [
        "table:jsh_material_current_stock"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_material_extend",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_material_extend",
      "excerpt": "CREATE TABLE `jsh_material_extend` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `material_id` bigint DEFAULT NULL COMMENT '商品id',\n  `bar_code` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品条码',\n  `commodity_unit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '商品单位',\n  `sku` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '多属性',\n  `purchase_decimal` decimal(24,6) DEFAULT NULL COMMENT '采购价格',\n  `commodity_decimal` decimal(24,6) DEFAULT NULL COMMENT '零售价格',\n  `wholesale_decimal` decimal(24,6) DEFAULT NULL COMMENT '销售价格',\n  `low_decimal` decimal(24,6) DEFAULT NULL COMMENT '最低售价',\n  `default_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '1' COMMENT '是否为默认单位，1是，0否',\n  `create_time` datetime DEFAULT NULL COMMENT '创建日期',\n  `create_serial` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '创建人编码',\n  `update_serial` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '更新人编码',\n  `update_time` bigint DEFAULT NULL COMMENT '更新时间戳',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_Flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `material_id` (`material_id`) USING BTREE,\n  KEY `bar_code` (`bar_code`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='产品价格扩展';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_material_extend.sql",
      "excerptSha256": "sha256:fa47f9792ecdec5f25e2567b15f0cefe8c63ce3e1c6d06c7db716290a71bcceb",
      "affectedObjectRefs": [
        "table:jsh_material_extend"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_material_initial_stock",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_material_initial_stock",
      "excerpt": "CREATE TABLE `jsh_material_initial_stock` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `material_id` bigint DEFAULT NULL COMMENT '产品id',\n  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',\n  `number` decimal(24,6) DEFAULT NULL COMMENT '初始库存数量',\n  `low_safe_stock` decimal(24,6) DEFAULT NULL COMMENT '最低库存数量',\n  `high_safe_stock` decimal(24,6) DEFAULT NULL COMMENT '最高库存数量',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `material_id` (`material_id`) USING BTREE,\n  KEY `depot_id` (`depot_id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='产品初始库存';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_material_initial_stock.sql",
      "excerptSha256": "sha256:4d221e221dc4dee24f17e590c3637d0e037380b84c3a7040562bd46c02d4ab4d",
      "affectedObjectRefs": [
        "table:jsh_material_initial_stock"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_material_property",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_material_property",
      "excerpt": "CREATE TABLE `jsh_material_property` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `native_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '原始名称',\n  `enabled` bit(1) DEFAULT NULL COMMENT '是否启用',\n  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',\n  `another_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '别名',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='产品扩展字段表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_material_property.sql",
      "excerptSha256": "sha256:3f6010c63344bb0e6c3a38d2d8e97ebbaa5efa8f27e613a6cbba541d2bc778f4",
      "affectedObjectRefs": [
        "table:jsh_material_property"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_msg",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_msg",
      "excerpt": "CREATE TABLE `jsh_msg` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `msg_title` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '消息标题',\n  `msg_content` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '消息内容',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '消息类型',\n  `user_id` bigint DEFAULT NULL COMMENT '接收人id',\n  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，1未读 2已读',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_Flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='消息表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_msg.sql",
      "excerptSha256": "sha256:88180009a3cc72d5607d9fb75bb381d72a1dcd7ea2c8783ec18587998bd45f36",
      "affectedObjectRefs": [
        "table:jsh_msg"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_orga_user_rel",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_orga_user_rel",
      "excerpt": "CREATE TABLE `jsh_orga_user_rel` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `orga_id` bigint DEFAULT NULL COMMENT '机构id',\n  `user_id` bigint NOT NULL COMMENT '用户id',\n  `user_blng_orga_dspl_seq` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '用户在所属机构中显示顺序',\n  `delete_flag` char(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `creator` bigint DEFAULT NULL COMMENT '创建人',\n  `update_time` datetime DEFAULT NULL COMMENT '更新时间',\n  `updater` bigint DEFAULT NULL COMMENT '更新人',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `orga_id` (`orga_id`) USING BTREE,\n  KEY `user_id` (`user_id`) USING BTREE,\n  KEY `creator` (`creator`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='机构用户关系表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_orga_user_rel.sql",
      "excerptSha256": "sha256:703b33d12eb1c40c2b7203991a54dcdbde208ec0bef2445973683f68ae5dd34b",
      "affectedObjectRefs": [
        "table:jsh_orga_user_rel"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_organization",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_organization",
      "excerpt": "CREATE TABLE `jsh_organization` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `org_no` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '机构编号',\n  `org_abr` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '机构简称',\n  `parent_id` bigint DEFAULT NULL COMMENT '父机构id',\n  `sort` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '机构显示顺序',\n  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `update_time` datetime DEFAULT NULL COMMENT '更新时间',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='机构表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_organization.sql",
      "excerptSha256": "sha256:82837fa84f7e5392ff1b2e060b0859e30c46dc02190075756efd78e3f3685906",
      "affectedObjectRefs": [
        "table:jsh_organization"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_person",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_person",
      "excerpt": "CREATE TABLE `jsh_person` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',\n  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '姓名',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用',\n  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='经手人表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_person.sql",
      "excerptSha256": "sha256:60b87d5b562e2c3224c9a9d17f44eeebb0aac189bb95171639e43064e4b74f1f",
      "affectedObjectRefs": [
        "table:jsh_person"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_platform_config",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_platform_config",
      "excerpt": "CREATE TABLE `jsh_platform_config` (\n  `id` bigint NOT NULL AUTO_INCREMENT,\n  `platform_key` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关键词',\n  `platform_key_info` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '关键词名称',\n  `platform_value` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '值',\n  PRIMARY KEY (`id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='平台参数';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_platform_config.sql",
      "excerptSha256": "sha256:28cb0976772a16f33ed02af9d7bcf7131ca964d176ee33414a53ce9a5ed946ef",
      "affectedObjectRefs": [
        "table:jsh_platform_config"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_role",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_role",
      "excerpt": "CREATE TABLE `jsh_role` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称',\n  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',\n  `price_limit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '价格屏蔽 1-屏蔽采购价 2-屏蔽零售价 3-屏蔽销售价',\n  `value` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '值',\n  `description` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '描述',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用',\n  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='角色表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_role.sql",
      "excerptSha256": "sha256:37067d873cfb245e9bcd1893ba043305989b6d67dcdce2371b45305bdcc75e58",
      "affectedObjectRefs": [
        "table:jsh_role"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_serial_number",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_serial_number",
      "excerpt": "CREATE TABLE `jsh_serial_number` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `material_id` bigint DEFAULT NULL COMMENT '产品表id',\n  `depot_id` bigint DEFAULT NULL COMMENT '仓库id',\n  `serial_number` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '序列号',\n  `is_sell` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '是否卖出，0未卖出，1卖出',\n  `in_price` decimal(24,6) DEFAULT NULL COMMENT '入库单价',\n  `remark` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `creator` bigint DEFAULT NULL COMMENT '创建人',\n  `update_time` datetime DEFAULT NULL COMMENT '更新时间',\n  `updater` bigint DEFAULT NULL COMMENT '更新人',\n  `in_bill_no` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '入库单号',\n  `out_bill_no` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '出库单号',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `material_id` (`material_id`) USING BTREE,\n  KEY `depot_id` (`depot_id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='序列号表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_serial_number.sql",
      "excerptSha256": "sha256:fcd37a024eedf1fdbdc8db41dd0ee09b4b54d49ec1955fe040e596a0418b57b0",
      "affectedObjectRefs": [
        "table:jsh_serial_number"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_supplier",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_supplier",
      "excerpt": "CREATE TABLE `jsh_supplier` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `supplier` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '供应商名称',\n  `contacts` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '联系人',\n  `phone_num` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '联系电话',\n  `email` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '电子邮箱',\n  `description` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `isystem` tinyint DEFAULT NULL COMMENT '是否系统自带 0==系统 1==非系统',\n  `type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类型',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用',\n  `advance_in` decimal(24,6) DEFAULT '0.000000' COMMENT '预收款',\n  `begin_need_get` decimal(24,6) DEFAULT NULL COMMENT '期初应收',\n  `begin_need_pay` decimal(24,6) DEFAULT NULL COMMENT '期初应付',\n  `all_need_get` decimal(24,6) DEFAULT NULL COMMENT '累计应收',\n  `all_need_pay` decimal(24,6) DEFAULT NULL COMMENT '累计应付',\n  `fax` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '传真',\n  `telephone` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '手机',\n  `address` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '地址',\n  `tax_num` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '纳税人识别号',\n  `bank_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '开户行',\n  `account_number` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '账号',\n  `tax_rate` decimal(24,6) DEFAULT NULL COMMENT '税率',\n  `sort` varchar(10) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '排序',\n  `creator` bigint DEFAULT NULL COMMENT '操作员',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `type` (`type`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='供应商/客户信息表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_supplier.sql",
      "excerptSha256": "sha256:2c5b79fc6c2c823b08c4d436d1dc22d384c20d7c9aed19f14a7dfbd3b6f94153",
      "affectedObjectRefs": [
        "table:jsh_supplier"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_sys_dict_data",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_sys_dict_data",
      "excerpt": "CREATE TABLE `jsh_sys_dict_data` (\n  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',\n  `dict_sort` int DEFAULT '0' COMMENT '字典排序',\n  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典标签',\n  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典键值',\n  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',\n  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '样式属性（其他样式扩展）',\n  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表格回显样式',\n  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',\n  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',\n  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',\n  `update_time` datetime DEFAULT NULL COMMENT '更新时间',\n  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`dict_code`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='字典数据表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_sys_dict_data.sql",
      "excerptSha256": "sha256:e0541708795051f2f8b0f05ab8990bd664796b5efd22f30a8200a2a16672140b",
      "affectedObjectRefs": [
        "table:jsh_sys_dict_data"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_sys_dict_type",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_sys_dict_type",
      "excerpt": "CREATE TABLE `jsh_sys_dict_type` (\n  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',\n  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典名称',\n  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',\n  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',\n  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',\n  `update_time` datetime DEFAULT NULL COMMENT '更新时间',\n  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`dict_id`) USING BTREE,\n  UNIQUE KEY `dict_type` (`dict_type`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='字典类型表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_sys_dict_type.sql",
      "excerptSha256": "sha256:2cf85495340a6a009ac680b6060b535f5206bdfd64ae090d3d0a85d9f39632e1",
      "affectedObjectRefs": [
        "table:jsh_sys_dict_type"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_system_config",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_system_config",
      "excerpt": "CREATE TABLE `jsh_system_config` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `company_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司名称',\n  `company_contacts` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司联系人',\n  `company_address` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司地址',\n  `company_tel` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司电话',\n  `company_fax` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司传真',\n  `company_post_code` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '公司邮编',\n  `sale_agreement` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '销售协议',\n  `depot_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '仓库启用标记，0未启用，1启用',\n  `customer_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '客户启用标记，0未启用，1启用',\n  `minus_stock_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '负库存启用标记，0未启用，1启用',\n  `purchase_by_sale_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '以销定购启用标记，0未启用，1启用',\n  `multi_level_approval_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '多级审核启用标记，0未启用，1启用',\n  `multi_bill_type` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '流程类型，可多选',\n  `force_approval_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '强审核启用标记，0未启用，1启用',\n  `update_unit_price_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '1' COMMENT '更新单价启用标记，0未启用，1启用',\n  `over_link_bill_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '超出关联单据启用标记，0未启用，1启用',\n  `in_out_manage_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '出入库管理启用标记，0未启用，1启用',\n  `multi_account_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '多账户启用标记，0未启用，1启用',\n  `move_avg_price_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '移动平均价启用标记，0未启用，1启用',\n  `audit_print_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '先审核后打印启用标记，0未启用，1启用',\n  `zero_change_amount_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '零收付款启用标记，0未启用，1启用',\n  `customer_static_price_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '客户静态单价启用标记，0未启用，1启用',\n  `material_price_tax_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '商品价格含税启用标记，0未启用，1启用',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='系统参数';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_system_config.sql",
      "excerptSha256": "sha256:9eefc1ec099abcc730a54efdf7a608843f86c0e8bd8428b291bd1d2356a11ce9",
      "affectedObjectRefs": [
        "table:jsh_system_config"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_tenant",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_tenant",
      "excerpt": "CREATE TABLE `jsh_tenant` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `tenant_id` bigint DEFAULT NULL COMMENT '用户id',\n  `login_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '登录名',\n  `user_num_limit` int DEFAULT NULL COMMENT '用户数量限制',\n  `type` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '租户类型，0免费租户，1付费租户',\n  `enabled` bit(1) DEFAULT b'1' COMMENT '启用 0-禁用  1-启用',\n  `create_time` datetime DEFAULT NULL COMMENT '创建时间',\n  `expire_time` datetime DEFAULT NULL COMMENT '到期时间',\n  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `create_time` (`create_time`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='租户';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_tenant.sql",
      "excerptSha256": "sha256:dce9efc9ace1ac1a2dbbbee11de85432de4f6e6be4d0f871f76b0c3461d9bd42",
      "affectedObjectRefs": [
        "table:jsh_tenant"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_unit",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_unit",
      "excerpt": "CREATE TABLE `jsh_unit` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '名称，支持多单位',\n  `basic_unit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '基础单位',\n  `other_unit` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '副单位',\n  `other_unit_two` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '副单位2',\n  `other_unit_three` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '副单位3',\n  `ratio` decimal(24,3) DEFAULT NULL COMMENT '比例',\n  `ratio_two` decimal(24,3) DEFAULT NULL COMMENT '比例2',\n  `ratio_three` decimal(24,3) DEFAULT NULL COMMENT '比例3',\n  `enabled` bit(1) DEFAULT NULL COMMENT '启用',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='多单位表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_unit.sql",
      "excerptSha256": "sha256:9925b174e03f5a3b71f0e17e446650028a8f4c7ce4326405280833f167c38d86",
      "affectedObjectRefs": [
        "table:jsh_unit"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_user",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_user",
      "excerpt": "CREATE TABLE `jsh_user` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `username` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '用户姓名--例如张三',\n  `login_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '登录用户名',\n  `password` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '登陆密码',\n  `leader_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '是否经理，0否，1是',\n  `position` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '职位',\n  `department` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '所属部门',\n  `email` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '电子邮箱',\n  `phonenum` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '手机号码',\n  `ismanager` tinyint NOT NULL DEFAULT '1' COMMENT '是否为管理者 0==管理者 1==员工',\n  `isystem` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统自带数据 ',\n  `status` tinyint DEFAULT '0' COMMENT '状态，0正常，2封禁',\n  `description` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '用户描述信息',\n  `remark` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '备注',\n  `weixin_open_id` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '微信绑定',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='用户表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_user.sql",
      "excerptSha256": "sha256:4612b3bf028e1bb90e51d8643267babef801c1bed3c7cbf6a690807e32612ce5",
      "affectedObjectRefs": [
        "table:jsh_user"
      ]
    },
    {
      "evidenceRef": "mysql:table:jsh_user_business",
      "evidenceClass": "OBSERVED",
      "title": "MySQL DDL · jsh_user_business",
      "excerpt": "CREATE TABLE `jsh_user_business` (\n  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',\n  `type` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '类别',\n  `key_id` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '主id',\n  `value` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci COMMENT '值',\n  `btn_str` varchar(20000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '按钮权限',\n  `tenant_id` bigint DEFAULT NULL COMMENT '租户id',\n  `delete_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1删除',\n  PRIMARY KEY (`id`) USING BTREE,\n  KEY `type` (`type`) USING BTREE,\n  KEY `key_id` (`key_id`) USING BTREE,\n  KEY `tenant_id` (`tenant_id`) USING BTREE\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=DYNAMIC COMMENT='用户/角色/模块关系表';\n",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "ddl/tables/jsh_user_business.sql",
      "excerptSha256": "sha256:ce4bc51c5c58c143628d5393e3d9fcd86e100fffecdaaff79f066f61bbd423fb",
      "affectedObjectRefs": [
        "table:jsh_user_business"
      ]
    },
    {
      "evidenceRef": "mysql:procedure:sp_rebalance_below_low_stock_requisition",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 存储过程 · sp_rebalance_below_low_stock_requisition",
      "excerpt": "CREATE DEFINER=CURRENT_USER PROCEDURE `sp_rebalance_below_low_stock_requisition`(\n    IN p_tenant_id BIGINT,\n    IN p_max_orders INT,\n    IN p_max_lines_per_order INT\n)\nBEGIN\n    DECLARE v_lock_name VARCHAR(128) DEFAULT '';\n    DECLARE v_lock_ok INT DEFAULT 0;\n    DECLARE v_log_id BIGINT DEFAULT NULL;\n    DECLARE v_status_approved CHAR(1) DEFAULT '1';\n    DECLARE v_order_limit INT DEFAULT 10000;\n    DECLARE v_line_limit INT DEFAULT 8;\n    DECLARE v_order_min_amount DECIMAL(24,6) DEFAULT 50;\n    DECLARE v_order_max_amount DECIMAL(24,6) DEFAULT 300;\n    DECLARE v_orders_created INT DEFAULT 0;\n    DECLARE v_items_touched INT DEFAULT 0;\n    DECLARE v_quantity_total DECIMAL(24,6) DEFAULT 0;\n    DECLARE v_remaining_rows INT DEFAULT 0;",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "programmability/procedures/sp_rebalance_below_low_stock_requisition.sql",
      "excerptSha256": "sha256:ed385e51de244deda11669037995b32b5e9a09b32f3e1ab9bafd98d018ddac05",
      "affectedObjectRefs": [
        "procedure:sp_rebalance_below_low_stock_requisition"
      ]
    },
    {
      "evidenceRef": "mysql:procedure:sp_rebalance_over_high_stock_sales",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 存储过程 · sp_rebalance_over_high_stock_sales",
      "excerpt": "CREATE DEFINER=CURRENT_USER PROCEDURE `sp_rebalance_over_high_stock_sales`(\n    IN p_tenant_id BIGINT,\n    IN p_max_orders INT,\n    IN p_max_lines_per_order INT\n)\nBEGIN\n    DECLARE v_lock_name VARCHAR(128) DEFAULT '';\n    DECLARE v_lock_ok INT DEFAULT 0;\n    DECLARE v_log_id BIGINT DEFAULT NULL;\n    DECLARE v_order_limit INT DEFAULT 10000;\n    DECLARE v_line_limit INT DEFAULT 8;\n    DECLARE v_order_min_amount DECIMAL(24,6) DEFAULT 10;\n    DECLARE v_order_max_amount DECIMAL(24,6) DEFAULT 800;\n    DECLARE v_orders_created INT DEFAULT 0;\n    DECLARE v_items_touched INT DEFAULT 0;\n    DECLARE v_quantity_total DECIMAL(24,6) DEFAULT 0;\n    DECLARE v_remaining_rows INT DEFAULT 0;\n    DECLARE v_candidate_count INT DEFAULT 0;",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "programmability/procedures/sp_rebalance_over_high_stock_sales.sql",
      "excerptSha256": "sha256:f33a2282dd97e899d853ac9eb1d94004bea72f8a4b9e5a11c99ac56a825c486a",
      "affectedObjectRefs": [
        "procedure:sp_rebalance_over_high_stock_sales"
      ]
    },
    {
      "evidenceRef": "mysql:procedure:sp_run_inventory_stock_rebalance",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 存储过程 · sp_run_inventory_stock_rebalance",
      "excerpt": "CREATE DEFINER=CURRENT_USER PROCEDURE `sp_run_inventory_stock_rebalance`(\n    IN p_tenant_id BIGINT,\n    IN p_max_orders_each INT,\n    IN p_max_lines_per_order INT\n)\nBEGIN\n    DECLARE v_replenish_limit INT DEFAULT 10000;\n    DECLARE v_replenish_loop INT DEFAULT 0;\n    DECLARE v_cycle_progress INT DEFAULT 0;\n    DECLARE v_success_count INT DEFAULT 0;\n    DECLARE v_requisition_log_id BIGINT DEFAULT NULL;\n    DECLARE v_requisition_orders_created INT DEFAULT 0;\n    DECLARE v_batch_start_time DATETIME DEFAULT NULL;\n\n    DECLARE EXIT HANDLER FOR SQLEXCEPTION\n    BEGIN\n        SET @is_batch_mode = 0;\n        SET @batch_mode = 0;",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "programmability/procedures/sp_run_inventory_stock_rebalance.sql",
      "excerptSha256": "sha256:d43cfb32f4e34359e95ada42bcba1c555c1373311f27a0305d4c96df841b9a5d",
      "affectedObjectRefs": [
        "procedure:sp_run_inventory_stock_rebalance"
      ]
    },
    {
      "evidenceRef": "mysql:procedure:sp_run_retail_return_rate_and_fact_sync",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 存储过程 · sp_run_retail_return_rate_and_fact_sync",
      "excerpt": "CREATE DEFINER=CURRENT_USER PROCEDURE `sp_run_retail_return_rate_and_fact_sync`(\n    IN p_login_name VARCHAR(50)\n        CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci,\n    IN p_target_month VARCHAR(7),\n    IN p_sync_batch_size INT\n)\nproc_main: BEGIN\n    DECLARE v_tenant_id BIGINT DEFAULT NULL;\n    DECLARE v_task_lock VARCHAR(128) DEFAULT NULL;\n    DECLARE v_task_lock_acquired INT DEFAULT 0;\n\n    DECLARE EXIT HANDLER FOR SQLEXCEPTION\n    BEGIN\n        IF COALESCE(v_task_lock_acquired,0)=1 THEN\n            DO RELEASE_LOCK(v_task_lock);\n            SET v_task_lock_acquired=0;\n        END IF;\n        RESIGNAL;",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "programmability/procedures/sp_run_retail_return_rate_and_fact_sync.sql",
      "excerptSha256": "sha256:3b6cf0ef73be48eb8b9fd30243829f852246dde5d6cc8eae8c93ef6c9233e44c",
      "affectedObjectRefs": [
        "procedure:sp_run_retail_return_rate_and_fact_sync"
      ]
    },
    {
      "evidenceRef": "mysql:procedure:sp_run_store_retail_return_coverage_and_fact_sync",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 存储过程 · sp_run_store_retail_return_coverage_and_fact_sync",
      "excerpt": "CREATE DEFINER=CURRENT_USER PROCEDURE `sp_run_store_retail_return_coverage_and_fact_sync`(\n    IN p_login_name VARCHAR(50)\n        CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci,\n    IN p_target_month VARCHAR(7),\n    IN p_sync_batch_size INT\n)\nproc_main: BEGIN\n    DECLARE v_login_id BIGINT DEFAULT NULL;\n    DECLARE v_tenant_id BIGINT DEFAULT NULL;\n    DECLARE v_month_start DATETIME DEFAULT NULL;\n    DECLARE v_task_lock VARCHAR(128) DEFAULT NULL;\n    DECLARE v_task_lock_acquired INT DEFAULT 0;\n\n    DECLARE EXIT HANDLER FOR SQLEXCEPTION\n    BEGIN\n        IF COALESCE(v_task_lock_acquired, 0) = 1 THEN\n            DO RELEASE_LOCK(v_task_lock);\n        END IF;",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "programmability/procedures/sp_run_store_retail_return_coverage_and_fact_sync.sql",
      "excerptSha256": "sha256:9880fae5056750da9f1af95feba1435400cc7e554fce09b51160651470370dce",
      "affectedObjectRefs": [
        "procedure:sp_run_store_retail_return_coverage_and_fact_sync"
      ]
    },
    {
      "evidenceRef": "mysql:procedure:sp_sync_retail_out_fact_batch",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 存储过程 · sp_sync_retail_out_fact_batch",
      "excerpt": "CREATE DEFINER=CURRENT_USER PROCEDURE `sp_sync_retail_out_fact_batch`(\n    IN p_tenant_id BIGINT,\n    IN p_start_time DATETIME,\n    IN p_stop_time DATETIME\n)\nBEGIN\n    -- 1. 批次控制与状态标志变量\n    DECLARE v_batch_id VARCHAR(50);\n    DECLARE v_done INT DEFAULT FALSE;\n    DECLARE v_batch_size INT DEFAULT 1000; -- 核心控制: 每 1000 条刷盘一次\n    DECLARE v_current_count INT DEFAULT 0;  -- 内存缓存计数器\n    \n    -- 2. 承接游标提取源数据的临时变量\n    DECLARE v_dh_id, v_di_id, v_creator, v_organ_id, v_depot_id BIGINT;\n    DECLARE v_oper_time DATETIME;\n    DECLARE v_type, v_sub_type VARCHAR(50);\n    DECLARE v_quantity, v_amount DECIMAL(24,6);\n    DECLARE v_store_id BIGINT;",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "programmability/procedures/sp_sync_retail_out_fact_batch.sql",
      "excerptSha256": "sha256:64c7d1423cdbc6f3eb2a56706b46c5ba07310dc785c48d08950c92d5065927ef",
      "affectedObjectRefs": [
        "procedure:sp_sync_retail_out_fact_batch"
      ]
    },
    {
      "evidenceRef": "mysql:constraints:indexes",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 索引定义 · 单据主表",
      "excerpt": "CREATE INDEX `idx_tenant_subtype_status_id` ON `jsh_depot_head` (`tenant_id`, `sub_type`, `status`, `delete_flag`, `id`);\nCREATE INDEX `idx_tenant_type_subtype_id` ON `jsh_depot_head` (`tenant_id`, `type`, `sub_type`, `id`, `number`);",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "constraints/indexes.sql#L112-L113",
      "excerptSha256": "sha256:53a5c82f750a302b50351f2bad160f20734314b3b44d6399d2c2116fdc4266d2",
      "affectedObjectRefs": [
        "table:jsh_depot_head"
      ]
    },
    {
      "evidenceRef": "mysql:constraints:foreign-keys",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 显式外键定义",
      "excerpt": "-- MySQL 显式外键定义证据。\nALTER TABLE `GRAPH_CHECKPOINT` ADD CONSTRAINT `GRAPH_FK_THREAD` FOREIGN KEY (`thread_id`) REFERENCES `GRAPH_THREAD` (`thread_id`);\nALTER TABLE `fe_agent_message` ADD CONSTRAINT `FK_fe_agent_message_session` FOREIGN KEY (`sessionId`) REFERENCES `fe_agent_session` (`id`);",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "constraints/foreign-keys.sql",
      "excerptSha256": "sha256:b979c950fa472f2eeced39765ff261e53cc466c11331f69c3707fb1c0959a7de",
      "affectedObjectRefs": [
        "relation:explicit-foreign-key"
      ]
    },
    {
      "evidenceRef": "mysql:dml:digests",
      "evidenceClass": "OBSERVED",
      "title": "MySQL 参数化 DML 摘要",
      "excerpt": "-- SELECT `header_id` , `oper_number` FROM `jsh_depot_item` FORCE INDEX ( `FK2A819F474BB6190E` ) WHERE `header_id` IN (...) AND ( `delete_flag` = ? OR `delete_flag` IS NULL );",
      "locationLabel": "MySQL 冻结快照文件",
      "locationValue": "dml/digests.sql#L3",
      "excerptSha256": "sha256:6a53b218935e316d108f48da4d9cca4b5e3ba27dc4bc4b910ba9f748eb161620",
      "affectedObjectRefs": [
        "dml:parameterized-summary"
      ]
    },
    {
      "evidenceRef": "github:frozen:negative-stock",
      "evidenceClass": "FROZEN_RECORD",
      "title": "GitHub 负库存界面词汇（冻结结构化记录）",
      "excerpt": "冻结结构化记录：SystemConfigList.vue 的 vocabulary 数组包含“支持负库存”（JSON 第 35-37 行）；本条不把界面词汇改写成字段名。",
      "locationLabel": "GitHub 冻结结构化记录",
      "locationValue": "github/snapshots/20260806193218Z-6f5194dc2836/vocabulary/524d21c78df3-SystemConfigList.vue.json#L35-L37",
      "excerptSha256": "sha256:99842bbb331b67a152ce139b68342c08c8d84a38fcfafe7bb1b313d52e1cf458",
      "affectedObjectRefs": [
        "governance:negative-stock"
      ]
    },
    {
      "evidenceRef": "github:frozen:debt-schema",
      "evidenceClass": "FROZEN_RECORD",
      "title": "GitHub 欠款字段结构（冻结结构化记录）",
      "excerpt": "冻结结构化记录：GitHub schema/tables/jsh_depot_head.sql 第 27-29 行保留 `last_deposit`、`debt`、`last_debt` 字段；这是冻结 schema 记录，不是迁移语句或当前 MySQL 定义。",
      "locationLabel": "GitHub 冻结结构化记录",
      "locationValue": "github/snapshots/20260806193218Z-6f5194dc2836/schema/tables/jsh_depot_head.sql#L27-L29",
      "excerptSha256": "sha256:45bb65aeca7bf4da0263ce5755196ef0541674e3a47e229b7346a8fa78452746",
      "affectedObjectRefs": [
        "governance:debt-fields"
      ]
    },
    {
      "evidenceRef": "github:frozen:status-nine",
      "evidenceClass": "FROZEN_RECORD",
      "title": "GitHub 单据状态界面词汇（冻结结构化记录）",
      "excerpt": "冻结结构化记录：PurchaseOrderList.vue 的 vocabulary 数组第 30-35 行包含“单据状态”“审核中”“已审核”等标签；本条不把界面标签映射为数字状态。",
      "locationLabel": "GitHub 冻结结构化记录",
      "locationValue": "github/snapshots/20260806193218Z-6f5194dc2836/vocabulary/97c62203a6ba-PurchaseOrderList.vue.json#L30-L35",
      "excerptSha256": "sha256:58a405433fdd231216d64d4d8063a38832abefa29f84a8cce7f3b207876605c1",
      "affectedObjectRefs": [
        "governance:document-status"
      ]
    },
    {
      "evidenceRef": "gap:official:status-9",
      "evidenceClass": "GAP",
      "title": "官方状态 9 原始资料缺口",
      "excerpt": "未找到官方状态 9 的可定位原始资料；不据此补写官方枚举或制度结论。",
      "locationLabel": "官方资料缺口",
      "locationValue": "gap://official/status-9",
      "excerptSha256": "sha256:1885e87dd443a83f1f929fa96d3aa3d714bd3061364e0389417e20a58253fa59",
      "affectedObjectRefs": [
        "governance:document-status"
      ]
    }
  ],
  "traceLinks": [
    {
      "linePrefix": "[TRACE:G5]",
      "markdownAnchor": "curated-g5",
      "evidenceRefs": [
        "mysql:constraints:indexes",
        "mysql:constraints:foreign-keys",
        "mysql:table:jsh_depot_item"
      ],
      "relatedBlockIds": [
        "review:relations"
      ]
    },
    {
      "linePrefix": "[TRACE:G7]",
      "markdownAnchor": "curated-g7",
      "evidenceRefs": [
        "mysql:dml:digests"
      ],
      "relatedBlockIds": [
        "review:metrics"
      ]
    },
    {
      "linePrefix": "[TRACE:G1]",
      "markdownAnchor": "curated-g1",
      "evidenceRefs": [
        "mysql:constraints:indexes"
      ],
      "relatedBlockIds": [
        "review:overview"
      ]
    },
    {
      "linePrefix": "[TRACE:G2]",
      "markdownAnchor": "curated-g2",
      "evidenceRefs": [
        "mysql:constraints:foreign-keys"
      ],
      "relatedBlockIds": [
        "review:overview"
      ]
    },
    {
      "linePrefix": "[TRACE:G3]",
      "markdownAnchor": "curated-g3",
      "evidenceRefs": [
        "mysql:dml:digests"
      ],
      "relatedBlockIds": [
        "review:goal"
      ]
    },
    {
      "linePrefix": "[TRACE:E001]",
      "markdownAnchor": "curated-e001",
      "evidenceRefs": [
        "mysql:table:jsh_account"
      ],
      "relatedBlockIds": [
        "review:table:jsh_account"
      ]
    },
    {
      "linePrefix": "[TRACE:E002]",
      "markdownAnchor": "curated-e002",
      "evidenceRefs": [
        "mysql:table:jsh_account_head"
      ],
      "relatedBlockIds": [
        "review:table:jsh_account_head"
      ]
    },
    {
      "linePrefix": "[TRACE:E003]",
      "markdownAnchor": "curated-e003",
      "evidenceRefs": [
        "mysql:table:jsh_account_item"
      ],
      "relatedBlockIds": [
        "review:table:jsh_account_item"
      ]
    },
    {
      "linePrefix": "[TRACE:E004]",
      "markdownAnchor": "curated-e004",
      "evidenceRefs": [
        "mysql:table:jsh_depot"
      ],
      "relatedBlockIds": [
        "review:table:jsh_depot"
      ]
    },
    {
      "linePrefix": "[TRACE:E005]",
      "markdownAnchor": "curated-e005",
      "evidenceRefs": [
        "mysql:table:jsh_depot_head"
      ],
      "relatedBlockIds": [
        "review:table:jsh_depot_head"
      ]
    },
    {
      "linePrefix": "[TRACE:E006]",
      "markdownAnchor": "curated-e006",
      "evidenceRefs": [
        "mysql:table:jsh_depot_item"
      ],
      "relatedBlockIds": [
        "review:table:jsh_depot_item"
      ]
    },
    {
      "linePrefix": "[TRACE:E007]",
      "markdownAnchor": "curated-e007",
      "evidenceRefs": [
        "mysql:table:jsh_function"
      ],
      "relatedBlockIds": [
        "review:table:jsh_function"
      ]
    },
    {
      "linePrefix": "[TRACE:E008]",
      "markdownAnchor": "curated-e008",
      "evidenceRefs": [
        "mysql:table:jsh_in_out_item"
      ],
      "relatedBlockIds": [
        "review:table:jsh_in_out_item"
      ]
    },
    {
      "linePrefix": "[TRACE:E009]",
      "markdownAnchor": "curated-e009",
      "evidenceRefs": [
        "mysql:table:jsh_material"
      ],
      "relatedBlockIds": [
        "review:table:jsh_material"
      ]
    },
    {
      "linePrefix": "[TRACE:E010]",
      "markdownAnchor": "curated-e010",
      "evidenceRefs": [
        "mysql:table:jsh_material_attribute"
      ],
      "relatedBlockIds": [
        "review:table:jsh_material_attribute"
      ]
    },
    {
      "linePrefix": "[TRACE:E011]",
      "markdownAnchor": "curated-e011",
      "evidenceRefs": [
        "mysql:table:jsh_material_category"
      ],
      "relatedBlockIds": [
        "review:table:jsh_material_category"
      ]
    },
    {
      "linePrefix": "[TRACE:E012]",
      "markdownAnchor": "curated-e012",
      "evidenceRefs": [
        "mysql:table:jsh_material_current_stock"
      ],
      "relatedBlockIds": [
        "review:table:jsh_material_current_stock"
      ]
    },
    {
      "linePrefix": "[TRACE:E013]",
      "markdownAnchor": "curated-e013",
      "evidenceRefs": [
        "mysql:table:jsh_material_extend"
      ],
      "relatedBlockIds": [
        "review:table:jsh_material_extend"
      ]
    },
    {
      "linePrefix": "[TRACE:E014]",
      "markdownAnchor": "curated-e014",
      "evidenceRefs": [
        "mysql:table:jsh_material_initial_stock"
      ],
      "relatedBlockIds": [
        "review:table:jsh_material_initial_stock"
      ]
    },
    {
      "linePrefix": "[TRACE:E015]",
      "markdownAnchor": "curated-e015",
      "evidenceRefs": [
        "mysql:table:jsh_material_property"
      ],
      "relatedBlockIds": [
        "review:table:jsh_material_property"
      ]
    },
    {
      "linePrefix": "[TRACE:E016]",
      "markdownAnchor": "curated-e016",
      "evidenceRefs": [
        "mysql:table:jsh_msg"
      ],
      "relatedBlockIds": [
        "review:table:jsh_msg"
      ]
    },
    {
      "linePrefix": "[TRACE:E017]",
      "markdownAnchor": "curated-e017",
      "evidenceRefs": [
        "mysql:table:jsh_orga_user_rel"
      ],
      "relatedBlockIds": [
        "review:table:jsh_orga_user_rel"
      ]
    },
    {
      "linePrefix": "[TRACE:E018]",
      "markdownAnchor": "curated-e018",
      "evidenceRefs": [
        "mysql:table:jsh_organization"
      ],
      "relatedBlockIds": [
        "review:table:jsh_organization"
      ]
    },
    {
      "linePrefix": "[TRACE:E019]",
      "markdownAnchor": "curated-e019",
      "evidenceRefs": [
        "mysql:table:jsh_person"
      ],
      "relatedBlockIds": [
        "review:table:jsh_person"
      ]
    },
    {
      "linePrefix": "[TRACE:E020]",
      "markdownAnchor": "curated-e020",
      "evidenceRefs": [
        "mysql:table:jsh_platform_config"
      ],
      "relatedBlockIds": [
        "review:table:jsh_platform_config"
      ]
    },
    {
      "linePrefix": "[TRACE:E021]",
      "markdownAnchor": "curated-e021",
      "evidenceRefs": [
        "mysql:table:jsh_role"
      ],
      "relatedBlockIds": [
        "review:table:jsh_role"
      ]
    },
    {
      "linePrefix": "[TRACE:E022]",
      "markdownAnchor": "curated-e022",
      "evidenceRefs": [
        "mysql:table:jsh_serial_number"
      ],
      "relatedBlockIds": [
        "review:table:jsh_serial_number"
      ]
    },
    {
      "linePrefix": "[TRACE:E023]",
      "markdownAnchor": "curated-e023",
      "evidenceRefs": [
        "mysql:table:jsh_supplier"
      ],
      "relatedBlockIds": [
        "review:table:jsh_supplier"
      ]
    },
    {
      "linePrefix": "[TRACE:E024]",
      "markdownAnchor": "curated-e024",
      "evidenceRefs": [
        "mysql:table:jsh_sys_dict_data"
      ],
      "relatedBlockIds": [
        "review:table:jsh_sys_dict_data"
      ]
    },
    {
      "linePrefix": "[TRACE:E025]",
      "markdownAnchor": "curated-e025",
      "evidenceRefs": [
        "mysql:table:jsh_sys_dict_type"
      ],
      "relatedBlockIds": [
        "review:table:jsh_sys_dict_type"
      ]
    },
    {
      "linePrefix": "[TRACE:E026]",
      "markdownAnchor": "curated-e026",
      "evidenceRefs": [
        "mysql:table:jsh_system_config"
      ],
      "relatedBlockIds": [
        "review:table:jsh_system_config"
      ]
    },
    {
      "linePrefix": "[TRACE:E027]",
      "markdownAnchor": "curated-e027",
      "evidenceRefs": [
        "mysql:table:jsh_tenant"
      ],
      "relatedBlockIds": [
        "review:table:jsh_tenant"
      ]
    },
    {
      "linePrefix": "[TRACE:E028]",
      "markdownAnchor": "curated-e028",
      "evidenceRefs": [
        "mysql:table:jsh_unit"
      ],
      "relatedBlockIds": [
        "review:table:jsh_unit"
      ]
    },
    {
      "linePrefix": "[TRACE:E029]",
      "markdownAnchor": "curated-e029",
      "evidenceRefs": [
        "mysql:table:jsh_user"
      ],
      "relatedBlockIds": [
        "review:table:jsh_user"
      ]
    },
    {
      "linePrefix": "[TRACE:E030]",
      "markdownAnchor": "curated-e030",
      "evidenceRefs": [
        "mysql:table:jsh_user_business"
      ],
      "relatedBlockIds": [
        "review:table:jsh_user_business"
      ]
    },
    {
      "linePrefix": "[TRACE:E031]",
      "markdownAnchor": "curated-e031",
      "evidenceRefs": [
        "mysql:procedure:sp_rebalance_below_low_stock_requisition"
      ],
      "relatedBlockIds": [
        "review:procedure:sp_rebalance_below_low_stock_requisition"
      ]
    },
    {
      "linePrefix": "[TRACE:E032]",
      "markdownAnchor": "curated-e032",
      "evidenceRefs": [
        "mysql:procedure:sp_rebalance_over_high_stock_sales"
      ],
      "relatedBlockIds": [
        "review:procedure:sp_rebalance_over_high_stock_sales"
      ]
    },
    {
      "linePrefix": "[TRACE:E033]",
      "markdownAnchor": "curated-e033",
      "evidenceRefs": [
        "mysql:procedure:sp_run_inventory_stock_rebalance"
      ],
      "relatedBlockIds": [
        "review:procedure:sp_run_inventory_stock_rebalance"
      ]
    },
    {
      "linePrefix": "[TRACE:E034]",
      "markdownAnchor": "curated-e034",
      "evidenceRefs": [
        "mysql:procedure:sp_run_retail_return_rate_and_fact_sync"
      ],
      "relatedBlockIds": [
        "review:procedure:sp_run_retail_return_rate_and_fact_sync"
      ]
    },
    {
      "linePrefix": "[TRACE:E035]",
      "markdownAnchor": "curated-e035",
      "evidenceRefs": [
        "mysql:procedure:sp_run_store_retail_return_coverage_and_fact_sync"
      ],
      "relatedBlockIds": [
        "review:procedure:sp_run_store_retail_return_coverage_and_fact_sync"
      ]
    },
    {
      "linePrefix": "[TRACE:E036]",
      "markdownAnchor": "curated-e036",
      "evidenceRefs": [
        "mysql:procedure:sp_sync_retail_out_fact_batch"
      ],
      "relatedBlockIds": [
        "review:procedure:sp_sync_retail_out_fact_batch"
      ]
    },
    {
      "linePrefix": "[TRACE:G4]",
      "markdownAnchor": "curated-g4",
      "evidenceRefs": [
        "mysql:table:jsh_depot_head",
        "mysql:table:jsh_account_head"
      ],
      "relatedBlockIds": [
        "review:fields"
      ]
    },
    {
      "linePrefix": "[TRACE:G6]",
      "markdownAnchor": "curated-g6",
      "evidenceRefs": [
        "mysql:table:jsh_system_config",
        "github:frozen:negative-stock"
      ],
      "relatedBlockIds": [
        "review:governance:negative-stock"
      ]
    },
    {
      "linePrefix": "[TRACE:G8]",
      "markdownAnchor": "curated-g8",
      "evidenceRefs": [
        "mysql:table:jsh_depot_head",
        "mysql:table:jsh_depot_item",
        "mysql:table:jsh_account_item"
      ],
      "relatedBlockIds": [
        "review:questions"
      ]
    },
    {
      "linePrefix": "[TRACE:G9]",
      "markdownAnchor": "curated-g9",
      "evidenceRefs": [
        "mysql:table:jsh_system_config",
        "github:frozen:negative-stock"
      ],
      "relatedBlockIds": [
        "review:governance:negative-stock"
      ]
    },
    {
      "linePrefix": "[TRACE:G10]",
      "markdownAnchor": "curated-g10",
      "evidenceRefs": [
        "mysql:table:jsh_depot_head",
        "github:frozen:debt-schema"
      ],
      "relatedBlockIds": [
        "review:governance:debt-fields"
      ]
    },
    {
      "linePrefix": "[TRACE:G11]",
      "markdownAnchor": "curated-g11",
      "evidenceRefs": [
        "mysql:table:jsh_depot_head",
        "github:frozen:status-nine",
        "gap:official:status-9"
      ],
      "relatedBlockIds": [
        "review:governance:document-status"
      ]
    },
    {
      "linePrefix": "[TRACE:G12]",
      "markdownAnchor": "curated-g12",
      "evidenceRefs": [
        "mysql:table:jsh_account",
        "mysql:procedure:sp_run_inventory_stock_rebalance",
        "mysql:dml:digests"
      ],
      "relatedBlockIds": [
        "review:coverage"
      ]
    }
  ],
  "generationManifest": {
    "provider": "CODEX_CHATGPT_SESSION",
    "model": "gpt-5.6-luna",
    "reasoningEffort": "xhigh",
    "inputDigest": "sha256:5530d4c103e82ddac9907d7ccce9232e18bb392e55f880b77f6e22d9cce44b49",
    "outputDigest": "sha256:9ece6fb99c7933bcdcd012a0ee3f936b8714f3f336c3a07b492027d31c3ccd66"
  }
} as const;

const excludedCrossSourceEvidence = new Set([
  'github:frozen:negative-stock',
  'github:frozen:debt-schema',
  'github:frozen:status-nine',
  'gap:official:status-9',
]);

const sourcePureMarkdown = legacyCuratedMysqlReviewDraft.markdown
  .replace(
    '目标是把数据库结构、扩展过程和跨源差异放在同一份可复核文档中；没有业务行分布结论。',
    '目标是把数据库结构、扩展过程和待确认问题放在同一份可复核文档中；没有业务行分布结论。',
  )
  .replace(
    '负库存同时保留 MySQL 的 `minus_stock_flag` 字段和 GitHub 冻结词汇“支持负库存”；不从快照推算库存分布或租户效果。',
    '数据库快照保留 `minus_stock_flag` 配置字段；字段存在不能单独推出各租户实际的负库存政策。',
  )
  .replace(
    '负库存治理关系：MySQL 的 `minus_stock_flag` 字段与 GitHub 冻结词汇“支持负库存”都被保留；统一禁止负库存仍是待确认目标，不是当前生产事实。',
    '负库存配置：`minus_stock_flag` 字段已保存；具体允许或禁止规则需后续来源确认。',
  )
  .replace(
    '欠款字段治理关系：当前部署 `jsh_depot_head` 的字段结构与 GitHub 冻结 schema 记录存在差异；欠款指标保持阻断。',
    '欠款字段：当前部署 `jsh_depot_head` 未见 `debt` 与 `last_debt` 字段；字段差异的历史原因需后续来源确认。',
  )
  .replace(
    '单据状态治理关系：MySQL `status` 字段明确包含 9=审核中，GitHub 冻结记录只包含“审核中”界面词汇；官方状态 9 的定义保留为资料缺口，存在时间漂移风险。',
    '单据状态：当前 `status` 列含 0、1、2、3、9；状态 9 的业务定义需后续来源确认。',
  );

export const generatedCuratedMysqlReview = {
  ...legacyCuratedMysqlReviewDraft,
  markdown: sourcePureMarkdown,
  markdownSha256: 'sha256:a4a3d1fa8655e72c7f4032f797157ed10a222ddaed5731ef68ca2b95e7f40665',
  evidence: legacyCuratedMysqlReviewDraft.evidence.filter((evidence) => (
    !excludedCrossSourceEvidence.has(evidence.evidenceRef)
  )),
  traceLinks: legacyCuratedMysqlReviewDraft.traceLinks.map((trace) => ({
    ...trace,
    evidenceRefs: trace.evidenceRefs.filter((evidenceRef) => !excludedCrossSourceEvidence.has(evidenceRef)),
  })),
  generationManifest: {
    ...legacyCuratedMysqlReviewDraft.generationManifest,
    outputDigest: 'sha256:1d81ba71292fa6c6fa68581818509707cef0eec2407aa29d85f229c1b9df3589',
  },
} as const;
