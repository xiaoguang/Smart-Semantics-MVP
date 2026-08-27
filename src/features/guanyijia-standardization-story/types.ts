import type { ModelBrowserObjectKind, ModelBrowserObjectRef } from '../model-browser/types.ts';
import type {
  ModelingDocumentSection,
  StructuredModelingAssertion,
} from '../modeling-document-bridge/types.ts';
import type {
  SourceDocumentBlock,
  SourceDocumentLineDiff,
  SourceDocumentSections,
  StructuredValue,
} from '../source-documents/types.ts';

export type { SourceDocumentBlock, StructuredValue } from '../source-documents/types.ts';

export type StorySourceClass = 'REAL' | 'DEMO_POLICY' | 'DERIVED';
export type StorySourceAuthority = 'PRIMARY' | 'CORROBORATING' | 'AUXILIARY' | 'DERIVED';
export type StoryLineageStatus = 'ROOT' | 'DERIVED_VALID' | 'UPSTREAM_MISSING';

export type DemoPolicyDocumentInput = {
  path: '业务术语/往来单位.md' | '单据管理/审核状态.md' | '库存管理/负库存与库存时点.md';
  version: string;
  content: string;
};

export type StorySource = {
  sourceId: string;
  sourceName: string;
  sourceClass: StorySourceClass;
  snapshotId: string;
  authority: StorySourceAuthority;
  lineageStatus: Exclude<StoryLineageStatus, 'UPSTREAM_MISSING'>;
  upstreamSourceIds: string[];
};

export type StorySourceReadSummary = {
  summary: string;
  objectCount: number;
  evidenceCount: number;
  objectCounts: Record<string, number>;
  versionRef: string;
  warnings: string[];
};

export type SourceDocumentCompilation = {
  sourceId: string;
  sourceName: string;
  sourceClass: StorySourceClass;
  snapshotId: string;
  authority: StorySourceAuthority;
  readSummary: StorySourceReadSummary;
  sections: SourceDocumentSections;
  blocks: SourceDocumentBlock[];
  assertions: StructuredModelingAssertion[];
  markdown: string;
  markdownSha256: string;
  evidenceLocators: Record<string, Record<string, StructuredValue>>;
  delta: {
    addedBlockIds: string[];
    changedBlockIds: string[];
    addedGapIds: string[];
  };
  introducedConflictIds: string[];
  corroboratedConflictIds: string[];
  lineageStatus: StoryLineageStatus;
  upstreamSourceIds: string[];
};

export type SemanticConflictVariant = {
  sourceId: string;
  stableCode: string;
  normalizedValue: string;
  evidenceRefs: string[];
};

export type ConflictResolutionStrategy =
  | 'KEEP_CURRENT'
  | 'ACCEPT_INCOMING'
  | 'MERGE'
  | 'DEFER_AS_GAP';

export type ConflictVariantDefinition = SemanticConflictVariant;

export type ConflictCorroboratorDefinition = ConflictVariantDefinition & {
  upstreamSourceId: string;
};

export type SemanticConflictDefinition = {
  conflictId: string;
  title: string;
  introducedBySourceId: string;
  current: ConflictVariantDefinition;
  incoming: ConflictVariantDefinition;
  corroborating: ConflictCorroboratorDefinition[];
  objectKinds: Array<ModelBrowserObjectKind | 'EXCLUSION'>;
  sections: ModelingDocumentSection[];
  variants: SemanticConflictVariant[];
  affectedObjectRefs: ModelBrowserObjectRef[];
  allowedStrategies: ConflictResolutionStrategy[];
  defaultStrategy: ConflictResolutionStrategy;
  defaultResolutionDraft: string;
};

export type ConflictSourceRevision = {
  documentId: string;
  documentRevision: number;
  compilation: SourceDocumentCompilation;
};

export type ConflictHunkSide = {
  role: 'CURRENT' | 'INCOMING' | 'CORROBORATING';
  sourceId: string;
  sourceName: string;
  sourceClass: StorySourceClass;
  authority: StorySourceAuthority;
  documentId: string;
  documentRevision: number;
  block: SourceDocumentBlock;
  assertion: StructuredModelingAssertion;
};

