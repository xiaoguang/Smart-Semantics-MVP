import assert from 'node:assert/strict';
import test from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import {
  createGuanyijiaStandardizationStory,
  defaultGuanyijiaPolicyDocuments,
} from '../guanyijia-standardization-story/index.ts';
import { projectConflictResolution } from '../guanyijia-standardization-story/conflict-resolution.ts';
import { createMemoryContentStore } from '../source-documents/runtime.ts';
import { createSourceDocumentRuntime } from '../source-documents/runtime.ts';
import { createStandardizationRunRuntime } from '../standardization-run/runtime.ts';
import { standardizationMetadataStorageKey } from '../standardization-run/standardization-metadata-store.ts';
import {
  createGuanyijiaWorkbenchRuntime,
  continueCurrentDocumentReviewAfterApply,
  factsInspectorFor,
  guanyijiaActiveRunPointerStorageKeyFor,
  openCurrentDocumentReview,
  pageCurrentDocumentBlocks,
  validateGuanyijiaCorroborationReceipt,
  validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions,
  validateGuanyijiaAssistantArtifactAgainstPersistedRun,
  validateGuanyijiaAssistantConfirmationAgainstPersistedRevision,
  validateGuanyijiaResolutionArtifactAgainstPersistedRevisions,
  type GuanyijiaWorkbenchCommand,
} from './guanyijia-workbench-runtime.ts';
import { projectReviewAssistantHistory } from './review-assistant-panel-model.ts';

class MemoryStorage implements Pick<Storage, 'getItem' | 'setItem'> {
  readonly values = new Map<string, string>();

  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

class ResolutionPointerFailureStorage extends MemoryStorage {
  failNextResolutionCompletion = false;

  override setItem(key: string, value: string) {
    const pointer = JSON.parse(value) as { commandFingerprints?: Record<string, string> };
    if (this.failNextResolutionCompletion && pointer.commandFingerprints?.['resolve:saga']) {
      this.failNextResolutionCompletion = false;
      throw new Error('模拟决定完成指针写入失败');
    }
    super.setItem(key, value);
  }
}

function fixture() {
  const metadataStorage = new MemoryStorage();
  const pointerStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const sourceDocuments = createSourceDocumentRuntime({ metadataStorage, contentStore });
  const story = createGuanyijiaStandardizationStory();
  const runs = createStandardizationRunRuntime({
    metadataStorage,
    contentStore,
    resolutionArtifactValidator: (artifact, run) => (
      validateGuanyijiaResolutionArtifactAgainstPersistedRevisions({
        artifact, run, sourceDocuments, story,
      })
    ),
    corroborationReceiptValidator: (receipt, run) => (
      validateGuanyijiaCorroborationReceipt({ receipt, run, story })
    ),
    corroborationReceiptSetValidator: (projection, run) => (
      validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions({
        projection, run, sourceDocuments, story,
      })
    ),
    assistantArtifactValidator: (turn, proposal, run, projection) => (
      validateGuanyijiaAssistantArtifactAgainstPersistedRun({
        turn, proposal, run, sourceDocuments, story, eventIndex: projection.eventIndex,
      })
    ),
    assistantPatchConfirmationValidator: (confirmation, run, projection) => (
      validateGuanyijiaAssistantConfirmationAgainstPersistedRevision({
        confirmation, run, sourceDocuments, story, contentStore, projection,
      })
    ),
  });
  const runtime = createGuanyijiaWorkbenchRuntime({
    pointerStorage,
    standardizationRuns: runs,
    sourceDocuments,
    story,
    contentStore,
  });
  return { runtime, metadataStorage, pointerStorage, contentStore, sourceDocuments, runs, story };
}

test('production review-window facade keeps all six streams summary-only until verified content is opened', async () => {
  const { runtime, contentStore } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'window:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'window:read:mysql'));
  await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'window:assistant',
    expectedRevision: mysql.run!.revision, actorUserId: 'user-author',
    message: '当前来源是什么？', selection: { sourceId: 'guanyijia_mysql' },
  });
  let bodyReads = 0;
  const originalGet = contentStore.get.bind(contentStore);
  contentStore.get = async (ref) => { bodyReads += 1; return originalGet(ref); };
  const runId = mysql.run!.runId;
  const read = (stream: 'TIMELINE' | 'ISSUES' | 'EVIDENCE' | 'ASSISTANT_HISTORY'
    | 'DOCUMENT_BLOCKS' | 'DELIVERABLE_SECTIONS') => runtime.readReviewWindow({
    actorUserId: 'user-author', runId, epoch: `revision:${mysql.run!.revision}`, stream, filter: 'all',
  });

  const [timeline, issues, evidence, assistant, blocks, deliverable] = await Promise.all([
    read('TIMELINE'), read('ISSUES'), read('EVIDENCE'), read('ASSISTANT_HISTORY'),
    read('DOCUMENT_BLOCKS'), read('DELIVERABLE_SECTIONS'),
  ]);

  assert.equal(timeline.items.length, 4);
  assert.equal(issues.items.length, 0);
  assert.equal(evidence.items.length, 20);
  assert.equal(assistant.items.length, 1);
  assert.equal(blocks.items.length, 20);
  assert.equal(blocks.total, 81);
  assert.equal(deliverable.items.length, 0);
  assert.equal(bodyReads, 0, 'summary queries must not open event, block, evidence, assistant, or deliverable bodies');
  for (const page of [timeline, evidence, assistant, blocks]) {
    assert.ok(page.items.every((item) => item.contentRef
      && item.contentSha256 === item.contentRef.slice('sha256:'.length)));
  }

  const blockBody = await runtime.readReviewContent({
    actorUserId: 'user-author', runId,
    contentRef: blocks.items[0]!.contentRef!,
    expectedSha256: blocks.items[0]!.contentSha256!,
  });
  assert.equal(bodyReads, 1);
  assert.equal((JSON.parse(blockBody.content) as unknown[]).length, 81);
});

test('a new five-source run pins the frozen reading-material version and preserves it after reload', async () => {
  const { runtime, pointerStorage, sourceDocuments, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'content-binding:start'));
  const contentSourceSnapshotIds: Record<string, string> = {
    guanyijia_mysql: '20260813T032528Z-abb0502c7d79',
    guanyijia_github: 'github-b3ab269b05070d40',
    guanyijia_official_docs: 'guanyijia-demo-official-v1',
    guanyijia_demo_policy: 'guanyijia-demo-policy-content-v1',
    guanyijia_semantica_demo: 'guanyijia-demo-terminology-v1',
  };

  assert.deepEqual(started.contentBinding, {
    runId: started.run!.runId,
    storyKey: 'guanyijia-five-source-v1',
    contentSnapshotId: 'guanyijia-demo-content-v6-20260826',
    contentSha256: 'sha256:6da765901357f4b0856e3b1c5aaab159aec75c895b60b1bb2ea11539997f8180',
    sourceBindings: [
      ['guanyijia_mysql', 'SNAPSHOT_REFERENCE'],
      ['guanyijia_github', 'SOURCE_NATIVE'],
      ['guanyijia_official_docs', 'DEMO_AUTHORED'],
      ['guanyijia_demo_policy', 'DEMO_AUTHORED'],
      ['guanyijia_semantica_demo', 'DERIVED_DEMO'],
    ].map(([sourceId, origin], index) => ({
      sourceId,
      formalSnapshotId: started.run!.sources[index]!.snapshotId,
      contentSourceSnapshotId: contentSourceSnapshotIds[sourceId]!,
      origin,
    })),
  });

  const restoredRuntime = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: runs, sourceDocuments,
  });
  const restored = await restoredRuntime.read('user-author');
  assert.deepEqual(restored.contentBinding, started.contentBinding);
});

test('production review shell reaches React without source bodies and hydrates only the selected verified document window', async () => {
  const { runtime, sourceDocuments, contentStore } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'shell:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'shell:read:mysql'));
  let sourceBodyReads = 0;
  for (const method of ['readBlocks', 'readAssertions', 'readSections', 'readMarkdown'] as const) {
    const original = sourceDocuments[method].bind(sourceDocuments);
    sourceDocuments[method] = (async (...args: Parameters<typeof original>) => {
      sourceBodyReads += 1;
      return original(...args);
    }) as typeof sourceDocuments[typeof method];
  }

  const shell = await runtime.readReviewShell('user-author');

  assert.equal(shell.run?.runId, mysql.run!.runId);
  assert.equal(shell.current?.compilation, undefined);
  assert.deepEqual(shell.current?.compilationSummary, {
    blockCount: 81,
    sectionCount: 9,
    firstReviewSection: 'GOAL',
  });
  assert.equal(shell.timeline.length, 4);
  const generatedDocument = shell.timeline.find((item) => item.kind === 'DOCUMENT_GENERATED');
  assert.equal(generatedDocument?.summary, '审阅文档已准备好；打开后可查看审阅结论和来源材料。');
  assert.doesNotMatch(generatedDocument?.summary ?? '', /章节|识别结果|81/u);
  assert.equal(sourceBodyReads, 0, 'the initial React shell must not read source block/section/assertion/markdown bodies');
  assert.equal(openCurrentDocumentReview(shell, { focusToken: 0 }).selectedSection, 'GOAL');

  const bodyRefs: string[] = [];
  const originalContentGet = contentStore.get.bind(contentStore);
  contentStore.get = async (contentRef) => {
    bodyRefs.push(contentRef);
    return originalContentGet(contentRef);
  };
  const opened = await runtime.readReviewCurrentDocument({
    actorUserId: 'user-author', runId: mysql.run!.runId,
    documentId: mysql.current!.document!.documentId,
    epoch: `revision:${mysql.run!.revision}`,
    filter: 'section:GOAL',
  });
  assert.ok('window' in opened, 'document selection must return its production review-window page');
  if (!('window' in opened)) return;
  assert.equal(opened.window.items.length, 20);
  assert.equal(opened.current.compilation, undefined, 'complete compilation bodies must never cross into React');
  assert.equal(opened.current.reviewCompilation?.blocks.length, 20);
  assert.deepEqual(
    opened.current.reviewCompilation?.blocks.map((block) => block.stableCode),
    opened.window.items.map((item) => item.title),
  );
  assert.equal('markdown' in opened.current.reviewCompilation!, false);
  assert.equal('sections' in opened.current.reviewCompilation!, false);
  assert.equal('assertions' in opened.current.reviewCompilation!, false);
  assert.deepEqual(bodyRefs, [mysql.current!.document!.blocksRef]);
  assert.equal(sourceBodyReads, 0, 'selected document bodies must cross only the verified review-content seam');
});

test('production assistant consumer first paint keeps history bodies unopened until the user loads history', async () => {
  const { runtime, contentStore } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'assistant-first-paint:start'));
  const current = await runtime.execute(command(
    'READ_NEXT_SOURCE', started.run!.revision, 'assistant-first-paint:read',
  ));
  await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'assistant-first-paint:ask',
    expectedRevision: current.run!.revision, actorUserId: 'user-author',
    message: '当前来源是什么？', selection: { sourceId: 'guanyijia_mysql' },
  });
  let bodyReads = 0;
  const originalGet = contentStore.get.bind(contentStore);
  contentStore.get = async (ref) => { bodyReads += 1; return originalGet(ref); };

  const summary = await runtime.readReviewWindow({
    actorUserId: 'user-author', runId: current.run!.runId,
    epoch: `revision:${current.run!.revision}`, stream: 'ASSISTANT_HISTORY', filter: 'all',
    direction: 'BACKWARD',
  });
  const panel = projectReviewAssistantHistory({
    history: [], historyTotal: summary.total, historyLoaded: false,
    pendingProposal: undefined,
  });
  assert.equal(panel.loadHistoryLabel, '查看最近 1 条审阅记录');
  assert.deepEqual(panel.visibleHistory, []);
  assert.equal(bodyReads, 0, 'assistant first paint must cross only the production summary seam');
});

