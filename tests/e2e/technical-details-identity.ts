export type TechnicalSourceRow = {
  sourceId: string;
  documentId: string;
  revision: string;
  blocksRef: string;
  assertionsRef: string;
  sectionsRef: string;
  markdownRef: string;
  markdownSha256: string;
};

type PersistedRunSource = {
  sourceId: string;
  order: number;
  documentId?: string;
  documentRevision?: number;
};

type PersistedTimelineEvent = {
  type: string;
  sourceId?: string;
  sourceDocumentId?: string;
  sourceDocumentRevision?: number;
};

export type PersistedTechnicalRun = {
  runId: string;
  sources: PersistedRunSource[];
  timeline: PersistedTimelineEvent[];
};

export type PersistedTechnicalDocument = {
  documentId: string;
  revision: number;
  blocksRef?: string;
  assertionsRef: string;
  sectionsRef: string;
  markdownRef: string;
  markdownSha256: string;
};

export function technicalSourceIdentityMismatches(input: {
  rows: TechnicalSourceRow[];
  run: PersistedTechnicalRun;
  documents: PersistedTechnicalDocument[];
  expectedSourceIds: readonly string[];
}): string[] {
  const mismatches: string[] = [];
  const actualSourceIds = input.rows.map((row) => row.sourceId);
  if (JSON.stringify(actualSourceIds) !== JSON.stringify(input.expectedSourceIds)) {
    mismatches.push(`sourceId/order mismatch: ${JSON.stringify(actualSourceIds)}`);
  }

  const documentIds = input.rows.map((row) => row.documentId).filter(Boolean);
  if (documentIds.some((documentId) => !documentId.trim())) {
    mismatches.push('documentId must be non-empty');
  }
  if (new Set(documentIds).size !== documentIds.length) {
    mismatches.push(`documentId must be unique: ${JSON.stringify(documentIds)}`);
  }

  for (const row of input.rows) {
    const runSource = input.run.sources.find((source) => source.sourceId === row.sourceId);
    if (!runSource) {
      mismatches.push(`${row.sourceId}: missing persisted run source step`);
      continue;
    }
    const document = input.documents.find((candidate) => candidate.documentId === row.documentId);
    if (!document) {
      mismatches.push(`${row.sourceId}: document ${row.documentId} missing from SourceDocumentRuntime metadata`);
      continue;
    }
    if (!runSource.documentId || !Number.isSafeInteger(runSource.documentRevision)) {
      mismatches.push(`${row.sourceId}: persisted run source has no document identity`);
      continue;
    }
    if (row.documentId !== runSource.documentId) {
      mismatches.push(`${row.sourceId}: row documentId ${row.documentId} != run documentId ${runSource.documentId}`);
    }
    if (row.revision !== `r${runSource.documentRevision}`) {
      mismatches.push(`${row.sourceId}: row revision ${row.revision} != run revision r${runSource.documentRevision}`);
    }
    if (document.revision !== runSource.documentRevision) {
      mismatches.push(`${row.sourceId}: metadata revision ${document.revision} != run revision ${runSource.documentRevision}`);
    }
    const expectedReferences: Array<keyof Pick<
      TechnicalSourceRow,
      'blocksRef' | 'assertionsRef' | 'sectionsRef' | 'markdownRef'
    >> = ['blocksRef', 'assertionsRef', 'sectionsRef', 'markdownRef'];
    for (const key of expectedReferences) {
      if (row[key] !== document[key]) {
        mismatches.push(`${row.sourceId}: ${key} does not match SourceDocumentRuntime metadata`);
      }
    }
    if (row.markdownSha256 !== document.markdownSha256) {
      mismatches.push(`${row.sourceId}: markdownSha256 does not match SourceDocumentRuntime metadata`);
    }
    if (document.markdownRef !== `sha256:${document.markdownSha256}`) {
      mismatches.push(`${row.sourceId}: markdownRef is not the persisted markdown SHA`);
    }

    const identityEvents = input.run.timeline.filter((event) => (
      (event.type === 'DOCUMENT_GENERATED' || event.type === 'DOCUMENT_REVISED')
      && event.sourceId === row.sourceId
      && event.sourceDocumentId === document.documentId
      && event.sourceDocumentRevision === document.revision
    ));
    if (identityEvents.length !== 1) {
      mismatches.push(`${row.sourceId}: expected one matching DOCUMENT_GENERATED/REVISED event, found ${identityEvents.length}`);
    }
  }
  return mismatches;
}
