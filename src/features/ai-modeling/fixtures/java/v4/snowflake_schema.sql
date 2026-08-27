-- 表：商品
-- 表说明：由商品编码标识并被库存、出入库、订单、发货和维修过程共享的商品主数据。
CREATE TABLE entity_product (
  -- 字段说明：商品实体的技术代理键。
  field_product_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识商品的业务编码。
  field_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：商品名称。
  field_product_name VARCHAR(255) NOT NULL,
  -- 字段说明：用于商品分类分析的品类。
  field_product_category VARCHAR(128) NOT NULL,
  -- 字段说明：用于商品系列分析的系列属性。
  field_product_series VARCHAR(128),
  -- 字段说明：商品规格描述。
  field_product_specification VARCHAR(255),
  -- 字段说明：商品主数据当前有效的标准成本，不回溯历史成本。
  field_product_standard_cost DECIMAL(18, 2) NOT NULL,
  CONSTRAINT uq_entity_product_business_key UNIQUE (field_product_code)
);

-- 表：仓库
-- 表说明：由仓库编码标识并承载仓库名称、区域、类型和行政区县归属的仓储主数据。
CREATE TABLE entity_warehouse (
  -- 字段说明：仓库实体的技术代理键。
  field_warehouse_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识仓库的业务编码。
  field_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：用于按具体仓库分析的仓库名称。
  field_warehouse_name VARCHAR(255) NOT NULL,
  -- 字段说明：仓库所属业务区域，如华北、华东、华南。
  field_warehouse_region VARCHAR(128) NOT NULL,
  -- 字段说明：仓库的业务类型。
  field_warehouse_type VARCHAR(128),
  -- 字段说明：仓库归属的行政区县编码。
  field_warehouse_district_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_warehouse_business_key UNIQUE (field_warehouse_code),
  CONSTRAINT fk_entity_warehouse_field_warehouse_district_code FOREIGN KEY (field_warehouse_district_code) REFERENCES entity_administrative_region(field_region_code)
);

-- 表：库位
-- 表说明：由WMS统一分配的全局唯一库位编码标识，并归属于一个仓库的库位主数据。
CREATE TABLE entity_location (
  -- 字段说明：库位实体的技术代理键。
  field_location_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：由WMS统一分配且在全部仓库中唯一的库位编码。
  field_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：用于按具体库位分析的库位名称。
  field_location_name VARCHAR(255) NOT NULL,
  -- 字段说明：库位的业务类型。
  field_location_type VARCHAR(128),
  -- 字段说明：库位所属仓库的业务编码。
  field_location_warehouse_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_location_business_key UNIQUE (field_location_code),
  CONSTRAINT fk_entity_location_field_location_warehouse_code FOREIGN KEY (field_location_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code)
);

-- 表：客户
-- 表说明：由客户编码标识、跨订单共享并参与订单履约、发货和维修过程的客户主数据。
CREATE TABLE entity_customer (
  -- 字段说明：客户实体的技术代理键。
  field_customer_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识客户的业务编码。
  field_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：用于按具体客户分析的客户名称。
  field_customer_name VARCHAR(255) NOT NULL,
  -- 字段说明：用于客户分层分析的客户等级。
  field_customer_level VARCHAR(128),
  -- 字段说明：客户的销售区域名称属性。
  field_customer_sales_region VARCHAR(128),
  -- 字段说明：客户所归属行政区域任一级成员的编码。
  field_customer_sales_region_code VARCHAR(255),
  CONSTRAINT uq_entity_customer_business_key UNIQUE (field_customer_code),
  CONSTRAINT fk_entity_customer_field_customer_sales_region_code FOREIGN KEY (field_customer_sales_region_code) REFERENCES entity_administrative_region(field_region_code)
);

-- 表：服务网点
-- 表说明：由服务网点编码标识、承担维修服务并跨维修工单复用的服务组织主数据。
CREATE TABLE entity_service_location (
  -- 字段说明：服务网点实体的技术代理键。
  field_service_location_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识服务网点的业务编码。
  field_service_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：用于按具体网点分析维修业务的服务网点名称。
  field_service_location_name VARCHAR(255) NOT NULL,
  -- 字段说明：服务网点覆盖的服务区域。
  field_service_area VARCHAR(128),
  -- 字段说明：服务网点的等级属性。
  field_service_location_level VARCHAR(128),
  -- 字段说明：服务网点所关联行政区域市级成员的编码。
  field_service_location_city_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_service_location_business_key UNIQUE (field_service_location_code),
  CONSTRAINT fk_entity_service_location_field_service_location_city_code FOREIGN KEY (field_service_location_city_code) REFERENCES entity_administrative_region(field_region_code)
);