test('production document facade hydrates the next cursor page through verified content', async () => {
  const { runtime, contentStore } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'document-next-page:start'));
  const current = await runtime.execute(command(
    'READ_NEXT_SOURCE', started.run!.revision, 'document-next-page:read',
  ));
  const bodyRefs: string[] = [];
  const originalGet = contentStore.get.bind(contentStore);
  contentStore.get = async (ref) => {
    bodyRefs.push(ref);
    return originalGet(ref);
  };
  const input = {
    actorUserId: 'user-author', runId: current.run!.runId,
    documentId: current.current!.document!.documentId,
    epoch: `revision:${current.run!.revision}`, filter: 'section:GOAL',
  };
  const first = await runtime.readReviewCurrentDocument(input);
  const second = await runtime.readReviewCurrentDocument({
    ...input, cursor: first.window.nextCursor!,
  });
  assert.equal(first.window.items.length, 20);
  assert.equal(second.window.items.length, 20);
  assert.equal(new Set([
    ...first.current.reviewCompilation!.blocks,
    ...second.current.reviewCompilation!.blocks,
  ].map((block) => block.blockId)).size, 40);
  assert.deepEqual(bodyRefs, [current.current!.document!.blocksRef, current.current!.document!.blocksRef]);
});

test('已审阅来源可按持久化文档身份重新只读打开，而不取代当前待审来源', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'history-document:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'history-document:mysql'));
  const afterMysqlReview = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'history-document:complete-mysql',
  ));
  const github = await runtime.execute(command(
    'READ_NEXT_SOURCE', afterMysqlReview.run!.revision, 'history-document:github',
  ));

  const reopened = await runtime.readReviewCurrentDocument({
    actorUserId: 'user-author',
    runId: github.run!.runId,
    documentId: mysql.current!.document!.documentId,
    epoch: `revision:${github.run!.revision}`,
    filter: 'document=historical',
  });

  assert.equal(github.current?.source.sourceId, 'guanyijia_github');
  assert.equal(reopened.current.source.sourceId, 'guanyijia_mysql');
  assert.equal(reopened.current.sourceStep.status, 'ALIGNED');
  assert.equal(reopened.current.document?.documentId, mysql.current?.document?.documentId);
  assert.equal(reopened.current.reviewCompilation?.blocks.length, 20);
});

test('控制器命令目标拒绝在查看历史文档时完成当前待审来源', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'history-target:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'history-target:read:mysql'));
  const reviewedMysql = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'history-target:review:mysql',
  ));
  const github = await runtime.execute(command(
    'READ_NEXT_SOURCE', reviewedMysql.run!.revision, 'history-target:read:github',
  ));

  await assert.rejects(
    runtime.execute({
      ...command('COMPLETE_CURRENT_DOCUMENT_REVIEW', github.run!.revision, 'history-target:forged-complete'),
      documentId: mysql.current!.document!.documentId,
      documentRevision: mysql.current!.document!.revision,
    }),
    /历史文档|当前待审阅来源文档/u,
  );

  await assert.rejects(
    runtime.execute({
      ...command('PREVIEW_SCRIPTED_REVIEW_EDIT', github.run!.revision, 'history-target:forged-preview'),
      documentId: mysql.current!.document!.documentId,
      documentRevision: mysql.current!.document!.revision,
      editId: 'scripted:github:clarify-negative-stock',
      patch: { label: '不应写入历史文档的修改' },
    }),
    /历史文档|当前待审阅来源文档/u,
  );
});

function command(
  type: GuanyijiaWorkbenchCommand['type'],
  expectedRevision: number,
  commandId = `command:${type}:${expectedRevision}`,
): GuanyijiaWorkbenchCommand {
  return { type, expectedRevision, commandId, actorUserId: 'user-author' };
}

async function advanceToSemanticaDocumentReady(runtime: ReturnType<typeof fixture>['runtime']) {
  let snapshot = await runtime.execute(command('START_RUN', 0, 'normalized-flow:start'));
  const readAndReview = async (source: string) => {
    const read = await runtime.execute(command(
      'READ_NEXT_SOURCE', snapshot.run!.revision, `normalized-flow:read:${source}`,
    ));
    snapshot = await runtime.execute(command(
      'COMPLETE_CURRENT_DOCUMENT_REVIEW', read.run!.revision, `normalized-flow:review:${source}`,
    ));
  };
  const resolve = async (
    conflictId: string,
    strategy: 'KEEP_CURRENT' | 'MERGE' | 'DEFER_AS_GAP',
    reason: string,
  ) => {
    const preview = await runtime.previewCurrentConflict({
      runId: snapshot.run!.runId, conflictId, strategy,
    });
    snapshot = await runtime.execute({
      type: 'RESOLVE_CURRENT_CONFLICT',
      commandId: `normalized-flow:resolve:${conflictId}`,
      expectedRevision: snapshot.run!.revision,
      actorUserId: 'user-author',
      conflictId,
      strategy,
      reason,
      expectedHunkSha256: preview.hunk.hunkSha256,
    });
  };

  await readAndReview('mysql');
  await readAndReview('github');
  await resolve('gyj-conflict-debt-schema', 'KEEP_CURRENT', '部署库为当前工作标准。');
  await readAndReview('official');
  await readAndReview('policy');
  await resolve('gyj-conflict-negative-stock', 'MERGE', '保留实现事实并登记制度缺口。');
  await resolve('gyj-conflict-status-nine', 'DEFER_AS_GAP', '状态九业务含义待确认。');
  return runtime.execute(command(
    'READ_NEXT_SOURCE', snapshot.run!.revision, 'normalized-flow:read:semantica',
  ));
}

async function advanceToReadyForOutput(runtime: ReturnType<typeof fixture>['runtime']) {
  const semantica = await advanceToSemanticaDocumentReady(runtime);
  return runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', semantica.run!.revision, 'normalized-flow:review:semantica',
  ));
}

test('technical resolution summaries stay lazy, verify three artifacts, and reject a stale revision', async () => {
  const { runtime, runs, contentStore } = fixture();
  const ready = await advanceToReadyForOutput(runtime);
  assert.equal(ready.run?.status, 'READY_FOR_OUTPUT');
  assert.ok(ready.current?.document?.blocksRef);
  const runId = ready.run!.runId;
  let eventPayloadReads = 0;
  const originalReadEventPayload = runs.readEventPayload.bind(runs);
  runs.readEventPayload = async (...args: Parameters<typeof originalReadEventPayload>) => {
    eventPayloadReads += 1;
    return originalReadEventPayload(...args);
  };
  let bodyReads = 0;
  const bodyRefs: string[] = [];
  const originalContentGet = contentStore.get.bind(contentStore);
  contentStore.get = async (ref) => {
    bodyReads += 1;
    bodyRefs.push(ref);
    return originalContentGet(ref);
  };

  const shell = await runtime.readReviewShell('user-author');
  assert.deepEqual(shell.resolutions, []);
  assert.equal(eventPayloadReads, 0);
  assert.deepEqual(bodyRefs, []);

  const summaries = await (runtime as typeof runtime & {
    readTechnicalResolutionSummaries(input: {
      actorUserId: string;
      runId: string;
      revision: number;
    }): Promise<Array<{
      resolutionId: string;
      sourceId: string;
      title: string;
      hunkSha256: string;
      previewSha256: string;
      eventId: string;
      sequence: number;
      createdAt: string;
    }>>;
  }).readTechnicalResolutionSummaries({ actorUserId: 'user-author', runId, revision: ready.run!.revision });
  assert.equal(summaries.length, 3);
  assert.equal(eventPayloadReads, 3);
  assert.equal(bodyReads, 3);
  assert.deepEqual(summaries.map(({ title }) => title), [
    '欠款字段结构冲突', '负库存制度与实现冲突', '状态 9 业务含义冲突',
  ]);
  assert.ok(summaries.every((summary) => /^sha256:[a-f0-9]{64}$/u.test(summary.hunkSha256)));
  assert.ok(summaries.every((summary) => /^sha256:[a-f0-9]{64}$/u.test(summary.previewSha256)));
  assert.ok(summaries.every((summary) => summary.eventId.startsWith(`${runId}:event:`)));

  await assert.rejects(
    () => (runtime as typeof runtime & {
      readTechnicalResolutionSummaries(input: {
        actorUserId: string;
        runId: string;
        revision: number;
      }): Promise<unknown>;
    }).readTechnicalResolutionSummaries({ actorUserId: 'user-author', runId, revision: ready.run!.revision - 1 }),
    /不属于当前运行|当前运行/,
  );
});

test('定版前重新处理历史来源差异会保留旧决定，并让新决定成为下一份结果的有效决定', async () => {
  const { runtime, runs } = fixture();
  const ready = await advanceToReadyForOutput(runtime);
  assert.equal(ready.run?.status, 'READY_FOR_OUTPUT');

  const preview = await runtime.previewCurrentConflict({
    runId: ready.run!.runId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'DEFER_AS_GAP',
  });
  const updated = await runtime.execute({
    type: 'REPLACE_RESOLVED_CONFLICT',
    commandId: 'replace:debt:as-gap',
    expectedRevision: ready.run!.revision,
    actorUserId: 'user-author',
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'DEFER_AS_GAP',
    reason: '部署结构和源码记录尚不能确认同一版本，登记为资料缺口。',
    expectedHunkSha256: preview.hunk.hunkSha256,
  });

  assert.equal(updated.run?.status, 'READY_FOR_OUTPUT');
  assert.equal(updated.resolutions.find((item) => item.hunk.conflictId === 'gyj-conflict-debt-schema')?.strategy, 'DEFER_AS_GAP');
  assert.equal(updated.timeline.filter((item) => item.kind === 'CONFLICT_RESOLVED').length, 3);
  assert.deepEqual(updated.timeline.findLast((item) => item.kind === 'CONFLICT_DECISION_REPLACED')?.action, {
    type: 'OPEN_CONFLICT', conflictId: 'gyj-conflict-debt-schema',
  });
  const summaries = await runtime.readTechnicalResolutionSummaries({
    actorUserId: 'user-author', runId: updated.run!.runId, revision: updated.run!.revision,
  });
  assert.equal(summaries.length, 4, '审计读取同时保留初始决定和新的替代决定');
  assert.ok(summaries.some((item) => item.resolutionId.startsWith('replacement:')));
  const persisted = await runs.read(updated.run!.runId);
  assert.equal(persisted?.timeline.filter((event) => event.type === 'CONFLICT_RESOLVED').length, 3);
  assert.equal(persisted?.timeline.filter((event) => event.type === 'CONFLICT_DECISION_REPLACED').length, 1);
});

