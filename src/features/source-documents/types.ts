import type {
  ModelingDocumentSection,
  StructuredModelingAssertion,
  ValidationIssue,
} from '../modeling-document-bridge/types.ts';
import type { ModelBrowserObjectKind, ModelBrowserObjectRef } from '../model-browser/types.ts';

export type SourceDocumentSections = Record<ModelingDocumentSection, string>;

export type SourceDocumentValidation = {
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
  gaps: ValidationIssue[];
};

export type ContentReference = `sha256:${string}`;

export type StructuredValue =
  | string | number | boolean | null
  | StructuredValue[]
  | { [key: string]: StructuredValue };

export type SourceDocumentBlock = {
  blockId: string;
  section: ModelingDocumentSection;
  semanticKind: ModelBrowserObjectKind | 'GAP' | 'PENDING_ASSET' | 'EXCLUSION';
  stableCode: string;
  label: string;
  value: StructuredValue;
  evidenceStatus: 'FACT' | 'INFERENCE' | 'GAP' | 'CONFLICT';
  evidenceRefs: string[];
  affectedObjectRefs: ModelBrowserObjectRef[];
};

export type SourceModelingDocument = {
  documentId: string;
  derivedFromDocumentId?: string;
  projectId: string;
  documentCode: string;
  sourceSnapshotId: string;
  sourceType: string;
  sourceName: string;
  revision: number;
  sectionsRef: ContentReference;
  assertionsRef: ContentReference;
  blocksRef?: ContentReference;
  markdownRef: ContentReference;
  markdownSha256: string;
  validation: SourceDocumentValidation;
  status: 'GENERATED' | 'NEEDS_REVIEW' | 'READY_FOR_ALIGNMENT' | 'SUPERSEDED';
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type ContentAddressedStore = {
  put(content: string): Promise<ContentReference>;
  get(ref: ContentReference): Promise<string | null>;
};

export type SourceDocumentMetadataSnapshot = {
  version: string;
  raw: string | null;
};

export type SourceDocumentMetadataStore = {
  read(): Promise<SourceDocumentMetadataSnapshot>;
  compareAndSet(expectedVersion: string, nextRaw: string): Promise<boolean>;
};

export type RegisterSourceDocumentInput = {
  projectId: string;
  documentCode: string;
  sourceSnapshotId: string;
  sourceType: string;
  sourceName: string;
  sections: SourceDocumentSections;
  assertions: StructuredModelingAssertion[];
  blocks?: SourceDocumentBlock[];
  validation: SourceDocumentValidation;
  actorUserId: string;
};

export type SourceDocumentLineDiff = {
  type: 'UNCHANGED' | 'REMOVED' | 'ADDED';
  line: string;
};

export type SourceDocumentRevisionDiff = {
  beforeDocumentId: string;
  afterDocumentId: string;
  beforeRevision: number;
  afterRevision: number;
  actorUserId: string;
  blockChanges: Array<{
    blockId: string;
    before?: SourceDocumentBlock;
    after?: SourceDocumentBlock;
  }>;
  assertionChanges: Array<{
    assertionId: string;
    before?: StructuredModelingAssertion;
    after?: StructuredModelingAssertion;
  }>;
  sectionChanges: Array<{
    section: ModelingDocumentSection;
    before: string;
    after: string;
    lines: SourceDocumentLineDiff[];
  }>;
  markdown: {
    before: string;
    after: string;
    lines: SourceDocumentLineDiff[];
  };
};

export type SourceDocumentRuntime = {
  list(projectId: string): Promise<SourceModelingDocument[]>;
  read(documentId: string): Promise<SourceModelingDocument | null>;
  readMarkdown(documentId: string): Promise<string>;
  readSections(documentId: string): Promise<SourceDocumentSections>;
  readAssertions(documentId: string): Promise<StructuredModelingAssertion[]>;
  readBlocks(documentId: string): Promise<SourceDocumentBlock[]>;
  getRevisionDiff(
    beforeDocumentId: string,
    afterDocumentId: string,
  ): Promise<SourceDocumentRevisionDiff>;
  getReviewSummary(documentId: string): Promise<{
    documentId: string;
    revision: number;
    sourceName: string;
    status: SourceModelingDocument['status'];
    sections: Array<{
      section: ModelingDocumentSection;
      lineCount: number;
      characterCount: number;
      assertionCount: number;
      evidenceCount: number;
    }>;
    validation: SourceDocumentValidation;
  }>;
  getSectionDiff(
    beforeDocumentId: string,
    afterDocumentId: string,
    section: ModelingDocumentSection,
  ): Promise<{
    section: ModelingDocumentSection;
    before: string;
    after: string;
    changed: boolean;
    actorUserId: string;
  }>;
  register(input: RegisterSourceDocumentInput): Promise<SourceModelingDocument>;
  revise(input: {
    documentId: string;
    expectedRevision: number;
    sections: SourceDocumentSections;
    assertions: StructuredModelingAssertion[];
    blocks: SourceDocumentBlock[];
    actorUserId: string;
  }): Promise<SourceModelingDocument>;
  updateSection(input: {
    documentId: string;
    expectedRevision: number;
    section: ModelingDocumentSection;
    content: string;
    assertions?: StructuredModelingAssertion[];
    actorUserId: string;
  }): Promise<SourceModelingDocument>;
  markReady(input: {
    documentId: string;
    expectedRevision: number;
    actorUserId: string;
  }): Promise<SourceModelingDocument>;
};
