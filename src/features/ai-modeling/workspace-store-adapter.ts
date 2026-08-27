import type {
  BusinessRule, EntityHierarchyLevel, FieldMapping, HolidayCalendar, HolidayPeriod,
  MetricTarget, ObjectAttribute, ObjectValue, RuleLink, SchemaMapping, SemanticEntity,
  SemanticEvent, SemanticMetric, SemanticRelation, SemanticType, TermAlias, SemanticTerm,
  TimeCalendar, TimeRule, UnknownTerm,
} from '../../types/index.ts';
import type { LinguanField, LinguanModelSnapshot, LinguanTable } from './types.ts';
import type { Metric2WorkspaceData, ObjAttribute } from '../../pages/metric2/mockData.ts';

export type LinguanWorkspaceData = {
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

function semanticType(value: string): SemanticType {
  const supported: SemanticType[] = [
    'AMOUNT', 'NUMBER', 'RATIO', 'DATE', 'DATETIME', 'TIME', 'BOOLEAN', 'TEXT',
    'CODE', 'ENUM', 'HIERARCHY', 'LIFECYCLE', 'ID', 'FOREIGN_KEY',
  ];
  return supported.includes(value as SemanticType) ? value as SemanticType : 'TEXT';
}

function stableId(key: string) {
  let hash = 2166136261;
  for (let index = 0; index < key.length; index += 1) {
    hash ^= key.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return Math.abs(hash) || 1;
}

function aliasTexts(snapshot: LinguanModelSnapshot, targetType: string, targetId: string, ownerId?: string) {
  return snapshot.aliases
    .filter((alias) => alias.targetType === targetType && alias.targetId === targetId && (!ownerId || alias.ownerId === ownerId))
    .map((alias) => alias.text);
}

function attribute(snapshot: LinguanModelSnapshot, field: LinguanField): ObjectAttribute {
  return {
    id: field.id,
    attributeCode: field.code,
    attributeName: field.name,
    synonyms: aliasTexts(snapshot, 'FIELD', field.code, field.ownerTableCode),
    dataType: field.dataType.toUpperCase(),
    semanticType: semanticType(field.semanticType),
    isFilterable: field.filterable,
    isGroupable: field.groupable,
    isDisplay: field.role === 'attribute',
    isPrimaryTime: field.role === 'event_time',
    isMetric: field.role === 'measure',
    ...(field.role === 'measure' ? { aggregateOperator: 'SUM' as const } : {}),
    ...(field.unit ? { unit: field.unit } : {}),
  };
}

function uniqueAttributeId(table: LinguanTable) {
  const businessKey = table.businessKeys[0];
  return table.fields.find((field) => field.code === businessKey)?.id
    ?? table.fields.find((field) => field.role === 'business_key' || field.role === 'surrogate_key')?.id;
}

function entity(snapshot: LinguanModelSnapshot, table: LinguanTable): SemanticEntity {
  return {
    id: table.id,
    entityCode: table.code,
    entityName: table.name,
    synonyms: aliasTexts(snapshot, 'ENTITY', table.code),
    description: table.description,
    owner: 'AI 建模',
    uniqueIdentifierAttributeId: uniqueAttributeId(table),
    status: 'ACTIVE',
    attributes: table.fields.map((field) => attribute(snapshot, field)),
  };
}

function event(snapshot: LinguanModelSnapshot, table: LinguanTable): SemanticEvent {
  return {
    id: table.id,
    eventCode: table.code,
    eventName: table.name,
    synonyms: aliasTexts(snapshot, 'EVENT', table.code),
    description: table.description,
    owner: 'AI 建模',
    uniqueIdentifierAttributeId: uniqueAttributeId(table),
    defaultTimeAttributeId: table.fields.find((field) => field.role === 'event_time')?.id,
    status: 'ACTIVE',
    attributes: table.fields.map((field) => attribute(snapshot, field)),
  };
}

function relationType(source: LinguanTable, target: LinguanTable): SemanticRelation['relationType'] {
  if (source.kind === 'EVENT' && target.kind === 'ENTITY') return 'EVENT_ENTITY';
  if (source.kind === 'ENTITY' && target.kind === 'ENTITY') return 'ENTITY_ENTITY';
  return 'EVENT_EVENT';
}

function cardinality(value: string): SemanticRelation['cardinality'] {
  const normalized = value.toUpperCase() as SemanticRelation['cardinality'];
  return ['ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_ONE', 'MANY_TO_MANY'].includes(normalized)
    ? normalized : 'MANY_TO_ONE';
}

function fieldRole(field: LinguanField): FieldMapping['fieldRole'] {
  if (field.role === 'surrogate_key') return 'PRIMARY_KEY';
  if (field.role === 'foreign_key' || field.role === 'business_key') return 'JOIN_KEY';
  if (field.role === 'event_time') return 'TIME';
  if (field.role === 'measure') return 'MEASURE';
  if (field.groupable || field.filterable) return 'DIMENSION';
  return 'TEXT';
}

function metric(snapshot: LinguanModelSnapshot, value: LinguanModelSnapshot['metrics'][number]): SemanticMetric {
  const eventTable = snapshot.events.find((table) => table.code === value.eventTableCode);
  const baseField = eventTable?.fields.find((field) => field.code === value.measureFieldCodes[0]);
  const simple = value.editability === 'EDITABLE';
  return {
    id: value.id,
    metricCode: value.code,
    metricName: value.name,
    metricType: simple ? 'BASIC' : 'COMPOSITE',
    description: value.description,
    unit: value.unit ?? undefined,
    owner: 'AI 建模',
    version: Number(snapshot.modelVersion.slice(1)),
    status: 'PUBLISHED',
    eventId: eventTable?.id,
    eventCode: eventTable?.code ?? value.eventTableCode,
    eventName: eventTable?.name ?? value.eventTableName,
    ...(simple ? {
      baseAttributeId: baseField?.id,
      baseAttributeCode: baseField?.code,
      defaultOperator: `$${value.aggregation.toLowerCase()}`,
    } : { formula: value.formula }),
    editability: value.editability,
    compatibilityReason: value.compatibilityReason,
    ruleIds: [],
  };
}

export function projectLinguanWorkspaceData(snapshot: LinguanModelSnapshot): LinguanWorkspaceData {
  const entities = snapshot.entities.map((table) => entity(snapshot, table));
  const events = snapshot.events.map((table) => event(snapshot, table));
  const tableByCode = new Map(snapshot.tables.map((table) => [table.code, table]));
  const semanticRelations = snapshot.relations.map<SemanticRelation>((value) => {
    const source = tableByCode.get(value.from.tableCode);
    const target = tableByCode.get(value.to.tableCode);
    if (!source || !target) throw new Error(`工作空间关系端点不存在：${value.code}`);
    return {
      id: value.id,
      relationCode: value.code,
      relationName: value.name,
      relationType: relationType(source, target),
      sourceType: source.kind,
      sourceId: source.id,
      targetType: target.kind,
      targetId: target.id,
      relationRole: value.kind,
      sourceAttributeId: source.fields.find((field) => field.code === value.from.fieldCode)?.id,
      targetAttributeId: target.fields.find((field) => field.code === value.to.fieldCode)?.id,
      cardinality: cardinality(value.cardinality),
      direction: 'FORWARD',
      priority: 1,
      joinType: 'LEFT',
      required: false,
      status: 'ACTIVE',
    };
  });
  const schemaMappings: SchemaMapping[] = snapshot.tables.flatMap((table) => (table.physicalMappings?.length
    ? table.physicalMappings.map<SchemaMapping>((mapping, index) => ({
      id: stableId(`SCHEMA_MAPPING:${table.code}:${index}`), objectType: table.kind, objectId: table.id,
      datasourceName: mapping.datasourceName, schemaName: mapping.schemaName, tableName: mapping.tableName, tableAlias: table.name,
    }))
    : [{ id: table.id, objectType: table.kind, objectId: table.id, datasourceName: '待配置', schemaName: '待配置', tableName: table.code, tableAlias: table.name }]));
  const fieldMappings = snapshot.tables.flatMap((table) => table.fields.map<FieldMapping>((field) => ({
    id: field.id,
    objectType: table.kind,
    objectId: table.id,
    attributeId: field.id,
    physicalFieldName: field.code,
    physicalDataType: field.dataType,
    fieldRole: fieldRole(field),
  })));
  const entityHierarchies = snapshot.hierarchies.flatMap((hierarchy) => {
    const table = snapshot.tables.find((item) => item.code === hierarchy.ownerTableCode);
    const field = table?.fields.find((item) => item.code === hierarchy.attributeCode);
    if (!table || !field) throw new Error(`成员层级归属无法解析：${hierarchy.id}`);
    return hierarchy.levels.map<EntityHierarchyLevel>((level) => ({
      id: stableId(`HIERARCHY_LEVEL:${hierarchy.id}:${level.level_id}`),
      objectType: table.kind, objectId: table.id, attributeId: field.id,
      hierarchyCode: hierarchy.id, hierarchyName: hierarchy.name,
      levelCode: level.level_id, levelName: level.name, levelDepth: level.depth,
      ...(level.parent_level_id ? { parentLevelId: stableId(`HIERARCHY_LEVEL:${hierarchy.id}:${level.parent_level_id}`) } : {}),
      status: 'ACTIVE',
    }));
  });
  const objectValues = snapshot.hierarchies.flatMap((hierarchy) => {
    const table = snapshot.tables.find((item) => item.code === hierarchy.ownerTableCode);
    const field = table?.fields.find((item) => item.code === hierarchy.attributeCode);
    if (!table || !field) throw new Error(`成员层级成员归属无法解析：${hierarchy.id}`);
    return hierarchy.members.map<ObjectValue>((member, index) => ({
      id: stableId(`HIERARCHY_MEMBER:${hierarchy.id}:${member.member_id}`),
      objectType: table.kind, objectId: table.id, attributeId: field.id,
      hierarchyLevelId: stableId(`HIERARCHY_LEVEL:${hierarchy.id}:${member.level_id}`),
      businessName: member.name, physicalCode: member.code,
      ...(member.parent_member_id ? { parentValueId: stableId(`HIERARCHY_MEMBER:${hierarchy.id}:${member.parent_member_id}`) } : {}),
      sortOrder: index + 1, sourceType: 'MANUAL', sourceKey: member.member_id, status: 'ACTIVE',
    }));
  });
  const targetIndex = new Map<string, { id: number; code: string; name: string }>();
  snapshot.entities.forEach((item) => targetIndex.set(`ENTITY:${item.code}`, { id: item.id, code: item.code, name: item.name }));
  snapshot.events.forEach((item) => targetIndex.set(`EVENT:${item.code}`, { id: item.id, code: item.code, name: item.name }));
  snapshot.fields.forEach((item) => targetIndex.set(`FIELD:${item.ownerTableCode}:${item.code}`, { id: item.id, code: item.code, name: `${item.ownerTableName}.${item.name}` }));
  snapshot.metrics.forEach((item) => targetIndex.set(`METRIC:${item.code}`, { id: item.id, code: item.code, name: item.name }));
  snapshot.dimensions.forEach((item) => targetIndex.set(`DIMENSION:${item.id}`, { id: stableId(`DIMENSION:${item.id}`), code: item.id, name: item.name }));
  snapshot.ruleCandidates.forEach((item) => targetIndex.set(`RULE:${item.id}`, { id: stableId(`RULE:${item.id}`), code: item.id, name: item.name }));
  const aliases = snapshot.aliases.map<TermAlias>((alias) => {
    const lookup = alias.targetType === 'FIELD' ? `FIELD:${alias.ownerId}:${alias.targetId}` : `${alias.targetType}:${alias.targetId}`;
    const target = targetIndex.get(lookup);
    if (!target) throw new Error(`同义词目标无法解析：${alias.id}`);
    return {
      id: stableId(`ALIAS:${alias.id}`), aliasText: alias.text,
      targetType: alias.targetType === 'FIELD' ? 'ATTRIBUTE' : alias.targetType,
      targetId: target.id, targetCode: target.code, targetName: target.name,
      priority: Math.round(alias.confidence * 100), source: 'IMPORT', isConfirmed: true, conflictStatus: 'NONE',
      sourceObjectCode: alias.id,
    };
  });
  const calendars = (snapshot.timeSemantics?.calendars ?? []).map<TimeCalendar>((calendar) => ({
    id: stableId(`CALENDAR:${calendar.code}`), calendarCode: calendar.code, calendarName: calendar.name,
    calendarType: calendar.type, yearStartMonth: 1, yearStartDay: 1, description: calendar.description, status: 'ACTIVE',
  }));
  const timeRules = (snapshot.timeSemantics?.rules ?? []).map<TimeRule>((rule, index) => ({
    id: stableId(`TIME_RULE:${rule.code}`), ruleCode: rule.code, ruleText: rule.text, calendarCode: rule.calendarCode,
    granularity: rule.granularity, rangeType: rule.rangeType ?? 'CURRENT', offsetValue: rule.offsetValue ?? 0,
    ...(rule.rollingDays !== undefined ? { rollingDays: rule.rollingDays } : {}),
    ...(rule.holidayCode ? { holidayCode: rule.holidayCode } : {}),
    ...(rule.offsetStart !== undefined ? { offsetStart: rule.offsetStart } : {}),
    ...(rule.offsetEnd !== undefined ? { offsetEnd: rule.offsetEnd } : {}),
    ...(rule.fixedStartDate ? { fixedStartDate: rule.fixedStartDate } : {}),
    ...(rule.fixedEndDate ? { fixedEndDate: rule.fixedEndDate } : {}),
    ...(rule.isRecurring !== undefined ? { isRecurring: rule.isRecurring } : {}),
    priority: index + 1, participateInReasoning: true,
    isAutoGenerated: rule.source === 'SYSTEM_TEMPLATE', status: 'ACTIVE',
  }));
  return {
    calendars,
    rules: timeRules,
    holidayCalendars: [],
    holidayPeriods: [],
    entities,
    events,
    entityHierarchies,
    objectValues,
    semanticRelations,
    schemaMappings,
    fieldMappings,
    metrics: snapshot.metrics.map((value) => metric(snapshot, value)),
    aliases,
    terms: [],
    unknownTerms: [],
    metricTargets: [],
    businessRules: [],
    ruleLinks: [],
  };
}

function metric2Attribute(field: LinguanField): ObjAttribute {
  const dataType = field.dataType === 'timestamp' || field.dataType === 'date' ? 'DATETIME'
    : field.dataType === 'integer' ? 'NUMBER'
      : field.dataType === 'currency' ? 'DECIMAL'
        : field.dataType === 'status' || field.dataType === 'category' ? 'ENUM' : 'STRING';
  const semanticType = field.role === 'surrogate_key' || field.role === 'business_key' ? 'IDENTIFIER'
    : field.role === 'measure' ? 'MEASURE'
      : field.role === 'event_time' ? 'TIME'
        : field.filterable || field.groupable ? 'DIMENSION' : 'TEXT';
  return {
    attributeCode: field.code,
    attributeName: field.name,
    dataType,
    semanticType,
    isFilterable: field.filterable,
    isMetric: field.role === 'measure',
    ...(field.role === 'measure' ? { aggregateOperator: 'SUM' as const } : {}),
  };
}

export function projectMetric2WorkspaceData(snapshot: LinguanModelSnapshot): Metric2WorkspaceData {
  const events = snapshot.events.map((table) => ({
    id: table.id,
    eventCode: table.code,
    eventName: table.name,
    attributes: table.fields.map(metric2Attribute),
  }));
  const entities = snapshot.entities.map((table) => ({
    id: table.id,
    entityCode: table.code,
    entityName: table.name,
    attributes: table.fields.map(metric2Attribute),
  }));
  const tableByCode = new Map(snapshot.tables.map((table) => [table.code, table]));
  const relations = snapshot.relations.map((relation) => {
    const source = tableByCode.get(relation.from.tableCode)!;
    const target = tableByCode.get(relation.to.tableCode)!;
    return {
      id: relation.id,
      sourceType: source.kind,
      sourceId: source.id,
      targetType: target.kind,
      targetId: target.id,
      relationRole: relation.kind,
    };
  });
  const metrics = snapshot.metrics.map((value) => {
    const event = snapshot.events.find((table) => table.code === value.eventTableCode);
    return {
      id: value.id,
      metricCode: value.code,
      metricName: value.name,
      metricType: value.editability === 'EDITABLE' ? 'BASIC' as const : 'COMPOSITE' as const,
      unit: value.unit ?? undefined,
      description: value.description,
      eventId: event?.id,
      eventName: event?.name,
      baseAttributeCode: value.measureFieldCodes[0],
      defaultOperator: value.aggregation,
      formula: value.formula,
      editability: value.editability,
      compatibilityReason: value.compatibilityReason,
      isAutoGenerated: true,
      status: 'ACTIVE' as const,
    };
  });
  const ruleCandidates = snapshot.ruleCandidates.map((rule) => {
    const metricTarget = snapshot.metrics.find((item) => item.code === rule.targetId);
    const eventTarget = snapshot.events.find((item) => item.code === (rule.ownerId ?? rule.targetId));
    const entityTarget = snapshot.entities.find((item) => item.code === (rule.ownerId ?? rule.targetId));
    const target = metricTarget ?? eventTarget ?? entityTarget;
    const targetObjectType = metricTarget ? 'METRIC' as const : eventTarget ? 'EVENT' as const : entityTarget ? 'ENTITY' as const : undefined;
    return {
      id: stableId(`RULE_CANDIDATE:${rule.id}`), ruleCode: rule.id, ruleName: rule.name,
      description: rule.description, targetObjectType, targetObjectId: target?.id, targetObjectName: target?.name,
      evidenceRefs: [...rule.evidenceIds], status: target ? 'NEEDS_STRUCTURE' as const : 'UNRESOLVED_TARGET' as const,
    };
  });
  return { events, entities, relations, metrics, rules: [], ruleCandidates, targets: [] };
}