test('technical resolution summaries reject a tampered verified artifact', async () => {
  const { runtime, runs, contentStore, metadataStorage } = fixture();
  const ready = await advanceToReadyForOutput(runtime);
  const runId = ready.run!.runId;
  const raw = metadataStorage.getItem(standardizationMetadataStorageKey);
  assert.ok(raw);
  const state = JSON.parse(raw!) as { runs: Array<{ runId: string; timeline: Array<{
    eventId: string;
    type: string;
    payloadRef: `sha256:${string}`;
  }> }> };
  const storedRun = state.runs.find((candidate) => candidate.runId === runId);
  const event = storedRun?.timeline.find((candidate) => candidate.type === 'CONFLICT_RESOLVED');
  assert.ok(event);
  const artifact = JSON.parse(await runs.readEventPayload(runId, event!.eventId)) as Record<string, unknown>;
  event!.payloadRef = await contentStore.put(JSON.stringify({
    ...artifact,
    previewSha256: `sha256:${'0'.repeat(64)}`,
  }));
  metadataStorage.setItem(standardizationMetadataStorageKey, JSON.stringify(state));

  await assert.rejects(
    () => (runtime as typeof runtime & {
      readTechnicalResolutionSummaries(input: {
        actorUserId: string;
        runId: string;
        revision: number;
      }): Promise<unknown>;
    }).readTechnicalResolutionSummaries({ actorUserId: 'user-author', runId, revision: ready.run!.revision }),
    /Artifact无法通过Story纯投影复算|Artifact.*不一致/,
  );
});

test('START_RUN creates the approved five-source run without touching the frozen V1 artifact', async () => {
  const { runtime } = fixture();

  const snapshot = await runtime.execute(command('START_RUN', 0));

  assert.equal(snapshot.run?.projectId, 'guanyijia_erp');
  assert.deepEqual(snapshot.run?.sources.map((source) => source.sourceId), [
    'guanyijia_mysql',
    'guanyijia_github',
    'guanyijia_official_docs',
    'guanyijia_demo_policy',
    'guanyijia_semantica_demo',
  ]);
  assert.deepEqual(snapshot.nextAction, { type: 'READ_NEXT_SOURCE', label: '载入数据库快照' });
  assert.equal(guanyijiaFrozenModelingArtifact.artifactId, 'artifact-guanyijia-v1-40c8572864bd');
  assert.equal(guanyijiaFrozenModelingArtifact.markdown.sha256, '5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210');
  assert.equal(guanyijiaFrozenModelingArtifact.semanticPayload?.sha256, 'c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248');
});

test('READ_NEXT_SOURCE reads MySQL, registers one nine-section document, and keeps Markdown out of events', async () => {
  const { runtime, sourceDocuments, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));

  const snapshot = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));

  assert.deepEqual(snapshot.run?.timeline.map((event) => event.type), [
    'SOURCE_READ_STARTED',
    'SOURCE_READ_COMPLETED',
    'DOCUMENT_GENERATED',
  ]);
  assert.equal(snapshot.current?.source.sourceId, 'guanyijia_mysql');
  assert.equal(snapshot.current?.compilation?.blocks.length, 81);
  assert.equal(Object.keys(snapshot.current?.compilation?.sections ?? {}).length, 9);
  const documents = await sourceDocuments.list('guanyijia_erp');
  assert.equal(documents.length, 1);
  assert.equal(documents[0]?.documentId, snapshot.current?.document?.documentId);
  const generatedPayload = await runs.readEventPayload(snapshot.run!.runId, snapshot.run!.timeline[2]!.eventId);
  assert.doesNotMatch(generatedPayload, /^---/);
  assert.doesNotMatch(generatedPayload, /## 1\. 概览/);
  assert.equal(JSON.parse(generatedPayload).documentId, documents[0]?.documentId);
});

test('source-loaded timeline receipt directs reviewers to the saved material instead of showing internal object counts', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));

  const snapshot = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));
  const receipt = snapshot.timeline.find((item) => item.kind === 'SOURCE_READ_COMPLETED');

  assert.equal(receipt?.summary, '固定快照已载入；可打开审阅事项查看已保存资料。');
  assert.doesNotMatch(receipt?.summary ?? '', /个对象|条依据|Tenant|快照身份/);
});

test('completing the MySQL document review marks the document ready before offering GitHub', async () => {
  const { runtime, sourceDocuments } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));

  const reviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW',
    mysql.run!.revision,
  ));

  assert.equal((await sourceDocuments.read(mysql.current!.document!.documentId))?.status, 'READY_FOR_ALIGNMENT');
  assert.equal(reviewed.run?.sources[0]?.status, 'ALIGNED');
  assert.deepEqual(reviewed.nextAction, { type: 'READ_NEXT_SOURCE', label: '载入GitHub代码仓库快照' });
});

test('reviewing GitHub keeps the debt-field conflict pending and permits the next source', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'read:mysql'));
  const mysqlReviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'review:mysql',
  ));
  const github = await runtime.execute(command('READ_NEXT_SOURCE', mysqlReviewed.run!.revision, 'read:github'));

  const blocked = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', github.run!.revision, 'review:github',
  ));

  assert.equal(blocked.run?.status, 'CONFLICT_BLOCKED');
  assert.deepEqual(blocked.nextAction, {
    type: 'READ_NEXT_SOURCE',
    label: '载入业务说明快照',
  });
  const conflict = blocked.timeline.find((item) => item.kind === 'CONFLICT_FOUND');
  assert.equal(conflict?.conflicts?.[0]?.title, '欠款字段结构冲突');
  assert.deepEqual(conflict?.conflicts?.[0]?.affectedObjects, ['指标：receivable_debt', '规则：deposit']);
  const official = await runtime.execute(command('READ_NEXT_SOURCE', blocked.run!.revision, 'read:official'));
  assert.equal(official.run?.status, 'REVIEWING_DOCUMENT');
  assert.equal(official.current?.source.sourceId, 'guanyijia_official_docs');
  assert.equal(official.timeline.find((item) => item.state === 'CURRENT')?.sourceId, 'guanyijia_official_docs');
  const asked = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT',
    commandId: 'read:official:assistant',
    expectedRevision: official.run!.revision,
    actorUserId: 'user-author',
    message: '当前来源的读取范围是什么？',
    selection: {},
  });
  assert.equal(asked.assistantTurnDelta?.item.response.title, '管伊佳官方核心文档');
});

test('review shell keeps the active source readable while an earlier conflict remains pending', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'active-shell:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'active-shell:read:mysql'));
  const mysqlReviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'active-shell:review:mysql',
  ));
  const github = await runtime.execute(command('READ_NEXT_SOURCE', mysqlReviewed.run!.revision, 'active-shell:read:github'));
  const githubReviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', github.run!.revision, 'active-shell:review:github',
  ));
  await runtime.execute(command('READ_NEXT_SOURCE', githubReviewed.run!.revision, 'active-shell:read:official'));

  const shell = await runtime.readReviewShell('user-author');

  assert.equal(shell.current?.source.sourceId, 'guanyijia_official_docs');
  assert.equal(shell.current?.sourceStep.status, 'DOCUMENT_READY');
  assert.equal(shell.currentConflict?.conflictId, 'gyj-conflict-debt-schema');
  assert.equal(shell.timeline.find((item) => item.state === 'CURRENT')?.sourceId, 'guanyijia_official_docs');
});

test('reloading a blocked conflict reads only the source blocks needed for the current hunk', async () => {
  const { runtime, runs, sourceDocuments } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'blocked-shell:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'blocked-shell:read:mysql'));
  const mysqlReviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'blocked-shell:review:mysql',
  ));
  const github = await runtime.execute(command(
    'READ_NEXT_SOURCE', mysqlReviewed.run!.revision, 'blocked-shell:read:github',
  ));
  const blocked = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', github.run!.revision, 'blocked-shell:review:github',
  ));
  assert.equal(blocked.run?.status, 'CONFLICT_BLOCKED');

  let eventPayloadReads = 0;
  const originalReadEventPayload = runs.readEventPayload.bind(runs);
  runs.readEventPayload = async (...args: Parameters<typeof originalReadEventPayload>) => {
    eventPayloadReads += 1;
    return originalReadEventPayload(...args);
  };
  const sourceReads = { blocks: 0, assertions: 0, sections: 0, markdown: 0 };
  for (const [method, key] of [
    ['readBlocks', 'blocks'], ['readAssertions', 'assertions'],
    ['readSections', 'sections'], ['readMarkdown', 'markdown'],
  ] as const) {
    const original = sourceDocuments[method].bind(sourceDocuments);
    sourceDocuments[method] = (async (...args: Parameters<typeof original>) => {
      sourceReads[key] += 1;
      return original(...args);
    }) as typeof sourceDocuments[typeof method];
  }

  const shell = await runtime.readReviewShell('user-author');

  assert.equal(shell.currentConflict?.conflictId, 'gyj-conflict-debt-schema');
  assert.equal(shell.current?.compilation, undefined);
  assert.equal(eventPayloadReads, 0, 'blocked shell must not expand the full event history');
  assert.deepEqual(sourceReads, { blocks: 2, assertions: 0, sections: 0, markdown: 0 });
});

test('ASK after entering CONFLICT_BLOCKED is immediately present in the production assistant history', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'history-integration:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'history-integration:read:mysql'));
  const mysqlReviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'history-integration:review:mysql',
  ));
  const github = await runtime.execute(command(
    'READ_NEXT_SOURCE', mysqlReviewed.run!.revision, 'history-integration:read:github',
  ));
  const blocked = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', github.run!.revision, 'history-integration:review:github',
  ));
  assert.equal(blocked.run?.status, 'CONFLICT_BLOCKED');

  const asked = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'history-integration:ask',
    expectedRevision: blocked.run!.revision, actorUserId: 'user-author',
    message: '当前差异是什么',
    selection: { sourceId: 'guanyijia_github', conflictId: 'gyj-conflict-debt-schema' },
  });
  assert.equal(asked.run?.timeline.at(-1)?.type, 'ASSISTANT_TURN_RECORDED');
  assert.ok(asked.assistantTurnDelta);
  assert.equal(asked.assistantTurnDelta?.total, 1);
  assert.equal(asked.assistantTurnDelta?.position, 1);
  assert.equal(asked.assistantTurnDelta?.eventId, asked.run?.timeline.at(-1)?.eventId);
  assert.equal(asked.assistantTurnDelta?.contentRef, asked.run?.timeline.at(-1)?.payloadRef);
  assert.equal(asked.assistantTurnDelta?.item.message, '当前差异是什么');
  assert.deepEqual(asked.assistantTurnDelta?.item.response.targets, [
    { kind: 'CONFLICT', conflictId: 'gyj-conflict-debt-schema' },
  ]);

  const receipt = asked.timeline.find((item) => item.kind === 'ASSISTANT_TURN_RECORDED');
  assert.equal(receipt?.summary, '审阅助手已记录本次说明；可继续查看或处理建议。');
  assert.doesNotMatch(receipt?.summary ?? '', /user-author|内容寻址|Proposal/);

  const history = await runtime.readAssistantHistory({
    actorUserId: 'user-author', runId: asked.run!.runId,
  });
  assert.equal(history.total, 1);
  assert.equal(history.items.length, 1);
  assert.equal(history.items[0]?.message, '当前差异是什么');
  assert.equal(history.items[0]?.response.kind, 'CONFLICT_EXPLANATION');
  assert.equal(history.items[0]?.response.title, '欠款字段结构冲突');
  assert.deepEqual(history.items[0]?.response.targets, [{ kind: 'CONFLICT', conflictId: 'gyj-conflict-debt-schema' }]);
});

