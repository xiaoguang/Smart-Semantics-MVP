import assert from 'node:assert/strict';
import test from 'node:test';
import { technicalSourceIdentityMismatches } from './technical-details-identity.ts';

const sourceIds = [
  'guanyijia_mysql',
  'guanyijia_github',
  'guanyijia_official_docs',
  'guanyijia_demo_policy',
  'guanyijia_semantica_demo',
];

const run = {
  runId: 'standardization-run-1',
  sources: sourceIds.map((sourceId, index) => ({
    sourceId,
    order: index + 1,
    documentId: `source-document-${index + 1}`,
    documentRevision: 1,
  })),
  timeline: sourceIds.map((sourceId, index) => ({
    type: 'DOCUMENT_GENERATED',
    sourceId,
    sourceDocumentId: `source-document-${index + 1}`,
    sourceDocumentRevision: 1,
  })),
};

const documents = sourceIds.map((sourceId, index) => ({
  documentId: `source-document-${index + 1}`,
  revision: 1,
  sectionsRef: `sha256:${String(index + 1).repeat(64)}`,
  assertionsRef: `sha256:${String(index + 2).repeat(64)}`,
  blocksRef: `sha256:${String(index + 3).repeat(64)}`,
  markdownRef: `sha256:${String(index + 4).repeat(64)}`,
  markdownSha256: String(index + 4).repeat(64),
}));

const rows = sourceIds.map((sourceId, index) => ({
  sourceId,
  documentId: `source-document-${index + 1}`,
  revision: 'r1',
  sectionsRef: documents[index]!.sectionsRef,
  assertionsRef: documents[index]!.assertionsRef,
  blocksRef: documents[index]!.blocksRef,
  markdownRef: documents[index]!.markdownRef,
  markdownSha256: documents[index]!.markdownSha256,
}));

test('技术详情 identity validator binds rows to persisted run and document metadata', () => {
  assert.deepEqual(technicalSourceIdentityMismatches({ rows, run, documents, expectedSourceIds: sourceIds }), []);
});

test('技术详情 identity validator rejects a static or stale document identity', () => {
  const staleRows = rows.map((row, index) => index === 0 ? { ...row, documentId: 'source-document-99' } : row);
  const mismatches = technicalSourceIdentityMismatches({
    rows: staleRows,
    run,
    documents,
    expectedSourceIds: sourceIds,
  });
  assert.ok(mismatches.some((message) => message.includes('guanyijia_mysql')));
  assert.ok(mismatches.some((message) => message.includes('source-document-99')));
});
