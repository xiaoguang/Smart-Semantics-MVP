import { sourceFamilyLabel } from '../ai-modeling/source-bundle.ts';
import type { ConnectorFamilyDefinition, ConnectorTypeDefinition, FormField, FormSchema } from './types.ts';

const section = (id: string, title: string, fields: FormField[]): FormSchema => ({ sections: [{ id, title, fields }] });
const endpointFields = (label = '服务地址'): FormField[] => [
  { key: 'endpoint', label, type: 'text', required: true, placeholder: 'service.example.internal' },
  { key: 'credentialRef', label: '凭据设置', type: 'credential-ref', placeholder: '安全凭据标识', help: '只保存安全设置，不保存密码或令牌。' },
];
const commonScope = (label: string): FormSchema => section('scope', '读取范围', [
  { key: 'includes', label, type: 'tags', required: true, placeholder: '输入范围后回车' },
  { key: 'excludes', label: '排除项', type: 'tags', placeholder: '可选' },
]);

export const connectorTypeCatalog: ConnectorTypeDefinition[] = [
  {
    connectorTypeId: 'mysql', family: 'RELATIONAL', vendor: 'MySQL', displayName: 'MySQL', protocols: ['JDBC_SQL'], maturity: 'GA',
    capabilities: ['PHYSICAL_SCHEMA', 'DATA_PROFILE', 'ACTUAL_QUERY'],
    configSchema: section('connection', '连接信息', [...endpointFields('主机'), { key: 'port', label: '端口', type: 'number', required: true }, { key: 'database', label: '数据库', type: 'text', required: true }]),
    scopeSchema: section('scope', '读取范围', [{ key: 'schemas', label: 'Schema', type: 'tags', required: true }, { key: 'tables', label: '表匹配', type: 'tags', required: true }]),
  },
  {
    connectorTypeId: 'postgresql', family: 'RELATIONAL', vendor: 'PostgreSQL', displayName: 'PostgreSQL', protocols: ['JDBC_SQL'], maturity: 'GA',
    capabilities: ['PHYSICAL_SCHEMA', 'DATA_PROFILE', 'ACTUAL_QUERY'],
    configSchema: section('connection', '连接信息', [...endpointFields('主机'), { key: 'port', label: '端口', type: 'number', required: true }, { key: 'database', label: '数据库', type: 'text', required: true }]),
    scopeSchema: section('scope', '读取范围', [{ key: 'schemas', label: 'Schema', type: 'tags', required: true }, { key: 'tables', label: '表匹配', type: 'tags', required: true }]),
  },
  {
    connectorTypeId: 'snowflake', family: 'WAREHOUSE_LAKEHOUSE', vendor: 'Snowflake', displayName: 'Snowflake', protocols: ['SNOWFLAKE_SQL'], maturity: 'BETA',
    capabilities: ['PHYSICAL_SCHEMA', 'DATA_PROFILE', 'ACTUAL_QUERY', 'LINEAGE'], configSchema: section('connection', '连接信息', [...endpointFields('账户地址'), { key: 'warehouse', label: '计算仓', type: 'text', required: true }, { key: 'database', label: '数据库', type: 'text', required: true }]), scopeSchema: commonScope('Schema／视图'),
  },
  {
    connectorTypeId: 'mongodb', family: 'DOCUMENT_DATABASE', vendor: 'MongoDB', displayName: 'MongoDB', protocols: ['MONGO_API'], maturity: 'GA',
    capabilities: ['PHYSICAL_SCHEMA', 'DATA_PROFILE', 'ACTUAL_QUERY'], configSchema: section('connection', '连接信息', [...endpointFields('集群地址'), { key: 'database', label: '数据库', type: 'text', required: true }]), scopeSchema: commonScope('Collection'),
  },
  {
    connectorTypeId: 'redis', family: 'KEY_VALUE', vendor: 'Redis', displayName: 'Redis', protocols: ['RESP'], maturity: 'BETA',
    capabilities: ['DATA_PROFILE', 'ACTUAL_QUERY'], configSchema: section('connection', '连接信息', [...endpointFields('服务地址'), { key: 'database', label: '逻辑库', type: 'number', required: true }]), scopeSchema: commonScope('Key 模式'),
  },
  {
    connectorTypeId: 'cassandra', family: 'WIDE_COLUMN_TIME_SERIES', vendor: 'Cassandra', displayName: 'Cassandra', protocols: ['CQL'], maturity: 'BETA',
    capabilities: ['PHYSICAL_SCHEMA', 'DATA_PROFILE', 'EVENT_SCHEMA'], configSchema: section('connection', '连接信息', [...endpointFields('Contact Points'), { key: 'keyspace', label: 'Keyspace', type: 'text', required: true }]), scopeSchema: commonScope('表／Measurement'),
  },
  {
    connectorTypeId: 'semantica', family: 'GRAPH', vendor: 'Semantica', displayName: 'Semantica', protocols: ['SPARQL'], maturity: 'GA',
    capabilities: ['BUSINESS_DEFINITION', 'VOCABULARY', 'LINEAGE'], configSchema: section('connection', '图连接', [...endpointFields('图服务地址'), { key: 'mode', label: '图模式', type: 'select', required: true, options: [{ label: '属性图（节点／关系）', value: 'PROPERTY_GRAPH' }, { label: 'RDF／SPARQL（三元组／本体）', value: 'RDF_GRAPH' }] }]), scopeSchema: commonScope('图／命名图'),
  },
  {
    connectorTypeId: 'elasticsearch', family: 'SEARCH_INDEX', vendor: 'Elasticsearch', displayName: 'Elasticsearch', protocols: ['ELASTIC_REST'], maturity: 'GA',
    capabilities: ['PHYSICAL_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE'], configSchema: section('connection', '连接信息', endpointFields('集群地址')), scopeSchema: commonScope('索引模式'),
  },
  {
    connectorTypeId: 'milvus', family: 'VECTOR_DATABASE', vendor: 'Milvus', displayName: 'Milvus', protocols: ['MILVUS_API'], maturity: 'BETA',
    capabilities: ['PHYSICAL_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE'], configSchema: section('connection', '连接信息', endpointFields('服务地址')), scopeSchema: commonScope('Collection'),
  },
  {
    connectorTypeId: 'minio', family: 'OBJECT_STORAGE', vendor: 'MinIO', displayName: 'MinIO', protocols: ['S3_API'], maturity: 'GA',
    capabilities: ['BUSINESS_DEFINITION', 'DATA_PROFILE', 'AUXILIARY_CONTENT'], configSchema: section('connection', '连接信息', [...endpointFields('S3 Endpoint'), { key: 'bucket', label: 'Bucket', type: 'text', required: true }]), scopeSchema: commonScope('对象前缀'),
  },
  {
    connectorTypeId: 'sharepoint', family: 'ENTERPRISE_CONTENT', vendor: 'SharePoint', displayName: 'SharePoint', protocols: ['MICROSOFT_GRAPH'], maturity: 'GA',
    capabilities: ['BUSINESS_DEFINITION', 'VOCABULARY', 'AUXILIARY_CONTENT'], configSchema: section('connection', '连接信息', [...endpointFields('站点地址'), { key: 'site', label: '站点', type: 'text', required: true }]), scopeSchema: commonScope('目录／文件类型'),
  },
  {
    connectorTypeId: 'github', family: 'CODE_REPOSITORY', vendor: 'GitHub', displayName: 'GitHub', protocols: ['GIT_REST'], maturity: 'GA',
    capabilities: ['PHYSICAL_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE', 'VOCABULARY'], configSchema: section('connection', '仓库信息', [{ key: 'repository', label: '仓库', type: 'text', required: true, placeholder: 'org/repository' }, { key: 'ref', label: '分支或Commit', type: 'text', required: true }, { key: 'credentialRef', label: '凭据设置', type: 'credential-ref' }]), scopeSchema: commonScope('路径模式'),
  },
  {
    connectorTypeId: 'kafka', family: 'EVENT_STREAM', vendor: 'Kafka', displayName: 'Kafka', protocols: ['KAFKA'], maturity: 'GA',
    capabilities: ['EVENT_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE'], configSchema: section('connection', '连接信息', endpointFields('Bootstrap Servers')), scopeSchema: commonScope('Topic 模式'),
  },
];