test('七条已持久化Assistant history后ASK只返回经过校验的第八条delta并支持幂等重放', async () => {
  const { runtime } = fixture();
  let current = await runtime.execute(command('START_RUN', 0, 'delta-eight:start'));
  current = await runtime.execute(command('READ_NEXT_SOURCE', current.run!.revision, 'delta-eight:read:mysql'));
  const ask = (commandId: string) => runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId,
    expectedRevision: current.run!.revision, actorUserId: 'user-author',
    message: '当前来源是什么？', selection: { sourceId: 'guanyijia_mysql' },
  });
  for (let index = 1; index <= 7; index += 1) {
    current = await ask('delta-eight:ask:' + index);
  }
  assert.equal(current.assistantTurnDelta?.position, 7);
  assert.equal(current.assistantTurnDelta?.total, 7);
  const final = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'delta-eight:ask:8',
    expectedRevision: current.run!.revision, actorUserId: 'user-author',
    message: '当前来源是什么？', selection: { sourceId: 'guanyijia_mysql' },
  });
  assert.equal(final.assistantTurnDelta?.position, 8);
  assert.equal(final.assistantTurnDelta?.total, 8);
  assert.equal(final.assistantTurnDelta?.item.message, '当前来源是什么?');
  const replay = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'delta-eight:ask:8',
    expectedRevision: current.run!.revision, actorUserId: 'user-author',
    message: '当前来源是什么？', selection: { sourceId: 'guanyijia_mysql' },
  });
  assert.deepEqual(replay.assistantTurnDelta, final.assistantTurnDelta);
});

test('ASK的delta正文被内容存储篡改时拒绝伪造item', async () => {
  const { runtime, contentStore } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'delta-tamper:start'));
  const current = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'delta-tamper:read:mysql'));
  const originalGet = contentStore.get.bind(contentStore);
  contentStore.get = async (ref) => {
    const raw = await originalGet(ref);
    return raw?.includes('"turnId"') ? raw.replace('当前来源是什么?', '被篡改的问题') : raw;
  };
  await assert.rejects(() => runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'delta-tamper:ask',
    expectedRevision: current.run!.revision, actorUserId: 'user-author',
    message: '当前来源是什么？', selection: { sourceId: 'guanyijia_mysql' },
  }), /校验和/);
});

test('完整生产时序在r1→手工r2→Proposal取消/确认r3→冲突ASK后即时重建全部Assistant history', async () => {
  const { runtime, sourceDocuments } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'exact-history:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'exact-history:read:mysql'));
  const mysqlR1 = snapshot.current!.document!;
  const block = snapshot.current!.compilation!.blocks[0]!;

  snapshot = await runtime.execute({
    type: 'APPLY_CURRENT_BLOCK_CHANGE', commandId: 'exact-history:manual-r2',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user-author',
    change: { blockId: block.blockId, label: '手工r2单据事实' },
  });
  assert.equal(snapshot.current!.document!.revision, 2);
  const mysqlR2 = snapshot.current!.document!;
  assert.equal(mysqlR2.derivedFromDocumentId, mysqlR1.documentId);

  const ordinary = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'exact-history:ordinary',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user-author',
    message: '当前来源的快照和读取范围是什么?',
    selection: { sourceId: 'guanyijia_mysql', documentId: mysqlR2.documentId },
  });
  assert.equal(ordinary.run!.timeline.at(-1)?.type, 'ASSISTANT_TURN_RECORDED');

  const target = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'exact-history:target',
    expectedRevision: ordinary.run!.revision, actorUserId: 'user-author',
    message: '依据是什么?',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: mysqlR2.documentId,
      section: block.section, blockId: block.blockId, evidenceRef: block.evidenceRefs[0],
    },
  });
  assert.equal(target.run!.timeline.at(-1)?.type, 'ASSISTANT_TURN_RECORDED');

  const cancelledProposalTurn = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'exact-history:proposal:cancel',
    expectedRevision: target.run!.revision, actorUserId: 'user-author',
    message: '建议把当前项名称改为：应取消的建议',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: mysqlR2.documentId,
      section: block.section, blockId: block.blockId,
    },
  });
  const cancelledBefore = await runtime.readAssistantHistory({
    actorUserId: 'user-author', runId: cancelledProposalTurn.run!.runId,
  });
  const cancelledResponse = cancelledBefore.items.at(-1)?.response;
  if (cancelledResponse?.kind !== 'PATCH_PREVIEW') assert.fail('应生成待取消Proposal');
  snapshot = await runtime.execute({
    type: 'CANCEL_ASSISTANT_PATCH', commandId: 'exact-history:cancel',
    expectedRevision: cancelledProposalTurn.run!.revision, actorUserId: 'user-author',
    proposalId: cancelledResponse.proposal.proposalId,
  });

  const confirmedProposalTurn = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'exact-history:proposal:confirm',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user-author',
    message: '建议把当前项名称改为：应确认的建议',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: mysqlR2.documentId,
      section: block.section, blockId: block.blockId,
    },
  });
  const confirmedBefore = await runtime.readAssistantHistory({
    actorUserId: 'user-author', runId: confirmedProposalTurn.run!.runId,
  });
  const confirmedResponse = confirmedBefore.items.at(-1)?.response;
  if (confirmedResponse?.kind !== 'PATCH_PREVIEW') assert.fail('应生成待确认Proposal');
  snapshot = await runtime.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'exact-history:confirm',
    expectedRevision: confirmedProposalTurn.run!.revision, actorUserId: 'user-author',
    proposalId: confirmedResponse.proposal.proposalId,
    expectedPreviewSha256: confirmedResponse.proposal.previewSha256,
  });
  assert.equal(snapshot.current!.document!.revision, 3);
  assert.equal(snapshot.run!.timeline.at(-2)?.type, 'ASSISTANT_PATCH_CONFIRMED');
  assert.equal(snapshot.run!.timeline.at(-1)?.type, 'DOCUMENT_REVISED');

  snapshot = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'exact-history:review:mysql',
  ));
  assert.equal(snapshot.run!.sources[0]?.status, 'ALIGNED');
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'exact-history:read:github'));
  snapshot = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'exact-history:review:github',
  ));
  assert.equal(snapshot.run!.status, 'CONFLICT_BLOCKED');

  const beforeConflictAsk = await runtime.readAssistantHistory({
    actorUserId: 'user-author', runId: snapshot.run!.runId,
  });
  const asked = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'exact-history:conflict-ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user-author',
    message: '当前差异是什么?',
    selection: { sourceId: 'guanyijia_github', conflictId: 'gyj-conflict-debt-schema' },
  });
  const history = await runtime.readAssistantHistory({
    actorUserId: 'user-author', runId: asked.run!.runId,
  });
  assert.equal(history.total, beforeConflictAsk.total + 1);
  assert.equal(history.items.length, beforeConflictAsk.items.length + 1);
  assert.equal(history.items.length, 5);
  assert.deepEqual(history.items.map((item) => item.message), [
    '当前来源的快照和读取范围是什么?',
    '依据是什么?',
    '建议把当前项名称改为:应取消的建议',
    '建议把当前项名称改为:应确认的建议',
    '当前差异是什么?',
  ]);
  assert.equal(history.items.find((item) => item.message.includes('应取消'))?.proposalStatus, 'CANCELLED');
  assert.equal(history.items.find((item) => item.message.includes('应确认'))?.proposalStatus, 'CONFIRMED');
  assert.equal(history.items.at(-1)?.response.kind, 'CONFLICT_EXPLANATION');
  assert.equal(history.items.at(-1)?.response.title, '欠款字段结构冲突');
  assert.deepEqual(history.items.at(-1)?.response.targets, [{
    kind: 'CONFLICT', conflictId: 'gyj-conflict-debt-schema',
  }]);
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map(({ revision }) => revision), [1, 2, 3, 1]);
});

test('Workbench从持久化revision预览并逐项保存决定，刷新只从事件Artifact恢复', async () => {
  const { runtime, pointerStorage, sourceDocuments, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'resolve:read:mysql'));
  const mysqlDone = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'resolve:review:mysql',
  ));
  const github = await runtime.execute(command('READ_NEXT_SOURCE', mysqlDone.run!.revision, 'resolve:read:github'));
  const blocked = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', github.run!.revision, 'resolve:review:github',
  ));
  assert.equal(blocked.currentConflict?.conflictId, 'gyj-conflict-debt-schema');
  assert.equal(factsInspectorFor(blocked)?.block?.label, blocked.currentConflict?.hunk.incoming.block.label);
  assert.ok(factsInspectorFor(blocked)?.block?.locator);
  assert.deepEqual(factsInspectorFor(blocked)?.block?.affectedObjects, ['指标：receivable_debt', '规则：deposit']);

  const preview = await runtime.previewCurrentConflict({
    runId: blocked.run!.runId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT',
  });
  assert.equal(preview.hunk.current.documentId, mysql.current?.document?.documentId);
  assert.equal(preview.hunk.incoming.documentId, github.current?.document?.documentId);
  assert.deepEqual(preview.result.objectDispositions.map(({ objectRef, disposition }) => ({
    objectId: objectRef.objectId, disposition,
  })), [
    { objectId: 'receivable_debt', disposition: 'EXCLUDED' },
    { objectId: 'deposit', disposition: 'CANDIDATE_ONLY' },
  ]);
  const apply = {
    type: 'RESOLVE_CURRENT_CONFLICT' as const,
    commandId: 'resolve:debt', expectedRevision: blocked.run!.revision,
    actorUserId: 'user-author', conflictId: 'gyj-conflict-debt-schema' as const,
    strategy: 'KEEP_CURRENT' as const, reason: '部署数据库是当前工作标准，源码差异保留为溯源。',
    expectedHunkSha256: preview.hunk.hunkSha256,
  };
  const resolved = await runtime.execute(apply);

  assert.equal(resolved.run?.status, 'READY');
  assert.equal(resolved.resolutions.length, 1);
  assert.equal(resolved.resolutions[0]?.previewSha256, preview.previewSha256);
  assert.equal(resolved.timeline.at(-1)?.kind, 'CONFLICT_RESOLVED');
  assert.deepEqual(resolved.timeline.at(-1)?.action, {
    type: 'OPEN_CONFLICT', conflictId: 'gyj-conflict-debt-schema',
  }, '已保存的差异决定必须保留可回看的时间线入口');
  const restoredRuntime = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: runs, sourceDocuments,
  });
  const restored = await restoredRuntime.read('user-author');
  assert.equal(restored.resolutions[0]?.reason, apply.reason);
  assert.equal(restored.resolutions[0]?.hunk.current.documentId, preview.hunk.current.documentId);
  const repeated = await restoredRuntime.execute(apply);
  assert.equal(repeated.run?.revision, resolved.run?.revision);
  assert.equal(repeated.run?.timeline.filter((event) => event.type === 'CONFLICT_RESOLVED').length, 1);
});

