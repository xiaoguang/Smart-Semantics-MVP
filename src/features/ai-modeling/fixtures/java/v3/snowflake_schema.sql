-- 表：商品
-- 表说明：以商品编码标识并被库存、移动、订单、发货和维修活动复用的商品主数据。
CREATE TABLE entity_product (
  -- 字段说明：商品实体的技术代理键。
  field_product_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：由业务系统稳定分配的商品编码。
  field_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：商品的业务名称。
  field_product_name VARCHAR(255) NOT NULL,
  -- 字段说明：商品所属品类，是商品属性而非独立实体。
  field_product_category VARCHAR(128),
  -- 字段说明：商品所属系列，是商品属性而非独立实体。
  field_product_series VARCHAR(128),
  -- 字段说明：商品的规格描述。
  field_product_specification VARCHAR(255),
  -- 字段说明：商品主数据当前有效的标准成本，不回溯历史成本。
  field_product_standard_cost DECIMAL(18, 4),
  CONSTRAINT uq_entity_product_business_key UNIQUE (field_product_code)
);

-- 表：仓库
-- 表说明：以仓库编码标识、具有区域和类型属性并归属于行政区域的仓储主数据。
CREATE TABLE entity_warehouse (
  -- 字段说明：仓库实体的技术代理键。
  field_warehouse_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：仓库的稳定业务编码。
  field_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：仓库的业务名称。
  field_warehouse_name VARCHAR(255) NOT NULL,
  -- 字段说明：仓库的大区分析属性，如华北、华东、华南。
  field_warehouse_region VARCHAR(128),
  -- 字段说明：仓库的业务类型。
  field_warehouse_type VARCHAR(128),
  -- 字段说明：仓库所属区县的行政区域编码。
  field_warehouse_district_region_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_warehouse_business_key UNIQUE (field_warehouse_code),
  CONSTRAINT fk_entity_warehouse_field_warehouse_district_region_code FOREIGN KEY (field_warehouse_district_region_code) REFERENCES entity_administrative_region(field_administrative_region_code)
);

-- 表：库位
-- 表说明：由WMS统一分配全局唯一编码、明确归属仓库的库位主数据。
CREATE TABLE entity_storage_location (
  -- 字段说明：库位实体的技术代理键。
  field_storage_location_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：由WMS统一分配且在全部仓库中唯一的库位编码。
  field_storage_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：库位所属仓库的业务编码。
  field_storage_location_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：库位的业务名称。
  field_storage_location_name VARCHAR(255) NOT NULL,
  -- 字段说明：库位的业务类型。
  field_storage_location_type VARCHAR(128),
  CONSTRAINT uq_entity_storage_location_business_key UNIQUE (field_storage_location_code),
  CONSTRAINT fk_entity_storage_location_field_storage_location_warehouse_code FOREIGN KEY (field_storage_location_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code)
);

-- 表：客户
-- 表说明：跨订单、发货及维修活动共享并可归属行政区域成员的客户主数据。
CREATE TABLE entity_customer (
  -- 字段说明：客户实体的技术代理键。
  field_customer_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：客户的稳定业务编码。
  field_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：客户的业务名称。
  field_customer_name VARCHAR(255) NOT NULL,
  -- 字段说明：用于客户分层分析的客户等级。
  field_customer_level VARCHAR(128),
  -- 字段说明：客户的销售区域分析属性。
  field_customer_sales_region VARCHAR(128),
  -- 字段说明：客户销售区域引用的行政区域成员编码，可指向任一级成员。
  field_customer_sales_region_code VARCHAR(255),
  CONSTRAINT uq_entity_customer_business_key UNIQUE (field_customer_code),
  CONSTRAINT fk_entity_customer_field_customer_sales_region_code FOREIGN KEY (field_customer_sales_region_code) REFERENCES entity_administrative_region(field_administrative_region_code)
);

