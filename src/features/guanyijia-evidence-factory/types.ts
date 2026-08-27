import type { StorySource } from '../guanyijia-standardization-story/types.ts';
import type { StructuredValue } from '../source-documents/types.ts';
import type { ModelingDocumentSection } from '../modeling-document-bridge/types.ts';

export type Sha256 = `sha256:${string}`;

export type SourceSnapshotIdentity = Omit<Pick<StorySource,
  'sourceId' | 'sourceName' | 'sourceClass' | 'snapshotId' | 'authority' | 'lineageStatus' | 'upstreamSourceIds'>,
  'lineageStatus'> & {
  lineageStatus: 'ROOT' | 'DERIVED_VALID';
};

/**
 * Private capture metadata. It identifies raw artifacts without carrying their
 * contents into the public demo Bundle.
 */
export type RawSourceSnapshotManifest = {
  schemaVersion: 1;
  manifestId: string;
  source: SourceSnapshotIdentity;
  capturedAt: string;
  artifacts: Array<{
    artifactRef: string;
    sha256: Sha256;
    locator: Record<string, StructuredValue>;
  }>;
  manifestSha256: Sha256;
};

export type ObservedEvidenceLocator =
  | { kind: 'SQL'; schema: string; object: string; symbol: string }
  | { kind: 'FILE_LINES'; path: string; startLine: number; endLine: number }
  | { kind: 'DOCUMENT_SECTION'; document: string; section: string; page?: number }
  | { kind: 'SOURCE_SYMBOL'; path: string; symbol: string; startLine: number; endLine: number };

export type GeneratedTargetEvidenceLocator = {
  kind: 'GENERATED_TARGET';
  confirmationStatus: 'PENDING_HUMAN_CONFIRMATION';
};

type EvidenceFragmentBase = {
  evidenceRef: string;
  title: string;
  excerpt: string;
};

/** A sanitized, displayable excerpt from one frozen source artifact. */
export type ObservedEvidenceFragment = EvidenceFragmentBase & {
  evidenceKind: 'OBSERVED';
  sourceId: string;
  snapshotId: string;
  /** Opaque private-manifest artifact reference; never raw artifact content. */
  artifactRef: string;
  locator: ObservedEvidenceLocator;
};

/** A clearly labeled proposal that is not observed evidence. */
export type GeneratedTargetEvidenceFragment = EvidenceFragmentBase & {
  evidenceKind: 'GENERATED_TARGET';
  locator: GeneratedTargetEvidenceLocator;
};

export type EvidenceFragment = ObservedEvidenceFragment | GeneratedTargetEvidenceFragment;

/** Public provenance for an explicit maintenance run; it never carries raw data. */
export type GenerationRunManifest = {
  schemaVersion: 1;
  runId: string;
  kind: 'CAPTURE' | 'GENERATE' | 'FREEZE';
  status: 'SUCCEEDED' | 'FAILED';
  inputManifestRefs: string[];
  outputSha256?: Sha256;
};

export type EvidenceTraceLink = {
  blockId: string;
  assertionId: string;
  section: ModelingDocumentSection;
  markdownAnchor: string;
  evidenceRefs: string[];
};

export type PublicStructuredBlockValue = {
  normalized: string;
  text: string;
};

export type PublicSemanticKind =
  | 'ENTITY' | 'EVENT' | 'FIELD' | 'RELATION' | 'DIMENSION' | 'METRIC'
  | 'HIERARCHY' | 'RULE' | 'ALIAS' | 'TIME_RULE'
  | 'GAP' | 'PENDING_ASSET' | 'EXCLUSION';

export type PublicAffectedObjectRef =
  | {
      scope: 'RESULT';
      documentVersion: string;
      kind: Exclude<PublicSemanticKind, 'GAP' | 'PENDING_ASSET' | 'EXCLUSION'>;
      objectId: string;
      ownerId?: string;
    }
  | {
      scope: 'CATALOG';
      catalogId: string;
      kind: Exclude<PublicSemanticKind, 'GAP' | 'PENDING_ASSET' | 'EXCLUSION'>;
      objectId: string;
      ownerId?: string;
    }
  | {
      scope: 'DRAFT';
      draftId: string;
      kind: Exclude<PublicSemanticKind, 'GAP' | 'PENDING_ASSET' | 'EXCLUSION'>;
      objectId: string;
      ownerId?: string;
    };

export type PublicSourceReadSummary = {
  summary: string;
  objectCount: number;
  evidenceCount: number;
  objectCounts: Record<string, number>;
  versionRef: string;
  warnings: string[];
};

export type PublicSourceDocumentBlock = {
  blockId: string;
  section: ModelingDocumentSection;
  semanticKind: PublicSemanticKind;
  stableCode: string;
  label: string;
  value: PublicStructuredBlockValue;
  evidenceStatus: 'FACT' | 'INFERENCE' | 'GAP' | 'CONFLICT';
  evidenceRefs: string[];
  affectedObjectRefs: PublicAffectedObjectRef[];
};