test('Run持久化revision校验拒绝同步伪造Hunk全文并重算整个Artifact链', async () => {
  const { runtime, sourceDocuments, story } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'forged:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'forged:read:mysql'));
  const mysqlDone = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'forged:review:mysql',
  ));
  const github = await runtime.execute(command(
    'READ_NEXT_SOURCE', mysqlDone.run!.revision, 'forged:read:github',
  ));
  const blocked = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', github.run!.revision, 'forged:review:github',
  ));
  const preview = await runtime.previewCurrentConflict({
    runId: blocked.run!.runId,
    conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT',
  });
  const forged = {
    schemaVersion: 1 as const,
    resolutionId: `resolution:${blocked.run!.runId}:gyj-conflict-debt-schema`,
    runId: blocked.run!.runId,
    sourceId: 'guanyijia_github',
    reason: '攻击者同步重算了所有派生字段。',
    actorUserId: 'user-author',
    decidedAt: '2026-08-18T15:59:59.000Z',
    ...structuredClone(preview),
  };
  forged.hunk.current.block.label = '攻击者伪造的部署事实';
  const forgedHunkContent = {
    conflictId: forged.hunk.conflictId,
    title: forged.hunk.title,
    sections: forged.hunk.sections,
    current: forged.hunk.current,
    incoming: forged.hunk.incoming,
    corroborating: forged.hunk.corroborating,
    affectedObjectRefs: forged.hunk.affectedObjectRefs,
  };
  forged.hunk.hunkSha256 = `sha256:${sha256HexSync(canonicalModelingJson(forgedHunkContent))}`;
  Object.assign(forged, projectConflictResolution({ hunk: forged.hunk, strategy: forged.strategy }));

  await assert.rejects(() => validateGuanyijiaResolutionArtifactAgainstPersistedRevisions({
    artifact: forged,
    run: blocked.run!,
    sourceDocuments,
    story,
  }), /无法由运行登记的持久化revision复算/);
});

test('冲突决定使用真实应用时间，Run成功后指针写失败可由同command恢复且不重复事件', async () => {
  const metadataStorage = new MemoryStorage();
  const pointerStorage = new ResolutionPointerFailureStorage();
  const contentStore = createMemoryContentStore();
  const sourceDocuments = createSourceDocumentRuntime({ metadataStorage, contentStore });
  const story = createGuanyijiaStandardizationStory();
  const runs = createStandardizationRunRuntime({
    metadataStorage,
    contentStore,
    resolutionArtifactValidator: (artifact, run) => (
      validateGuanyijiaResolutionArtifactAgainstPersistedRevisions({
        artifact, run, sourceDocuments, story,
      })
    ),
    corroborationReceiptValidator: (receipt, run) => (
      validateGuanyijiaCorroborationReceipt({ receipt, run, story })
    ),
    corroborationReceiptSetValidator: (projection, run) => (
      validateGuanyijiaCorroborationReceiptSetAgainstPersistedRevisions({
        projection, run, sourceDocuments, story,
      })
    ),
    now: () => '2026-08-18T16:00:00.000Z',
  });
  const runtime = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: runs, sourceDocuments, story,
    now: () => '2026-08-18T15:59:59.000Z',
  });
  const started = await runtime.execute(command('START_RUN', 0, 'saga:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'saga:read:mysql'));
  const mysqlDone = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'saga:review:mysql',
  ));
  const github = await runtime.execute(command('READ_NEXT_SOURCE', mysqlDone.run!.revision, 'saga:read:github'));
  const blocked = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', github.run!.revision, 'saga:review:github',
  ));
  const preview = await runtime.previewCurrentConflict({
    runId: blocked.run!.runId, conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT',
  });
  const resolveCommand = {
    type: 'RESOLVE_CURRENT_CONFLICT' as const,
    commandId: 'resolve:saga', expectedRevision: blocked.run!.revision,
    actorUserId: 'user-author', conflictId: 'gyj-conflict-debt-schema',
    strategy: 'KEEP_CURRENT' as const, reason: '部署事实优先，源码结论保留溯源。',
    expectedHunkSha256: preview.hunk.hunkSha256,
  };
  pointerStorage.failNextResolutionCompletion = true;

  await assert.rejects(() => runtime.execute(resolveCommand), /模拟决定完成指针写入失败/);
  const committed = await runs.read(blocked.run!.runId);
  assert.equal(committed?.timeline.filter((event) => event.type === 'CONFLICT_RESOLVED').length, 1);
  const recovered = await runtime.execute(resolveCommand);
  assert.equal(recovered.run?.revision, committed?.revision);
  assert.equal(recovered.run?.timeline.filter((event) => event.type === 'CONFLICT_RESOLVED').length, 1);
  assert.equal(recovered.resolutions[0]?.decidedAt, '2026-08-18T15:59:59.000Z');
  assert.notEqual(
    recovered.resolutions[0]?.decidedAt,
    blocked.run?.timeline.find((event) => event.type === 'CONFLICT_FOUND')?.createdAt,
  );
});

test('五源均已审阅且差异未决定时保留最后文档并进入第一项差异', async () => {
  const { runtime } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'deferred-conflicts:start'));
  const readAndReview = async (source: string) => {
    const read = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, `deferred-conflicts:read:${source}`));
    snapshot = await runtime.execute(command(
      'COMPLETE_CURRENT_DOCUMENT_REVIEW', read.run!.revision, `deferred-conflicts:review:${source}`,
    ));
  };

  await readAndReview('mysql');
  await readAndReview('github');
  await readAndReview('official');
  await readAndReview('policy');
  await readAndReview('semantica');

  assert.equal(snapshot.run?.status, 'READY');
  assert.equal(snapshot.current?.source.sourceId, 'guanyijia_semantica_demo');
  assert.equal(snapshot.nextAction.type, 'RESOLVE_CONFLICT');
  assert.equal(snapshot.currentConflict?.conflictId, 'gyj-conflict-debt-schema');
});

test('READY 状态按稳定顺序预览并保存延后差异后进入交付阶段', async () => {
  const { runtime } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'deferred-resolution:start'));
  const readAndReview = async (source: string) => {
    const read = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, `deferred-resolution:read:${source}`));
    snapshot = await runtime.execute(command(
      'COMPLETE_CURRENT_DOCUMENT_REVIEW', read.run!.revision, `deferred-resolution:review:${source}`,
    ));
  };
  const resolve = async (
    conflictId: string,
    strategy: 'KEEP_CURRENT' | 'MERGE' | 'DEFER_AS_GAP',
    reason: string,
  ) => {
    const preview = await runtime.previewCurrentConflict({
      runId: snapshot.run!.runId,
      conflictId,
      strategy,
    });
    snapshot = await runtime.execute({
      type: 'RESOLVE_CURRENT_CONFLICT',
      commandId: `deferred-resolution:resolve:${conflictId}`,
      expectedRevision: snapshot.run!.revision,
      actorUserId: 'user-author',
      conflictId,
      strategy,
      reason,
      expectedHunkSha256: preview.hunk.hunkSha256,
    });
  };

  await readAndReview('mysql');
  await readAndReview('github');
  await readAndReview('official');
  await readAndReview('policy');
  await readAndReview('semantica');

  await resolve('gyj-conflict-debt-schema', 'KEEP_CURRENT', '部署库作为当前工作标准，源码差异只作溯源。');
  assert.equal(snapshot.currentConflict?.conflictId, 'gyj-conflict-negative-stock');
  await resolve('gyj-conflict-negative-stock', 'MERGE', '保留按租户配置实现并登记统一制度尚未落地。');
  assert.equal(snapshot.currentConflict?.conflictId, 'gyj-conflict-status-nine');
  await resolve('gyj-conflict-status-nine', 'DEFER_AS_GAP', '状态九业务含义尚未得到正式确认。');

  assert.equal(snapshot.run?.status, 'READY_FOR_OUTPUT');
  assert.equal(snapshot.currentConflict, undefined);
});