-- 表：服务网点
-- 表说明：以服务网点编码标识、承担维修服务并归属行政区域城市成员的服务组织主数据。
CREATE TABLE entity_service_site (
  -- 字段说明：服务网点实体的技术代理键。
  field_service_site_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：服务网点的稳定业务编码。
  field_service_site_code VARCHAR(255) NOT NULL,
  -- 字段说明：服务网点的业务名称。
  field_service_site_name VARCHAR(255) NOT NULL,
  -- 字段说明：服务网点覆盖的服务区域。
  field_service_site_region VARCHAR(128),
  -- 字段说明：服务网点的等级属性。
  field_service_site_level VARCHAR(128),
  -- 字段说明：服务网点所在城市的行政区域编码。
  field_service_site_city_region_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_service_site_business_key UNIQUE (field_service_site_code),
  CONSTRAINT fk_entity_service_site_field_service_site_city_region_code FOREIGN KEY (field_service_site_city_region_code) REFERENCES entity_administrative_region(field_administrative_region_code)
);

-- 表：行政区域
-- 表说明：跨仓库、客户和服务网点复用，具有固定成员树及父级引用的标准行政区域主数据。
CREATE TABLE entity_administrative_region (
  -- 字段说明：行政区域实体的技术代理键。
  field_administrative_region_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：行政区域成员的稳定业务编码。
  field_administrative_region_code VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员的名称。
  field_administrative_region_name VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员的级别，取值为大区、省、市、区县。
  field_administrative_region_level VARCHAR(128) NOT NULL,
  -- 字段说明：同一行政区域成员树中直接父级成员的编码；顶级大区无父级。
  field_administrative_region_parent_code VARCHAR(255),
  -- 字段说明：承载大区到省、市、区县固定下钻顺序的行政区域成员层级字段。
  field_administrative_region_member VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员从大区到当前成员的编码路径。
  field_administrative_region_member_path VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_administrative_region_business_key UNIQUE (field_administrative_region_code),
  CONSTRAINT fk_entity_administrative_region_field_administrative_region_parent_code FOREIGN KEY (field_administrative_region_parent_code) REFERENCES entity_administrative_region(field_administrative_region_code)
);

