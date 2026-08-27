import type { WorkspaceRole } from '../collaboration/types.ts';
import type { ModelBrowserObjectRef } from '../model-browser/types.ts';
import type { ModelingDocumentSection } from '../modeling-document-bridge/types.ts';
import type { ContentReference, SourceDocumentBlock, StructuredValue } from '../source-documents/types.ts';
import type { GuanyijiaBlockChangePreview } from './guanyijia-workbench-runtime.ts';
import type { ConflictHunk, StorySourceAuthority, StorySourceClass } from '../guanyijia-standardization-story/types.ts';

export type ReviewAssistantContextSelection = {
  timelineItemId?: string;
  sourceId?: string;
  documentId?: string;
  section?: ModelingDocumentSection;
  blockId?: string;
  conflictId?: string;
  evidenceRef?: string;
  objectRef?: ModelBrowserObjectRef;
};

export type ReviewAssistantTarget =
  | { kind: 'SOURCE_DOCUMENT'; documentId: string; section?: ModelingDocumentSection; blockId?: string }
  | { kind: 'CONFLICT'; conflictId: string }
  | { kind: 'EVIDENCE'; sourceId: string; blockId: string; evidenceRef: string }
  | { kind: 'AFFECTED_OBJECT'; objectRef: ModelBrowserObjectRef };

export type AssistantStructuredChangeProposal = {
  schemaVersion: 1;
  proposalId: string;
  runId: string;
  sourceId: string;
  documentId: string;
  documentRevision: number;
  blockId: string;
  change: { blockId: string; label?: string; value?: StructuredValue };
  beforeBlock: SourceDocumentBlock;
  preview: GuanyijiaBlockChangePreview;
  previewSha256: `sha256:${string}`;
  createdBy: string;
  createdAt: string;
};

type ExplanationResponse = {
  kind: 'SOURCE_EXPLANATION' | 'CONFLICT_EXPLANATION' | 'EVIDENCE_EXPLANATION';
  title: string;
  body: string[];
  targets: ReviewAssistantTarget[];
};

export type ReviewAssistantResponse =
  | ExplanationResponse
  | { kind: 'AFFECTED_OBJECTS'; title: string; body: string[]; targets: ReviewAssistantTarget[] }
  | { kind: 'OPEN_TARGET'; title: string; body: string[]; target: ReviewAssistantTarget }
  | { kind: 'PATCH_PREVIEW'; title: string; body: string[]; proposal: AssistantStructuredChangeProposal }
  | { kind: 'BOUNDARY' | 'SUPPORTED_SCOPE'; title: string; body: string[] };

export type ReviewAssistantAuditResponse = Exclude<ReviewAssistantResponse, { kind: 'PATCH_PREVIEW' }> | {
  kind: 'PATCH_PREVIEW';
  title: string;
  body: string[];
  proposalId: string;
  proposalRef: ContentReference;
};

export type ReviewAssistantTurnArtifact = {
  schemaVersion: 1;
  turnId: string;
  runId: string;
  actorUserId: string;
  message: string;
  selection: ReviewAssistantContextSelection;
  knowledgeSourceRevisions: Array<{
    sourceId: string;
    documentId: string;
    documentRevision: number;
  }>;
  response: ReviewAssistantAuditResponse;
  createdAt: string;
};

export type ReviewAssistantCancellationArtifact = {
  schemaVersion: 1;
  cancellationId: string;
  runId: string;
  proposalId: string;
  documentId: string;
  documentRevision: number;
  cancelledBy: string;
  cancelledAt: string;
};

export type ReviewAssistantHistoryItem = {
  eventId: string;
  sequence: number;
  position: number;
  actorUserId: string;
  message: string;
  response: ReviewAssistantResponse;
  createdAt: string;
  proposalStatus?: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'SUPERSEDED';
};

export type ReviewAssistantHistoryPage = {
  items: ReviewAssistantHistoryItem[];
  nextCursor: string | null;
  total: number;
  pendingProposal?: ReviewAssistantHistoryItem;
};

export type ReviewAssistantAccess = {
  active: boolean;
  role: WorkspaceRole;
};

export type ReviewAssistantSourceFact = {
  sourceId: string;
  sourceName: string;
  sourceClass: StorySourceClass;
  authority: StorySourceAuthority;
  snapshotId: string;
  versionRef: string;
  readSummary: string;
  documentId?: string;
  documentRevision?: number;
  blocks: SourceDocumentBlock[];
  evidenceLocators: Record<string, Record<string, StructuredValue>>;
};

export type ReviewAssistantConflictFact = {
  conflictId: string;
  title: string;
  hunk?: ConflictHunk;
  affectedObjectRefs: ModelBrowserObjectRef[];
};

export type ReviewAssistantKnowledge = {
  sources: ReviewAssistantSourceFact[];
  conflicts: ReviewAssistantConflictFact[];
  currentSourceId?: string;
  currentDocumentId?: string;
  currentBlockId?: string;
  currentConflictId?: string;
};
