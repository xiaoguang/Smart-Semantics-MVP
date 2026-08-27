import type { AccessProtocol, EvidenceAuthority, EvidenceCapability, SourceFamily } from '../ai-modeling/source-bundle.ts';

export type FormFieldOption = { label: string; value: string };
export type FormField = {
  key: string;
  label: string;
  type: 'text' | 'number' | 'select' | 'tags' | 'credential-ref';
  required?: boolean;
  placeholder?: string;
  help?: string;
  options?: FormFieldOption[];
};
export type FormSchema = { sections: Array<{ id: string; title: string; fields: FormField[] }> };

export type ConnectorTypeDefinition = {
  connectorTypeId: string;
  family: SourceFamily;
  vendor: string;
  displayName: string;
  protocols: AccessProtocol[];
  capabilities: EvidenceCapability[];
  maturity: 'GA' | 'BETA';
  configSchema: FormSchema;
  scopeSchema: FormSchema;
};

/** A user-facing family groups technical adapters without changing persisted IDs. */
export type ConnectorFamilyDefinition = {
  familyId: SourceFamily;
  displayName: string;
  adapterTypeIds: string[];
  capabilitySummary: string;
};

export type SourceConnection = {
  connectionId: string;
  workspaceId: string;
  connectorTypeId: string;
  displayName: string;
  environment: string;
  activeRevision: number;
  state: 'DRAFT' | 'READY' | 'ERROR' | 'DISABLED';
};

export type ConnectionTestResult = {
  status: 'PASSED' | 'FAILED' | 'NO_RESULT';
  message: string;
  testedAt: string;
};

export type SourceConnectionRevision = {
  connectionId: string;
  revision: number;
  stage: 'DRAFT' | 'TESTED' | 'DISCOVERED' | 'ACTIVE';
  sanitizedConfig: Record<string, unknown>;
  credentialRef?: string;
  defaultScope: Record<string, unknown>;
  lastTest?: ConnectionTestResult;
  discovery?: DiscoveredScope;
  createdBy: string;
  createdAt: string;
};

export type DiscoveredScope = {
  connectionId: string;
  connectionRevision: number;
  summary: string;
  objectCounts: Record<string, number>;
  suggestedScope: Record<string, unknown>;
};

export type SourceSnapshot = {
  snapshotId: string;
  sourceSnapshotIdentity?: string;
  connectionId: string;
  connectionRevision: number;
  scope: Record<string, unknown>;
  versionRef: string;
  fingerprint: string;
  status: 'QUEUED' | 'READING' | 'READY' | 'PARTIAL' | 'FAILED';
  manifestRef: string;
  evidenceIds: string[];
  objectCounts: Record<string, number>;
  summary: string;
  capturedAt: string;
  legacySourceId?: string;
  upstreamSnapshotIds?: string[];
  physicalSchema?: SnapshotPhysicalTable[];
};

export type SnapshotPhysicalTable = {
  schemaName: string;
  tableName: string;
  label: string;
  fields: Array<{ name: string; label: string; dataType: string }>;
};

export type EvidenceBatch = {
  batchId: string;
  projectId: string;
  revision: number;
  snapshotIds: string[];
  fingerprint: string;
  state: 'COLLECTING' | 'FROZEN' | 'RECONCILING' | 'AWAITING_DECISION' | 'MODEL_READY' | 'BLOCKED' | 'PUBLISHED';
};

export type SourceEvidenceClaim = {
  claimId: string;
  subject: string;
  predicate: string;
  value: unknown;
  evidenceRefs: string[];
  authority: EvidenceAuthority;
  snapshotId: string;
  connectionRevision: number;
  upstreamClaimIds?: string[];
};

export type SourceAuditRecord = {
  auditId: string;
  actorUserId: string;
  action: string;
  targetId: string;
  createdAt: string;
  detail: string;
};

