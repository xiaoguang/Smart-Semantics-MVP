import { adaptFixtureDocument } from './adapter.ts';
import { groupRetailScenarioFixture } from './group-retail-fixture.ts';

export function adaptGroupRetailPublishedModel() {
  const snapshot = adaptFixtureDocument(groupRetailScenarioFixture, 'v1', 'V1');
  const mappings = (code: string) => [{
    datasourceName: 'group_retail_core', schemaName: 'retail', tableName: code.replace(/^(entity|event)_/, ''),
  }];
  return {
    ...snapshot,
    projectionRevision: 1,
    tables: snapshot.tables.map((table) => ({ ...table, physicalMappings: mappings(table.code) })),
    entities: snapshot.entities.map((table) => ({ ...table, physicalMappings: mappings(table.code) })),
    events: snapshot.events.map((table) => ({ ...table, physicalMappings: mappings(table.code) })),
    timeSemantics: {
      calendars: [{
        code: 'group_retail_business_calendar', name: '集团零售营业日历', type: 'RETAIL_445' as const,
        description: '统一集团、区域公司和门店的营业日及经营周口径。',
      }],
      rules: [
        { code: 'order_business_time', text: '销售订单按支付完成时间归属营业日', calendarCode: 'group_retail_business_calendar', granularity: 'DAY' as const },
        { code: 'inventory_snapshot_time', text: '库存读取营业日最后一次有效快照', calendarCode: 'group_retail_business_calendar', granularity: 'DAY' as const },
        { code: 'search_event_time', text: '搜索转化按事件发生时间归属营业日', calendarCode: 'group_retail_business_calendar', granularity: 'DAY' as const },
      ],
    },
    compatibilityNotes: [
      'MySQL提供物理表映射，其他来源通过证据和血缘关联到相同模型对象。',
      'Elasticsearch与Semantica中的派生内容保留原始来源，不作为独立业务真相。',
      '规则继续作为只读候选，不进入可执行规则列表。',
    ],
  };
}
