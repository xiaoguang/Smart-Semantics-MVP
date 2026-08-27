import { create } from 'zustand';
import type {
  FieldMapping, SchemaMapping, SemanticRelation, TimeCalendar, TimeRule, HolidayCalendar, HolidayPeriod,
  SemanticEntity, SemanticEvent, SemanticMetric, TermAlias, SemanticTerm, UnknownTerm, ValidationIssue,
  MetricTarget, BusinessRule, RuleLink, MetricPreviewResult, RulePreviewResult,
  ComparisonExpression, FilterExpression, ExistenceExpression, CompositeExpression,
  EntityHierarchyLevel, ObjectValue,
} from '../types';

export type StoreDataSlices = {
  calendars: TimeCalendar[];
  rules: TimeRule[];
  holidayCalendars: HolidayCalendar[];
  holidayPeriods: HolidayPeriod[];
  entities: SemanticEntity[];
  events: SemanticEvent[];
  entityHierarchies: EntityHierarchyLevel[];
  objectValues: ObjectValue[];
  semanticRelations: SemanticRelation[];
  schemaMappings: SchemaMapping[];
  fieldMappings: FieldMapping[];
  metrics: SemanticMetric[];
  aliases: TermAlias[];
  terms: SemanticTerm[];
  unknownTerms: UnknownTerm[];
  metricTargets: MetricTarget[];
  businessRules: BusinessRule[];
  ruleLinks: RuleLink[];
};

interface StoreState extends StoreDataSlices {
  addCalendar: (c: TimeCalendar) => void;
  updateCalendar: (id: number, c: Partial<TimeCalendar>) => void;
  deleteCalendar: (id: number) => void;
  addRule: (r: TimeRule) => void;
  updateRule: (id: number, r: Partial<TimeRule>) => void;
  deleteRule: (id: number) => void;
  toggleRuleStatus: (id: number) => void;
  addHolidayCalendar: (h: HolidayCalendar) => void;
  updateHolidayCalendar: (id: number, h: Partial<HolidayCalendar>) => void;
  deleteHolidayCalendar: (id: number) => void;
  addHolidayPeriod: (p: HolidayPeriod) => void;
  updateHolidayPeriod: (id: number, p: Partial<HolidayPeriod>) => void;
  deleteHolidayPeriod: (id: number) => void;
  addAlias: (a: TermAlias) => ValidationIssue[];
  updateAlias: (id: number, a: Partial<TermAlias>) => void;
  deleteAlias: (id: number) => void;
  batchConfirmAliases: (ids: number[]) => void;
  addTerm: (t: SemanticTerm) => ValidationIssue[];
  updateTerm: (id: number, t: Partial<SemanticTerm>) => void;
  deleteTerm: (id: number) => void;
  mapUnknownTerm: (id: number, alias: TermAlias) => void;
  ignoreUnknownTerm: (id: number) => void;
  addEntity: (e: Omit<SemanticEntity, 'id' | 'attributes'> & { attributes?: SemanticEntity['attributes'] }) => ValidationIssue[];
  updateEntity: (id: number, e: Partial<SemanticEntity>) => void;
  deleteEntity: (id: number) => void;
  addEvent: (e: Omit<SemanticEvent, 'id' | 'attributes'> & { attributes?: SemanticEvent['attributes'] }) => ValidationIssue[];
  updateEvent: (id: number, e: Partial<SemanticEvent>) => void;
  deleteEvent: (id: number) => void;
  deleteObjectAttributes: (objectType: 'EVENT' | 'ENTITY', objectId: number, attributeIds: number[]) => void;
  saveObjectHierarchy: (objectType: 'EVENT' | 'ENTITY', objectId: number, attributeId: number, hierarchyName: string, hierarchyCode: string, levels: Array<Pick<EntityHierarchyLevel, 'id' | 'levelCode' | 'levelName' | 'status'>>) => ValidationIssue[];
  deleteObjectHierarchy: (objectType: 'EVENT' | 'ENTITY', objectId: number, attributeId: number) => ValidationIssue[];
  upsertObjectValue: (value: Omit<ObjectValue, 'id'> & { id?: number }) => ValidationIssue[];
  deleteObjectValue: (id: number) => ValidationIssue[];
  addRelation: (r: Omit<SemanticRelation, 'id'>) => ValidationIssue[];
  updateRelation: (id: number, r: Partial<SemanticRelation>) => void;
  deleteRelation: (id: number) => void;
  upsertSchemaMapping: (m: Omit<SchemaMapping, 'id'>) => void;
  upsertFieldMapping: (m: Omit<FieldMapping, 'id'>) => void;
  deleteFieldMapping: (id: number) => void;
  validateEntity: (id: number) => ValidationIssue[];
  validateEvent: (id: number) => ValidationIssue[];
  addMetric: (m: Omit<SemanticMetric, 'id'>) => ValidationIssue[];
  updateMetric: (id: number, m: Partial<SemanticMetric>) => void;
  deleteMetric: (id: number) => void;
  validateMetric: (id: number) => ValidationIssue[];
  previewMetric: (id: number) => MetricPreviewResult;
  publishMetric: (id: number) => ValidationIssue[];
  addMetricTarget: (t: Omit<MetricTarget, 'id'>) => void;
  updateMetricTarget: (id: number, t: Partial<MetricTarget>) => void;
  deleteMetricTarget: (id: number) => void;
  validateMetricTarget: (t: Omit<MetricTarget, 'id'>, exceptId?: number) => ValidationIssue[];
  addBusinessRule: (r: Omit<BusinessRule, 'id'>) => ValidationIssue[];
  updateBusinessRule: (id: number, r: Partial<BusinessRule>) => void;
  deleteBusinessRule: (id: number) => void;
  validateBusinessRule: (r: Partial<BusinessRule> & { mode: BusinessRule['mode']; targetObjectType: BusinessRule['targetObjectType']; targetObjectId: number; expression: BusinessRule['expression'] }, id?: number) => ValidationIssue[];
  previewRule: (r: BusinessRule) => RulePreviewResult;
  publishBusinessRule: (id: number) => ValidationIssue[];
  setBusinessRuleOpposite: (ruleId: number, oppositeId?: number) => void;
  addRuleLink: (ownerType: 'EVENT' | 'ENTITY', ownerId: number, ruleId: number) => void;
  deleteRuleLink: (linkId: number) => void;
}

const mockEntities: SemanticEntity[] = [
  { id: 1, entityCode: 'customer', entityName: '用户', description: '平台注册和下单用户', owner: '张三', uniqueIdentifierAttributeId: 101, defaultTimeAttributeId: 104, status: 'ACTIVE', attributes: [
    { id: 101, attributeCode: 'customer_id', attributeName: '用户ID', dataType: 'STRING', semanticType: 'ID', isFilterable: false, isGroupable: false },
    { id: 102, attributeCode: 'customer_name', attributeName: '用户名称', dataType: 'STRING', semanticType: 'TEXT', isFilterable: true, isGroupable: true, isDisplay: true },
    { id: 103, attributeCode: 'customer_level', attributeName: '用户等级', dataType: 'ENUM', semanticType: 'ENUM', isFilterable: true, isGroupable: true, enumValues: ['HIGH', 'MID', 'LOW'] },
    { id: 104, attributeCode: 'register_time', attributeName: '注册时间', dataType: 'DATETIME', semanticType: 'DATETIME', isFilterable: true },
    { id: 105, attributeCode: 'age', attributeName: '年龄', dataType: 'NUMBER', semanticType: 'NUMBER', unit: '岁', isFilterable: true },
  ]},
  { id: 2, entityCode: 'product', entityName: '商品', description: '可售卖商品主数据', owner: '李四', uniqueIdentifierAttributeId: 201, defaultTimeAttributeId: 205, status: 'ACTIVE', attributes: [
    { id: 201, attributeCode: 'product_id', attributeName: '商品ID', dataType: 'STRING', semanticType: 'ID', isFilterable: false },
    { id: 202, attributeCode: 'product_name', attributeName: '商品名称', dataType: 'STRING', semanticType: 'TEXT', isFilterable: true, isGroupable: true, isDisplay: true },
    { id: 203, attributeCode: 'category', attributeName: '品类', dataType: 'ENUM', semanticType: 'ENUM', isFilterable: true, isGroupable: true, enumValues: ['服装', '数码', '食品', '家居', '美妆'] },
    { id: 204, attributeCode: 'price', attributeName: '单价', dataType: 'DECIMAL', semanticType: 'AMOUNT', unit: '元', formatPattern: '#,##0.00', isFilterable: true },
    { id: 205, attributeCode: 'listing_time', attributeName: '上架时间', dataType: 'DATETIME', semanticType: 'DATETIME', isFilterable: true },
  ]},
  { id: 3, entityCode: 'store', entityName: '门店', description: '线下履约和销售门店', owner: '王五', uniqueIdentifierAttributeId: 301, defaultTimeAttributeId: 304, status: 'ACTIVE', attributes: [
    { id: 301, attributeCode: 'store_id', attributeName: '门店ID', dataType: 'STRING', semanticType: 'ID', isFilterable: false },
    { id: 302, attributeCode: 'store_name', attributeName: '门店名称', dataType: 'STRING', semanticType: 'TEXT', isFilterable: true, isGroupable: true, isDisplay: true },
    { id: 303, attributeCode: 'region', attributeName: '区域', dataType: 'ENUM', semanticType: 'ENUM', isFilterable: true, isGroupable: true, enumValues: ['华东', '华南', '华北', '华中', '西南', '西北', '东北'] },
    { id: 304, attributeCode: 'open_time', attributeName: '开业时间', dataType: 'DATETIME', semanticType: 'DATETIME', isFilterable: true },
    { id: 305, attributeCode: 'city_id', attributeName: '城市ID', dataType: 'STRING', semanticType: 'FOREIGN_KEY', isFilterable: false },
  ]},
  { id: 4, entityCode: 'city', entityName: '城市', description: '城市地理维度', owner: '王五', uniqueIdentifierAttributeId: 401, status: 'ACTIVE', attributes: [
    { id: 401, attributeCode: 'city_id', attributeName: '城市ID', dataType: 'STRING', semanticType: 'ID', isFilterable: false },
    { id: 402, attributeCode: 'city_name', attributeName: '城市名称', dataType: 'STRING', semanticType: 'TEXT', isFilterable: true, isGroupable: true, isDisplay: true },
    { id: 403, attributeCode: 'city_level', attributeName: '城市等级', dataType: 'ENUM', semanticType: 'ENUM', isFilterable: true, isGroupable: true, enumValues: ['TIER_1', 'TIER_2', 'TIER_3', 'TIER_4', 'OTHER'] },
  ]},
  { id: 5, entityCode: 'geo', entityName: '区域', description: '行政区域层级与标准编码', owner: '王五', uniqueIdentifierAttributeId: 502, status: 'ACTIVE', attributes: [
    { id: 501, attributeCode: 'geo_node', attributeName: '行政区域', synonyms: ['地域', '地区'], dataType: 'ENUM', semanticType: 'HIERARCHY', isFilterable: true, isGroupable: true },
    { id: 502, attributeCode: 'geo_code', attributeName: '区域编码', dataType: 'STRING', semanticType: 'ID', isFilterable: false },
  ]},
];

const mockEntityHierarchies: EntityHierarchyLevel[] = [
  { id: 3101, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyCode: 'geo_admin', hierarchyName: '行政区域层级', levelCode: 'REGION', levelName: '大区', levelDepth: 1, status: 'ACTIVE' },
  { id: 3102, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyCode: 'geo_admin', hierarchyName: '行政区域层级', levelCode: 'PROVINCE', levelName: '省', levelDepth: 2, parentLevelId: 3101, status: 'ACTIVE' },
  { id: 3103, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyCode: 'geo_admin', hierarchyName: '行政区域层级', levelCode: 'CITY', levelName: '市', levelDepth: 3, parentLevelId: 3102, status: 'ACTIVE' },
  { id: 3104, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyCode: 'geo_admin', hierarchyName: '行政区域层级', levelCode: 'DISTRICT', levelName: '区县', levelDepth: 4, parentLevelId: 3103, status: 'ACTIVE' },
];

