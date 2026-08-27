import type { ConnectorDefinition } from './source-bundle.ts';

export const groupRetailConnectorCatalog: ConnectorDefinition[] = [
  {
    connectorId: 'mysql-group-retail', family: 'RELATIONAL', vendor: 'MySQL',
    label: 'MySQL · group_retail_core', protocol: 'JDBC_SQL', status: 'CONNECTED',
    capabilities: ['PHYSICAL_SCHEMA', 'DATA_PROFILE', 'ACTUAL_QUERY'],
  },
  {
    connectorId: 'mongodb-product-profile', family: 'DOCUMENT_DATABASE', vendor: 'MongoDB',
    label: 'MongoDB · product_customer_profile', protocol: 'MONGO_API', status: 'CONNECTED',
    capabilities: ['PHYSICAL_SCHEMA', 'DATA_PROFILE', 'ACTUAL_QUERY'],
  },
  {
    connectorId: 'elasticsearch-retail-search', family: 'SEARCH_INDEX', vendor: 'Elasticsearch',
    label: 'Elasticsearch · retail_search', protocol: 'ELASTIC_REST', status: 'CONNECTED',
    capabilities: ['PHYSICAL_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE'],
  },
  {
    connectorId: 'semantica-rdf', family: 'GRAPH', vendor: 'Semantica',
    label: 'Semantica · RDF/SPARQL', protocol: 'SPARQL', status: 'CONNECTED',
    capabilities: ['BUSINESS_DEFINITION', 'VOCABULARY', 'LINEAGE'], modes: ['PROPERTY_GRAPH', 'RDF_GRAPH'],
  },
  {
    connectorId: 'minio-retail-knowledge', family: 'OBJECT_STORAGE', vendor: 'MinIO',
    label: 'MinIO · retail-knowledge', protocol: 'S3_API', status: 'CONNECTED',
    capabilities: ['BUSINESS_DEFINITION', 'AUXILIARY_CONTENT'],
  },
  {
    connectorId: 'github-group-retail', family: 'CODE_REPOSITORY', vendor: 'GitHub',
    label: 'GitHub · group-retail/analytics', protocol: 'GIT_REST', status: 'CONNECTED',
    capabilities: ['PHYSICAL_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE'],
  },
  {
    connectorId: 'sharepoint-operations', family: 'ENTERPRISE_CONTENT', vendor: 'SharePoint',
    label: 'SharePoint · 集团经营知识库', protocol: 'MICROSOFT_GRAPH', status: 'CONNECTED',
    capabilities: ['BUSINESS_DEFINITION', 'VOCABULARY', 'AUXILIARY_CONTENT'],
  },
  {
    connectorId: 'kafka-retail-events', family: 'EVENT_STREAM', vendor: 'Kafka',
    label: 'Kafka · retail-business-events', protocol: 'KAFKA', status: 'CONNECTED',
    capabilities: ['EVENT_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE'],
  },
  {
    connectorId: 'snowflake-warehouse', family: 'WAREHOUSE_LAKEHOUSE', vendor: 'Snowflake',
    label: 'Snowflake · 集团经营数仓', protocol: 'SNOWFLAKE_SQL', status: 'AVAILABLE',
    capabilities: ['PHYSICAL_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE'],
  },
  {
    connectorId: 'redis-cache', family: 'KEY_VALUE', vendor: 'Redis',
    label: 'Redis · 实时经营缓存', protocol: 'RESP', status: 'AVAILABLE',
    capabilities: ['DATA_PROFILE', 'ACTUAL_QUERY'],
  },
  {
    connectorId: 'milvus-vector', family: 'VECTOR_DATABASE', vendor: 'Milvus',
    label: 'Milvus · 商品语义检索', protocol: 'MILVUS_API', status: 'AVAILABLE',
    capabilities: ['PHYSICAL_SCHEMA', 'ACTUAL_QUERY', 'LINEAGE'],
  },
  {
    connectorId: 'cassandra-timeseries', family: 'WIDE_COLUMN_TIME_SERIES', vendor: 'Cassandra',
    label: 'Cassandra · 门店时序明细', protocol: 'CQL', status: 'AVAILABLE',
    capabilities: ['PHYSICAL_SCHEMA', 'DATA_PROFILE', 'EVENT_SCHEMA'],
  },
];

export const connectedGroupRetailConnectors = groupRetailConnectorCatalog.filter((item) => item.status === 'CONNECTED');