test('五源主演示按GitHub debt、Policy两项与Semantica只佐证的时点到达READY_FOR_OUTPUT', async () => {
  const { runtime, metadataStorage, contentStore } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'flow:start'));
  const readAndReview = async (source: string) => {
    const read = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, `flow:read:${source}`));
    snapshot = await runtime.execute(command(
      'COMPLETE_CURRENT_DOCUMENT_REVIEW', read.run!.revision, `flow:review:${source}`,
    ));
  };
  const resolve = async (
    conflictId: string,
    strategy: 'KEEP_CURRENT' | 'MERGE' | 'DEFER_AS_GAP',
    reason: string,
  ) => {
    const preview = await runtime.previewCurrentConflict({
      runId: snapshot.run!.runId, conflictId, strategy,
    });
    snapshot = await runtime.execute({
      type: 'RESOLVE_CURRENT_CONFLICT', commandId: `flow:resolve:${conflictId}`,
      expectedRevision: snapshot.run!.revision, actorUserId: 'user-author',
      conflictId, strategy, reason, expectedHunkSha256: preview.hunk.hunkSha256,
    });
    return preview;
  };

  await readAndReview('mysql');
  await readAndReview('github');
  assert.equal(snapshot.currentConflict?.conflictId, 'gyj-conflict-debt-schema');
  await resolve('gyj-conflict-debt-schema', 'KEEP_CURRENT', '部署库作为当前工作标准，源码差异只作溯源。');
  await readAndReview('official');
  assert.equal(snapshot.run?.status, 'READY', '官方文档不能引入新冲突');
  await readAndReview('policy');
  assert.equal(snapshot.currentConflict?.conflictId, 'gyj-conflict-negative-stock');
  const negative = await resolve(
    'gyj-conflict-negative-stock', 'MERGE', '保留按租户配置实现并登记统一制度尚未落地。',
  );
  assert.equal(snapshot.run?.status, 'CONFLICT_BLOCKED');
  assert.equal(snapshot.currentConflict?.conflictId, 'gyj-conflict-status-nine');
  assert.deepEqual(negative.result.blocks.map((block) => block.stableCode), [
    'rule.negative_stock', 'resolution.gap.negative_stock_policy_not_implemented',
  ]);
  const status = await resolve(
    'gyj-conflict-status-nine', 'DEFER_AS_GAP', '状态九业务含义尚未得到正式确认。',
  );
  assert.equal(snapshot.run?.status, 'READY');
  assert.deepEqual(
    snapshot.timeline
      .filter((event) => event.kind === 'CONFLICT_RESOLVED')
      .map((event) => event.action),
    [
      { type: 'OPEN_CONFLICT', conflictId: 'gyj-conflict-debt-schema' },
      { type: 'OPEN_CONFLICT', conflictId: 'gyj-conflict-negative-stock' },
      { type: 'OPEN_CONFLICT', conflictId: 'gyj-conflict-status-nine' },
    ],
    '每条已保存的来源差异决定都必须回到它自己的历史详情，不能一律跳到最后一个决定。',
  );
  assert.deepEqual(
    snapshot.timeline
      .filter((event) => event.kind === 'CONFLICT_FOUND')
      .map((event) => event.action),
    [
      { type: 'OPEN_CONFLICT', conflictId: 'gyj-conflict-debt-schema' },
      { type: 'OPEN_CONFLICT', conflictId: 'gyj-conflict-negative-stock' },
      { type: 'OPEN_CONFLICT', conflictId: 'gyj-conflict-status-nine' },
    ],
    '即使来源差异已经处理，原始发现记录也必须能回看双方材料和决定。',
  );
  assert.deepEqual(status.result.objectDispositions.map(({ objectRef, disposition }) => ({
    objectId: objectRef.objectId, disposition,
  })), [
    { objectId: 'document_status', disposition: 'KEEP_WITHOUT_ENUM_MEMBER' },
    { objectId: 'audit_status', disposition: 'CANDIDATE_ONLY' },
  ]);

  await readAndReview('semantica');
  assert.equal(snapshot.run?.status, 'READY_FOR_OUTPUT');
  assert.equal(snapshot.resolutions.length, 3);
  assert.deepEqual(snapshot.run?.timeline
    .filter((event) => event.type === 'CONFLICT_CORROBORATED')
    .map((event) => event.sourceId), ['guanyijia_semantica_demo', 'guanyijia_semantica_demo']);
  assert.equal(snapshot.run?.timeline.some((event) => (
    event.type === 'CONFLICT_FOUND' && event.sourceId === 'guanyijia_semantica_demo'
  )), false);

  const raw = metadataStorage.getItem(standardizationMetadataStorageKey);
  assert.ok(raw);
  const state = JSON.parse(raw) as {
    runs: Array<{ runId: string; timeline: Array<{
      eventId: string;
      sequence: number;
      type: string;
      sourceId?: string;
      payloadRef: `sha256:${string}`;
    }> }>;
  };
  const timeline = state.runs[0]!.timeline;
  const receiptIndex = timeline.findIndex((event) => event.type === 'CONFLICT_CORROBORATED');
  const firstResolutionIndex = timeline.findIndex((event) => event.type === 'CONFLICT_RESOLVED');
  assert.ok(receiptIndex > firstResolutionIndex && firstResolutionIndex >= 0);
  const [receipt] = timeline.splice(receiptIndex, 1);
  timeline.splice(firstResolutionIndex, 0, receipt!);
  timeline.forEach((event, index) => {
    event.sequence = index + 1;
    event.eventId = `${state.runs[0]!.runId}:event:${index + 1}`;
  });
  metadataStorage.setItem(standardizationMetadataStorageKey, JSON.stringify(state));
  await assert.rejects(() => runtime.read('user-author'), (
    /来源事件语义顺序无效|佐证事件没有回链此前已验证的冲突/
  ));

  const forgedState = JSON.parse(raw) as typeof state;
  const forgedReceipt = forgedState.runs[0]!.timeline.find((event) => (
    event.type === 'CONFLICT_CORROBORATED'
  ));
  assert.ok(forgedReceipt);
  forgedReceipt.payloadRef = await contentStore.put(JSON.stringify({
    conflictId: 'gyj-conflict-debt-schema',
    sourceId: 'guanyijia_semantica_demo',
  }));
  metadataStorage.setItem(standardizationMetadataStorageKey, JSON.stringify(forgedState));
  await assert.rejects(() => runtime.read('user-author'), /不是Story声明的Corroborating来源/);

  const reorderedState = JSON.parse(raw) as typeof state;
  const reorderedTimeline = reorderedState.runs[0]!.timeline;
  const reviewedIndex = reorderedTimeline.findIndex((event) => (
    event.type === 'DOCUMENT_REVIEWED' && event.sourceId === 'guanyijia_github'
  ));
  const foundIndex = reorderedTimeline.findIndex((event) => (
    event.type === 'CONFLICT_FOUND' && event.sourceId === 'guanyijia_github'
  ));
  assert.ok(reviewedIndex >= 0 && foundIndex > reviewedIndex);
  [reorderedTimeline[reviewedIndex], reorderedTimeline[foundIndex]] = [
    reorderedTimeline[foundIndex]!, reorderedTimeline[reviewedIndex]!,
  ];
  reorderedTimeline.forEach((event, index) => {
    event.sequence = index + 1;
    event.eventId = `${reorderedState.runs[0]!.runId}:event:${index + 1}`;
  });
  metadataStorage.setItem(standardizationMetadataStorageKey, JSON.stringify(reorderedState));
  await assert.rejects(() => runtime.read('user-author'), /来源事件语义顺序无效/);

  const prematureReceiptState = JSON.parse(raw) as typeof state;
  const prematureTimeline = prematureReceiptState.runs[0]!.timeline;
  const prematureReceiptIndex = prematureTimeline.findIndex((event) => (
    event.type === 'CONFLICT_CORROBORATED'
  ));
  const semanticaStartIndex = prematureTimeline.findIndex((event) => (
    event.type === 'SOURCE_READ_STARTED' && event.sourceId === 'guanyijia_semantica_demo'
  ));
  assert.ok(prematureReceiptIndex > semanticaStartIndex && semanticaStartIndex >= 0);
  const [prematureReceipt] = prematureTimeline.splice(prematureReceiptIndex, 1);
  prematureTimeline.splice(semanticaStartIndex, 0, prematureReceipt!);
  prematureTimeline.forEach((event, index) => {
    event.sequence = index + 1;
    event.eventId = `${prematureReceiptState.runs[0]!.runId}:event:${index + 1}`;
  });
  metadataStorage.setItem(standardizationMetadataStorageKey, JSON.stringify(prematureReceiptState));
  await assert.rejects(() => runtime.read('user-author'), /来源事件语义顺序无效/);

  const duplicateReceiptState = JSON.parse(raw) as typeof state;
  const duplicateTimeline = duplicateReceiptState.runs[0]!.timeline;
  const duplicateAfterIndex = duplicateTimeline.findIndex((event) => (
    event.type === 'CONFLICT_CORROBORATED'
  ));
  assert.ok(duplicateAfterIndex >= 0);
  duplicateTimeline.splice(duplicateAfterIndex + 1, 0, structuredClone(duplicateTimeline[duplicateAfterIndex]!));
  duplicateTimeline.forEach((event, index) => {
    event.sequence = index + 1;
    event.eventId = `${duplicateReceiptState.runs[0]!.runId}:event:${index + 1}`;
  });
  metadataStorage.setItem(standardizationMetadataStorageKey, JSON.stringify(duplicateReceiptState));
  await assert.rejects(() => runtime.read('user-author'), /佐证事件重复/);

  const missingReceiptState = JSON.parse(raw) as typeof state;
  const missingReceiptTimeline = missingReceiptState.runs[0]!.timeline;
  const missingReceiptIndex = missingReceiptTimeline.findIndex((event) => (
    event.type === 'CONFLICT_CORROBORATED'
  ));
  assert.ok(missingReceiptIndex >= 0);
  missingReceiptTimeline.splice(missingReceiptIndex, 1);
  missingReceiptTimeline.forEach((event, index) => {
    event.sequence = index + 1;
    event.eventId = `${missingReceiptState.runs[0]!.runId}:event:${index + 1}`;
  });
  metadataStorage.setItem(standardizationMetadataStorageKey, JSON.stringify(missingReceiptState));
  await assert.rejects(
    () => runtime.read('user-author'),
    /佐证回执集合不是持久化来源revision的唯一投影/,
  );
});

test('Semantica normalized-only修订生成r2并更新当前投影且保留生成r1佐证回执', async () => {
  const { runtime, runs, metadataStorage } = fixture();
  const semantica = await advanceToSemanticaDocumentReady(runtime);
  const block = semantica.current!.compilation!.blocks.find((candidate) => (
    candidate.stableCode === 'derived.rule.negative_stock'
  ));
  assert.ok(block && typeof block.value === 'object' && block.value !== null && !Array.isArray(block.value));
  const changedValue = {
    ...block.value,
    normalized: 'DERIVED_RULE_REMOVED_BY_REVIEW',
  };
  const initialReceipts = semantica.run!.timeline.filter((event) => (
    event.type === 'CONFLICT_CORROBORATED'
  ));
  assert.equal(initialReceipts.length, 2);

  const applied = await runtime.execute({
    type: 'APPLY_CURRENT_BLOCK_CHANGE',
    commandId: 'normalized-flow:apply:semantica',
    expectedRevision: semantica.run!.revision,
    actorUserId: 'user-author',
    change: { blockId: block.blockId, value: changedValue },
  });

  assert.equal(applied.current?.document?.revision, 2);
  assert.equal(applied.run?.revision, semantica.run!.revision + 1);
  assert.deepEqual(applied.current?.compilation?.corroboratedConflictIds, ['gyj-conflict-status-nine']);
  assert.equal(applied.run?.timeline.filter((event) => event.type === 'CONFLICT_CORROBORATED').length, 2,
    'revision不得重写或重复首次DOCUMENT_GENERATED绑定的佐证回执');
  const revisedEvent = applied.run!.timeline.findLast((event) => event.type === 'DOCUMENT_REVISED')!;
  assert.deepEqual(JSON.parse(await runs.readEventPayload(
    applied.run!.runId, revisedEvent.eventId,
  )).changedSections, []);
  assert.equal((await runs.read(applied.run!.runId))?.revision, applied.run?.revision,
    'Run加载必须以生成r1而非当前r2复算历史佐证集合');
  const reviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', applied.run!.revision, 'normalized-flow:review:semantica',
  ));
  assert.equal(reviewed.run?.status, 'READY_FOR_OUTPUT');

  const raw = metadataStorage.getItem(standardizationMetadataStorageKey);
  assert.ok(raw);
  const state = JSON.parse(raw) as {
    runs: Array<{ runId: string; timeline: Array<{
      eventId: string;
      sequence: number;
      type: string;
      sourceId?: string;
    }> }>;
  };
  const timeline = state.runs[0]!.timeline;
  const receipts = timeline.filter((event) => (
    event.type === 'CONFLICT_CORROBORATED' && event.sourceId === 'guanyijia_semantica_demo'
  ));
  state.runs[0]!.timeline = timeline.filter((event) => !receipts.includes(event));
  const revisedIndex = state.runs[0]!.timeline.findIndex((event) => (
    event.type === 'DOCUMENT_REVISED' && event.sourceId === 'guanyijia_semantica_demo'
  ));
  assert.equal(receipts.length, 2);
  assert.ok(revisedIndex >= 0);
  state.runs[0]!.timeline.splice(revisedIndex + 1, 0, ...receipts);
  state.runs[0]!.timeline.forEach((event, index) => {
    event.sequence = index + 1;
    event.eventId = `${state.runs[0]!.runId}:event:${index + 1}`;
  });
  metadataStorage.setItem(standardizationMetadataStorageKey, JSON.stringify(state));
  await assert.rejects(() => runtime.read('user-author'), /来源事件语义顺序无效/);
});

test('repeating a workbench command does not duplicate documents, events, or revisions', async () => {
  const { runtime, sourceDocuments } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const readCommand = command('READ_NEXT_SOURCE', started.run!.revision, 'double-click:read:mysql');
  const firstRead = await runtime.execute(readCommand);
  const repeatedRead = await runtime.execute(readCommand);
  assert.equal(repeatedRead.run?.revision, firstRead.run?.revision);
  assert.equal(repeatedRead.run?.timeline.length, firstRead.run?.timeline.length);
  assert.equal((await sourceDocuments.list('guanyijia_erp')).length, 1);

  const reviewCommand = command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', firstRead.run!.revision, 'double-click:review:mysql',
  );
  const firstReview = await runtime.execute(reviewCommand);
  const repeatedReview = await runtime.execute(reviewCommand);
  assert.equal(repeatedReview.run?.revision, firstReview.run?.revision);
  assert.equal(repeatedReview.run?.timeline.length, firstReview.run?.timeline.length);
});