export type PublicSourceDocumentAssertion = {
  assertionId: string;
  section: ModelingDocumentSection;
  statement: string;
  provenance: 'OBSERVED' | 'INFERRED' | 'USER_CONFIRMED';
  evidenceRefs: string[];
};

export type PublicSourceDocumentCompilation = {
  sourceId: string;
  sourceName: string;
  sourceClass: 'REAL' | 'DEMO_POLICY' | 'DERIVED';
  snapshotId: string;
  authority: 'PRIMARY' | 'CORROBORATING' | 'AUXILIARY' | 'DERIVED';
  readSummary: PublicSourceReadSummary;
  sections: Record<ModelingDocumentSection, string>;
  blocks: PublicSourceDocumentBlock[];
  assertions: PublicSourceDocumentAssertion[];
  markdown: string;
  markdownSha256: string;
  evidenceLocators: Record<string, ObservedEvidenceLocator>;
  delta: {
    addedBlockIds: string[];
    changedBlockIds: string[];
    addedGapIds: string[];
  };
  introducedConflictIds: string[];
  corroboratedConflictIds: string[];
  lineageStatus: 'ROOT' | 'DERIVED_VALID';
  upstreamSourceIds: string[];
};

export type PublicArtifactProvenance = {
  artifactRef: string;
  sourceId: string;
  snapshotId: string;
  rawArtifactSha256: Sha256;
};

/**
 * Static application trust root. Freeze tooling may propose a pin but may not
 * write this registry; an independently reviewed source change does that.
 */
export type TrustedPublicationRegistry = {
  schemaVersion: 1;
  registryVersion: 'trusted-publications-v1';
  publications: TrustedPublicationPin[];
};

export type TrustedPublicationPin = {
  storyKey: string;
  receiptId: string;
  receiptSha256: Sha256;
  bundleSha256: Sha256;
  rawManifestDigest: Sha256;
  redactionPolicySha256: Sha256;
};

/** A public attestation of one private raw manifest, without its raw contents. */
export type PublishedRawManifestAttestation = {
  manifestRef: string;
  sourceId: string;
  snapshotId: string;
  manifestSha256: Sha256;
};

/** A unique raw artifact membership; many approved fragments may cite it. */
export type PublishedArtifactMembership = {
  artifactRef: string;
  manifestRef: string;
  rawArtifactSha256: Sha256;
};

/** Approval of the exact public locator and decoded excerpt for one fragment. */
export type PublishedFragmentApproval = {
  evidenceRef: string;
  artifactRef: string;
  locatorSha256: Sha256;
  excerptSha256: Sha256;
};

export type RedactionPublicationApproval = {
  decision: 'APPROVED';
  scope: 'ENTIRE_PUBLIC_BUNDLE';
  approvedBundleSha256: Sha256;
  policyId: string;
  policyVersion: string;
  policySha256: Sha256;
  sanitizerVersion: string;
  approvalRef: string;
  approvedAt: string;
};

/**
 * Untrusted published metadata. It becomes usable only after every digest and
 * relationship is pinned by TrustedPublicationRegistry.
 */
export type PublicationReceipt = {
  schemaVersion: 1;
  receiptId: string;
  storyKey: string;
  rawManifests: PublishedRawManifestAttestation[];
  rawManifestDigest: Sha256;
  artifactMemberships: PublishedArtifactMembership[];
  fragmentApprovals: PublishedFragmentApproval[];
  publicBundleSha256: Sha256;
  redactionApproval: RedactionPublicationApproval;
  receiptSha256: Sha256;
};

/**
 * The only evidence payload routine demo code may read. Raw source content and
 * capture manifests remain private; this Bundle contains safe fragments only.
 */
export type FrozenDemoEvidenceBundle = {
  schemaVersion: 1;
  storyKey: string;
  compilerVersion: 'evidence-factory-v1';
  sourceIdentities: SourceSnapshotIdentity[];
  compilations: PublicSourceDocumentCompilation[];
  traceLinks: Record<string, EvidenceTraceLink[]>;
  evidenceFragments: EvidenceFragment[];
  /** Stable pre-digest publication ID selecting the trusted receipt pin. */
  publicationReceiptId: string;
  /** Pinned aggregate of private raw-manifest attestations, not a trust root itself. */
  rawManifestDigest: Sha256;
  /** Public convenience projection, checked against the trusted receipt. */
  artifactProvenance: PublicArtifactProvenance[];
  generationRuns: GenerationRunManifest[];
  bundleSha256: Sha256;
};

/** The complete caller-facing interface for verified frozen evidence. */
export type EvidenceFactory = {
  readBundle(input: { storyKey: string }): FrozenDemoEvidenceBundle;
  readFragment(input: { evidenceRef: string }): EvidenceFragment;
};

/** Internal creation dependency; callers receive only EvidenceFactory. */
export type EvidenceFactoryDependencies = {
  bundles: readonly unknown[];
  publicationReceipts: readonly unknown[];
  trustedRegistry: TrustedPublicationRegistry;
};
