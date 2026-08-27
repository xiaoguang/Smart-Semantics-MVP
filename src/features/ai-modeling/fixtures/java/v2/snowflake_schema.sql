-- 表：商品
-- 表说明：由商品编码标识并被库存、库存移动、订单履约和发货过程复用的商品主数据。
CREATE TABLE entity_product (
  -- 字段说明：商品实体的技术代理键。
  field_product_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：由业务系统分配并稳定标识商品的编码。
  field_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：商品名称。
  field_product_name VARCHAR(255) NOT NULL,
  -- 字段说明：商品所属品类，作为稳定分析切片。
  field_product_category VARCHAR(128),
  -- 字段说明：商品所属系列，作为稳定分析切片。
  field_product_series VARCHAR(128),
  -- 字段说明：商品的型号或规格描述。
  field_product_specification VARCHAR(255),
  -- 字段说明：商品主数据当前有效的标准单位成本。
  field_product_standard_cost DECIMAL(18, 4),
  CONSTRAINT uq_entity_product_business_key UNIQUE (field_product_code)
);

-- 表：仓库
-- 表说明：由仓库编码标识并被库存、库存移动及发货过程复用的仓库主数据。
CREATE TABLE entity_warehouse (
  -- 字段说明：仓库实体的技术代理键。
  field_warehouse_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识仓库的业务编码。
  field_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：仓库名称。
  field_warehouse_name VARCHAR(255) NOT NULL,
  -- 字段说明：仓库所属业务区域，例如华北、华东或华南。
  field_warehouse_region VARCHAR(128) NOT NULL,
  -- 字段说明：仓库主数据中的仓库类型。
  field_warehouse_type VARCHAR(128),
  -- 字段说明：仓库所归属区县的行政区域编码。
  field_warehouse_district_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_warehouse_business_key UNIQUE (field_warehouse_code),
  CONSTRAINT fk_entity_warehouse_field_warehouse_district_code FOREIGN KEY (field_warehouse_district_code) REFERENCES entity_administrative_region(field_administrative_region_code)
);

-- 表：库位
-- 表说明：由WMS全局唯一库位编码标识、归属于仓库并被库存及库存移动过程复用的库位主数据。
CREATE TABLE entity_storage_location (
  -- 字段说明：库位实体的技术代理键。
  field_storage_location_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：由WMS统一分配并在全部仓库中唯一的库位编码。
  field_storage_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：库位名称。
  field_storage_location_name VARCHAR(255) NOT NULL,
  -- 字段说明：库位主数据中的库位类型。
  field_storage_location_type VARCHAR(128),
  -- 字段说明：库位所属仓库的业务编码。
  field_storage_location_warehouse_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_storage_location_business_key UNIQUE (field_storage_location_code),
  CONSTRAINT fk_entity_storage_location_field_storage_location_warehouse_code FOREIGN KEY (field_storage_location_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code)
);

-- 表：客户
-- 表说明：由客户编码标识、跨订单共享并参与订单履约和发货过程的客户主数据。
CREATE TABLE entity_customer (
  -- 字段说明：客户实体的技术代理键。
  field_customer_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识客户的业务编码。
  field_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：客户名称。
  field_customer_name VARCHAR(255) NOT NULL,
  -- 字段说明：用于客户分层分析的客户等级。
  field_customer_level VARCHAR(128),
  -- 字段说明：客户的销售区域业务属性。
  field_customer_sales_region VARCHAR(128),
  -- 字段说明：引用行政区域任一级成员的销售区域编码。
  field_customer_sales_region_code VARCHAR(255),
  CONSTRAINT uq_entity_customer_business_key UNIQUE (field_customer_code),
  CONSTRAINT fk_entity_customer_field_customer_sales_region_code FOREIGN KEY (field_customer_sales_region_code) REFERENCES entity_administrative_region(field_administrative_region_code)
);

-- 表：行政区域
-- 表说明：跨仓库和客户复用的标准行政区域主数据，内部承载大区、省、市、区县成员树。
CREATE TABLE entity_administrative_region (
  -- 字段说明：行政区域实体的技术代理键。
  field_administrative_region_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：稳定标识行政区域成员的业务编码。
  field_administrative_region_code VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员名称。
  field_administrative_region_name VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员的级别，取值范围为大区、省、市或区县。
  field_administrative_region_level VARCHAR(128) NOT NULL,
  -- 字段说明：当前行政区域成员的直接上级区域编码；根成员可以为空。
  field_administrative_region_parent_code VARCHAR(255),
  -- 字段说明：承载大区到区县下钻的行政区域成员树节点。
  field_administrative_region_member VARCHAR(255) NOT NULL,
  -- 字段说明：从大区到当前成员的成员编码路径。
  field_administrative_region_code_path VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_administrative_region_business_key UNIQUE (field_administrative_region_code),
  CONSTRAINT fk_entity_administrative_region_field_administrative_region_parent_code FOREIGN KEY (field_administrative_region_parent_code) REFERENCES entity_administrative_region(field_administrative_region_code)
);