-- 表：行政区域
-- 表说明：跨仓库、客户和服务网点复用的标准行政区域主数据，内部承载大区、省、市、区县成员树。
CREATE TABLE entity_administrative_region (
  -- 字段说明：行政区域实体的技术代理键。
  field_region_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识一个行政区域成员的业务编码。
  field_region_code VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员名称。
  field_region_name VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员所处级别，取值为大区、省、市、区县。
  field_region_level VARCHAR(128) NOT NULL,
  -- 字段说明：同一行政区域成员树中的直接上级区域编码。
  field_parent_region_code VARCHAR(255),
  -- 字段说明：承载大区到省、市、区县下钻的行政区域成员层级字段。
  field_region_member VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员在大区、省、市、区县成员树中的编码路径。
  field_region_member_code_path VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_administrative_region_business_key UNIQUE (field_region_code),
  CONSTRAINT fk_entity_administrative_region_field_parent_region_code FOREIGN KEY (field_parent_region_code) REFERENCES entity_administrative_region(field_region_code)
);

-- 表：库存快照
-- 表说明：在明确快照时间保存指定仓库、库位、商品和批次的库存状态。
-- 业务粒度：每个快照时点、仓库、库位、商品、批次一行
CREATE TABLE event_inventory_snapshot (
  -- 字段说明：唯一标识一条库存快照事件记录的技术键。
  field_inventory_snapshot_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：库存状态被观察和保存的快照时间。
  field_inventory_snapshot_time TIMESTAMP NOT NULL,
  -- 字段说明：库存快照所对应商品的业务编码。
  field_snapshot_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存快照所在仓库的业务编码。
  field_snapshot_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存快照所在库位的业务编码。
  field_snapshot_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：区分库存批次的退化维度。
  field_inventory_batch_number VARCHAR(255) NOT NULL,
  -- 字段说明：快照时点的在库数量。
  field_on_hand_quantity INTEGER NOT NULL,
  -- 字段说明：快照时点的锁定数量。
  field_locked_quantity INTEGER NOT NULL,
  -- 字段说明：用于低库存判断的安全库存阈值。
  field_safety_stock_quantity INTEGER NOT NULL,
  -- 字段说明：库存批次的入库日期，用于计算库龄。
  field_inventory_receipt_date DATE,
  -- 字段说明：逐行由在库数量减锁定数量得到的可用库存数量；业务汇总还需排除冻结库位。
  field_available_inventory_quantity INTEGER NOT NULL,
  -- 字段说明：逐行等于在库数量乘商品当前有效标准成本的库存金额。
  field_inventory_amount DECIMAL(18, 2) NOT NULL,
  -- 字段说明：统计日期与批次入库日期之差，不跨批次相加。
  field_inventory_age_days INTEGER,
  CONSTRAINT fk_event_inventory_snapshot_field_snapshot_product_code FOREIGN KEY (field_snapshot_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_inventory_snapshot_field_snapshot_warehouse_code FOREIGN KEY (field_snapshot_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_inventory_snapshot_field_snapshot_location_code FOREIGN KEY (field_snapshot_location_code) REFERENCES entity_location(field_location_code)
);

-- 表：库存移动
-- 表说明：以业务单据商品明细为粒度统一记录已完成的商品入库和出库流水，并以移动方向和移动类型区分动作。
-- 业务粒度：每张入库单或出库单的商品明细一行，以移动方向区分入库与出库
CREATE TABLE event_inventory_movement (
  -- 字段说明：唯一标识一条库存移动事件记录的技术键。
  field_inventory_movement_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：库存移动来源业务单据的单号。
  field_movement_document_number VARCHAR(255) NOT NULL,
  -- 字段说明：入库完成时间或出库完成时间统一形成的库存移动事件时间。
  field_movement_time TIMESTAMP NOT NULL,
  -- 字段说明：区分入库方向和出库方向的库存移动方向。
  field_movement_direction VARCHAR(128) NOT NULL,
  -- 字段说明：统一承载入库类型或出库类型的库存移动业务分类。
  field_movement_type VARCHAR(128) NOT NULL,
  -- 字段说明：库存移动单据的普通状态分类；指标仅统计已完成记录。
  field_movement_status VARCHAR(64) NOT NULL,
  -- 字段说明：一次入库或出库明细的移动数量。
  field_movement_quantity INTEGER NOT NULL,
  -- 字段说明：库存移动所对应商品的业务编码。
  field_movement_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存移动进入或离开的仓库业务编码。
  field_movement_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存移动进入或离开的库位业务编码。
  field_movement_location_code VARCHAR(255) NOT NULL,
  CONSTRAINT fk_event_inventory_movement_field_movement_product_code FOREIGN KEY (field_movement_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_inventory_movement_field_movement_warehouse_code FOREIGN KEY (field_movement_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_inventory_movement_field_movement_location_code FOREIGN KEY (field_movement_location_code) REFERENCES entity_location(field_location_code)
);

-- 表：销售订单履约
-- 表说明：在订单创建时间记录销售订单商品明细的订购、取消、承诺发货和履约状态。
-- 业务粒度：每张销售订单的商品明细一行，由销售订单号和订单行号共同标识
CREATE TABLE event_sales_order_fulfillment (
  -- 字段说明：唯一标识一条销售订单履约事件记录的技术键。
  field_order_fulfillment_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：销售订单号，与订单行号共同组成订单商品明细业务键。
  field_sales_order_number VARCHAR(255) NOT NULL,
  -- 字段说明：销售订单中的商品明细行号，与销售订单号共同组成业务键。
  field_sales_order_line_number INTEGER NOT NULL,
  -- 字段说明：销售订单商品明细所属订单的创建时间。
  field_order_created_at TIMESTAMP NOT NULL,
  -- 字段说明：订单所属客户的业务编码。
  field_order_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：订单行所包含商品的业务编码。
  field_order_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：销售订单行的订购数量。
  field_ordered_quantity INTEGER NOT NULL,
  -- 字段说明：销售订单行的取消数量。
  field_cancelled_quantity INTEGER NOT NULL,
  -- 字段说明：逐订单行由订购数量减取消数量得到的有效订购数量。
  field_effective_order_quantity INTEGER NOT NULL,
  -- 字段说明：订单行有效订购数量减累计已发货数量且最小为零的预计算欠货数量。
  field_backorder_quantity INTEGER NOT NULL,
  -- 字段说明：订单行承诺发货日期，是履约和按时发货判断的基准。
  field_promised_ship_date DATE,
  -- 字段说明：销售订单行的普通履约状态分类。
  field_order_status VARCHAR(64) NOT NULL,
  CONSTRAINT fk_event_sales_order_fulfillment_field_order_customer_code FOREIGN KEY (field_order_customer_code) REFERENCES entity_customer(field_customer_code),
  CONSTRAINT fk_event_sales_order_fulfillment_field_order_product_code FOREIGN KEY (field_order_product_code) REFERENCES entity_product(field_product_code)
);

-- 表：商品发货
-- 表说明：记录一张发货单对某个订单商品明细的一次实际发货，同一订单明细允许多次发货。
-- 业务粒度：每张发货单的订单商品明细一行，同一订单明细可以对应多次发货
CREATE TABLE event_product_shipment (
  -- 字段说明：唯一标识一条商品发货事件记录的技术键。
  field_product_shipment_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：发货明细携带的发货单号退化维度。
  field_shipment_number VARCHAR(255) NOT NULL,
  -- 字段说明：发货所履行订单行的销售订单号。
  field_shipment_sales_order_number VARCHAR(255) NOT NULL,
  -- 字段说明：发货所履行订单行的行号。
  field_shipment_order_line_number INTEGER NOT NULL,
  -- 字段说明：商品发货事件的实际发生时间。
  field_actual_shipment_time TIMESTAMP NOT NULL,
  -- 字段说明：发货所对应商品的业务编码。
  field_shipment_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：发货来源仓库的业务编码。
  field_shipment_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：发货所关联客户的业务编码。
  field_shipment_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：一次发货明细的发货数量。
  field_shipped_quantity INTEGER NOT NULL,
  -- 字段说明：商品发货所使用的承运商。
  field_carrier VARCHAR(128),
  -- 字段说明：发货所对应的物流运单号退化维度。
  field_tracking_number VARCHAR(255),
  -- 字段说明：发货单的普通状态分类，用于识别作废发货单。
  field_shipment_status VARCHAR(64) NOT NULL,
  -- 字段说明：实际发货日期不晚于承诺日期时记1，否则记0。
  field_on_time_shipment_flag INTEGER NOT NULL,
  -- 字段说明：承诺日期非空且发货单未作废时记1，否则记0。
  field_valid_shipment_line_flag INTEGER NOT NULL,
  CONSTRAINT fk_event_product_shipment_field_shipment_product_code FOREIGN KEY (field_shipment_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_product_shipment_field_shipment_warehouse_code FOREIGN KEY (field_shipment_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_product_shipment_field_shipment_customer_code FOREIGN KEY (field_shipment_customer_code) REFERENCES entity_customer(field_customer_code)
);

-- 表：维修工单处理
-- 表说明：从工单创建开始记录一张维修工单的首次响应、维修完成、状态、故障分类和SLA判断信息。
-- 业务粒度：每张维修工单一行，所有按日、月统计固定按工单创建日期归属
CREATE TABLE event_repair_order (
  -- 字段说明：唯一标识一张维修工单处理事件的技术键。
  field_repair_order_sk BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识一张维修工单的业务单号。
  field_repair_order_number VARCHAR(255) NOT NULL,
  -- 字段说明：维修工单创建时间，是SLA起点和维修日月统计的固定日期归属。
  field_repair_created_at TIMESTAMP NOT NULL,
  -- 字段说明：维修工单首次响应时间，是响应SLA终点。
  field_first_response_at TIMESTAMP,
  -- 字段说明：维修工单完成时间，是完成SLA终点。
  field_repair_completed_at TIMESTAMP,
  -- 字段说明：维修工单所属客户的业务编码。
  field_repair_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：维修工单所对应商品的业务编码。
  field_repair_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：负责处理维修工单的服务网点业务编码。
  field_repair_service_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：维修工单的普通状态分类。
  field_repair_status VARCHAR(64) NOT NULL,
  -- 字段说明：维修工单记录的故障分类。
  field_fault_type VARCHAR(128) NOT NULL,
  -- 字段说明：正常工单记1；已取消、测试或重复工单记0。
  field_repair_sla_valid_flag INTEGER NOT NULL,
  -- 字段说明：维修响应SLA阈值，默认4个自然小时。
  field_response_target_hours DECIMAL(18, 4) NOT NULL,
  -- 字段说明：维修完成SLA阈值，默认48个自然小时。
  field_completion_target_hours DECIMAL(18, 4) NOT NULL,
  -- 字段说明：SLA有效且已响应工单从创建到首次响应所用的自然小时数。
  field_repair_response_duration_hours DECIMAL(18, 4),
  -- 字段说明：SLA有效且已完成工单从创建到维修完成所用的自然小时数。
  field_repair_completion_duration_hours DECIMAL(18, 4),
  -- 字段说明：响应时长不超过响应目标小时数且SLA有效时记1，否则记0。
  field_repair_response_sla_met_flag INTEGER NOT NULL,
  -- 字段说明：SLA有效且首次响应时间非空的工单记1，否则记0。
  field_repair_valid_responded_flag INTEGER NOT NULL,
  -- 字段说明：完成时长不超过完成目标小时数且SLA有效时记1，否则记0。
  field_repair_completion_sla_met_flag INTEGER NOT NULL,
  -- 字段说明：SLA有效且维修完成时间非空的工单记1，否则记0。
  field_repair_valid_completed_flag INTEGER NOT NULL,
  CONSTRAINT fk_event_repair_order_field_repair_customer_code FOREIGN KEY (field_repair_customer_code) REFERENCES entity_customer(field_customer_code),
  CONSTRAINT fk_event_repair_order_field_repair_product_code FOREIGN KEY (field_repair_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_repair_order_field_repair_service_location_code FOREIGN KEY (field_repair_service_location_code) REFERENCES entity_service_location(field_service_location_code)
);
