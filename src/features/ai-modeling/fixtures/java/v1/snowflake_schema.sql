-- 表：商品
-- 表说明：以商品编码唯一识别的商品主数据，承载名称、品类、系列、规格和当前有效标准成本。
CREATE TABLE entity_product (
  -- 字段说明：商品实体的技术代理键。
  field_product_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：由业务系统分配并用于唯一识别商品的编码。
  field_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：商品的业务名称，可用于搜索和展示。
  field_product_name VARCHAR(255) NOT NULL,
  -- 字段说明：商品所属品类，是稳定的分析切片。
  field_product_category VARCHAR(128) NOT NULL,
  -- 字段说明：商品所属系列，是稳定的分析切片。
  field_product_series VARCHAR(128) NOT NULL,
  -- 字段说明：商品的型号或规格描述。
  field_product_specification VARCHAR(255) NOT NULL,
  -- 字段说明：商品主数据中的当前有效标准单位成本，不回溯历史成本。
  field_product_standard_cost DECIMAL(18, 4) NOT NULL,
  CONSTRAINT uq_entity_product_business_key UNIQUE (field_product_code)
);

-- 表：仓库
-- 表说明：以仓库编码识别的仓库主数据，承载名称、业务区域、类型和标准行政区县归属。
CREATE TABLE entity_warehouse (
  -- 字段说明：仓库实体的技术代理键。
  field_warehouse_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：用于唯一识别仓库的业务编码。
  field_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：仓库的业务名称和分析维度。
  field_warehouse_name VARCHAR(255) NOT NULL,
  -- 字段说明：仓库业务区域，文档示例包括华北、华东和华南。
  field_warehouse_region VARCHAR(128) NOT NULL,
  -- 字段说明：仓库的业务类型。
  field_warehouse_type VARCHAR(128) NOT NULL,
  -- 字段说明：仓库归属区县的行政区域编码。
  field_warehouse_district_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_warehouse_business_key UNIQUE (field_warehouse_code),
  CONSTRAINT fk_entity_warehouse_field_warehouse_district_code FOREIGN KEY (field_warehouse_district_code) REFERENCES entity_administrative_region(field_region_code)
);

-- 表：库位
-- 表说明：以WMS全局唯一库位编码识别的库位主数据，承载名称、类型和所属仓库。
CREATE TABLE entity_storage_location (
  -- 字段说明：库位实体的技术代理键。
  field_location_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：由WMS统一分配且在全部仓库中唯一的库位编码。
  field_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：库位的业务名称和分析维度。
  field_location_name VARCHAR(255) NOT NULL,
  -- 字段说明：库位的业务类型。
  field_location_type VARCHAR(128) NOT NULL,
  -- 字段说明：库位必须归属的仓库编码。
  field_location_warehouse_code VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_storage_location_business_key UNIQUE (field_location_code),
  CONSTRAINT fk_entity_storage_location_field_location_warehouse_code FOREIGN KEY (field_location_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code)
);

-- 表：行政区域
-- 表说明：跨仓库复用的标准行政地域主数据，通过级别、父级编码和成员层级字段承载大区、省、市、区县成员树。
CREATE TABLE entity_administrative_region (
  -- 字段说明：行政区域实体的技术代理键。
  field_region_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：用于唯一识别行政区域成员的业务编码。
  field_region_code VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员的名称，可搜索和分组。
  field_region_name VARCHAR(255) NOT NULL,
  -- 字段说明：行政区域成员所处级别，取值为大区、省、市或区县。
  field_region_level VARCHAR(128) NOT NULL,
  -- 字段说明：同一行政区域成员树中的直接上级区域编码；根成员没有上级。
  field_region_parent_code VARCHAR(255),
  -- 字段说明：承载大区到省、市、区县下钻语义的行政区域成员层级字段。
  field_region_member_hierarchy VARCHAR(255) NOT NULL,
  CONSTRAINT uq_entity_administrative_region_business_key UNIQUE (field_region_code),
  CONSTRAINT fk_entity_administrative_region_field_region_parent_code FOREIGN KEY (field_region_parent_code) REFERENCES entity_administrative_region(field_region_code)
);

