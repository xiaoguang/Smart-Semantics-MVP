import { adaptFixtureDocument } from './adapter.ts';
import { omnichannelScenarioFixture } from './omnichannel-fixture.ts';

export function adaptOmnichannelPublishedModel() {
  const snapshot = adaptFixtureDocument(omnichannelScenarioFixture, 'v1', 'V1');
  return {
    ...snapshot,
    projectionRevision: 1,
    tables: snapshot.tables.map((table) => ({
      ...table,
      physicalMappings: [
        { datasourceName: 'retail_core_prod', schemaName: 'retail_core', tableName: table.code.replace(/^(entity|event)_/, '') },
        { datasourceName: 'retail_analytics_prod', schemaName: 'analytics', tableName: `dim_fact_${table.code.replace(/^(entity|event)_/, '')}` },
      ],
    })),
    entities: snapshot.entities.map((table) => ({
      ...table,
      physicalMappings: [
        { datasourceName: 'retail_core_prod', schemaName: 'retail_core', tableName: table.code.replace('entity_', '') },
        { datasourceName: 'retail_analytics_prod', schemaName: 'analytics', tableName: `dim_${table.code.replace('entity_', '')}` },
      ],
    })),
    events: snapshot.events.map((table) => ({
      ...table,
      physicalMappings: [
        { datasourceName: 'retail_core_prod', schemaName: 'retail_core', tableName: table.code.replace('event_', '') },
        { datasourceName: 'retail_analytics_prod', schemaName: 'analytics', tableName: `fact_${table.code.replace('event_', '')}` },
      ],
    })),
    timeSemantics: {
      calendars: [{ code: 'retail_business_calendar', name: '零售营业日历', type: 'RETAIL_445' as const, description: '统一门店营业日和经营周口径。' }],
      rules: [
        { code: 'order_business_time', text: '订单按支付完成时间归属业务日期', calendarCode: 'retail_business_calendar', granularity: 'DAY' as const },
        { code: 'inventory_snapshot_time', text: '库存量读取业务日最后一次快照', calendarCode: 'retail_business_calendar', granularity: 'DAY' as const },
        { code: 'promotion_validity_time', text: '促销核销必须落在活动有效期内', calendarCode: 'retail_business_calendar', granularity: 'DAY' as const },
      ],
    },
    compatibilityNotes: [
      '物理映射来自 MySQL 与 Snowflake 固定快照。',
      '规则作为只读候选展示，不进入可执行规则列表。',
      '成员层级和同义词保留全部来源定位。',
    ],
  };
}