test('preview/apply沿完整revision链写回，刷新保持r2且重复command不产生r3', async () => {
  const { runtime, pointerStorage, sourceDocuments, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));
  const gap = mysql.current!.compilation!.blocks.find((block) => block.stableCode === 'gap.current_stock_as_of');
  assert.ok(gap);
  const change = {
    blockId: gap.blockId,
    label: '当前库存生效时点',
    value: { normalized: 'CURRENT_STOCK_AS_OF_END_OF_DAY', text: '当前库存按日终生效。' },
  };

  const preview = await runtime.execute({
    type: 'PREVIEW_CURRENT_BLOCK_CHANGE', commandId: 'preview:mysql:stock-as-of',
    expectedRevision: mysql.run!.revision, actorUserId: 'user-author', change,
  });

  assert.equal(preview.run?.revision, mysql.run?.revision);
  assert.equal(preview.current?.document?.revision, 1);
  assert.equal(preview.preview?.block.before.blockId, gap.blockId);
  assert.equal(preview.preview?.block.after.label, '当前库存生效时点');
  assert.equal(preview.preview?.assertion.after.statement, '当前库存生效时点：当前库存按日终生效。');
  assert.equal(preview.preview?.section.lines.some((line) => line.type === 'REMOVED'), true);
  assert.equal(preview.preview?.section.lines.some((line) => line.type === 'ADDED'), true);
  assert.equal(preview.preview?.markdown.lines.some((line) => line.type === 'REMOVED'), true);
  assert.deepEqual(preview.preview?.affectedObjects, ['实体：inventory_position']);

  const applyCommand = {
    type: 'APPLY_CURRENT_BLOCK_CHANGE' as const,
    commandId: 'apply:mysql:stock-as-of', expectedRevision: mysql.run!.revision,
    actorUserId: 'user-author', change,
  };
  const applied = await runtime.execute(applyCommand);

  assert.equal(applied.run?.revision, mysql.run!.revision + 1);
  assert.equal(applied.current?.document?.revision, 2);
  assert.equal(applied.current?.sourceStep.documentId, applied.current?.document?.documentId);
  assert.equal(applied.current?.compilation?.blocks.find((block) => block.blockId === gap.blockId)?.label,
    '当前库存生效时点');
  assert.equal(applied.timeline.at(-1)?.kind, 'DOCUMENT_REVISED');
  assert.equal(applied.timeline.at(-1)?.summary.includes('user-author'), true);
  assert.equal(applied.current?.history?.length, 2);
  assert.equal(applied.current?.revisionDiff?.blockChanges[0]?.blockId, gap.blockId);
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2]);

  const restoredRuntime = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: runs, sourceDocuments,
  });
  const restored = await restoredRuntime.read('user-author');
  assert.equal(restored.current?.document?.revision, 2);
  assert.equal(restored.current?.compilation?.blocks.find((block) => block.blockId === gap.blockId)?.label,
    '当前库存生效时点', '刷新必须从持久化blocks重建，不得回退默认编译');

  const repeated = await restoredRuntime.execute(applyCommand);
  assert.equal(repeated.current?.document?.revision, 2);
  assert.equal((await sourceDocuments.list('guanyijia_erp')).length, 2);
  assert.equal(repeated.run?.timeline.filter((event) => event.type === 'DOCUMENT_REVISED').length, 1);

  const navigation = continueCurrentDocumentReviewAfterApply(applied, {
    selectedDocumentId: mysql.current!.document!.documentId,
    selectedSection: gap.section,
    selectedBlockId: gap.blockId,
    focusTargetId: `guanyijia-document-review:${mysql.current!.document!.documentId}:block:${gap.blockId}`,
    focusToken: 4,
  }, gap.blockId);
  assert.deepEqual(navigation, {
    selectedDocumentId: applied.current?.document?.documentId,
    selectedSection: gap.section,
    selectedBlockId: gap.blockId,
    focusTargetId: `guanyijia-document-review:${applied.current?.document?.documentId}:block:${gap.blockId}`,
    focusToken: 5,
  });
});

test('scripted review suggestions apply through the existing revision chain and persist their decision', async () => {
  const { runtime, pointerStorage, sourceDocuments, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'scripted:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'scripted:read:mysql'));

  assert.equal(mysql.scriptedEdits?.[0]?.status, 'PENDING');

  const preview = await runtime.execute({
    type: 'PREVIEW_SCRIPTED_REVIEW_EDIT',
    commandId: 'scripted:preview:mysql', expectedRevision: mysql.run!.revision,
    actorUserId: 'user-author', editId: 'scripted:mysql:rename-depot-head', patch: {},
  });
  assert.equal(preview.preview?.block.before.blockId, 'gyj-block:physical.table.jsh_depot_head');
  assert.equal(preview.preview?.block.after.label, '库存单据表头（jsh_depot_head）');

  const applied = await runtime.execute({
    type: 'DECIDE_SCRIPTED_REVIEW_EDIT',
    commandId: 'scripted:apply:mysql', expectedRevision: mysql.run!.revision,
    actorUserId: 'user-author', editId: 'scripted:mysql:rename-depot-head',
    decision: 'APPLY_SUGGESTION', patch: {}, expectedPreviewSha256: preview.scriptedEditPreviewSha256,
  });
  assert.equal(applied.current?.document?.revision, 2);
  assert.equal(applied.current?.compilation?.blocks.find((block) => block.blockId === 'gyj-block:physical.table.jsh_depot_head')?.label,
    '库存单据表头（jsh_depot_head）');
  assert.equal(applied.scriptedEdits?.[0]?.status, 'APPLIED');

  const restored = await createGuanyijiaWorkbenchRuntime({ pointerStorage, standardizationRuns: runs, sourceDocuments }).read('user-author');
  assert.equal(restored.scriptedEdits?.[0]?.status, 'APPLIED');
  const reviewed = await runtime.execute(command('COMPLETE_CURRENT_DOCUMENT_REVIEW', applied.run!.revision, 'scripted:complete:mysql'));
  assert.equal(reviewed.run?.sources[0]?.status, 'ALIGNED');
});

test('keeping a scripted conclusion records the decision without creating a document revision', async () => {
  const { runtime, sourceDocuments } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'scripted-keep:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'scripted-keep:read:mysql'));

  const kept = await runtime.execute({
    type: 'DECIDE_SCRIPTED_REVIEW_EDIT',
    commandId: 'scripted-keep:mysql',
    expectedRevision: mysql.run!.revision,
    actorUserId: 'user-author',
    editId: 'scripted:mysql:rename-depot-head',
    decision: 'KEEP_CURRENT',
  });

  assert.equal(kept.current?.document?.revision, 1);
  assert.equal(kept.scriptedEdits?.[0]?.status, 'KEPT');
  assert.equal((await sourceDocuments.list('guanyijia_erp')).length, 1);
  const reviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', kept.run!.revision, 'scripted-keep:complete:mysql',
  ));
  assert.equal(reviewed.run?.sources[0]?.status, 'ALIGNED');
});

test('GitHub scripted clarification survives to the later negative-stock conflict', async () => {
  const { runtime, pointerStorage, sourceDocuments, runs } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'scripted-github:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'scripted-github:read:mysql'));
  snapshot = await runtime.execute({
    type: 'DECIDE_SCRIPTED_REVIEW_EDIT', commandId: 'scripted-github:keep:mysql',
    expectedRevision: mysql.run!.revision, actorUserId: 'user-author',
    editId: 'scripted:mysql:rename-depot-head', decision: 'KEEP_CURRENT',
  });
  snapshot = await runtime.execute(command('COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'scripted-github:complete:mysql'));
  const github = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'scripted-github:read:github'));

  const preview = await runtime.execute({
    type: 'PREVIEW_SCRIPTED_REVIEW_EDIT', commandId: 'scripted-github:preview',
    expectedRevision: github.run!.revision, actorUserId: 'user-author',
    editId: 'scripted:github:clarify-negative-stock',
    patch: { label: '租户级负库存控制（人工确认）', text: '源码通过租户配置项决定是否允许负库存；这说明当前实现支持按租户控制，不等同于统一制度结论。' },
  });
  snapshot = await runtime.execute({
    type: 'DECIDE_SCRIPTED_REVIEW_EDIT', commandId: 'scripted-github:apply',
    expectedRevision: github.run!.revision, actorUserId: 'user-author',
    editId: 'scripted:github:clarify-negative-stock', decision: 'APPLY_SUGGESTION',
    patch: { label: '租户级负库存控制（人工确认）', text: '源码通过租户配置项决定是否允许负库存；这说明当前实现支持按租户控制，不等同于统一制度结论。' },
    expectedPreviewSha256: preview.scriptedEditPreviewSha256,
  });
  assert.equal(snapshot.current?.document?.revision, 2);
  assert.equal(snapshot.scriptedEdits?.[0]?.status, 'APPLIED');
  assert.equal(snapshot.current?.compilation?.blocks.find((block) => (
    block.blockId === 'gyj-block:rule.negative_stock'
  ))?.label, '租户级负库存控制（人工确认）');
  const restored = await createGuanyijiaWorkbenchRuntime({ pointerStorage, standardizationRuns: runs, sourceDocuments }).read('user-author');
  assert.equal(restored.current?.compilation?.blocks.find((block) => (
    block.blockId === 'gyj-block:rule.negative_stock'
  ))?.label, '租户级负库存控制（人工确认）');
  snapshot = await runtime.execute(command('COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'scripted-github:complete:github'));
  const debtPreview = await runtime.previewCurrentConflict({
    runId: snapshot.run!.runId, conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT',
  });
  snapshot = await runtime.execute({
    type: 'RESOLVE_CURRENT_CONFLICT', commandId: 'scripted-github:resolve:debt',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user-author',
    conflictId: 'gyj-conflict-debt-schema', strategy: 'KEEP_CURRENT',
    reason: '当前部署结构仍为审阅基线。', expectedHunkSha256: debtPreview.hunk.hunkSha256,
  });
  const official = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'scripted-github:read:official'));
  snapshot = await runtime.execute(command('COMPLETE_CURRENT_DOCUMENT_REVIEW', official.run!.revision, 'scripted-github:complete:official'));
  const policy = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'scripted-github:read:policy'));
  snapshot = await runtime.execute(command('COMPLETE_CURRENT_DOCUMENT_REVIEW', policy.run!.revision, 'scripted-github:complete:policy'));

  assert.equal(snapshot.currentConflict?.conflictId, 'gyj-conflict-negative-stock');
  const negativePreview = await runtime.previewCurrentConflict({
    runId: snapshot.run!.runId, conflictId: 'gyj-conflict-negative-stock', strategy: 'MERGE',
  });
  assert.equal(negativePreview.hunk.current.block.label, '租户级负库存控制（人工确认）');
  assert.equal((negativePreview.hunk.current.block.value as { text?: string }).text,
    '源码通过租户配置项决定是否允许负库存；这说明当前实现支持按租户控制，不等同于统一制度结论。');
});

test('apply在run CAS失败后保留未关联r2并以同一command恢复关联而不产生r3', async () => {
  const { pointerStorage, sourceDocuments, runs } = fixture();
  let failRevisionRegistration = true;
  const flakyRuns = {
    read: (runId: string) => runs.read(runId),
    readEventPayload: (runId: string, eventId: string) => runs.readEventPayload(runId, eventId),
    execute: async (runCommand: Parameters<typeof runs.execute>[0]) => {
      if (runCommand.type === 'REVISE_SOURCE_DOCUMENT' && failRevisionRegistration) {
        failRevisionRegistration = false;
        throw new Error('运行已被其他窗口更新');
      }
      return runs.execute(runCommand);
    },
  };
  const runtime = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: flakyRuns, sourceDocuments,
  });
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));
  const block = mysql.current!.compilation!.blocks[0]!;
  const applyCommand = {
    type: 'APPLY_CURRENT_BLOCK_CHANGE' as const,
    commandId: 'apply:recoverable', expectedRevision: mysql.run!.revision,
    actorUserId: 'user-author',
    change: { blockId: block.blockId, label: `${block.label}（已核对）` },
  };

  await assert.rejects(() => runtime.execute(applyCommand), /运行已被其他窗口更新/);
  const afterFailure = await runs.read(mysql.run!.runId);
  assert.equal(afterFailure?.sources[0]?.documentId, mysql.current?.document?.documentId,
    'run CAS失败不能把活动revision切到孤儿文档');
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2]);

  const recovered = await runtime.execute(applyCommand);
  assert.equal(recovered.current?.document?.revision, 2);
  assert.equal(recovered.run?.sources[0]?.documentId, recovered.current?.document?.documentId);
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2]);
});

