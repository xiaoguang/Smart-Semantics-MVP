import assert from 'node:assert/strict';
import test from 'node:test';
import { bindDemoContentRun } from '../guanyijia-demo-content/demo-content-review.ts';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';
import { listScriptedReviewEdits } from './scripted-review-edits.ts';
import { createGuanyijiaReviewedSourceDocumentReader } from './guanyijia-reviewed-source-documents.ts';
import type { StandardizationRun } from '../standardization-run/types.ts';
import type { SourceDocumentBlock, SourceModelingDocument } from '../source-documents/types.ts';

test('完整来源 Reader 只读取本运行绑定的五份 V6 九章文档，并应用已保存的用户修改', async () => {
  const story = createGuanyijiaStandardizationStory();
  const sources = story.listSources();
  const run = {
    runId: 'run-rich-reviewed-source-documents',
    projectId: 'guanyijia_erp', scenarioKey: 'guanyijia-five-source-v1',
    batchId: 'batch-rich-reviewed-source-documents', revision: 12, status: 'READY_FOR_OUTPUT',
    sources: sources.map((source, index) => ({
      sourceId: source.sourceId,
      sourceName: source.sourceName,
      sourceType: source.sourceClass,
      snapshotId: source.snapshotId,
      order: index + 1,
      status: 'ALIGNED',
      documentId: `document:${source.sourceId}`,
      documentRevision: 1,
      introducedConflictIds: [],
      resolvedConflictIds: [],
    })),
    timeline: [], createdBy: 'user-author', createdAt: '2026-08-28T00:00:00.000Z',
    updatedAt: '2026-08-28T00:00:00.000Z',
  } as StandardizationRun;
  const documents = new Map(sources.map((source) => {
    const document = {
      documentId: `document:${source.sourceId}`,
      revision: 1,
      sourceSnapshotId: source.snapshotId,
      sourceName: source.sourceName,
    } as SourceModelingDocument;
    return [document.documentId, document] as const;
  }));
  const githubEdit = listScriptedReviewEdits({ sourceId: 'guanyijia_github' })[0];
  assert.ok(githubEdit);
  const reader = createGuanyijiaReviewedSourceDocumentReader({
    sourceDocuments: {
      async read(documentId) { return documents.get(documentId) ?? null; },
      async readBlocks(documentId) {
        return documentId === 'document:guanyijia_github'
          ? [{ blockId: githubEdit.formalBlockId, label: '已确认的负库存控制说明' } as SourceDocumentBlock]
          : [];
      },
    },
    contentBindingForRun(candidate) {
      return bindDemoContentRun({
        runId: candidate.runId,
        formalSources: candidate.sources.map((source) => ({
          sourceId: source.sourceId,
          snapshotId: source.snapshotId!,
        })),
      });
    },
  });

  const reviewed = await reader.readForRun(run);

  assert.deepEqual(reviewed.map((document) => document.sourceId), sources.map((source) => source.sourceId));
  assert.ok(reviewed.every((document) => document.sections.length === 9));
  assert.ok(reviewed.every((document) => document.sections.every((section) => section.markdown.trim())));
  const github = reviewed.find((document) => document.sourceId === 'guanyijia_github');
  assert.ok(github?.markdown.includes('已确认的负库存控制说明'));
  assert.equal(github?.changes.length, 1);
  assert.equal(github?.changes[0]?.field, 'TITLE');
  assert.equal(github?.changes[0]?.after, '已确认的负库存控制说明');
});
