import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type {
  ConnectionTestResult, ConnectorAdapter, ConnectorContext, DiscoveredScope, NormalizedSnapshot,
} from './types.ts';
import type { SnapshotPhysicalTable, SourceEvidenceClaim } from './types.ts';

export type FixtureProfile = {
  connectionId: string;
  connectorTypeId: string;
  config: Record<string, unknown>;
  versionRef: string;
  sourceSnapshotIdentity?: string;
  sourceFingerprint?: string;
  sourceManifestRef?: string;
  summary: string;
  objectCounts: Record<string, number>;
  evidenceIds: string[];
  revisionEvidenceIds?: Record<number, string[]>;
  revisionVersionRefs?: Record<number, string>;
  legacySourceId?: string;
  authority?: 'PRIMARY' | 'CORROBORATING' | 'DERIVED' | 'AUXILIARY';
  upstreamSnapshotIds?: string[];
  physicalSchema?: SnapshotPhysicalTable[];
  available?: boolean;
  claims?: Array<Omit<SourceEvidenceClaim, 'snapshotId' | 'connectionRevision'>>;
};

const stable = (value: unknown): string => {
  if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.entries(value as Record<string, unknown>).sort(([a], [b]) => a.localeCompare(b)).map(([key, item]) => `${JSON.stringify(key)}:${stable(item)}`).join(',')}}`;
  return JSON.stringify(value);
};

function sameConfig(expected: Record<string, unknown>, actual: Record<string, unknown>) {
  return Object.entries(expected).every(([key, value]) => stable(actual[key]) === stable(value));
}

export function createFixtureConnectorAdapter(profiles: FixtureProfile[]): ConnectorAdapter {
  const find = (input: ConnectorContext) => profiles.find((profile) => profile.available !== false
    && profile.connectorTypeId === input.connection.connectorTypeId
    && sameConfig(profile.config, input.revision.sanitizedConfig));
  return {
    async test(input): Promise<ConnectionTestResult> {
      const profile = find(input);
      return profile
        ? { status: 'PASSED', message: '配置格式与预置快照身份一致，可以读取确定性证据。', testedAt: input.now }
        : { status: 'NO_RESULT', message: '配置格式有效，但尚无预生成读取结果。', testedAt: input.now };
    },
    async discover(input): Promise<DiscoveredScope> {
      const profile = find(input);
      if (!profile) throw new Error('尚无预生成范围发现结果');
      return { connectionId: input.connection.connectionId, connectionRevision: input.revision.revision, summary: profile.summary, objectCounts: { ...profile.objectCounts }, suggestedScope: structuredClone(input.revision.defaultScope) };
    },
    async capture(input, scope): Promise<NormalizedSnapshot> {
      const profile = find(input);
      if (!profile) throw new Error('尚无预生成读取结果');
      const connectionId = input.connection.connectionId;
      const versionRef = profile.revisionVersionRefs?.[input.revision.revision]
        ?? (input.revision.revision === 1 ? profile.versionRef : `${profile.versionRef}-r${input.revision.revision}`);
      const evidenceIds = [...profile.evidenceIds, ...(profile.revisionEvidenceIds?.[input.revision.revision] ?? [])];
      const fingerprint = profile.sourceFingerprint
        ?? sha256HexSync(stable({ connectionId, revision: input.revision.revision, scope, versionRef, evidenceIds }));
      const provisionalSnapshotId = `${connectionId}-r${input.revision.revision}-${fingerprint.slice(0, 10)}`;
      return {
        versionRef, fingerprint, status: 'READY', manifestRef: profile.sourceManifestRef ?? `fixture://${connectionId}/${versionRef}/manifest.json`,
        sourceSnapshotIdentity: profile.sourceSnapshotIdentity,
        evidenceIds, objectCounts: { ...profile.objectCounts }, summary: profile.summary,
        legacySourceId: profile.legacySourceId, upstreamSnapshotIds: profile.upstreamSnapshotIds,
        physicalSchema: profile.physicalSchema ? structuredClone(profile.physicalSchema) : undefined,
        claims: profile.claims
          ? structuredClone(profile.claims).filter((claim) => claim.evidenceRefs.every((evidenceId) => evidenceIds.includes(evidenceId)))
          : evidenceIds.map((evidenceId, index) => ({
            claimId: `${provisionalSnapshotId}:claim:${index + 1}`, subject: connectionId, predicate: 'supports', value: evidenceId,
            evidenceRefs: [evidenceId], authority: profile.authority ?? 'PRIMARY', upstreamClaimIds: [],
          })),
      };
    },
  };
}

export { stable as stableSourceJson };