export type SourceManagementSnapshot = {
  schemaVersion: 1;
  workspaceId: string;
  revision: number;
  connectorTypes: ConnectorTypeDefinition[];
  connections: SourceConnection[];
  revisions: SourceConnectionRevision[];
  snapshots: SourceSnapshot[];
  claims: SourceEvidenceClaim[];
  defaultBatch: EvidenceBatch;
  audit: SourceAuditRecord[];
};

export type SourceActor = {
  userId: string;
  role: 'VIEWER' | 'EDITOR' | 'REVIEWER' | 'PUBLISHER' | 'ADMIN';
};

type BaseCommand = { workspaceId: string; expectedRevision: number; actor: SourceActor };
export type SourceManagementCommand =
  | (BaseCommand & { type: 'CREATE_CONNECTION'; connectionId: string; connectorTypeId: string; displayName: string; environment: string; sanitizedConfig: Record<string, unknown>; credentialRef?: string; defaultScope: Record<string, unknown> })
  | (BaseCommand & { type: 'UPDATE_CONNECTION'; connectionId: string; sanitizedConfig: Record<string, unknown>; credentialRef?: string; defaultScope: Record<string, unknown> })
  | (BaseCommand & { type: 'TEST_CONNECTION'; connectionId: string; revision: number })
  | (BaseCommand & { type: 'DISCOVER_SCOPE'; connectionId: string; revision: number })
  | (BaseCommand & { type: 'ACTIVATE_CONNECTION'; connectionId: string; revision: number })
  | (BaseCommand & { type: 'DISABLE_CONNECTION'; connectionId: string })
  | (BaseCommand & { type: 'CAPTURE_SNAPSHOT'; connectionId: string; revision: number; scope: Record<string, unknown> })
  | (BaseCommand & { type: 'FREEZE_BATCH'; projectId: string; revision: number; snapshotIds: string[] })
  | (BaseCommand & { type: 'RESET_DEMO' })
  | (BaseCommand & { type: 'PREVIEW_YAML_IMPORT'; content: string })
  | (BaseCommand & { type: 'APPLY_YAML_IMPORT'; content: string });

export type YamlConnection = {
  connectionId: string;
  connectorType: string;
  displayName: string;
  environment: string;
  config: Record<string, unknown>;
  credentialRef?: string;
  readScope: Record<string, unknown>;
};
export type ConnectionYamlDocument = { schemaVersion: 1; workspaceId: string; connections: YamlConnection[] };
export type ImportPreview = {
  created: string[];
  updated: string[];
  unchanged: string[];
  conflicts: Array<{ connectionId: string; reason: string }>;
  errors: Array<{ connectionId?: string; reason: string }>;
};
export type ExportArtifact = { fileName: string; mediaType: 'application/yaml'; content: string };
export type SourceManagementResult = {
  snapshot: SourceManagementSnapshot;
  discovery?: DiscoveredScope;
  importPreview?: ImportPreview;
};

export type ConnectorContext = {
  connection: SourceConnection;
  revision: SourceConnectionRevision;
  now: string;
};
export type NormalizedSnapshot = Omit<SourceSnapshot, 'snapshotId' | 'connectionId' | 'connectionRevision' | 'scope' | 'capturedAt'> & {
  claims: Array<Omit<SourceEvidenceClaim, 'snapshotId' | 'connectionRevision'>>;
};
export type ConnectorAdapter = {
  test(input: ConnectorContext): Promise<ConnectionTestResult>;
  discover(input: ConnectorContext): Promise<DiscoveredScope>;
  capture(input: ConnectorContext, scope: Record<string, unknown>): Promise<NormalizedSnapshot>;
};

export type SourceManagementRuntime = {
  read(workspaceId: string): Promise<SourceManagementSnapshot>;
  execute(command: SourceManagementCommand): Promise<SourceManagementResult>;
  exportYaml(workspaceId: string): Promise<ExportArtifact>;
};