test('连续修改时每条DOCUMENT_REVISED时间线回执保持自己的revision与真实block', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));
  const block = mysql.current!.compilation!.blocks[0]!;
  const second = await runtime.execute({
    type: 'APPLY_CURRENT_BLOCK_CHANGE', commandId: 'apply:timeline:r2',
    expectedRevision: mysql.run!.revision, actorUserId: 'reviewer-r2',
    change: { blockId: block.blockId, label: `${block.label}（第一次核对）` },
  });
  const third = await runtime.execute({
    type: 'APPLY_CURRENT_BLOCK_CHANGE', commandId: 'apply:timeline:r3',
    expectedRevision: second.run!.revision, actorUserId: 'reviewer-r3',
    change: { blockId: block.blockId, label: `${block.label}（第二次核对）` },
  });

  const revisions = third.timeline.filter((item) => item.kind === 'DOCUMENT_REVISED');
  const generated = third.timeline.find((item) => item.kind === 'DOCUMENT_GENERATED');
  assert.equal(generated?.document?.revision, 1);
  assert.equal(generated?.state, 'RECEIPT');
  assert.equal(revisions.length, 2);
  assert.equal(third.current?.revisionHistory?.length, 2);
  assert.equal(revisions[0]?.document?.revision, 2);
  assert.equal(revisions[0]?.state, 'RECEIPT');
  assert.match(revisions[0]?.summary ?? '', /reviewer-r2.*第一次核对.*新的文档版本/);
  assert.equal(revisions[1]?.document?.revision, 3);
  assert.equal(revisions[1]?.state, 'CURRENT');
  assert.match(revisions[1]?.summary ?? '', /reviewer-r3.*第二次核对.*新的文档版本/);
});

test('opening the current review selects its document, first unconfirmed section, and a repeatable focus target', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));

  const first = openCurrentDocumentReview(mysql, { focusToken: 0 });
  const reopened = openCurrentDocumentReview(mysql, first);

  assert.deepEqual(first, {
    selectedDocumentId: mysql.current?.document?.documentId,
    selectedSection: 'GOAL',
    focusTargetId: `guanyijia-document-review:${mysql.current?.document?.documentId}:heading`,
    focusToken: 1,
  });
  assert.equal(reopened.selectedDocumentId, first.selectedDocumentId);
  assert.equal(reopened.selectedSection, first.selectedSection);
  assert.equal(reopened.focusTargetId, first.focusTargetId);
  assert.equal(reopened.focusToken, 2);
});

test('timeline stays summary-only while the 63 pending assets are exposed in 20-item document pages', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));

  const page = pageCurrentDocumentBlocks(mysql, 'UNRESOLVED', 1);

  assert.equal(page.items.length, 20);
  assert.equal(page.pageSize, 20);
  assert.equal(page.total, 64);
  assert.equal(page.pageCount, 4);
  assert.equal(page.groups.find((group) => group.evidenceStatus === 'GAP')?.items.length, 20);
  const timelineJson = JSON.stringify(mysql.timeline);
  assert.doesNotMatch(timelineJson, /gyj-block:pending\.table:01/);
  assert.doesNotMatch(timelineJson, /mysql_jsh_erp@v1:/);
});

test('facts inspector projects source identity, read counts, locator, lineage, and affected objects', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));
  const stockGap = mysql.current!.compilation!.blocks.find((block) => block.stableCode === 'gap.current_stock_as_of')!;

  const inspector = factsInspectorFor(mysql, stockGap.blockId);

  assert.equal(inspector?.sourceName, '管伊佳部署数据库');
  assert.equal(inspector?.sourceClass, 'REAL');
  assert.equal(inspector?.authority, 'PRIMARY');
  assert.equal(inspector?.evidenceCount, 1458);
  assert.equal(inspector?.block?.label, '当前库存生效时点缺口');
  assert.equal(inspector?.block?.locator?.objectName, 'jsh_material_current_stock');
  assert.deepEqual(inspector?.block?.affectedObjects, ['实体：inventory_position']);
  assert.deepEqual(inspector?.lineage, { status: 'ROOT', upstreamSourceNames: [] });
});

test('each source timeline receipt carries its own lightweight inspector projection', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));

  const receipt = mysql.timeline.find((item) => item.kind === 'SOURCE_READ_COMPLETED');

  assert.equal(receipt?.inspector?.sourceId, 'guanyijia_mysql');
  assert.equal(receipt?.inspector?.evidenceCount, 1458);
  assert.equal(receipt?.inspector?.block, undefined);
});

test('only an unfinished reading, document, or conflict event stays expanded as current', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));
  assert.equal(mysql.timeline.find((item) => item.kind === 'DOCUMENT_GENERATED')?.state, 'CURRENT');
  const reviewed = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', mysql.run!.revision, 'review:timeline-state',
  ));
  assert.equal(reviewed.timeline.every((item) => item.state === 'RECEIPT'), true);
});

test('a malformed or cross-project active-run pointer fails readably without falling back', async () => {
  const { runtime, pointerStorage } = fixture();
  const initial = await runtime.read('user-author');
  const pointerKey = guanyijiaActiveRunPointerStorageKeyFor(initial.batchId);
  pointerStorage.setItem(pointerKey, '{broken');
  await assert.rejects(runtime.read('user-author'), /活动运行指针已损坏：无法解析 JSON/);

  pointerStorage.setItem(pointerKey, JSON.stringify({
    schemaVersion: 1,
    projectId: 'group_retail_ops',
    storyId: 'guanyijia-five-source-v1',
    batchId: 'wrong-batch',
    runId: 'standardization-run-99',
    commandFingerprints: {},
  }));
  await assert.rejects(runtime.read('user-author'), /项目、故事版本或运行标识不匹配/);
});

test('reload拒绝hash合法但与blocks assertions sections不一致的Markdown', async () => {
  const { runtime, metadataStorage, contentStore } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));
  const unrelatedRef = await contentStore.put('另一份hash合法但不属于当前来源投影的Markdown。');
  const raw = JSON.parse(metadataStorage.getItem('linguan:source-documents:v1')!);
  raw.documents[0].markdownRef = unrelatedRef;
  raw.documents[0].markdownSha256 = unrelatedRef.slice('sha256:'.length);
  metadataStorage.setItem('linguan:source-documents:v1', JSON.stringify(raw));

  await assert.rejects(() => runtime.read('user-author'), /Markdown与结构化投影不一致/);
});

test('同snapshot旧文档无blocks时workbench只读且不从默认story猜测结构化块', async () => {
  const { runtime, sourceDocuments } = fixture();
  const story = createGuanyijiaStandardizationStory();
  const compilation = story.compileSource({ sourceId: 'guanyijia_mysql', priorCompilations: [] });
  const legacy = await sourceDocuments.register({
    projectId: 'guanyijia_erp', documentCode: 'guanyijia-five-source--guanyijia_mysql',
    sourceSnapshotId: compilation.snapshotId, sourceType: compilation.sourceClass,
    sourceName: compilation.sourceName, sections: compilation.sections,
    assertions: compilation.assertions, validation: { errors: [], warnings: [], gaps: [] },
    actorUserId: 'legacy-author',
  });
  const started = await runtime.execute(command('START_RUN', 0));
  const snapshot = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));

  assert.equal(snapshot.current?.document?.documentId, legacy.documentId);
  assert.equal(snapshot.current?.document?.blocksRef, undefined);
  assert.equal(snapshot.current?.compilation, undefined, '旧文档不能回退默认story blocks');
  assert.deepEqual(snapshot.current?.legacyReadOnly?.sections, compilation.sections);
  await assert.rejects(() => runtime.execute({
    type: 'APPLY_CURRENT_BLOCK_CHANGE', commandId: 'legacy:apply',
    expectedRevision: snapshot.run!.revision, actorUserId: 'legacy-author',
    change: { blockId: compilation.blocks[0]!.blockId, label: '不能修改' },
  }), /旧版来源文档没有结构化块，只能只读/);
  await assert.rejects(() => runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'legacy:complete',
  )), /旧版来源文档没有结构化块，只能只读/);
});

test('reload要求run中的document code source identity与revision精确匹配', async () => {
  const { runtime, pointerStorage, sourceDocuments, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));
  await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision));
  const mismatchedDocuments = {
    ...sourceDocuments,
    async read(documentId: string) {
      const document = await sourceDocuments.read(documentId);
      return document ? { ...document, revision: document.revision + 1 } : null;
    },
  };
  const restored = createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns: runs, sourceDocuments: mismatchedDocuments,
  });

  await assert.rejects(() => restored.read('user-author'), /来源文档身份不匹配/);
});

test('active-run pointers are isolated by the canonical story version', async () => {
  const { runtime, pointerStorage, sourceDocuments, runs } = fixture();
  const first = await runtime.execute(command('START_RUN', 0));
  const changedPolicy = structuredClone(defaultGuanyijiaPolicyDocuments);
  changedPolicy[2]!.content = `${changedPolicy[2]!.content}\n`;
  const changedStoryRuntime = createGuanyijiaWorkbenchRuntime({
    pointerStorage,
    standardizationRuns: runs,
    sourceDocuments,
    story: createGuanyijiaStandardizationStory({ policyDocuments: changedPolicy }),
  });

  const changedStory = await changedStoryRuntime.read('user-author');

  assert.notEqual(changedStory.batchId, first.batchId);
  assert.equal(changedStory.run, null);
});

test('prototype-named command IDs remain valid and idempotent at the workbench seam', async () => {
  const { runtime } = fixture();
  const started = await runtime.execute(command('START_RUN', 0));

  const mysql = await runtime.execute(command(
    'READ_NEXT_SOURCE', started.run!.revision, 'constructor',
  ));
  const repeated = await runtime.execute(command(
    'READ_NEXT_SOURCE', started.run!.revision, 'constructor',
  ));

  assert.equal(mysql.run?.revision, repeated.run?.revision);
  assert.equal(mysql.current?.document?.documentId, repeated.current?.document?.documentId);
});

test('a reused or restored same-batch run must still match the exact five-source story', async () => {
  const { runtime, runs, pointerStorage } = fixture();
  const initial = await runtime.read('user-author');
  const foreignRun = await runs.execute({
    type: 'CREATE_RUN',
    commandId: 'foreign:create',
    expectedRevision: 0,
    actor: { userId: 'foreign-user' },
    projectId: 'guanyijia_erp',
    scenarioKey: 'guanyijia-five-source-v1',
    batchId: initial.batchId,
    sources: [{ sourceId: 'foreign-source', sourceName: '外部来源' }],
  });

  await assert.rejects(
    runtime.execute(command('START_RUN', 0, 'start:strict-five-source')),
    /运行来源与当前标准化流程不匹配/,
  );
  const pointerKey = guanyijiaActiveRunPointerStorageKeyFor(initial.batchId);
  assert.equal(pointerStorage.getItem(pointerKey), null);

  pointerStorage.setItem(pointerKey, JSON.stringify({
    schemaVersion: 1,
    projectId: 'guanyijia_erp',
    storyId: 'guanyijia-five-source-v1',
    batchId: initial.batchId,
    runId: foreignRun.runId,
    commandFingerprints: {},
  }));
  await assert.rejects(runtime.read('user-author'), /运行来源与当前标准化流程不匹配/);
});