-- 表：库存快照
-- 表说明：在指定快照时间观察仓库、库位、商品及批次库存状态的库存快照事实。
-- 业务粒度：每个快照时点、仓库、库位、商品、批次一行。
CREATE TABLE event_inventory_snapshot (
  -- 字段说明：库存快照行的技术事件标识。
  field_inventory_snapshot_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：库存状态被观察和保存的快照时间。
  field_inventory_snapshot_time TIMESTAMP NOT NULL,
  -- 字段说明：快照记录对应的商品编码。
  field_inventory_snapshot_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：快照记录所在仓库的编码。
  field_inventory_snapshot_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：快照记录所在库位的编码。
  field_inventory_snapshot_storage_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存快照粒度中的批次退化维度。
  field_inventory_snapshot_batch_number VARCHAR(255) NOT NULL,
  -- 字段说明：快照行记录的在库数量。
  field_inventory_snapshot_on_hand_quantity INTEGER NOT NULL,
  -- 字段说明：快照行记录的锁定数量。
  field_inventory_snapshot_locked_quantity INTEGER NOT NULL,
  -- 字段说明：快照行上的安全库存阈值。
  field_inventory_snapshot_safety_stock_quantity INTEGER,
  -- 字段说明：库存批次的入库日期，用于计算库龄。
  field_inventory_snapshot_receipt_date DATE,
  -- 字段说明：快照行的库存金额，文档定义为在库数量乘商品标准成本。
  field_inventory_snapshot_inventory_amount DECIMAL(18, 4),
  -- 字段说明：统计日期与库存批次入库日期之差，不跨批次相加。
  field_inventory_snapshot_inventory_age_days INTEGER,
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_snapshot_product_code FOREIGN KEY (field_inventory_snapshot_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_snapshot_warehouse_code FOREIGN KEY (field_inventory_snapshot_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_snapshot_storage_location_code FOREIGN KEY (field_inventory_snapshot_storage_location_code) REFERENCES entity_storage_location(field_storage_location_code)
);

-- 表：库存移动
-- 表说明：统一承载已完成商品入库与商品出库明细，以移动方向和移动类型区分业务动作。
-- 业务粒度：每张库存移动业务单的一条商品明细一行；入库和出库以移动方向区分。
CREATE TABLE event_inventory_movement (
  -- 字段说明：库存移动明细的技术事件标识。
  field_inventory_movement_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：入库完成时间或出库完成时间归一后的库存移动时间。
  field_inventory_movement_time TIMESTAMP NOT NULL,
  -- 字段说明：库存移动明细对应的商品编码。
  field_inventory_movement_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存移动明细进入或离开的仓库编码。
  field_inventory_movement_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存移动明细涉及的库位编码。
  field_inventory_movement_storage_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：入库单号或出库单号归一后的库存移动业务单号。
  field_inventory_movement_business_document_number VARCHAR(255) NOT NULL,
  -- 字段说明：入库数量或出库数量归一后的移动数量。
  field_inventory_movement_quantity INTEGER NOT NULL,
  -- 字段说明：入库类型或出库类型归一后的库存移动业务类型。
  field_inventory_movement_type VARCHAR(128) NOT NULL,
  -- 字段说明：区分商品入库与商品出库的库存移动方向。
  field_inventory_movement_direction VARCHAR(128) NOT NULL,
  -- 字段说明：库存移动单据的状态；本建模范围只统计已完成明细。
  field_inventory_movement_status VARCHAR(64) NOT NULL,
  CONSTRAINT fk_event_inventory_movement_field_inventory_movement_product_code FOREIGN KEY (field_inventory_movement_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_inventory_movement_field_inventory_movement_warehouse_code FOREIGN KEY (field_inventory_movement_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_inventory_movement_field_inventory_movement_storage_location_code FOREIGN KEY (field_inventory_movement_storage_location_code) REFERENCES entity_storage_location(field_storage_location_code)
);

-- 表：销售订单履约
-- 表说明：在订单创建时间形成的销售订单商品履约行，承载订购、取消、欠货、承诺日期和状态。
-- 业务粒度：每张销售订单的每个商品明细一行，由销售订单号与订单行号共同标识。
CREATE TABLE event_order_fulfillment (
  -- 字段说明：销售订单履约行的技术事件标识。
  field_order_fulfillment_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：销售订单履约行的订单创建时间。
  field_order_fulfillment_created_at TIMESTAMP NOT NULL,
  -- 字段说明：订单所属客户的编码。
  field_order_fulfillment_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：订单行所包含商品的编码。
  field_order_fulfillment_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：销售订单号，与订单行号共同构成订单履约行的业务键。
  field_order_fulfillment_sales_order_number VARCHAR(255) NOT NULL,
  -- 字段说明：订单内的商品行号，与销售订单号共同构成业务键。
  field_order_fulfillment_order_line_number INTEGER NOT NULL,
  -- 字段说明：订单履约行的订购数量。
  field_order_fulfillment_ordered_quantity INTEGER NOT NULL,
  -- 字段说明：订单履约行的取消数量。
  field_order_fulfillment_cancelled_quantity INTEGER NOT NULL,
  -- 字段说明：订单行约定的承诺发货日期。
  field_order_fulfillment_promised_ship_date DATE,
  -- 字段说明：订单履约行的状态分类。
  field_order_fulfillment_status VARCHAR(64) NOT NULL,
  -- 字段说明：订单行预计算的欠货数量，等于有效订购数量减累计已发货数量且最小为零。
  field_order_fulfillment_backorder_quantity INTEGER NOT NULL,
  CONSTRAINT fk_event_order_fulfillment_field_order_fulfillment_customer_code FOREIGN KEY (field_order_fulfillment_customer_code) REFERENCES entity_customer(field_customer_code),
  CONSTRAINT fk_event_order_fulfillment_field_order_fulfillment_product_code FOREIGN KEY (field_order_fulfillment_product_code) REFERENCES entity_product(field_product_code)
);

-- 表：商品发货
-- 表说明：在实际发货时间发生的一条发货单订单商品明细，同一订单行拆分发货时每次发货分别成行。
-- 业务粒度：每张发货单的一条订单商品明细一行；同一订单行拆分多次发货时，每次发货分别成行。
CREATE TABLE event_product_shipment (
  -- 字段说明：商品发货明细的技术事件标识。
  field_product_shipment_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：商品实际发出的日期时间。
  field_product_shipment_time TIMESTAMP NOT NULL,
  -- 字段说明：发货明细对应的客户编码。
  field_product_shipment_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：发货明细对应的商品编码。
  field_product_shipment_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：实际执行发货的仓库编码。
  field_product_shipment_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：发货事件中的发货单号退化维度。
  field_product_shipment_number VARCHAR(255) NOT NULL,
  -- 字段说明：发货明细所履行订单的销售订单号。
  field_product_shipment_sales_order_number VARCHAR(255) NOT NULL,
  -- 字段说明：发货明细所履行订单的订单行号。
  field_product_shipment_order_line_number INTEGER NOT NULL,
  -- 字段说明：本次发货明细的发货数量。
  field_product_shipment_quantity INTEGER NOT NULL,
  -- 字段说明：承担本次发货运输的承运商。
  field_product_shipment_carrier VARCHAR(255),
  -- 字段说明：本次发货对应的物流运单号。
  field_product_shipment_tracking_number VARCHAR(255),
  -- 字段说明：实际发货日期不晚于承诺日期时为1，否则为0；不参与记录可以为空。
  field_product_shipment_on_time_flag INTEGER,
  -- 字段说明：商品发货明细的状态分类，用于识别作废发货单。
  field_product_shipment_status VARCHAR(64) NOT NULL,
  -- 字段说明：发货及时率分母中每条有效发货订单行的计数贡献；拆分发货的最终计数方式仍待确认。
  field_product_shipment_valid_order_line_count INTEGER NOT NULL,
  CONSTRAINT fk_event_product_shipment_field_product_shipment_customer_code FOREIGN KEY (field_product_shipment_customer_code) REFERENCES entity_customer(field_customer_code),
  CONSTRAINT fk_event_product_shipment_field_product_shipment_product_code FOREIGN KEY (field_product_shipment_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_product_shipment_field_product_shipment_warehouse_code FOREIGN KEY (field_product_shipment_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code)
);