export type ConflictHunk = {
  conflictId: string;
  title: string;
  sections: ModelingDocumentSection[];
  current: ConflictHunkSide;
  incoming: ConflictHunkSide;
  corroborating: ConflictHunkSide[];
  affectedObjectRefs: ModelBrowserObjectRef[];
  hunkSha256: string;
};

export type ConflictObjectDisposition = {
  objectRef: ModelBrowserObjectRef;
  disposition:
    | 'KEEP'
    | 'EXCLUDED'
    | 'CANDIDATE_ONLY'
    | 'KEEP_WITHOUT_ENUM_MEMBER'
    | 'DEFERRED';
};

export type SemanticPatch = {
  schemaVersion: 1;
  conflictId: string;
  blockOperations: Array<{ op: 'UPSERT'; block: SourceDocumentBlock }>;
  assertionOperations: Array<{ op: 'UPSERT'; assertion: StructuredModelingAssertion }>;
  objectDispositionOperations: Array<{ op: 'SET'; value: ConflictObjectDisposition }>;
};

export type ConflictProvenanceSource = {
  role: ConflictHunkSide['role'];
  sourceId: string;
  sourceName: string;
  sourceClass: StorySourceClass;
  authority: StorySourceAuthority;
  documentId: string;
  documentRevision: number;
  evidenceRefs: string[];
  usage: 'CANONICAL' | 'PROVENANCE_ONLY' | 'GAP_EVIDENCE' | 'CORROBORATION';
};

export type ConflictResolutionPreview = {
  hunk: ConflictHunk;
  strategy: ConflictResolutionStrategy;
  result: {
    blocks: SourceDocumentBlock[];
    assertions: StructuredModelingAssertion[];
    objectDispositions: ConflictObjectDisposition[];
  };
  structuredPatch: SemanticPatch;
  markdownDiff: SourceDocumentLineDiff[];
  provenanceSources: ConflictProvenanceSource[];
  previewSha256: string;
};

export type ConflictResolutionArtifact = ConflictResolutionPreview & {
  schemaVersion: 1;
  resolutionId: string;
  runId: string;
  sourceId: string;
  reason: string;
  actorUserId: string;
  decidedAt: string;
};

export type SourceCompilationDiff = {
  changedBlockIds: string[];
  blockChanges: Array<{ blockId: string; before: SourceDocumentBlock; after: SourceDocumentBlock }>;
  assertionChanges: Array<{
    assertionId: string;
    before: StructuredModelingAssertion;
    after: StructuredModelingAssertion;
  }>;
  sectionChanges: Array<{
    section: ModelingDocumentSection;
    before: string;
    after: string;
  }>;
  markdown: { before: string; after: string };
  affectedConflicts: Array<{
    conflictId: string;
    title: string;
    beforeVariant?: string;
    afterVariant?: string;
    affectedObjectRefs: ModelBrowserObjectRef[];
  }>;
  affectedObjectRefs: ModelBrowserObjectRef[];
};

export type GuanyijiaStandardizationStory = {
  listSources(): StorySource[];
  compileSource(input: {
    sourceId: string;
    priorCompilations: SourceDocumentCompilation[];
  }): SourceDocumentCompilation;
  reviseCompilation(input: {
    compilation: SourceDocumentCompilation;
    priorCompilations: SourceDocumentCompilation[];
    changes: Array<{
      blockId: string;
      label?: string;
      value?: StructuredValue;
    }>;
  }): { compilation: SourceDocumentCompilation; diff: SourceCompilationDiff };
  buildConflictHunk(input: {
    conflictId: string;
    sourceRevisions: ConflictSourceRevision[];
  }): ConflictHunk;
  previewConflictResolution(input: {
    conflictId: string;
    strategy: ConflictResolutionStrategy;
    sourceRevisions: ConflictSourceRevision[];
  }): ConflictResolutionPreview;
  validateConflictResolutionArtifactProjection(artifact: ConflictResolutionArtifact): void;
  listConflictDefinitions(): SemanticConflictDefinition[];
};