-- 表：库存快照
-- 表说明：按快照时间、仓库、库位、商品和批次保存库存状态的周期快照事件。
-- 业务粒度：每个快照时点、仓库、库位、商品、批次一行。
CREATE TABLE event_inventory_snapshot (
  -- 字段说明：库存快照行的技术事件标识。
  field_inventory_snapshot_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：观察库存状态的快照时点。
  field_inventory_snapshot_time TIMESTAMP NOT NULL,
  -- 字段说明：库存记录对应商品的业务编码。
  field_inventory_snapshot_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存记录所在仓库的业务编码。
  field_inventory_snapshot_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存记录所在库位的全局唯一业务编码。
  field_inventory_snapshot_storage_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存快照行的批次退化维度。
  field_inventory_snapshot_batch_number VARCHAR(255) NOT NULL,
  -- 字段说明：快照行记录的在库数量。
  field_inventory_snapshot_on_hand_quantity INTEGER NOT NULL,
  -- 字段说明：快照行记录的锁定数量。
  field_inventory_snapshot_locked_quantity INTEGER NOT NULL,
  -- 字段说明：用于低库存判断的安全库存数量阈值。
  field_inventory_snapshot_safety_stock_quantity INTEGER,
  -- 字段说明：当前库存批次的入库日期，用于计算库龄。
  field_inventory_snapshot_inbound_date DATE,
  -- 字段说明：逐行按在库数量乘商品当前有效标准成本计算的库存金额。
  field_inventory_snapshot_amount DECIMAL(18, 4),
  -- 字段说明：统计日期减去入库日期所得的库存批次库龄，不能跨批次相加。
  field_inventory_snapshot_age_days INTEGER,
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_snapshot_product_code FOREIGN KEY (field_inventory_snapshot_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_snapshot_warehouse_code FOREIGN KEY (field_inventory_snapshot_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_snapshot_storage_location_code FOREIGN KEY (field_inventory_snapshot_storage_location_code) REFERENCES entity_storage_location(field_storage_location_code)
);

-- 表：库存移动
-- 表说明：统一承载已完成商品入库和商品出库明细，以移动方向、类型和状态区分具体库存动作。
-- 业务粒度：每张入库单或出库单的商品明细一行，由业务单号、商品及明细粒度区分，移动方向标识入库或出库。
CREATE TABLE event_inventory_movement (
  -- 字段说明：库存移动明细的技术事件标识。
  field_inventory_movement_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：库存移动完成的统一业务时间。
  field_movement_time TIMESTAMP NOT NULL,
  -- 字段说明：库存移动对应商品的业务编码。
  field_movement_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存移动进入或离开的仓库编码。
  field_movement_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存移动涉及的库位编码。
  field_movement_storage_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：入库单号或出库单号统一映射后的业务单号退化维度。
  field_movement_document_number VARCHAR(255) NOT NULL,
  -- 字段说明：入库数量或出库数量统一映射后的移动数量。
  field_movement_quantity INTEGER NOT NULL,
  -- 字段说明：库存移动的业务类型，承载原入库类型或出库类型。
  field_movement_type VARCHAR(128),
  -- 字段说明：库存移动单据的状态；当前指标仅统计已完成单据。
  field_movement_status VARCHAR(64) NOT NULL,
  -- 字段说明：区分商品入库和商品出库的移动方向。
  field_movement_direction VARCHAR(128) NOT NULL,
  CONSTRAINT fk_event_inventory_movement_field_movement_product_code FOREIGN KEY (field_movement_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_inventory_movement_field_movement_warehouse_code FOREIGN KEY (field_movement_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_inventory_movement_field_movement_storage_location_code FOREIGN KEY (field_movement_storage_location_code) REFERENCES entity_storage_location(field_storage_location_code)
);

-- 表：销售订单履约
-- 表说明：在订单创建时形成的销售订单商品行履约记录，承载订购、取消、欠货、承诺日期和订单状态。
-- 业务粒度：每张销售订单的商品明细一行，由销售订单号和订单行号共同标识。
CREATE TABLE event_sales_order_fulfillment (
  -- 字段说明：销售订单履约行的技术事件标识。
  field_sales_order_fulfillment_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：销售订单商品行对应订单的创建时间。
  field_sales_order_created_time TIMESTAMP NOT NULL,
  -- 字段说明：订单所属客户的业务编码。
  field_sales_order_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：订单行对应商品的业务编码。
  field_sales_order_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：销售订单号，与订单行号共同标识履约行。
  field_sales_order_number VARCHAR(255) NOT NULL,
  -- 字段说明：销售订单中的商品行号，与销售订单号共同标识履约行。
  field_sales_order_line_number INTEGER NOT NULL,
  -- 字段说明：销售订单行的订购数量。
  field_sales_order_ordered_quantity INTEGER NOT NULL,
  -- 字段说明：销售订单行的取消数量。
  field_sales_order_cancelled_quantity INTEGER NOT NULL,
  -- 字段说明：销售订单行约定的发货日期，是履约时效基准时间。
  field_sales_order_promised_ship_date DATE,
  -- 字段说明：销售订单行的履约状态。
  field_sales_order_status VARCHAR(64) NOT NULL,
  -- 字段说明：订单行有效订购数量减累计已发货数量后的非负余额。
  field_sales_order_backlog_quantity INTEGER NOT NULL,
  CONSTRAINT fk_event_sales_order_fulfillment_field_sales_order_customer_code FOREIGN KEY (field_sales_order_customer_code) REFERENCES entity_customer(field_customer_code),
  CONSTRAINT fk_event_sales_order_fulfillment_field_sales_order_product_code FOREIGN KEY (field_sales_order_product_code) REFERENCES entity_product(field_product_code)
);

-- 表：商品发货
-- 表说明：按实际发货时间记录一次发货单对订单商品行的履约明细，同一订单行允许多次发货。
-- 业务粒度：每张发货单的订单商品明细一行；一张订单明细可对应多行发货。
CREATE TABLE event_product_shipment (
  -- 字段说明：商品发货明细的技术事件标识。
  field_product_shipment_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：商品发货实际发生的时间。
  field_product_shipment_time TIMESTAMP NOT NULL,
  -- 字段说明：发货来源仓库的业务编码。
  field_product_shipment_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：发货明细对应商品的业务编码。
  field_product_shipment_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：发货明细所服务客户的业务编码。
  field_product_shipment_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：发货单的退化维度编号。
  field_product_shipment_number VARCHAR(255) NOT NULL,
  -- 字段说明：发货明细关联的销售订单号。
  field_product_shipment_sales_order_number VARCHAR(255) NOT NULL,
  -- 字段说明：发货明细关联的销售订单行号。
  field_product_shipment_order_line_number INTEGER NOT NULL,
  -- 字段说明：本次发货明细的商品数量。
  field_product_shipment_quantity INTEGER NOT NULL,
  -- 字段说明：承担本次发货运输的承运商。
  field_product_shipment_carrier VARCHAR(128),
  -- 字段说明：本次发货的物流运单号退化维度。
  field_product_shipment_tracking_number VARCHAR(255),
  -- 字段说明：实际发货日期不晚于承诺日期时为1，否则为0。
  field_product_shipment_on_time_flag INTEGER,
  -- 字段说明：发货及时率公式中作为分母的有效发货订单行计数；不符合排除规则的行不计入。
  field_product_shipment_valid_line_count INTEGER NOT NULL,
  -- 字段说明：商品发货单的业务状态，用于排除已作废发货单。
  field_product_shipment_status VARCHAR(64) NOT NULL,
  CONSTRAINT fk_event_product_shipment_field_product_shipment_warehouse_code FOREIGN KEY (field_product_shipment_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_product_shipment_field_product_shipment_product_code FOREIGN KEY (field_product_shipment_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_product_shipment_field_product_shipment_customer_code FOREIGN KEY (field_product_shipment_customer_code) REFERENCES entity_customer(field_customer_code)
);

-- 表：维修工单处理
-- 表说明：从创建开始记录首次响应、维修完成和状态的一张维修工单处理过程。
-- 业务粒度：每张维修工单一行。
CREATE TABLE event_repair_work_order (
  -- 字段说明：维修工单事件的技术代理键。
  field_repair_work_order_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：维修工单的业务键和退化维度。
  field_repair_work_order_number VARCHAR(255) NOT NULL,
  -- 字段说明：维修工单创建时间，也是维修SLA起点。
  field_repair_work_order_created_time TIMESTAMP NOT NULL,
  -- 字段说明：维修工单首次响应时间，是响应SLA终点。
  field_repair_work_order_first_response_time TIMESTAMP,
  -- 字段说明：维修工单完成维修的时间，是完成SLA终点。
  field_repair_work_order_completed_time TIMESTAMP,
  -- 字段说明：维修工单所属客户的业务编码。
  field_repair_work_order_customer_code VARCHAR(255) NOT NULL,
  -- 字段说明：维修工单对应商品的业务编码。
  field_repair_work_order_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：负责处理维修工单的服务网点编码。
  field_repair_work_order_service_site_code VARCHAR(255) NOT NULL,
  -- 字段说明：维修工单当前状态。
  field_repair_work_order_status VARCHAR(64) NOT NULL,
  -- 字段说明：维修工单记录的故障分类。
  field_repair_work_order_fault_type VARCHAR(128),
  -- 字段说明：维修首次响应的目标阈值，具体单位和适用范围尚未确定。
  field_repair_work_order_response_target DECIMAL(18, 4),
  -- 字段说明：维修完成的目标阈值，具体单位和适用范围尚未确定。
  field_repair_work_order_completion_target DECIMAL(18, 4),
  CONSTRAINT fk_event_repair_work_order_field_repair_work_order_customer_code FOREIGN KEY (field_repair_work_order_customer_code) REFERENCES entity_customer(field_customer_code),
  CONSTRAINT fk_event_repair_work_order_field_repair_work_order_product_code FOREIGN KEY (field_repair_work_order_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_repair_work_order_field_repair_work_order_service_site_code FOREIGN KEY (field_repair_work_order_service_site_code) REFERENCES entity_service_site(field_service_site_code)
);