const mockObjectValues: ObjectValue[] = [
  { id: 1101, objectType: 'ENTITY', objectId: 1, attributeId: 103, businessName: '高级用户', physicalCode: 'HIGH', sortOrder: 10, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 1102, objectType: 'ENTITY', objectId: 1, attributeId: 103, businessName: '中级用户', physicalCode: 'MID', sortOrder: 20, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 1103, objectType: 'ENTITY', objectId: 1, attributeId: 103, businessName: '普通用户', physicalCode: 'LOW', sortOrder: 30, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 1201, objectType: 'ENTITY', objectId: 2, attributeId: 203, businessName: '服装', physicalCode: 'CLOTHING', sortOrder: 10, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 1202, objectType: 'ENTITY', objectId: 2, attributeId: 203, businessName: '数码', physicalCode: 'DIGITAL', sortOrder: 20, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 2101, objectType: 'EVENT', objectId: 1, attributeId: 1003, businessName: '待支付', physicalCode: 'PENDING', sortOrder: 10, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 2102, objectType: 'EVENT', objectId: 1, attributeId: 1003, businessName: '已支付', physicalCode: 'PAID', sortOrder: 20, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 2103, objectType: 'EVENT', objectId: 1, attributeId: 1003, businessName: '已完成', physicalCode: 'FINISHED', sortOrder: 30, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 2104, objectType: 'EVENT', objectId: 1, attributeId: 1003, businessName: '已取消', physicalCode: 'CANCELLED', sortOrder: 40, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 2105, objectType: 'EVENT', objectId: 1, attributeId: 1003, businessName: '已退款', physicalCode: 'REFUNDED', sortOrder: 50, sourceType: 'MANUAL', status: 'ACTIVE' },
  { id: 3201, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3101, businessName: '华东', physicalCode: 'EAST', sortOrder: 10, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
  { id: 3202, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3101, businessName: '华南', physicalCode: 'SOUTH', sortOrder: 20, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
  { id: 3211, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3102, businessName: '江苏', physicalCode: 'JS', parentValueId: 3201, sortOrder: 10, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
  { id: 3212, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3102, businessName: '浙江', physicalCode: 'ZJ', parentValueId: 3201, sortOrder: 20, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
  { id: 3213, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3102, businessName: '广东', physicalCode: 'GD', parentValueId: 3202, sortOrder: 10, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
  { id: 3221, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3103, businessName: '南京', physicalCode: 'NJ', parentValueId: 3211, sortOrder: 10, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
  { id: 3222, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3103, businessName: '苏州', physicalCode: 'SZ', parentValueId: 3211, sortOrder: 20, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
  { id: 3223, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3103, businessName: '杭州', physicalCode: 'HZ', parentValueId: 3212, sortOrder: 10, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
  { id: 3231, objectType: 'ENTITY', objectId: 5, attributeId: 501, hierarchyLevelId: 3104, businessName: '玄武区', physicalCode: 'XW', parentValueId: 3221, sortOrder: 10, sourceType: 'PHYSICAL_SYNC', lastSyncedAt: '2026-08-01 10:30', status: 'ACTIVE' },
];

const mockEvents: SemanticEvent[] = [
  { id: 1, eventCode: 'order_event', eventName: '订单事件', description: '用户下单和支付形成的交易事实', owner: '张三', uniqueIdentifierAttributeId: 1001, defaultTimeAttributeId: 1004, status: 'ACTIVE', attributes: [
    { id: 1001, attributeCode: 'order_id', attributeName: '订单ID', dataType: 'STRING', semanticType: 'ID', isFilterable: false },
    { id: 1002, attributeCode: 'pay_amount', attributeName: '支付金额', dataType: 'DECIMAL', semanticType: 'AMOUNT', unit: '元', formatPattern: '#,##0.00', isFilterable: true, isMetric: true, aggregateOperator: 'SUM' },
    { id: 1003, attributeCode: 'order_status', attributeName: '订单状态', dataType: 'ENUM', semanticType: 'LIFECYCLE', isFilterable: true, isGroupable: true, enumValues: ['PENDING', 'PAID', 'FINISHED', 'CANCELLED', 'REFUNDED'] },
    { id: 1004, attributeCode: 'pay_time', attributeName: '支付时间', dataType: 'DATETIME', semanticType: 'DATETIME', isFilterable: true, isPrimaryTime: true },
    { id: 1005, attributeCode: 'order_time', attributeName: '下单时间', dataType: 'DATETIME', semanticType: 'DATETIME', isFilterable: true },
    { id: 1006, attributeCode: 'ship_time', attributeName: '发货时间', dataType: 'DATETIME', semanticType: 'DATETIME', isFilterable: true },
    { id: 1007, attributeCode: 'quantity', attributeName: '数量', dataType: 'NUMBER', semanticType: 'NUMBER', unit: '件', isFilterable: true },
    { id: 1008, attributeCode: 'customer_id', attributeName: '用户ID', dataType: 'STRING', semanticType: 'FOREIGN_KEY', isFilterable: false },
    { id: 1009, attributeCode: 'product_id', attributeName: '商品ID', dataType: 'STRING', semanticType: 'FOREIGN_KEY', isFilterable: false },
    { id: 1010, attributeCode: 'store_id', attributeName: '门店ID', dataType: 'STRING', semanticType: 'FOREIGN_KEY', isFilterable: false },
  ]},
  { id: 2, eventCode: 'refund_event', eventName: '退款事件', description: '订单退款形成的售后事实', owner: '李四', uniqueIdentifierAttributeId: 2001, defaultTimeAttributeId: 2003, status: 'ACTIVE', attributes: [
    { id: 2001, attributeCode: 'refund_id', attributeName: '退款ID', dataType: 'STRING', semanticType: 'ID', isFilterable: false },
    { id: 2002, attributeCode: 'refund_amount', attributeName: '退款金额', dataType: 'DECIMAL', semanticType: 'AMOUNT', unit: '元', formatPattern: '#,##0.00', isFilterable: true, isMetric: true },
    { id: 2003, attributeCode: 'refund_time', attributeName: '退款时间', dataType: 'DATETIME', semanticType: 'DATETIME', isFilterable: true, isPrimaryTime: true },
    { id: 2004, attributeCode: 'refund_reason', attributeName: '退款原因', dataType: 'ENUM', semanticType: 'ENUM', isFilterable: true, enumValues: ['质量问题', '物流问题', '不想要', '发错货'] },
    { id: 2005, attributeCode: 'customer_id', attributeName: '用户ID', dataType: 'STRING', semanticType: 'FOREIGN_KEY', isFilterable: false },
    { id: 2006, attributeCode: 'order_id', attributeName: '订单ID', dataType: 'STRING', semanticType: 'FOREIGN_KEY', isFilterable: false },
  ]},
  { id: 3, eventCode: 'login_event', eventName: '登录事件', description: '用户登录行为事实', owner: '赵六', uniqueIdentifierAttributeId: 3001, defaultTimeAttributeId: 3002, status: 'ACTIVE', attributes: [
    { id: 3001, attributeCode: 'login_id', attributeName: '登录ID', dataType: 'STRING', semanticType: 'ID', isFilterable: false },
    { id: 3002, attributeCode: 'login_time', attributeName: '登录时间', dataType: 'DATETIME', semanticType: 'DATETIME', isFilterable: true, isPrimaryTime: true },
    { id: 3003, attributeCode: 'login_type', attributeName: '登录方式', dataType: 'ENUM', semanticType: 'ENUM', isFilterable: true, enumValues: ['APP', 'WEB', 'MINI_PROGRAM'] },
    { id: 3004, attributeCode: 'customer_id', attributeName: '用户ID', dataType: 'STRING', semanticType: 'FOREIGN_KEY', isFilterable: false },
  ]},
];

const mockSemanticRelations: SemanticRelation[] = [
  { id: 1, relationCode: 'order_customer', relationName: '订单用户', relationType: 'EVENT_ENTITY', sourceType: 'EVENT', sourceId: 1, targetType: 'ENTITY', targetId: 1, relationRole: 'buyer', sourceAttributeId: 1008, targetAttributeId: 101, cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 1, joinType: 'INNER', required: true, status: 'ACTIVE' },
  { id: 2, relationCode: 'order_product', relationName: '订单商品', relationType: 'EVENT_ENTITY', sourceType: 'EVENT', sourceId: 1, targetType: 'ENTITY', targetId: 2, relationRole: 'sku', sourceAttributeId: 1009, targetAttributeId: 201, cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 1, joinType: 'INNER', required: true, status: 'ACTIVE' },
  { id: 3, relationCode: 'order_store', relationName: '订单门店', relationType: 'EVENT_ENTITY', sourceType: 'EVENT', sourceId: 1, targetType: 'ENTITY', targetId: 3, relationRole: 'store', sourceAttributeId: 1010, targetAttributeId: 301, cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 1, joinType: 'LEFT', required: false, status: 'ACTIVE' },
  { id: 4, relationCode: 'store_city', relationName: '门店所属城市', relationType: 'ENTITY_ENTITY', sourceType: 'ENTITY', sourceId: 3, targetType: 'ENTITY', targetId: 4, relationRole: 'belongs_to', sourceAttributeId: 305, targetAttributeId: 401, cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 2, joinType: 'LEFT', required: false, status: 'ACTIVE' },
  { id: 5, relationCode: 'refund_customer', relationName: '退款用户', relationType: 'EVENT_ENTITY', sourceType: 'EVENT', sourceId: 2, targetType: 'ENTITY', targetId: 1, relationRole: 'refund_user', sourceAttributeId: 2005, targetAttributeId: 101, cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 1, joinType: 'INNER', required: true, status: 'ACTIVE' },
  { id: 6, relationCode: 'login_customer', relationName: '登录用户', relationType: 'EVENT_ENTITY', sourceType: 'EVENT', sourceId: 3, targetType: 'ENTITY', targetId: 1, relationRole: 'login_user', sourceAttributeId: 3004, targetAttributeId: 101, cardinality: 'MANY_TO_ONE', direction: 'FORWARD', priority: 1, joinType: 'INNER', required: true, status: 'ACTIVE' },
];

const mockSchemaMappings: SchemaMapping[] = [
  { id: 1, objectType: 'EVENT', objectId: 1, datasourceName: 'dw_prod', schemaName: 'dwd', tableName: 'dwd_order_detail', tableAlias: 'o' },
  { id: 2, objectType: 'EVENT', objectId: 2, datasourceName: 'dw_prod', schemaName: 'dwd', tableName: 'dwd_refund_detail', tableAlias: 'r' },
  { id: 3, objectType: 'ENTITY', objectId: 1, datasourceName: 'dw_prod', schemaName: 'dim', tableName: 'dim_customer', tableAlias: 'c' },
  { id: 4, objectType: 'ENTITY', objectId: 2, datasourceName: 'dw_prod', schemaName: 'dim', tableName: 'dim_product', tableAlias: 'p' },
  { id: 5, objectType: 'ENTITY', objectId: 3, datasourceName: 'dw_prod', schemaName: 'dim', tableName: 'dim_store', tableAlias: 's' },
  { id: 6, objectType: 'ENTITY', objectId: 4, datasourceName: 'dw_prod', schemaName: 'dim', tableName: 'dim_city', tableAlias: 'ct' },
  { id: 7, objectType: 'EVENT', objectId: 3, datasourceName: 'dw_prod', schemaName: 'dwd', tableName: 'dwd_login_event', tableAlias: 'l' },
  { id: 8, objectType: 'ENTITY', objectId: 5, datasourceName: 'dw_prod', schemaName: 'dim', tableName: 'dim_geo', tableAlias: 'g' },
];

const mockFieldMappings: FieldMapping[] = [
  { id: 1, objectType: 'EVENT', objectId: 1, attributeId: 1001, physicalFieldName: 'order_id', physicalDataType: 'varchar', fieldRole: 'PRIMARY_KEY' },
  { id: 2, objectType: 'EVENT', objectId: 1, attributeId: 1002, physicalFieldName: 'pay_amount', physicalDataType: 'decimal', fieldRole: 'MEASURE' },
  { id: 3, objectType: 'EVENT', objectId: 1, attributeId: 1004, physicalFieldName: 'pay_time', physicalDataType: 'datetime', fieldRole: 'TIME' },
  { id: 4, objectType: 'EVENT', objectId: 1, attributeId: 1008, physicalFieldName: 'customer_id', physicalDataType: 'varchar', fieldRole: 'JOIN_KEY' },
  { id: 5, objectType: 'EVENT', objectId: 1, attributeId: 1009, physicalFieldName: 'product_id', physicalDataType: 'varchar', fieldRole: 'JOIN_KEY' },
  { id: 6, objectType: 'EVENT', objectId: 1, attributeId: 1010, physicalFieldName: 'store_id', physicalDataType: 'varchar', fieldRole: 'JOIN_KEY' },
  { id: 7, objectType: 'ENTITY', objectId: 1, attributeId: 101, physicalFieldName: 'customer_id', physicalDataType: 'varchar', fieldRole: 'PRIMARY_KEY' },
  { id: 8, objectType: 'ENTITY', objectId: 1, attributeId: 102, physicalFieldName: 'customer_name', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 9, objectType: 'ENTITY', objectId: 2, attributeId: 201, physicalFieldName: 'product_id', physicalDataType: 'varchar', fieldRole: 'PRIMARY_KEY' },
  { id: 10, objectType: 'ENTITY', objectId: 3, attributeId: 301, physicalFieldName: 'store_id', physicalDataType: 'varchar', fieldRole: 'PRIMARY_KEY' },
  { id: 11, objectType: 'ENTITY', objectId: 3, attributeId: 305, physicalFieldName: 'city_id', physicalDataType: 'varchar', fieldRole: 'JOIN_KEY' },
  { id: 12, objectType: 'ENTITY', objectId: 4, attributeId: 401, physicalFieldName: 'city_id', physicalDataType: 'varchar', fieldRole: 'PRIMARY_KEY' },
  { id: 13, objectType: 'EVENT', objectId: 2, attributeId: 2003, physicalFieldName: 'refund_time', physicalDataType: 'datetime', fieldRole: 'TIME' },
  { id: 14, objectType: 'EVENT', objectId: 2, attributeId: 2005, physicalFieldName: 'customer_id', physicalDataType: 'varchar', fieldRole: 'JOIN_KEY' },
  { id: 15, objectType: 'ENTITY', objectId: 1, attributeId: 103, physicalFieldName: 'customer_level', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 16, objectType: 'ENTITY', objectId: 1, attributeId: 104, physicalFieldName: 'register_time', physicalDataType: 'datetime', fieldRole: 'TIME' },
  { id: 17, objectType: 'ENTITY', objectId: 1, attributeId: 105, physicalFieldName: 'age', physicalDataType: 'int', fieldRole: 'MEASURE' },
  { id: 18, objectType: 'ENTITY', objectId: 2, attributeId: 202, physicalFieldName: 'product_name', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 19, objectType: 'ENTITY', objectId: 2, attributeId: 203, physicalFieldName: 'category', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 20, objectType: 'ENTITY', objectId: 2, attributeId: 204, physicalFieldName: 'price', physicalDataType: 'decimal', fieldRole: 'MEASURE' },
  { id: 21, objectType: 'ENTITY', objectId: 2, attributeId: 205, physicalFieldName: 'listing_time', physicalDataType: 'datetime', fieldRole: 'TIME' },
  { id: 22, objectType: 'ENTITY', objectId: 3, attributeId: 302, physicalFieldName: 'store_name', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 23, objectType: 'ENTITY', objectId: 3, attributeId: 303, physicalFieldName: 'region', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 24, objectType: 'ENTITY', objectId: 3, attributeId: 304, physicalFieldName: 'open_time', physicalDataType: 'datetime', fieldRole: 'TIME' },
  { id: 25, objectType: 'ENTITY', objectId: 4, attributeId: 402, physicalFieldName: 'city_name', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 26, objectType: 'ENTITY', objectId: 4, attributeId: 403, physicalFieldName: 'city_level', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 27, objectType: 'EVENT', objectId: 3, attributeId: 3002, physicalFieldName: 'login_time', physicalDataType: 'datetime', fieldRole: 'TIME' },
  { id: 28, objectType: 'EVENT', objectId: 3, attributeId: 3004, physicalFieldName: 'customer_id', physicalDataType: 'varchar', fieldRole: 'JOIN_KEY' },
  { id: 29, objectType: 'ENTITY', objectId: 5, attributeId: 501, physicalFieldName: 'geo_code', physicalDataType: 'varchar', fieldRole: 'DIMENSION' },
  { id: 30, objectType: 'ENTITY', objectId: 5, attributeId: 502, physicalFieldName: 'geo_code', physicalDataType: 'varchar', fieldRole: 'PRIMARY_KEY' },
];

const mockMetrics: SemanticMetric[] = [
  { id: 1, metricCode: 'gmv', metricName: 'GMV', metricType: 'BASIC', description: '已支付订单成交金额总和', unit: 'CNY', owner: '张三', version: 3, status: 'PUBLISHED', publishedAt: '2026-07-01 10:20', eventId: 1, eventCode: 'order_event', eventName: '订单事件', baseAttributeId: 1002, baseAttributeCode: 'pay_amount', defaultOperator: 'SUM', ruleIds: [1, 2] },
  { id: 2, metricCode: 'order_count', metricName: '订单数', metricType: 'BASIC', description: '下单去重订单数', unit: '笔', owner: '张三', version: 1, status: 'PUBLISHED', publishedAt: '2026-06-20 09:00', eventId: 1, eventCode: 'order_event', eventName: '订单事件', baseAttributeId: 1001, baseAttributeCode: 'order_id', defaultOperator: 'COUNT_DISTINCT', ruleIds: [] },
  { id: 3, metricCode: 'avg_order_value', metricName: '客单价', metricType: 'COMPOSITE', description: '客单价 = GMV / 订单数', unit: 'CNY', owner: '张三', version: 1, status: 'PUBLISHED', publishedAt: '2026-06-20 09:05', eventId: 1, eventCode: 'order_event', eventName: '订单事件', formula: 'gmv / order_count', formulaTokens: [{ type: 'METRIC', value: 'gmv', metricId: 1, metricCode: 'gmv' }, { type: 'OP', value: '/' }, { type: 'METRIC', value: 'order_count', metricId: 2, metricCode: 'order_count' }], ruleIds: [] },
  { id: 4, metricCode: 'pay_user_count', metricName: '支付用户数', metricType: 'BASIC', description: '下单去重支付用户数', unit: '人', owner: '李四', version: 1, status: 'PUBLISHED', publishedAt: '2026-06-20 09:10', eventId: 1, eventCode: 'order_event', eventName: '订单事件', baseAttributeId: 1008, baseAttributeCode: 'customer_id', defaultOperator: 'COUNT_DISTINCT', ruleIds: [] },
  { id: 5, metricCode: 'refund_amount', metricName: '退款金额', metricType: 'BASIC', description: '退款事件金额总和', unit: 'CNY', owner: '李四', version: 1, status: 'PUBLISHED', publishedAt: '2026-06-21 09:00', eventId: 2, eventCode: 'refund_event', eventName: '退款事件', baseAttributeId: 2002, baseAttributeCode: 'refund_amount', defaultOperator: 'SUM', ruleIds: [] },
  { id: 6, metricCode: 'normal_gmv', metricName: '普通GMV', metricType: 'BASIC', description: '不含税口径的 GMV', unit: 'CNY', owner: '张三', version: 1, status: 'PUBLISHED', publishedAt: '2026-06-22 09:00', eventId: 1, eventCode: 'order_event', eventName: '订单事件', baseAttributeId: 1002, baseAttributeCode: 'pay_amount', defaultOperator: 'SUM', ruleIds: [] },
  { id: 7, metricCode: 'refund_rate', metricName: '退款率', metricType: 'COMPOSITE', description: '退款率 = 退款金额 / GMV', unit: '%', owner: '李四', version: 1, status: 'PUBLISHED', publishedAt: '2026-06-21 09:10', eventId: 2, eventCode: 'refund_event', eventName: '退款事件', formula: 'refund_amount / gmv', formulaTokens: [{ type: 'METRIC', value: 'refund_amount', metricId: 5, metricCode: 'refund_amount' }, { type: 'OP', value: '/' }, { type: 'METRIC', value: 'gmv', metricId: 1, metricCode: 'gmv' }], ruleIds: [] },
  { id: 8, metricCode: 'valid_sales', metricName: '有效销售额', metricType: 'BASIC', description: '订单状态为 PAID/FINISHED 的成交金额', unit: 'CNY', owner: '张三', version: 1, status: 'DRAFT', eventId: 1, eventCode: 'order_event', eventName: '订单事件', baseAttributeId: 1002, baseAttributeCode: 'pay_amount', defaultOperator: 'SUM', caliberFilter: { logic: 'AND', conditions: [{ attributeId: 1003, attributeCode: 'order_status', attributeName: '订单状态', op: 'IN', values: ['PAID', 'FINISHED'] }] }, ruleIds: [] },
];

const mockCalendars: TimeCalendar[] = [
  { id: 1, calendarCode: 'natural', calendarName: '自然日历', calendarType: 'NATURAL', yearStartMonth: 1, yearStartDay: 1, description: '标准自然年日历', status: 'ACTIVE' },
  { id: 2, calendarCode: 'fiscal_04', calendarName: '财年日历(4月起始)', calendarType: 'FISCAL', yearStartMonth: 4, yearStartDay: 1, description: '企业财年日历，每年4月1日起算', status: 'ACTIVE' },
];

const mockRules: TimeRule[] = [
  // 自动生成 — 自然日历 YEAR
  { id: 1, ruleCode: 'this_year', ruleText: '今年', calendarCode: 'natural', granularity: 'YEAR', rangeType: 'CURRENT', offsetValue: 0, explanationTemplate: '{year}年1月1日 至 {today}', priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 2, ruleCode: 'last_year', ruleText: '去年', calendarCode: 'natural', granularity: 'YEAR', rangeType: 'PREVIOUS', offsetValue: -1, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 3, ruleCode: 'the_year_before_last', ruleText: '前年', calendarCode: 'natural', granularity: 'YEAR', rangeType: 'PREVIOUS', offsetValue: -2, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 100, ruleCode: 'ytd', ruleText: '年初至今', calendarCode: 'natural', granularity: 'YEAR', rangeType: 'SINCE_START', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  // 自动生成 — 自然日历 QUARTER
  { id: 5, ruleCode: 'this_quarter', ruleText: '本季度', calendarCode: 'natural', granularity: 'QUARTER', rangeType: 'CURRENT', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 50, ruleCode: 'last_quarter', ruleText: '上季度', calendarCode: 'natural', granularity: 'QUARTER', rangeType: 'PREVIOUS', offsetValue: -1, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 101, ruleCode: 'qtd', ruleText: '季度初至今', calendarCode: 'natural', granularity: 'QUARTER', rangeType: 'SINCE_START', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  // 自动生成 — 自然日历 MONTH
  { id: 3, ruleCode: 'this_month', ruleText: '本月', calendarCode: 'natural', granularity: 'MONTH', rangeType: 'CURRENT', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 4, ruleCode: 'last_month', ruleText: '上月', calendarCode: 'natural', granularity: 'MONTH', rangeType: 'PREVIOUS', offsetValue: -1, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 30, ruleCode: 'the_month_before_last', ruleText: '上上月', calendarCode: 'natural', granularity: 'MONTH', rangeType: 'PREVIOUS', offsetValue: -2, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 102, ruleCode: 'mtd', ruleText: '月初至今', calendarCode: 'natural', granularity: 'MONTH', rangeType: 'SINCE_START', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  // 自动生成 — 自然日历 WEEK
  { id: 60, ruleCode: 'this_week', ruleText: '本周', calendarCode: 'natural', granularity: 'WEEK', rangeType: 'CURRENT', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 61, ruleCode: 'last_week', ruleText: '上周', calendarCode: 'natural', granularity: 'WEEK', rangeType: 'PREVIOUS', offsetValue: -1, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  // 自动生成 — 自然日历 DAY
  { id: 70, ruleCode: 'today', ruleText: '今天', calendarCode: 'natural', granularity: 'DAY', rangeType: 'CURRENT', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 71, ruleCode: 'yesterday', ruleText: '昨天', calendarCode: 'natural', granularity: 'DAY', rangeType: 'PREVIOUS', offsetValue: -1, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  // 自动生成 — 滚动窗口
  { id: 6, ruleCode: 'last_7_days', ruleText: '近7天', calendarCode: 'natural', granularity: 'DAY', rangeType: 'ROLLING', offsetValue: 0, rollingDays: 7, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 7, ruleCode: 'last_30_days', ruleText: '近30天', calendarCode: 'natural', granularity: 'DAY', rangeType: 'ROLLING', offsetValue: 0, rollingDays: 30, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 72, ruleCode: 'last_90_days', ruleText: '近90天', calendarCode: 'natural', granularity: 'DAY', rangeType: 'ROLLING', offsetValue: 0, rollingDays: 90, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 8, ruleCode: 'last_n_days', ruleText: '近N天', calendarCode: 'natural', granularity: 'DAY', rangeType: 'ROLLING', offsetValue: 0, paramPattern: 'N', paramDefault: 30, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  // 自动生成 — 财年日历
  { id: 9, ruleCode: 'this_fiscal_year', ruleText: '本财年', calendarCode: 'fiscal_04', granularity: 'YEAR', rangeType: 'CURRENT', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 80, ruleCode: 'last_fiscal_year', ruleText: '上财年', calendarCode: 'fiscal_04', granularity: 'YEAR', rangeType: 'PREVIOUS', offsetValue: -1, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 81, ruleCode: 'this_fiscal_quarter', ruleText: '本财季', calendarCode: 'fiscal_04', granularity: 'QUARTER', rangeType: 'CURRENT', offsetValue: 0, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  { id: 82, ruleCode: 'last_fiscal_quarter', ruleText: '上财季', calendarCode: 'fiscal_04', granularity: 'QUARTER', rangeType: 'PREVIOUS', offsetValue: -1, priority: 0, participateInReasoning: true, isAutoGenerated: true, status: 'ACTIVE' },
  // 用户自定义 — 节假日规则
  { id: 200, ruleCode: 'spring_festival', ruleText: '春节期间', calendarCode: 'natural', granularity: 'DAY', rangeType: 'HOLIDAY', offsetValue: 0, holidayCode: 'SPRING_FEST', offsetStart: 0, offsetEnd: 0, priority: 0, participateInReasoning: true, isAutoGenerated: false, status: 'ACTIVE' },
  { id: 201, ruleCode: 'national_day', ruleText: '国庆期间', calendarCode: 'natural', granularity: 'DAY', rangeType: 'HOLIDAY', offsetValue: 0, holidayCode: 'NATIONAL_DAY', offsetStart: 0, offsetEnd: 0, priority: 0, participateInReasoning: true, isAutoGenerated: false, status: 'ACTIVE' },
  { id: 202, ruleCode: 'before_spring_festival', ruleText: '春节前一周', calendarCode: 'natural', granularity: 'DAY', rangeType: 'HOLIDAY', offsetValue: 0, holidayCode: 'SPRING_FEST', offsetStart: -7, offsetEnd: -1, priority: 0, participateInReasoning: true, isAutoGenerated: false, status: 'ACTIVE' },
  { id: 203, ruleCode: 'after_national_day', ruleText: '国庆后三天', calendarCode: 'natural', granularity: 'DAY', rangeType: 'HOLIDAY', offsetValue: 0, holidayCode: 'NATIONAL_DAY', offsetStart: 1, offsetEnd: 3, priority: 0, participateInReasoning: true, isAutoGenerated: false, status: 'ACTIVE' },
  { id: 204, ruleCode: 'double_11_preheat', ruleText: '双十一预热期', calendarCode: 'natural', granularity: 'DAY', rangeType: 'HOLIDAY', offsetValue: 0, holidayCode: 'DOUBLE_11', offsetStart: -15, offsetEnd: -1, priority: 0, participateInReasoning: true, isAutoGenerated: false, status: 'ACTIVE' },
  // 用户自定义 — 自定义口径
  { id: 210, ruleCode: 'double_11_promo', ruleText: '双十一大促', calendarCode: 'natural', granularity: 'DAY', rangeType: 'CUSTOM', offsetValue: 0, fixedStartDate: '11-01', fixedEndDate: '11-11', isRecurring: true, priority: 0, participateInReasoning: true, isAutoGenerated: false, status: 'ACTIVE' },
  { id: 211, ruleCode: 'year_end_settlement', ruleText: '年终结算期', calendarCode: 'natural', granularity: 'DAY', rangeType: 'CUSTOM', offsetValue: 0, fixedStartDate: '12-15', fixedEndDate: '12-31', isRecurring: true, priority: 0, participateInReasoning: true, isAutoGenerated: false, status: 'ACTIVE' },
];

const mockHolidayCalendars: HolidayCalendar[] = [
  { id: 1, holidayCode: 'NEW_YEAR', holidayName: '元旦', holidayType: 'LEGAL', countryCode: 'CN', description: '新年假期', status: 'ACTIVE' },
  { id: 2, holidayCode: 'SPRING_FEST', holidayName: '春节', holidayType: 'LEGAL', countryCode: 'CN', description: '农历新年', status: 'ACTIVE' },
  { id: 3, holidayCode: 'QINGMING', holidayName: '清明节', holidayType: 'LEGAL', countryCode: 'CN', description: '清明假期', status: 'ACTIVE' },
  { id: 4, holidayCode: 'LABOR_DAY', holidayName: '劳动节', holidayType: 'LEGAL', countryCode: 'CN', description: '五一假期', status: 'ACTIVE' },
  { id: 5, holidayCode: 'NATIONAL_DAY', holidayName: '国庆节', holidayType: 'LEGAL', countryCode: 'CN', description: '国庆假期', status: 'ACTIVE' },
  { id: 6, holidayCode: 'DOUBLE_11', holidayName: '双十一', holidayType: 'ENTERPRISE', countryCode: 'CN', description: '电商大促活动', status: 'ACTIVE' },
  { id: 7, holidayCode: 'SIX_EIGHTEEN', holidayName: '618', holidayType: 'ENTERPRISE', countryCode: 'CN', description: '年中大促活动', status: 'ACTIVE' },
];

const mockHolidayPeriods: HolidayPeriod[] = [
  // 2025 年
  { id: 10, holidayCalendarId: 1, year: 2025, startDate: '2025-01-01', endDate: '2025-01-01', workdayAdjustments: [] },
  { id: 11, holidayCalendarId: 2, year: 2025, startDate: '2025-01-28', endDate: '2025-02-04', workdayAdjustments: [{ date: '2025-01-26', description: '春节调休上班' }, { date: '2025-02-08', description: '春节调休上班' }] },
  { id: 12, holidayCalendarId: 3, year: 2025, startDate: '2025-04-04', endDate: '2025-04-06', workdayAdjustments: [] },
  { id: 13, holidayCalendarId: 4, year: 2025, startDate: '2025-05-01', endDate: '2025-05-05', workdayAdjustments: [{ date: '2025-04-27', description: '劳动节调休上班' }] },
  { id: 14, holidayCalendarId: 5, year: 2025, startDate: '2025-10-01', endDate: '2025-10-07', workdayAdjustments: [{ date: '2025-09-28', description: '国庆调休上班' }, { date: '2025-10-11', description: '国庆调休上班' }] },
  { id: 15, holidayCalendarId: 6, year: 2025, startDate: '2025-11-01', endDate: '2025-11-11', workdayAdjustments: [] },
  { id: 16, holidayCalendarId: 7, year: 2025, startDate: '2025-06-01', endDate: '2025-06-18', workdayAdjustments: [] },
  // 2026 年
  { id: 1, holidayCalendarId: 1, year: 2026, startDate: '2026-01-01', endDate: '2026-01-01', workdayAdjustments: [] },
  { id: 2, holidayCalendarId: 2, year: 2026, startDate: '2026-02-17', endDate: '2026-02-23', workdayAdjustments: [{ date: '2026-02-15', description: '春节调休上班' }, { date: '2026-02-28', description: '春节调休上班' }] },
  { id: 3, holidayCalendarId: 3, year: 2026, startDate: '2026-04-04', endDate: '2026-04-06', workdayAdjustments: [] },
  { id: 4, holidayCalendarId: 4, year: 2026, startDate: '2026-05-01', endDate: '2026-05-05', workdayAdjustments: [{ date: '2026-04-26', description: '劳动节调休上班' }] },
  { id: 5, holidayCalendarId: 5, year: 2026, startDate: '2026-10-01', endDate: '2026-10-07', workdayAdjustments: [{ date: '2026-09-27', description: '国庆调休上班' }, { date: '2026-10-10', description: '国庆调休上班' }] },
  { id: 6, holidayCalendarId: 6, year: 2026, startDate: '2026-11-01', endDate: '2026-11-11', workdayAdjustments: [] },
  { id: 7, holidayCalendarId: 7, year: 2026, startDate: '2026-06-01', endDate: '2026-06-18', workdayAdjustments: [] },
];

const mockAliases: TermAlias[] = [
  { id: 1, aliasText: '成交额', targetType: 'METRIC', targetId: 1, targetCode: 'gmv', targetName: 'GMV', priority: 0, source: 'MANUAL', isConfirmed: true, conflictStatus: 'NONE' },
  { id: 2, aliasText: '交易额', targetType: 'METRIC', targetId: 1, targetCode: 'gmv', targetName: 'GMV', priority: 0, source: 'MANUAL', isConfirmed: true, conflictStatus: 'NONE' },
  { id: 3, aliasText: '销售额', targetType: 'METRIC', targetId: 1, targetCode: 'gmv', targetName: 'GMV', priority: 0, source: 'MANUAL', isConfirmed: true, conflictStatus: 'NONE' },
  { id: 4, aliasText: '订单量', targetType: 'METRIC', targetId: 2, targetCode: 'order_count', targetName: '订单数', priority: 0, source: 'MANUAL', isConfirmed: true, conflictStatus: 'NONE' },
  { id: 5, aliasText: '客单', targetType: 'METRIC', targetId: 3, targetCode: 'avg_order_value', targetName: '客单价', priority: 0, source: 'MANUAL', isConfirmed: true, conflictStatus: 'NONE' },
  { id: 6, aliasText: '大区', targetType: 'ENTITY', targetId: 3, targetCode: 'store', targetName: '门店', priority: 0, source: 'DISCOVERY', isConfirmed: false, conflictStatus: 'NONE' },
  { id: 7, aliasText: '今年', targetType: 'TIME_RULE', targetId: 1, targetCode: 'this_year', targetName: '今年', priority: 0, source: 'MANUAL', isConfirmed: true, conflictStatus: 'NONE' },
  { id: 8, aliasText: '这个月', targetType: 'TIME_RULE', targetId: 3, targetCode: 'this_month', targetName: '本月', priority: 0, source: 'MANUAL', isConfirmed: true, conflictStatus: 'NONE' },
  { id: 11, aliasText: '销售额', targetType: 'METRIC', targetId: 6, targetCode: 'normal_gmv', targetName: '普通GMV', priority: 0, source: 'DISCOVERY', isConfirmed: false, conflictStatus: 'CONFLICTED' },
];

const mockTerms: SemanticTerm[] = [
  {
    id: 1, termCode: 'gmv_term', termName: 'GMV',
    businessDefinition: 'GMV（Gross Merchandise Volume）为已支付订单的成交金额总和，不扣除退款和取消订单的金额。GMV 反映平台交易规模，是运营核心指标。',
    tags: ['核心指标', '运营'], owner: '张三',
    relatedObjects: [
      { objectType: 'METRIC', objectId: 1, objectCode: 'gmv', objectName: 'GMV', relationType: 'DEFINES' },
      { objectType: 'ALIAS', objectId: 1, objectCode: 'gmv', objectName: '成交额', relationType: 'SYNONYM' },
      { objectType: 'ALIAS', objectId: 2, objectCode: 'gmv', objectName: '交易额', relationType: 'SYNONYM' },
      { objectType: 'TERM', objectId: 2, objectCode: 'net_sales', objectName: '净销售额', relationType: 'RELATED' },
    ],
    usageContext: '日报、月度经营分析会', notes: '与财务"营收"口径不同，营收包含退款冲减后的净额',
    participateInExplanation: true, status: 'ACTIVE',
  },
  {
    id: 2, termCode: 'net_sales', termName: '净销售额',
    businessDefinition: '扣除退款和取消订单后的实际到账销售额，反映真实经营收入。',
    tags: ['核心指标', '财务'], owner: '张三',
    relatedObjects: [
      { objectType: 'METRIC', objectId: 6, objectCode: 'normal_gmv', objectName: '普通GMV', relationType: 'DEFINES' },
      { objectType: 'TERM', objectId: 1, objectCode: 'gmv_term', objectName: 'GMV', relationType: 'RELATED' },
    ],
    usageContext: '财务报表、损益分析', notes: '净销售额 = GMV - 退款金额 - 取消订单金额',
    participateInExplanation: true, status: 'ACTIVE',
  },
  {
    id: 3, termCode: 'high_value_user', termName: '高价值用户',
    businessDefinition: '企业定义的高价值用户群体，按用户等级 HIGH 识别。高价值用户贡献了平台约 60% 的 GMV。',
    tags: ['用户概念', '运营'], owner: '李四',
    relatedObjects: [
      { objectType: 'ENTITY', objectId: 1, objectCode: 'customer', objectName: '用户', relationType: 'DEFINES' },
      { objectType: 'ALIAS', objectId: 10, objectCode: 'high_value_user', objectName: '高价值', relationType: 'SYNONYM' },
    ],
    usageContext: '用户分层分析、精准营销', notes: '用户等级由 CRM 系统维护，每月更新一次',
    participateInExplanation: true, status: 'ACTIVE',
  },
  {
    id: 4, termCode: 'valid_order', termName: '有效订单',
    businessDefinition: '订单状态为已支付（PAID）或已完成（FINISHED）的订单。排除待支付、已取消、已退款状态的订单。',
    tags: ['交易概念', '运营'], owner: '李四',
    relatedObjects: [
      { objectType: 'EVENT', objectId: 1, objectCode: 'order_event', objectName: '订单事件', relationType: 'DEFINES' },
    ],
    usageContext: '日报、周报、月度经营分析', notes: '"有效订单"与"成交订单"不同，成交订单还包含 SHIPPING 状态',
    participateInExplanation: true, status: 'ACTIVE',
  },
  {
    id: 5, termCode: 'tax_included', termName: '含税口径',
    businessDefinition: '统计金额指标时包含税额部分。含税口径下的 GMV 会比不含税口径高约 6%-13%。',
    tags: ['口径说明', '财务'], owner: '张三',
    relatedObjects: [
      { objectType: 'METRIC', objectId: 1, objectCode: 'gmv', objectName: 'GMV', relationType: 'DEFINES' },
      { objectType: 'TERM', objectId: 6, objectCode: 'tax_excluded', objectName: '不含税口径', relationType: 'RELATED' },
    ],
    usageContext: '财务报表、税务分析', notes: '默认报表使用不含税口径，财务分析时使用含税口径',
    participateInExplanation: true, status: 'ACTIVE',
  },
  {
    id: 6, termCode: 'tax_excluded', termName: '不含税口径',
    businessDefinition: '统计金额指标时不含税额部分，为企业默认口径。',
    tags: ['口径说明', '财务'], owner: '张三',
    relatedObjects: [
      { objectType: 'METRIC', objectId: 1, objectCode: 'gmv', objectName: 'GMV', relationType: 'DEFINES' },
      { objectType: 'TERM', objectId: 5, objectCode: 'tax_included', objectName: '含税口径', relationType: 'RELATED' },
    ],
    usageContext: '日常报表、经营分析', notes: '企业默认使用不含税口径',
    participateInExplanation: true, status: 'ACTIVE',
  },
];

const mockUnknownTerms: UnknownTerm[] = [
  { id: 1, termText: '复购客户', occurrenceCount: 47, lastOccurredAt: '2026-07-06', resolutionStatus: 'OPEN' },
  { id: 2, termText: '退运率', occurrenceCount: 23, lastOccurredAt: '2026-07-05', resolutionStatus: 'OPEN' },
  { id: 3, termText: 'VIP客户', occurrenceCount: 12, lastOccurredAt: '2026-07-04', resolutionStatus: 'IGNORED' },
  { id: 4, termText: '新品销量', occurrenceCount: 8, lastOccurredAt: '2026-07-03', resolutionStatus: 'OPEN' },
  { id: 5, termText: '毛利率', occurrenceCount: 34, lastOccurredAt: '2026-07-06', resolutionStatus: 'OPEN' },
];

const mockMetricTargets: MetricTarget[] = [
  { id: 1, metricId: 1, metricCode: 'gmv', metricName: 'GMV', valueType: 'ABSOLUTE', period: 'MONTH', targetValue: 200000, effectiveStart: '2026-01-01', effectiveEnd: '2026-12-31', status: 'ACTIVE' },
  { id: 2, metricId: 1, metricCode: 'gmv', metricName: 'GMV', valueType: 'ABSOLUTE', period: 'MONTH', targetValue: 30000, dimensionFilter: { logic: 'AND', conditions: [{ attributeId: 103, attributeCode: 'customer_level', attributeName: '用户等级', op: 'IN', values: ['HIGH'] }] }, dimensionLabel: '用户等级 ∈ HIGH', effectiveStart: '2026-01-01', effectiveEnd: '2026-12-31', status: 'ACTIVE' },
  { id: 3, metricId: 2, metricCode: 'order_count', metricName: '订单数', valueType: 'ABSOLUTE', period: 'MONTH', targetValue: 5000, effectiveStart: '2026-06-01', effectiveEnd: '2026-06-30', status: 'DRAFT' },
  { id: 4, metricId: 1, metricCode: 'gmv', metricName: 'GMV', valueType: 'ABSOLUTE', period: 'MONTH', targetValue: 35000, dimensionFilter: { logic: 'AND', conditions: [{ attributeId: 103, attributeCode: 'customer_level', attributeName: '用户等级', op: 'IN', values: ['HIGH'] }] }, dimensionLabel: '用户等级 ∈ HIGH', effectiveStart: '2026-06-01', effectiveEnd: '2026-06-30', status: 'DRAFT' },
];

const mockBusinessRules: BusinessRule[] = [
  {
    id: 1, ruleCode: 'underperform', ruleName: '未达标',
    description: '指标未达到目标值的 95% 即判定为未达标',
    mode: 'COMPARISON', targetObjectType: 'METRIC', targetObjectId: 1, targetObjectName: 'GMV (gmv)',
    expression: { metricId: 1, metricCode: 'gmv', operator: '<', thresholdSource: 'METRIC_TARGET', ratio: 0.95 },
    triggerWords: ['未达标', '没达标'], isNegative: true, oppositeRuleId: 2, status: 'PUBLISHED', version: 1, owner: '张三', publishedAt: '2026-07-01 10:30',
    lastValidation: { blockers: 0, warnings: 0, issues: [] },
  },
  {
    id: 2, ruleCode: 'on_target', ruleName: '达标',
    description: '指标达到或超过目标值的 95% 即判定为达标',
    mode: 'COMPARISON', targetObjectType: 'METRIC', targetObjectId: 1, targetObjectName: 'GMV (gmv)',
    expression: { metricId: 1, metricCode: 'gmv', operator: '>=', thresholdSource: 'METRIC_TARGET', ratio: 0.95 },
    triggerWords: ['达标'], isNegative: false, oppositeRuleId: 1, status: 'PUBLISHED', version: 1, owner: '张三', publishedAt: '2026-07-01 10:31',
    lastValidation: { blockers: 0, warnings: 0, issues: [] },
  },
  {
    id: 3, ruleCode: 'core_high_value_user', ruleName: '核心高价值用户',
    description: '用户等级为 HIGH 的核心用户群体',
    mode: 'FILTER', targetObjectType: 'ENTITY', targetObjectId: 1, targetObjectName: '用户 (customer)',
    expression: { entityId: 1, logic: 'AND', conditions: [{ attributeId: 103, attributeCode: 'customer_level', attributeName: '用户等级', op: '=', values: ['HIGH'] }] },
    triggerWords: ['核心用户', '高价值用户'], isNegative: false, status: 'PUBLISHED', version: 1, owner: '李四', publishedAt: '2026-07-02 09:00',
    lastValidation: { blockers: 0, warnings: 0, issues: [] },
  },
  {
    id: 4, ruleCode: 'high_freq_refund', ruleName: '高频退款',
    description: '近30天退款次数达到阈值的用户',
    mode: 'EXISTENCE', targetObjectType: 'EVENT', targetObjectId: 2, targetObjectName: '退款事件 (refund_event)',
    expression: { eventId: 2, aggregate: 'COUNT', operator: '>=', value: 3, timeRuleCode: 'last_30_days' },
    triggerWords: ['高频退款'], isNegative: false, status: 'DRAFT', version: 1, owner: '李四',
  },
  {
    id: 5, ruleCode: 'high_value_underperform', ruleName: '高价值未达标',
    description: '核心高价值用户且 GMV 未达标',
    mode: 'COMPOSITE', targetObjectType: 'EVENT', targetObjectId: 1, targetObjectName: '订单事件 (order_event)',
    expression: { logic: 'AND', ruleIds: [3, 1] },
    triggerWords: ['高价值未达标'], isNegative: false, status: 'DRAFT', version: 1, owner: '张三',
  },
];

const mockRuleLinks: RuleLink[] = [
  { id: 1, ownerType: 'EVENT', ownerId: 1, ruleId: 1 },
  { id: 2, ownerType: 'EVENT', ownerId: 1, ruleId: 2 },
];

let nextId = 1000;
const genId = () => ++nextId;
let workspaceWriteAccess = false;

export function setWorkspaceWriteAccess(allowed: boolean) {
  workspaceWriteAccess = allowed;
}

export const useStore = create<StoreState>()((rawSet, get) => {
  const set: typeof rawSet = (partial, replace) => {
    if (!workspaceWriteAccess) return;
    rawSet(partial, replace as never);
  };
  return ({
  calendars: mockCalendars,
  rules: mockRules,
  holidayCalendars: mockHolidayCalendars,
  holidayPeriods: mockHolidayPeriods,
  entities: mockEntities,
  events: mockEvents,
  entityHierarchies: mockEntityHierarchies,
  objectValues: mockObjectValues,
  semanticRelations: mockSemanticRelations,
  schemaMappings: mockSchemaMappings,
  fieldMappings: mockFieldMappings,
  metrics: mockMetrics,
  aliases: mockAliases,
  terms: mockTerms,
  unknownTerms: mockUnknownTerms,
  metricTargets: mockMetricTargets,
  businessRules: mockBusinessRules,
  ruleLinks: mockRuleLinks,

  addCalendar: (c) => set((s) => ({ calendars: [...s.calendars, { ...c, id: genId() }] })),
  updateCalendar: (id, c) => set((s) => ({ calendars: s.calendars.map((x) => (x.id === id ? { ...x, ...c } : x)) })),
  deleteCalendar: (id) => set((s) => ({ calendars: s.calendars.filter((x) => x.id !== id) })),

  addRule: (r) => set((s) => ({ rules: [...s.rules, { ...r, id: genId() }] })),
  updateRule: (id, r) => set((s) => ({ rules: s.rules.map((x) => (x.id === id ? { ...x, ...r } : x)) })),
  deleteRule: (id) => set((s) => ({ rules: s.rules.filter((x) => x.id !== id) })),
  toggleRuleStatus: (id) => set((s) => ({ rules: s.rules.map((x) => (x.id === id ? { ...x, status: x.status === 'ACTIVE' ? 'INACTIVE' as const : 'ACTIVE' as const } : x)) })),

  addHolidayCalendar: (h) => set((s) => ({ holidayCalendars: [...s.holidayCalendars, { ...h, id: genId() }] })),
  updateHolidayCalendar: (id, h) => set((s) => ({ holidayCalendars: s.holidayCalendars.map((x) => (x.id === id ? { ...x, ...h } : x)) })),
  deleteHolidayCalendar: (id) => set((s) => ({ holidayCalendars: s.holidayCalendars.filter((x) => x.id !== id) })),

  addHolidayPeriod: (p) => set((s) => ({ holidayPeriods: [...s.holidayPeriods, { ...p, id: genId() }] })),
  updateHolidayPeriod: (id, p) => set((s) => ({ holidayPeriods: s.holidayPeriods.map((x) => (x.id === id ? { ...x, ...p } : x)) })),
  deleteHolidayPeriod: (id) => set((s) => ({ holidayPeriods: s.holidayPeriods.filter((x) => x.id !== id) })),

  addAlias: (a) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      const dup = s.aliases.find((x) => x.aliasText === a.aliasText && x.targetType === a.targetType && x.targetId === a.targetId);
      if (dup) issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_ALIAS', message: `同义词"${a.aliasText}"已存在相同映射` });
      const conflicts = s.aliases.filter((x) => x.aliasText === a.aliasText && x.targetType === a.targetType && x.targetId !== a.targetId);
      if (conflicts.length > 0) issues.push({ severity: 'WARNING', checkCode: 'TERM_ALIAS_CONFLICT', message: `"${a.aliasText}"已指向${conflicts.map((c) => c.targetName).join('、')}，存在冲突` });
      return { aliases: [...s.aliases, { ...a, id: genId(), conflictStatus: conflicts.length > 0 ? 'CONFLICTED' : 'NONE' }] };
    });
    return issues;
  },
  updateAlias: (id, a) => set((s) => ({ aliases: s.aliases.map((x) => (x.id === id ? { ...x, ...a } : x)) })),
  deleteAlias: (id) => set((s) => ({ aliases: s.aliases.filter((x) => x.id !== id) })),
  batchConfirmAliases: (ids) => set((s) => ({ aliases: s.aliases.map((x) => (ids.includes(x.id) ? { ...x, isConfirmed: true } : x)) })),

  addTerm: (t) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      const dup = s.terms.find((x) => x.termCode === t.termCode);
      if (dup) issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_TERM_CODE', message: `术语编码"${t.termCode}"已存在` });
      return { terms: [...s.terms, { ...t, id: genId() }] };
    });
    return issues;
  },
  updateTerm: (id, t) => set((s) => ({ terms: s.terms.map((x) => (x.id === id ? { ...x, ...t } : x)) })),
  deleteTerm: (id) => set((s) => ({ terms: s.terms.filter((x) => x.id !== id) })),

  mapUnknownTerm: (id, alias) => set((s) => ({
    unknownTerms: s.unknownTerms.map((x) => (x.id === id ? { ...x, resolutionStatus: 'RESOLVED' as const } : x)),
    aliases: [...s.aliases, { ...alias, id: genId(), source: 'UNKNOWN_TERM' as const }],
  })),
  ignoreUnknownTerm: (id) => set((s) => ({ unknownTerms: s.unknownTerms.map((x) => (x.id === id ? { ...x, resolutionStatus: 'IGNORED' as const } : x)) })),

  addEntity: (e) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      if (s.entities.some((x) => x.entityCode === e.entityCode)) {
        issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_ENTITY_CODE', message: `实体编码"${e.entityCode}"已存在` });
        return {};
      }
      return { entities: [...s.entities, { ...e, id: genId(), attributes: e.attributes || [], status: e.status || 'ACTIVE' }] };
    });
    return issues;
  },
  updateEntity: (id, e) => set((s) => ({ entities: s.entities.map((x) => (x.id === id ? { ...x, ...e } : x)) })),
  deleteEntity: (id) => set((s) => ({
    entities: s.entities.filter((x) => x.id !== id),
    entityHierarchies: s.entityHierarchies.filter((x) => !(x.objectType === 'ENTITY' && x.objectId === id)),
    objectValues: s.objectValues.filter((x) => !(x.objectType === 'ENTITY' && x.objectId === id)),
    semanticRelations: s.semanticRelations.filter((x) => !(
      (x.sourceType === 'ENTITY' && x.sourceId === id)
      || (x.targetType === 'ENTITY' && x.targetId === id)
    )),
    schemaMappings: s.schemaMappings.filter((x) => !(x.objectType === 'ENTITY' && x.objectId === id)),
    fieldMappings: s.fieldMappings.filter((x) => !(x.objectType === 'ENTITY' && x.objectId === id)),
    ruleLinks: s.ruleLinks.filter((x) => !(x.ownerType === 'ENTITY' && x.ownerId === id)),
    businessRules: s.businessRules.filter((x) => !(x.targetObjectType === 'ENTITY' && x.targetObjectId === id)),
    aliases: s.aliases.filter((x) => !(x.targetType === 'ENTITY' && x.targetId === id)),
  })),

  addEvent: (e) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      if (s.events.some((x) => x.eventCode === e.eventCode)) {
        issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_EVENT_CODE', message: `事件编码"${e.eventCode}"已存在` });
        return {};
      }
      return { events: [...s.events, { ...e, id: genId(), attributes: e.attributes || [], status: e.status || 'ACTIVE' }] };
    });
    return issues;
  },
  updateEvent: (id, e) => set((s) => ({ events: s.events.map((x) => (x.id === id ? { ...x, ...e } : x)) })),
  deleteEvent: (id) => set((s) => {
    const metricIds = s.metrics.filter((x) => x.eventId === id).map((x) => x.id);
    return {
      events: s.events.filter((x) => x.id !== id),
      entityHierarchies: s.entityHierarchies.filter((x) => !(x.objectType === 'EVENT' && x.objectId === id)),
      objectValues: s.objectValues.filter((x) => !(x.objectType === 'EVENT' && x.objectId === id)),
      semanticRelations: s.semanticRelations.filter((x) => !(
        (x.sourceType === 'EVENT' && x.sourceId === id)
        || (x.targetType === 'EVENT' && x.targetId === id)
      )),
      schemaMappings: s.schemaMappings.filter((x) => !(x.objectType === 'EVENT' && x.objectId === id)),
      fieldMappings: s.fieldMappings.filter((x) => !(x.objectType === 'EVENT' && x.objectId === id)),
      metrics: s.metrics.filter((x) => x.eventId !== id),
      metricTargets: s.metricTargets.filter((x) => !metricIds.includes(x.metricId)),
      ruleLinks: s.ruleLinks.filter((x) => !(x.ownerType === 'EVENT' && x.ownerId === id)),
      businessRules: s.businessRules.filter((x) => !(
        (x.targetObjectType === 'EVENT' && x.targetObjectId === id)
        || (x.targetObjectType === 'METRIC' && metricIds.includes(x.targetObjectId))
      )),
      aliases: s.aliases.filter((x) => !(
        (x.targetType === 'EVENT' && x.targetId === id)
        || (x.targetType === 'METRIC' && metricIds.includes(x.targetId))
      )),
    };
  }),
  deleteObjectAttributes: (objectType, objectId, attributeIds) => set((s) => {
    const removed = new Set(attributeIds);
    const semanticRelations = s.semanticRelations.map((relation) => ({
      ...relation,
      sourceAttributeId: relation.sourceType === objectType && relation.sourceId === objectId && relation.sourceAttributeId && removed.has(relation.sourceAttributeId)
        ? undefined : relation.sourceAttributeId,
      targetAttributeId: relation.targetType === objectType && relation.targetId === objectId && relation.targetAttributeId && removed.has(relation.targetAttributeId)
        ? undefined : relation.targetAttributeId,
    }));
    if (objectType === 'EVENT') {
      return {
        events: s.events.map((event) => event.id === objectId ? {
          ...event,
          attributes: event.attributes.filter((attr) => !removed.has(attr.id)),
          uniqueIdentifierAttributeId: event.uniqueIdentifierAttributeId && removed.has(event.uniqueIdentifierAttributeId) ? undefined : event.uniqueIdentifierAttributeId,
          defaultTimeAttributeId: event.defaultTimeAttributeId && removed.has(event.defaultTimeAttributeId) ? undefined : event.defaultTimeAttributeId,
        } : event),
        fieldMappings: s.fieldMappings.filter((mapping) => !(
          mapping.objectType === objectType && mapping.objectId === objectId && removed.has(mapping.attributeId)
        )),
        entityHierarchies: s.entityHierarchies.filter((level) => !(level.objectType === objectType && level.objectId === objectId && removed.has(level.attributeId))),
        objectValues: s.objectValues.filter((value) => !(value.objectType === objectType && value.objectId === objectId && removed.has(value.attributeId))),
        semanticRelations,
        metrics: s.metrics.map((metric) => metric.eventId === objectId && metric.baseAttributeId && removed.has(metric.baseAttributeId)
          ? { ...metric, baseAttributeId: undefined, baseAttributeCode: undefined }
          : metric),
      };
    }
    return {
      entities: s.entities.map((entity) => entity.id === objectId ? {
        ...entity,
        attributes: entity.attributes.filter((attr) => !removed.has(attr.id)),
        uniqueIdentifierAttributeId: entity.uniqueIdentifierAttributeId && removed.has(entity.uniqueIdentifierAttributeId) ? undefined : entity.uniqueIdentifierAttributeId,
        defaultTimeAttributeId: entity.defaultTimeAttributeId && removed.has(entity.defaultTimeAttributeId) ? undefined : entity.defaultTimeAttributeId,
      } : entity),
      fieldMappings: s.fieldMappings.filter((mapping) => !(
        mapping.objectType === objectType && mapping.objectId === objectId && removed.has(mapping.attributeId)
      )),
      entityHierarchies: s.entityHierarchies.filter((level) => !(level.objectType === objectType && level.objectId === objectId && removed.has(level.attributeId))),
      objectValues: s.objectValues.filter((value) => !(value.objectType === objectType && value.objectId === objectId && removed.has(value.attributeId))),
      semanticRelations,
    };
  }),

  saveObjectHierarchy: (objectType, objectId, attributeId, hierarchyName, hierarchyCode, levels) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      const object = objectType === 'ENTITY' ? s.entities.find((item) => item.id === objectId) : s.events.find((item) => item.id === objectId);
      const attribute = object?.attributes.find((item) => item.id === attributeId);
      if (!attribute || attribute.semanticType !== 'HIERARCHY') {
        issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_HIERARCHY_ATTRIBUTE', message: '层级属性必须属于当前本体且语义类型为 HIERARCHY' });
        return {};
      }
      if (!hierarchyName.trim() || levels.length === 0) {
        issues.push({ severity: 'BLOCKER', checkCode: 'EMPTY_HIERARCHY', message: '请填写层级名称并至少配置一个级别' });
        return {};
      }
      const codes = levels.map((level) => level.levelCode.trim().toUpperCase());
      if (codes.some((code) => !code) || new Set(codes).size !== codes.length) {
        issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_LEVEL_CODE', message: '级别编码不能为空且不能重复' });
        return {};
      }
      if (levels.some((level) => !level.levelName.trim())) {
        issues.push({ severity: 'BLOCKER', checkCode: 'EMPTY_LEVEL_NAME', message: '级别名称不能为空' });
        return {};
      }
      const existing = s.entityHierarchies.filter((level) => level.objectType === objectType && level.objectId === objectId && level.attributeId === attributeId);
      const retainedIds = new Set(levels.map((level) => level.id).filter(Boolean));
      const removedIds = existing.filter((level) => !retainedIds.has(level.id)).map((level) => level.id);
      const referenced = s.objectValues.filter((value) => value.hierarchyLevelId && removedIds.includes(value.hierarchyLevelId));
      if (referenced.length) {
        issues.push({ severity: 'BLOCKER', checkCode: 'LEVEL_IN_USE', message: `有 ${referenced.length} 个实体值正在引用被删除级别，请先迁移或停用相关值` });
        return {};
      }
      const ids = levels.map((level) => level.id || genId());
      const normalized: EntityHierarchyLevel[] = levels.map((level, index) => ({
        id: ids[index],
        objectType,
        objectId,
        attributeId,
        hierarchyCode: hierarchyCode.trim() || `${attribute.attributeCode}_hierarchy`,
        hierarchyName: hierarchyName.trim(),
        levelCode: level.levelCode.trim().toUpperCase(),
        levelName: level.levelName.trim(),
        levelDepth: index + 1,
        parentLevelId: index === 0 ? undefined : ids[index - 1],
        status: level.status,
      }));
      const normalizedById = new Map(normalized.map((level) => [level.id, level]));
      const scopedValues = s.objectValues.filter((value) => value.objectType === objectType && value.objectId === objectId && value.attributeId === attributeId);
      const invalidValue = scopedValues.find((value) => {
        const level = normalizedById.get(value.hierarchyLevelId || 0);
        if (!level) return true;
        if (level.levelDepth === 1) return !!value.parentValueId;
        const parent = scopedValues.find((item) => item.id === value.parentValueId);
        return !parent || parent.hierarchyLevelId !== level.parentLevelId;
      });
      if (invalidValue) {
        issues.push({ severity: 'BLOCKER', checkCode: 'HIERARCHY_REORDER_BREAKS_VALUES', message: `调整后的级别顺序会使实体值“${invalidValue.businessName}”的父子关系失效，请先迁移相关值` });
        return {};
      }
      return {
        entityHierarchies: [
          ...s.entityHierarchies.filter((level) => !(level.objectType === objectType && level.objectId === objectId && level.attributeId === attributeId)),
          ...normalized,
        ],
      };
    });
    return issues;
  },

  deleteObjectHierarchy: (objectType, objectId, attributeId) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      const levels = s.entityHierarchies.filter((level) => level.objectType === objectType && level.objectId === objectId && level.attributeId === attributeId);
      const levelIds = new Set(levels.map((level) => level.id));
      const referenceCount = s.objectValues.filter((value) => value.hierarchyLevelId && levelIds.has(value.hierarchyLevelId)).length;
      if (referenceCount) {
        issues.push({ severity: 'BLOCKER', checkCode: 'HIERARCHY_IN_USE', message: `该层级正被 ${referenceCount} 个值引用，请先迁移或停用相关值` });
        return {};
      }
      return { entityHierarchies: s.entityHierarchies.filter((level) => !(level.objectType === objectType && level.objectId === objectId && level.attributeId === attributeId)) };
    });
    return issues;
  },

  upsertObjectValue: (input) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      const object = input.objectType === 'ENTITY' ? s.entities.find((item) => item.id === input.objectId) : s.events.find((item) => item.id === input.objectId);
      const attribute = object?.attributes.find((item) => item.id === input.attributeId);
      if (!attribute) {
        issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_VALUE_ATTRIBUTE', message: '值属性不属于当前本体' });
        return {};
      }
      const duplicate = s.objectValues.find((value) => value.objectType === input.objectType && value.objectId === input.objectId && value.attributeId === input.attributeId && value.physicalCode === input.physicalCode.trim() && value.id !== input.id);
      if (duplicate) {
        issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_PHYSICAL_CODE', message: `物理编码“${input.physicalCode}”在当前属性中已存在` });
        return {};
      }
      const isHierarchy = attribute.semanticType === 'HIERARCHY';
      if (!isHierarchy && (input.hierarchyLevelId || input.parentValueId)) {
        issues.push({ severity: 'BLOCKER', checkCode: 'FLAT_VALUE_HAS_HIERARCHY', message: '普通枚举值不能配置所属级别或父值' });
        return {};
      }
      if (isHierarchy) {
        const level = s.entityHierarchies.find((item) => item.id === input.hierarchyLevelId && item.objectType === input.objectType && item.objectId === input.objectId && item.attributeId === input.attributeId);
        if (!level) {
          issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_VALUE_LEVEL', message: '层级值必须选择当前属性的合法级别' });
          return {};
        }
        if (level.levelDepth === 1 && input.parentValueId) {
          issues.push({ severity: 'BLOCKER', checkCode: 'ROOT_VALUE_HAS_PARENT', message: '根级值不能选择父值' });
          return {};
        }
        if (level.levelDepth > 1) {
          const parent = s.objectValues.find((value) => value.id === input.parentValueId && value.objectType === input.objectType && value.objectId === input.objectId && value.attributeId === input.attributeId);
          if (!parent || parent.hierarchyLevelId !== level.parentLevelId) {
            issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_VALUE_PARENT', message: '父值必须属于当前级别的合法上一级' });
            return {};
          }
        }
        if (input.id && input.parentValueId) {
          let ancestorId: number | undefined = input.parentValueId;
          const visited = new Set<number>();
          while (ancestorId) {
            if (ancestorId === input.id || visited.has(ancestorId)) {
              issues.push({ severity: 'BLOCKER', checkCode: 'VALUE_CYCLE', message: '调整父值后会形成循环关系' });
              return {};
            }
            visited.add(ancestorId);
            ancestorId = s.objectValues.find((value) => value.id === ancestorId)?.parentValueId;
          }
        }
      }
      if (input.status === 'INACTIVE' && input.id) {
        const activeChildren = s.objectValues.filter((value) => value.parentValueId === input.id && value.status === 'ACTIVE').length;
        if (activeChildren) issues.push({ severity: 'WARNING', checkCode: 'ACTIVE_CHILDREN', message: `该值还有 ${activeChildren} 个启用子值` });
      }
      const next: ObjectValue = {
        ...input,
        id: input.id || genId(),
        businessName: input.businessName.trim(),
        physicalCode: input.physicalCode.trim(),
      };
      return {
        objectValues: input.id
          ? s.objectValues.map((value) => value.id === input.id ? next : value)
          : [...s.objectValues, next],
      };
    });
    return issues;
  },

  deleteObjectValue: (id) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      const childCount = s.objectValues.filter((value) => value.parentValueId === id).length;
      if (childCount) {
        issues.push({ severity: 'BLOCKER', checkCode: 'VALUE_HAS_CHILDREN', message: `该值包含 ${childCount} 个直接子值，请先迁移或删除子值` });
        return {};
      }
      return { objectValues: s.objectValues.filter((value) => value.id !== id) };
    });
    return issues;
  },

  addRelation: (r) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      if (s.semanticRelations.some((x) => x.relationCode === r.relationCode)) {
        issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_RELATION_CODE', message: `关系编码"${r.relationCode}"已存在` });
        return {};
      }
      return { semanticRelations: [...s.semanticRelations, { ...r, id: genId() }] };
    });
    return issues;
  },
  updateRelation: (id, r) => set((s) => ({ semanticRelations: s.semanticRelations.map((x) => (x.id === id ? { ...x, ...r } : x)) })),
  deleteRelation: (id) => set((s) => ({ semanticRelations: s.semanticRelations.filter((x) => x.id !== id) })),

  upsertSchemaMapping: (m) => set((s) => {
    const existing = s.schemaMappings.find((x) => x.objectType === m.objectType && x.objectId === m.objectId);
    if (existing) {
      return { schemaMappings: s.schemaMappings.map((x) => (x.id === existing.id ? { ...x, ...m } : x)) };
    }
    return { schemaMappings: [...s.schemaMappings, { ...m, id: genId() }] };
  }),
  upsertFieldMapping: (m) => set((s) => {
    const existing = s.fieldMappings.find((x) => x.objectType === m.objectType && x.objectId === m.objectId && x.attributeId === m.attributeId);
    if (existing) {
      return { fieldMappings: s.fieldMappings.map((x) => (x.id === existing.id ? { ...x, ...m } : x)) };
    }
    return { fieldMappings: [...s.fieldMappings, { ...m, id: genId() }] };
  }),
  deleteFieldMapping: (id) => set((s) => ({ fieldMappings: s.fieldMappings.filter((x) => x.id !== id) })),

  validateEntity: (id) => {
    const s = get();
    const entity = s.entities.find((x) => x.id === id);
    if (!entity) return [];
    const issues: ValidationIssue[] = [];
    if (!entity.uniqueIdentifierAttributeId) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_ENTITY_UNIQUE_IDENTIFIER', message: '实体必须配置唯一标识' });
    if (!entity.defaultTimeAttributeId) issues.push({ severity: 'WARNING', checkCode: 'MISSING_ENTITY_DEFAULT_TIME', message: '实体建议配置默认时间' });
    const mappings = s.fieldMappings.filter((x) => x.objectType === 'ENTITY' && x.objectId === id);
    entity.attributes.filter((a) => a.isFilterable || a.isGroupable).forEach((a) => {
      if (!mappings.some((m) => m.attributeId === a.id)) {
        issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_FIELD_MAPPING', message: `属性"${a.attributeName}"缺少字段映射` });
      }
    });
    entity.attributes.filter((a) => ['ENUM', 'CODE', 'HIERARCHY', 'LIFECYCLE'].includes(a.semanticType)).forEach((a) => {
      if (!s.objectValues.some((value) => value.objectType === 'ENTITY' && value.objectId === id && value.attributeId === a.id)) {
        issues.push({ severity: 'WARNING', checkCode: 'MISSING_ENTITY_VALUE', message: `属性"${a.attributeName}"建议维护业务值` });
      }
    });
    return issues;
  },
  validateEvent: (id) => {
    const s = get();
    const event = s.events.find((x) => x.id === id);
    if (!event) return [];
    const issues: ValidationIssue[] = [];
    if (!event.uniqueIdentifierAttributeId) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_EVENT_UNIQUE_IDENTIFIER', message: '事件必须配置唯一标识' });
    if (!event.defaultTimeAttributeId) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_EVENT_DEFAULT_TIME', message: '事件必须配置默认时间' });
    const schemaMapping = s.schemaMappings.find((x) => x.objectType === 'EVENT' && x.objectId === id);
    if (!schemaMapping) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_EVENT_SCHEMA_MAPPING', message: '事件必须有表映射' });
    const relations = s.semanticRelations.filter((x) => x.status === 'ACTIVE' && x.sourceType === 'EVENT' && x.sourceId === id && x.targetType === 'ENTITY');
    if (relations.length === 0) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_EVENT_PARTICIPANT', message: '事件必须至少有一个参与实体关系' });
    const mappings = s.fieldMappings.filter((x) => x.objectType === 'EVENT' && x.objectId === id);
    relations.forEach((r) => {
      if (!r.sourceAttributeId || !r.targetAttributeId) {
        issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_RELATION_ATTRIBUTE', message: `关系"${r.relationName}"必须配置两端连接属性` });
      } else if (!mappings.some((m) => m.attributeId === r.sourceAttributeId && m.fieldRole === 'JOIN_KEY')) {
        issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_JOIN_KEY_MAPPING', message: `关系"${r.relationName}"的事件连接属性缺少 JOIN_KEY 映射` });
      }
    });
    const defaultTimeMapping = event.defaultTimeAttributeId ? mappings.find((m) => m.attributeId === event.defaultTimeAttributeId) : undefined;
    if (event.defaultTimeAttributeId && defaultTimeMapping?.fieldRole !== 'TIME') {
      issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_EVENT_TIME_BINDING', message: '默认时间必须映射为 TIME' });
    }
    event.attributes.filter((a) => ['ENUM', 'CODE', 'HIERARCHY', 'LIFECYCLE'].includes(a.semanticType)).forEach((a) => {
      if (!s.objectValues.some((value) => value.objectType === 'EVENT' && value.objectId === id && value.attributeId === a.id)) {
        issues.push({ severity: 'WARNING', checkCode: 'MISSING_EVENT_VALUE', message: `事件属性"${a.attributeName}"建议维护业务值` });
      }
    });
    return issues;
  },

  addMetric: (m) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      if (s.metrics.some((x) => x.metricCode === m.metricCode)) {
        issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_METRIC_CODE', message: `指标编码"${m.metricCode}"已存在` });
        return {};
      }
      return { metrics: [...s.metrics, { ...m, id: genId() }] };
    });
    return issues;
  },
  updateMetric: (id, m) => set((s) => ({ metrics: s.metrics.map((x) => (x.id === id ? { ...x, ...m } : x)) })),
  deleteMetric: (id) => set((s) => ({ metrics: s.metrics.filter((x) => x.id !== id) })),

  validateMetric: (id) => {
    const s = get();
    const metric = s.metrics.find((x) => x.id === id);
    if (!metric) return [];
    const issues: ValidationIssue[] = [];
    if (!metric.metricCode) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_METRIC_CODE', message: '指标编码不能为空' });
    if (!metric.metricName) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_METRIC_NAME', message: '指标名称不能为空' });
    if (metric.metricType === 'BASIC') {
      if (!metric.eventId) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_METRIC_EVENT', message: '基础指标必须选择所属事件' });
      else {
        const event = s.events.find((e) => e.id === metric.eventId);
        if (!event) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_METRIC_EVENT', message: '所属事件不存在' });
        else if (!metric.baseAttributeId) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_METRIC_BASE_ATTRIBUTE', message: '基础指标必须选择度量属性' });
        else {
          const attr = event.attributes.find((a) => a.id === metric.baseAttributeId);
          if (!attr) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_METRIC_BASE_ATTRIBUTE', message: '基础属性不属于当前事件' });
          else if (!attr.isMetric) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_METRIC_BASE_ATTRIBUTE', message: `属性"${attr.attributeName}"不可聚合（未标记生成指标）` });
        }
      }
      if (!metric.defaultOperator) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_METRIC_OPERATOR', message: '基础指标必须选择默认算子' });
      if (metric.caliberFilter) {
        const event = s.events.find((e) => e.id === metric.eventId);
        metric.caliberFilter.conditions.forEach((c) => {
          const attr = event?.attributes.find((a) => a.id === c.attributeId);
          if (!attr) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_CALIBER_ATTRIBUTE', message: `口径引用了不属于当前事件的属性：${c.attributeCode}` });
          else if (!attr.isFilterable) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_CALIBER_ATTRIBUTE', message: `口径引用了不可筛选的属性：${attr.attributeName}` });
          else if (c.op !== 'IS_NULL' && c.op !== 'NOT_NULL' && (!c.values || c.values.length === 0)) issues.push({ severity: 'WARNING', checkCode: 'EMPTY_CALIBER_VALUE', message: `口径条件"${c.attributeCode}"未填写取值` });
        });
      }
    } else {
      const tokens = metric.formulaTokens || [];
      const metricTokens = tokens.filter((t) => t.type === 'METRIC');
      if (metricTokens.length === 0) issues.push({ severity: 'BLOCKER', checkCode: 'EMPTY_FORMULA', message: '复合指标公式必须至少引用一个已发布指标' });
      metricTokens.forEach((t) => {
        const ref = s.metrics.find((m) => m.metricCode === t.metricCode);
        if (!ref) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_FORMULA_REF', message: `公式引用了不存在的指标：${t.metricCode}` });
        else if (ref.id === metric.id) issues.push({ severity: 'BLOCKER', checkCode: 'METRIC_FORMULA_SELF', message: '公式不能引用自身' });
        else if (ref.status !== 'PUBLISHED') issues.push({ severity: 'WARNING', checkCode: 'METRIC_FORMULA_UNPUBLISHED', message: `公式引用了未发布的指标：${ref.metricName}` });
      });
      const visited = new Set<number>();
      const stack = new Set<number>();
      const hasCycle = (mid: number): boolean => {
        if (stack.has(mid)) return true;
        if (visited.has(mid)) return false;
        visited.add(mid); stack.add(mid);
        const node = s.metrics.find((m) => m.id === mid);
        const refs = (node?.formulaTokens || []).filter((t) => t.type === 'METRIC').map((t) => s.metrics.find((m) => m.metricCode === t.metricCode)?.id).filter((x): x is number => !!x);
        for (const r of refs) if (hasCycle(r)) return true;
        stack.delete(mid);
        return false;
      };
      if (metricTokens.length > 0 && hasCycle(metric.id)) issues.push({ severity: 'BLOCKER', checkCode: 'METRIC_FORMULA_CYCLE', message: '公式存在循环依赖' });
    }
    return issues;
  },

  previewMetric: (id) => {
    const s = get();
    const metric = s.metrics.find((x) => x.id === id);
    if (!metric) return { value: undefined, dependencies: [], sql: '' };
    const deps = (metric.formulaTokens || []).filter((t) => t.type === 'METRIC').map((t) => {
      const ref = s.metrics.find((m) => m.metricCode === t.metricCode);
      return ref ? { metricCode: ref.metricCode, metricName: ref.metricName } : { metricCode: t.metricCode || '', metricName: t.metricCode || '' };
    });
    const value = metric.metricCode === 'gmv' ? 1280000 : metric.metricCode === 'valid_sales' ? 980000 : metric.metricCode === 'order_count' ? 8600 : metric.metricCode === 'pay_user_count' ? 6200 : metric.metricCode === 'avg_order_value' ? 149 : undefined;
    const table = metric.eventId === 2 ? 'dwd_refund_detail' : 'dwd_order_detail';
    const where = metric.caliberFilter ? ` AND ${metric.caliberFilter.conditions.map((c) => `${c.attributeCode} ${c.op} (${c.values.join(',')})`).join(' ')}` : '';
    const sql = `SELECT ${metric.defaultOperator || (metric.metricType === 'COMPOSITE' ? 'FORMULA' : 'SUM')}(${metric.baseAttributeCode || metric.formula || '*'}) AS ${metric.metricCode}\nFROM ${table} WHERE 1=1${where}\n-- 示例预览，非真实执行`;
    return { value, dependencies: deps.length ? deps : [{ metricCode: metric.metricCode, metricName: metric.metricName }], sql };
  },

  publishMetric: (id) => {
    const s = get();
    const issues = s.validateMetric(id);
    if (issues.some((i) => i.severity === 'BLOCKER')) return issues;
    set((st) => ({ metrics: st.metrics.map((x) => (x.id === id ? { ...x, status: 'PUBLISHED', version: x.version + 1, publishedAt: '2026-07-20 14:30' } : x)) }));
    return issues;
  },

  addMetricTarget: (t) => set((s) => ({ metricTargets: [...s.metricTargets, { ...t, id: genId() }] })),
  updateMetricTarget: (id, t) => set((s) => ({ metricTargets: s.metricTargets.map((x) => (x.id === id ? { ...x, ...t } : x)) })),
  deleteMetricTarget: (id) => set((s) => ({ metricTargets: s.metricTargets.filter((x) => x.id !== id) })),

  validateMetricTarget: (t, exceptId) => {
    const s = get();
    const issues: ValidationIssue[] = [];
    if (!t.metricId) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_TARGET_METRIC', message: '必须选择指标' });
    if (!t.period) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_TARGET_PERIOD', message: '必须选择周期' });
    if (t.targetValue === undefined || t.targetValue === null) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_TARGET_VALUE', message: '必须填写目标值' });
    if (!t.effectiveStart || !t.effectiveEnd) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_TARGET_DATE', message: '必须填写有效日期' });
    else if (t.effectiveStart > t.effectiveEnd) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_TARGET_DATE', message: '开始日期不能晚于结束日期' });
    const sig = JSON.stringify(t.dimensionFilter || { logic: 'AND', conditions: [] });
    const overlap = s.metricTargets.some((x) =>
      x.id !== exceptId && x.metricId === t.metricId && JSON.stringify(x.dimensionFilter || { logic: 'AND', conditions: [] }) === sig
      && x.effectiveStart <= t.effectiveEnd && x.effectiveEnd >= t.effectiveStart,
    );
    if (overlap) issues.push({ severity: 'WARNING', checkCode: 'METRIC_TARGET_OVERLAP', message: '相同指标、相同维度范围、有效期重叠的目标已存在，发布时将被阻止' });
    return issues;
  },

  addBusinessRule: (r) => {
    const issues: ValidationIssue[] = [];
    set((s) => {
      if (s.businessRules.some((x) => x.ruleCode === r.ruleCode)) {
        issues.push({ severity: 'BLOCKER', checkCode: 'DUPLICATE_RULE_CODE', message: `规则编码"${r.ruleCode}"已存在` });
        return {};
      }
      return { businessRules: [...s.businessRules, { ...r, id: genId() }] };
    });
    return issues;
  },
  updateBusinessRule: (id, r) => set((s) => ({ businessRules: s.businessRules.map((x) => (x.id === id ? { ...x, ...r } : x)) })),
  deleteBusinessRule: (id) => set((s) => ({
    businessRules: s.businessRules.filter((x) => x.id !== id).map((x) => (x.oppositeRuleId === id ? { ...x, oppositeRuleId: undefined } : x)),
    ruleLinks: s.ruleLinks.filter((x) => x.ruleId !== id),
  })),

  validateBusinessRule: (r, id) => {
    const s = get();
    const issues: ValidationIssue[] = [];
    if (!r.ruleCode) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_RULE_CODE', message: '规则编码不能为空' });
    if (!r.ruleName) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_RULE_NAME', message: '规则名称不能为空' });
    const expr = r.expression;
    if (r.mode === 'COMPARISON') {
      const e = expr as ComparisonExpression;
      if (!e.metricId) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_RULE_METRIC', message: 'COMPARISON 必须选择指标' });
      else {
        const ref = s.metrics.find((m) => m.id === e.metricId);
        if (!ref) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_RULE_METRIC', message: '所选指标不存在' });
        else if (ref.status !== 'PUBLISHED') issues.push({ severity: 'WARNING', checkCode: 'RULE_METRIC_UNPUBLISHED', message: '所选指标未发布' });
      }
      if (e.thresholdSource === 'CONSTANT' && (e.thresholdValue === undefined || e.thresholdValue === null)) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_THRESHOLD', message: '阈值来源为常量时必须填写阈值' });
      if (e.thresholdSource === 'METRIC_TARGET' && (e.ratio === undefined || e.ratio === null)) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_RATIO', message: '阈值来源为目标值时必须填写比例' });
    } else if (r.mode === 'FILTER') {
      const e = expr as FilterExpression;
      const entity = s.entities.find((en) => en.id === (e.entityId ?? r.targetObjectId));
      if (!entity) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_FILTER_ENTITY', message: 'FILTER 必须选择有效的主判定实体' });
      e.conditions.forEach((c) => {
        const attr = entity?.attributes.find((a) => a.id === c.attributeId);
        if (!attr) issues.push({ severity: 'BLOCKER', checkCode: 'RULE_FILTER_ATTR_BELONG', message: `条件引用了不属于主判定实体的属性：${c.attributeCode}` });
        else if (!attr.isFilterable) issues.push({ severity: 'BLOCKER', checkCode: 'RULE_FILTER_ATTR_BELONG', message: `条件引用了不可筛选的属性：${attr.attributeName}` });
        else if (c.op !== 'IS_NULL' && c.op !== 'NOT_NULL' && (!c.values || c.values.length === 0)) issues.push({ severity: 'WARNING', checkCode: 'EMPTY_FILTER_VALUE', message: `条件"${c.attributeCode}"未填写取值` });
      });
    } else if (r.mode === 'EXISTENCE') {
      const e = expr as ExistenceExpression;
      if (!e.eventId) issues.push({ severity: 'BLOCKER', checkCode: 'MISSING_EXISTENCE_EVENT', message: 'EXISTENCE 必须选择事件' });
      else {
        const event = s.events.find((ev) => ev.id === e.eventId);
        if (!event) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_EXISTENCE_EVENT', message: '所选事件不存在' });
        else if (!event.attributes.some((a) => ['DATE', 'DATETIME', 'TIME'].includes(a.semanticType))) issues.push({ severity: 'WARNING', checkCode: 'RULE_EXISTENCE_TIME', message: '事件缺少时间属性，时间窗口将失效' });
      }
    } else if (r.mode === 'COMPOSITE') {
      const e = expr as CompositeExpression;
      if (!e.ruleIds || e.ruleIds.length === 0) issues.push({ severity: 'BLOCKER', checkCode: 'EMPTY_COMPOSITE', message: 'COMPOSITE 必须至少选择一个子规则' });
      if (id && e.ruleIds?.includes(id)) issues.push({ severity: 'BLOCKER', checkCode: 'RULE_COMPOSITE_SELF', message: 'COMPOSITE 不能引用自身' });
      e.ruleIds?.forEach((rid) => {
        const ref = s.businessRules.find((b) => b.id === rid);
        if (!ref) issues.push({ severity: 'BLOCKER', checkCode: 'INVALID_COMPOSITE_REF', message: `子规则不存在：#${rid}` });
        else if (ref.status !== 'PUBLISHED') issues.push({ severity: 'WARNING', checkCode: 'COMPOSITE_REF_UNPUBLISHED', message: `子规则未发布：${ref.ruleName}` });
      });
      const visited = new Set<number>(); const stack = new Set<number>();
      const hasCycle = (rid: number): boolean => {
        if (stack.has(rid)) return true;
        if (visited.has(rid)) return false;
        visited.add(rid); stack.add(rid);
        const node = s.businessRules.find((b) => b.id === rid);
        const refs = (node?.mode === 'COMPOSITE' ? (node.expression as CompositeExpression).ruleIds : []) || [];
        for (const x of refs) if (hasCycle(x)) return true;
        stack.delete(rid);
        return false;
      };
      if (id && e.ruleIds.length > 0 && hasCycle(id)) issues.push({ severity: 'BLOCKER', checkCode: 'RULE_COMPOSITE_CYCLE', message: '子规则存在循环依赖' });
    }
    r.triggerWords?.forEach((w) => {
      const conflict = s.businessRules.find((x) => x.id !== id && x.status !== 'INACTIVE' && x.triggerWords.includes(w));
      if (conflict) issues.push({ severity: 'WARNING', checkCode: 'TRIGGER_WORD_CONFLICT', message: `触发词"${w}"已被规则"${conflict.ruleName}"占用` });
    });
    if (r.oppositeRuleId) {
      const opp = s.businessRules.find((b) => b.id === r.oppositeRuleId);
      if (!opp) issues.push({ severity: 'WARNING', checkCode: 'OPPOSITE_NOT_FOUND', message: '对立规则不存在' });
      else if (opp.targetObjectType !== r.targetObjectType || opp.targetObjectId !== r.targetObjectId) issues.push({ severity: 'WARNING', checkCode: 'OPPOSITE_NOT_COMPARABLE', message: '对立规则的主判定对象不一致，可能不可比较' });
    }
    return issues;
  },

  previewRule: (r) => {
    const actual = 23000;
    const threshold = 28500;
    const verdict: RulePreviewResult['verdict'] = r.mode === 'COMPARISON' ? (actual < threshold ? 'FAIL' : 'PASS') : 'UNKNOWN';
    return {
      verdict, actual: r.mode === 'COMPARISON' ? actual : undefined, threshold: r.mode === 'COMPARISON' ? threshold : undefined,
      matchedObjectCount: r.mode === 'FILTER' ? 128 : r.mode === 'EXISTENCE' ? 42 : 0,
      logicForm: `${r.mode}(${r.targetObjectName || r.targetObjectId}) -> ${JSON.stringify(r.expression).slice(0, 80)}`,
      sql: `-- 规则 ${r.ruleCode} 预览（示例，非真实执行）\nSELECT CASE WHEN ${r.mode === 'COMPARISON' ? 'metric_value < threshold' : '1=1'} THEN 'FAIL' ELSE 'PASS' END AS verdict\nFROM semantic_runtime WHERE target = '${r.targetObjectName || r.targetObjectId}'`,
    };
  },

  publishBusinessRule: (id) => {
    const s = get();
    const rule = s.businessRules.find((x) => x.id === id);
    if (!rule) return [{ severity: 'BLOCKER', checkCode: 'RULE_NOT_FOUND', message: '规则不存在' }];
    const issues = s.validateBusinessRule({ ...rule }, id);
    if (issues.some((i) => i.severity === 'BLOCKER')) return issues;
    set((st) => ({ businessRules: st.businessRules.map((x) => (x.id === id ? { ...x, status: 'PUBLISHED', version: x.version + 1, publishedAt: '2026-07-20 14:30', lastValidation: { blockers: 0, warnings: issues.filter((i) => i.severity === 'WARNING').length, issues } } : x)) }));
    return issues;
  },

  setBusinessRuleOpposite: (ruleId, oppositeId) => set((s) => {
    const prev = s.businessRules.find((x) => x.id === ruleId)?.oppositeRuleId;
    let rules = s.businessRules.map((x) => {
      if (x.id === ruleId) return { ...x, oppositeRuleId: oppositeId };
      if (prev && x.id === prev) return { ...x, oppositeRuleId: undefined };
      return x;
    });
    if (oppositeId) rules = rules.map((x) => (x.id === oppositeId ? { ...x, oppositeRuleId: ruleId } : x));
    return { businessRules: rules };
  }),

  addRuleLink: (ownerType, ownerId, ruleId) => set((s) => {
    if (s.ruleLinks.some((l) => l.ownerType === ownerType && l.ownerId === ownerId && l.ruleId === ruleId)) return {};
    return { ruleLinks: [...s.ruleLinks, { id: genId(), ownerType, ownerId, ruleId }] };
  }),
  deleteRuleLink: (linkId) => set((s) => ({ ruleLinks: s.ruleLinks.filter((l) => l.id !== linkId) })),
  });
});

const workspaceDataKeys: Array<keyof StoreDataSlices> = [
  'calendars', 'rules', 'holidayCalendars', 'holidayPeriods', 'entities', 'events',
  'entityHierarchies', 'objectValues', 'semanticRelations', 'schemaMappings',
  'fieldMappings', 'metrics', 'aliases', 'terms', 'unknownTerms', 'metricTargets',
  'businessRules', 'ruleLinks',
];

export function readStoreData(): StoreDataSlices {
  const state = useStore.getState();
  return structuredClone(Object.fromEntries(workspaceDataKeys.map((key) => [key, state[key]])) as StoreDataSlices);
}

export function replaceStoreData(data: StoreDataSlices) {
  useStore.setState(structuredClone(data));
}

export const defaultStoreData = readStoreData();