-- 表：库存快照
-- 表说明：按快照时点记录仓库、库位、商品和批次库存状态的小时级快照事件。
-- 业务粒度：每个快照时点、仓库、库位、商品、批次一行
CREATE TABLE event_inventory_snapshot (
  -- 字段说明：库存快照行的技术事件标识。
  field_inventory_snapshot_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：库存状态被采集和观察的快照时间。
  field_inventory_snapshot_time TIMESTAMP NOT NULL,
  -- 字段说明：库存记录对应的商品编码。
  field_inventory_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存记录所在仓库的编码。
  field_inventory_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存记录所在库位的全局唯一编码。
  field_inventory_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：区分库存批次的退化维度。
  field_inventory_batch_number VARCHAR(255) NOT NULL,
  -- 字段说明：快照粒度下实际在库数量。
  field_inventory_on_hand_quantity INTEGER NOT NULL,
  -- 字段说明：快照粒度下已锁定的库存数量。
  field_inventory_locked_quantity INTEGER NOT NULL,
  -- 字段说明：快照粒度下用于低库存判断的安全库存阈值。
  field_inventory_safety_quantity INTEGER NOT NULL,
  -- 字段说明：库存批次的入库日期，用于计算库龄；缺失时该记录不参与库龄计算。
  field_inventory_receipt_date DATE,
  -- 字段说明：按快照行由在库数量减锁定数量得到的可用库存语义度量。
  field_inventory_available_quantity INTEGER NOT NULL,
  -- 字段说明：按快照行由在库数量乘以商品当前有效标准成本得到的库存金额语义度量。
  field_inventory_amount DECIMAL(18, 2) NOT NULL,
  -- 字段说明：按库存批次由统计日期减入库日期得到的库龄天数。
  field_inventory_age_days INTEGER,
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_product_code FOREIGN KEY (field_inventory_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_warehouse_code FOREIGN KEY (field_inventory_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_inventory_snapshot_field_inventory_location_code FOREIGN KEY (field_inventory_location_code) REFERENCES entity_storage_location(field_location_code)
);

-- 表：库存移动
-- 表说明：统一承载已完成商品入库和商品出库明细的库存移动事件族，以移动方向区分入库与出库。
-- 业务粒度：每张库存移动单的每个商品明细一行，以移动方向区分商品入库与商品出库
CREATE TABLE event_inventory_movement (
  -- 字段说明：库存移动明细行的技术事件标识。
  field_movement_key BIGINT NOT NULL PRIMARY KEY,
  -- 字段说明：统一承载入库单号或出库单号的退化维度。
  field_movement_document_number VARCHAR(255) NOT NULL,
  -- 字段说明：统一承载入库完成时间或出库完成时间。
  field_movement_time TIMESTAMP NOT NULL,
  -- 字段说明：区分商品入库和商品出库的移动方向。
  field_movement_direction VARCHAR(128) NOT NULL,
  -- 字段说明：统一承载原入库类型或出库类型的库存移动业务类型。
  field_movement_type VARCHAR(128) NOT NULL,
  -- 字段说明：库存移动单据的状态；本模型指标只统计已完成明细。
  field_movement_status VARCHAR(64) NOT NULL,
  -- 字段说明：库存移动明细对应的商品编码。
  field_movement_product_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存移动明细进入或离开的仓库编码。
  field_movement_warehouse_code VARCHAR(255) NOT NULL,
  -- 字段说明：库存移动明细涉及的库位编码。
  field_movement_location_code VARCHAR(255) NOT NULL,
  -- 字段说明：统一承载入库数量或出库数量，具体业务方向由移动方向字段判定。
  field_movement_quantity INTEGER NOT NULL,
  CONSTRAINT fk_event_inventory_movement_field_movement_product_code FOREIGN KEY (field_movement_product_code) REFERENCES entity_product(field_product_code),
  CONSTRAINT fk_event_inventory_movement_field_movement_warehouse_code FOREIGN KEY (field_movement_warehouse_code) REFERENCES entity_warehouse(field_warehouse_code),
  CONSTRAINT fk_event_inventory_movement_field_movement_location_code FOREIGN KEY (field_movement_location_code) REFERENCES entity_storage_location(field_location_code)
);
