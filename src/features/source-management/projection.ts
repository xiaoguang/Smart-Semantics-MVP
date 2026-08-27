import type { ConnectorDefinition, SourceAsset, SourceOrigin, SourceRole } from '../ai-modeling/source-bundle.ts';
import { connectorTypeById } from './connector-catalog.ts';
import type { SourceManagementSnapshot } from './types.ts';

const originByConnector: Record<string, SourceOrigin> = {
  mysql: 'DATABASE_CONNECTOR', snowflake: 'DATABASE_CONNECTOR', mongodb: 'DOCUMENT_DATABASE',
  redis: 'DATABASE_CONNECTOR', cassandra: 'DATABASE_CONNECTOR', semantica: 'GRAPH_STORE',
  elasticsearch: 'SEARCH_INDEX', milvus: 'DATABASE_CONNECTOR', minio: 'OBJECT_STORAGE',
  sharepoint: 'ENTERPRISE_CONTENT', github: 'CODE_REPOSITORY', kafka: 'EVENT_STREAM',
};

function rolesFor(capabilities: string[]): SourceRole[] {
  const roles = new Set<SourceRole>();
  if (capabilities.some((item) => ['BUSINESS_DEFINITION', 'VOCABULARY'].includes(item))) roles.add('BUSINESS_DEFINITION');
  if (capabilities.includes('PHYSICAL_SCHEMA')) roles.add('PHYSICAL_STRUCTURE');
  if (capabilities.some((item) => ['ACTUAL_QUERY', 'EVENT_SCHEMA', 'DATA_PROFILE'].includes(item))) roles.add('ACTUAL_USAGE');
  if (capabilities.includes('AUXILIARY_CONTENT')) roles.add('AUXILIARY_EVIDENCE');
  return [...roles];
}

export function projectSnapshotAsset(snapshot: SourceManagementSnapshot, snapshotId: string): SourceAsset | null {
  const sourceSnapshot = snapshot.snapshots.find((item) => item.snapshotId === snapshotId);
  if (!sourceSnapshot) return null;
  const connection = snapshot.connections.find((item) => item.connectionId === sourceSnapshot.connectionId);
  if (!connection) return null;
  const connectorType = connectorTypeById(connection.connectorTypeId);
  if (!connectorType) return null;
  const connector: ConnectorDefinition = {
    connectorId: connection.connectionId, family: connectorType.family, vendor: connectorType.vendor,
    label: connection.displayName, protocol: connectorType.protocols[0], capabilities: [...connectorType.capabilities],
    status: connection.state === 'READY' ? 'CONNECTED' : 'AVAILABLE',
    modes: connection.connectorTypeId === 'semantica' ? ['PROPERTY_GRAPH', 'RDF_GRAPH'] : undefined,
  };
  const claims = snapshot.claims.filter((item) => item.snapshotId === sourceSnapshot.snapshotId);
  const authority = claims.some((item) => item.authority === 'DERIVED') ? 'DERIVED'
    : claims.some((item) => item.authority === 'AUXILIARY') ? 'AUXILIARY' : 'PRIMARY';
  return {
    sourceId: sourceSnapshot.legacySourceId ?? sourceSnapshot.snapshotId,
    displayName: connection.displayName,
    origin: originByConnector[connection.connectorTypeId] ?? 'DATABASE_CONNECTOR',
    contentKinds: Object.keys(sourceSnapshot.objectCounts), roles: rolesFor(connectorType.capabilities),
    fingerprint: sourceSnapshot.fingerprint, fixtureKey: sourceSnapshot.snapshotId,
    status: sourceSnapshot.status === 'READY' || sourceSnapshot.status === 'PARTIAL' ? 'READY' : sourceSnapshot.status === 'FAILED' ? 'FAILED' : 'PROCESSING',
    summary: sourceSnapshot.summary, evidenceIds: [...sourceSnapshot.evidenceIds], connector, authority,
    upstreamSourceIds: sourceSnapshot.upstreamSnapshotIds,
    evidenceDetail: {
      kind: 'CONNECTOR', connector, snapshotId: sourceSnapshot.snapshotId, versionRef: sourceSnapshot.versionRef,
      connectionRevision: sourceSnapshot.connectionRevision, capturedAt: sourceSnapshot.capturedAt,
      manifestRef: sourceSnapshot.manifestRef, objectCounts: { ...sourceSnapshot.objectCounts },
    },
  };
}

export function projectPhysicalSources(snapshot: SourceManagementSnapshot) {
  return snapshot.connections.flatMap((connection) => {
    const connectorType = connectorTypeById(connection.connectorTypeId);
    if (connection.state !== 'READY' || !connectorType?.capabilities.includes('PHYSICAL_SCHEMA')) return [];
    return snapshot.snapshots.filter((item) => item.connectionId === connection.connectionId
      && item.connectionRevision === connection.activeRevision
      && (item.status === 'READY' || item.status === 'PARTIAL'))
      .map((item) => ({ connection, connectorTypeId: connection.connectorTypeId, connectorType, snapshot: item }));
  });
}

export function projectEvidenceQuality(snapshot: SourceManagementSnapshot) {
  const usableClaims = snapshot.claims.filter((item) => item.evidenceRefs.length > 0);
  const derivedClaims = usableClaims.filter((item) => item.authority === 'DERIVED').length;
  const weakClaims = usableClaims.filter((item) => item.authority === 'AUXILIARY').length
    + snapshot.snapshots.filter((item) => item.status === 'PARTIAL').length;
  const independentSnapshotIds = new Set(usableClaims.filter((item) => item.authority === 'PRIMARY' || item.authority === 'CORROBORATING').map((item) => item.snapshotId));
  const grouped = new Map<string, Set<string>>();
  usableClaims.filter((item) => item.authority !== 'DERIVED').forEach((item) => {
    const key = `${item.subject}\u0000${item.predicate}\u0000${JSON.stringify(item.value)}`;
    if (!grouped.has(key)) grouped.set(key, new Set());
    grouped.get(key)!.add(item.snapshotId);
  });
  return {
    totalClaims: snapshot.claims.length,
    coveredClaims: usableClaims.length,
    evidenceCoverage: snapshot.claims.length ? Math.round((usableClaims.length / snapshot.claims.length) * 100) : 0,
    independentSources: independentSnapshotIds.size,
    consistentConclusions: [...grouped.values()].filter((ids) => ids.size > 1).length,
    conflicts: 0,
    weakClaims,
    derivedClaims,
    candidateChanges: 0,
  };
}