/** Family cards are generic; only their child adapters own instance forms. */
export const connectorFamilyCatalog: ConnectorFamilyDefinition[] = [
  { familyId: 'RELATIONAL', displayName: '关系型数据库', adapterTypeIds: ['mysql', 'postgresql'], capabilitySummary: '读取结构、关系与查询口径' },
  { familyId: 'WAREHOUSE_LAKEHOUSE', displayName: sourceFamilyLabel('WAREHOUSE_LAKEHOUSE'), adapterTypeIds: ['snowflake'], capabilitySummary: '读取数仓结构、使用与血缘' },
  { familyId: 'DOCUMENT_DATABASE', displayName: sourceFamilyLabel('DOCUMENT_DATABASE'), adapterTypeIds: ['mongodb'], capabilitySummary: '读取集合结构与查询口径' },
  { familyId: 'KEY_VALUE', displayName: sourceFamilyLabel('KEY_VALUE'), adapterTypeIds: ['redis'], capabilitySummary: '读取键空间与使用摘要' },
  { familyId: 'WIDE_COLUMN_TIME_SERIES', displayName: sourceFamilyLabel('WIDE_COLUMN_TIME_SERIES'), adapterTypeIds: ['cassandra'], capabilitySummary: '读取宽列表与时序结构' },
  { familyId: 'GRAPH', displayName: '属性图／RDF 图谱', adapterTypeIds: ['semantica'], capabilitySummary: '读取术语、关系与血缘' },
  { familyId: 'SEARCH_INDEX', displayName: sourceFamilyLabel('SEARCH_INDEX'), adapterTypeIds: ['elasticsearch'], capabilitySummary: '读取索引结构与检索口径' },
  { familyId: 'VECTOR_DATABASE', displayName: sourceFamilyLabel('VECTOR_DATABASE'), adapterTypeIds: ['milvus'], capabilitySummary: '读取集合与向量检索结构' },
  { familyId: 'OBJECT_STORAGE', displayName: sourceFamilyLabel('OBJECT_STORAGE'), adapterTypeIds: ['minio'], capabilitySummary: '读取对象、文档与附属资料' },
  { familyId: 'ENTERPRISE_CONTENT', displayName: sourceFamilyLabel('ENTERPRISE_CONTENT'), adapterTypeIds: ['sharepoint'], capabilitySummary: '读取业务文档与制度资料' },
  { familyId: 'CODE_REPOSITORY', displayName: sourceFamilyLabel('CODE_REPOSITORY'), adapterTypeIds: ['github'], capabilitySummary: '读取源码、迁移与词汇记录' },
  { familyId: 'EVENT_STREAM', displayName: sourceFamilyLabel('EVENT_STREAM'), adapterTypeIds: ['kafka'], capabilitySummary: '读取事件结构与处理口径' },
];

export const defaultRetailConnectorTypeIds = ['mysql', 'mongodb', 'elasticsearch', 'semantica', 'minio', 'github', 'sharepoint', 'kafka'] as const;
export const optionalRetailConnectorTypeIds = ['snowflake', 'redis', 'milvus', 'cassandra'] as const;

export function connectorTypeById(connectorTypeId: string) {
  return connectorTypeCatalog.find((item) => item.connectorTypeId === connectorTypeId);
}
