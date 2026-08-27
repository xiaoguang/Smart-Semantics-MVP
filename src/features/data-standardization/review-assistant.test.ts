import assert from 'node:assert/strict';
import test from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import {
  answerReviewAssistant,
  classifyReviewAssistantIntent,
  normalizeReviewAssistantMessage,
  reviewAssistantDiffWindow,
} from './review-assistant.ts';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';
import {
  createMemoryContentStore,
  createSourceDocumentRuntime,
  diffSourceDocumentLines,
} from '../source-documents/runtime.ts';
import { createStandardizationRunRuntime } from '../standardization-run/runtime.ts';
import {
  createGuanyijiaWorkbenchRuntime,
  validateGuanyijiaAssistantArtifactAgainstPersistedRun,
  validateGuanyijiaAssistantConfirmationAgainstPersistedRevision,
  type GuanyijiaWorkbenchCommand,
} from './guanyijia-workbench-runtime.ts';
import type { ReviewAssistantAccess, ReviewAssistantKnowledge } from './review-assistant-types.ts';

class MemoryStorage implements Pick<Storage, 'getItem' | 'setItem'> {
  readonly values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

class AssistantPointerFailureStorage extends MemoryStorage {
  failCompletionFor?: string;

  override setItem(key: string, value: string) {
    const pointer = JSON.parse(value) as { commandFingerprints?: Record<string, string> };
    if (this.failCompletionFor && pointer.commandFingerprints?.[this.failCompletionFor]) {
      const commandId = this.failCompletionFor;
      this.failCompletionFor = undefined;
      throw new Error(`模拟助手确认指针完成失败:${commandId}`);
    }
    super.setItem(key, value);
  }
}

function fixture(
  accessForActor?: (actorUserId: string) => ReviewAssistantAccess,
  pointerStorage: MemoryStorage = new MemoryStorage(),
  now: () => string = () => '2026-08-18T18:00:00.000Z',
) {
  const metadataStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const sourceDocuments = createSourceDocumentRuntime({ metadataStorage, contentStore });
  const story = createGuanyijiaStandardizationStory();
  const runs = createStandardizationRunRuntime({
    metadataStorage,
    contentStore,
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
  const createRuntime = (standardizationRuns = runs) => createGuanyijiaWorkbenchRuntime({
    pointerStorage, standardizationRuns, sourceDocuments, story,
    contentStore,
    ...(accessForActor ? { accessForActor } : {}),
    now,
  });
  const runtime = createRuntime();
  return {
    runtime, createRuntime, runs, contentStore, sourceDocuments,
    pointerStorage, metadataStorage, story,
  };
}

function command(
  type: GuanyijiaWorkbenchCommand['type'],
  expectedRevision: number,
  commandId = `assistant:${type}:${expectedRevision}`,
): GuanyijiaWorkbenchCommand {
  return { type, expectedRevision, commandId, actorUserId: 'user_bo_gao' } as GuanyijiaWorkbenchCommand;
}

test('审阅助手规范化NFKC与空白并按固定优先级识别意图', () => {
  assert.equal(normalizeReviewAssistantMessage('  查看\u3000当前  来源  '), '查看 当前 来源');
  assert.deepEqual(classifyReviewAssistantIntent('建议把当前项名称改为： 新名称 '), {
    kind: 'PROPOSE_BLOCK_CHANGE',
    field: 'label',
    value: '新名称',
    normalizedMessage: '建议把当前项名称改为: 新名称',
  });
  assert.deepEqual(classifyReviewAssistantIntent('建议把当前项识别结果改为：允许分仓负库存'), {
    kind: 'PROPOSE_BLOCK_CHANGE',
    field: 'value',
    value: '允许分仓负库存',
    normalizedMessage: '建议把当前项识别结果改为:允许分仓负库存',
  });
  assert.equal(classifyReviewAssistantIntent('打开当前冲突并直接应用').kind, 'OPEN_TARGET');
  assert.equal(classifyReviewAssistantIntent('影响哪些对象，为什么？').kind, 'AFFECTED_OBJECTS');
  assert.equal(classifyReviewAssistantIntent('依据是什么，有什么冲突？').kind, 'EVIDENCE');
  assert.equal(classifyReviewAssistantIntent('冻结并交付').kind, 'BOUNDARY');
  assert.equal(classifyReviewAssistantIntent('请替我解决冲突并交付').kind, 'BOUNDARY');
  assert.equal(classifyReviewAssistantIntent('确认应用刚才的建议').kind, 'BOUNDARY');
  assert.equal(classifyReviewAssistantIntent('今天天气如何').kind, 'SUPPORTED_SCOPE');
});

test('影响对象使用一条简洁说明，并最多给出3个打开候选', () => {
  const story = createGuanyijiaStandardizationStory();
  const source = story.listSources()[0]!;
  const compilation = story.compileSource({ sourceId: source.sourceId, priorCompilations: [] });
  const block = compilation.blocks[0]!;
  const affectedObjectRefs = Array.from({ length: 5 }, (_, index) => ({
    kind: 'ENTITY' as const, objectId: `assistant-target-${index + 1}`,
  }));
  const knowledge: ReviewAssistantKnowledge = {
    sources: [{
      sourceId: source.sourceId, sourceName: source.sourceName,
      sourceClass: source.sourceClass, authority: source.authority,
      snapshotId: source.snapshotId, versionRef: compilation.readSummary.versionRef,
      readSummary: compilation.readSummary.summary,
      documentId: 'assistant-target-document', documentRevision: 1,
      blocks: [{ ...block, affectedObjectRefs }],
      evidenceLocators: compilation.evidenceLocators,
    }],
    conflicts: [], currentSourceId: source.sourceId,
    currentDocumentId: 'assistant-target-document', currentBlockId: block.blockId,
  };
  const response = answerReviewAssistant({
    intent: classifyReviewAssistantIntent('影响哪些对象'),
    selection: {
      sourceId: source.sourceId, documentId: 'assistant-target-document', blockId: block.blockId,
    },
    knowledge,
  });
  if (response.kind !== 'AFFECTED_OBJECTS') assert.fail('应返回影响对象解释');
  assert.equal(response.body.length, 1);
  assert.match(response.body[0]!, /当前结论关联 5 个业务对象/u);
  assert.equal(response.targets.length, 3);
});

test('助手Diff窗口围绕尾部真实变更且总行数不超过120', () => {
  const lines = Array.from({ length: 200 }, (_, index) => ({
    type: index === 180 ? 'REMOVED' as const : index === 181 ? 'ADDED' as const : 'UNCHANGED' as const,
    line: `line-${index + 1}`,
  }));
  const window = reviewAssistantDiffWindow(lines);
  assert.equal(window.lines.length, 120);
  assert.equal(window.lines.some((line) => line.type === 'REMOVED'), true);
  assert.equal(window.lines.some((line) => line.type === 'ADDED'), true);
  assert.ok(window.omittedBefore > 0);
});

test('Run持久化seam在CAS前拒绝跨运行目标的伪造Assistant Turn', async () => {
  const { runtime, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'forged-turn:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'forged-turn:read'));
  await assert.rejects(runs.execute({
    type: 'RECORD_ASSISTANT_TURN', commandId: 'forged-turn:direct-run',
    expectedRevision: mysql.run!.revision, actor: { userId: 'user_bo_gao' },
    runId: mysql.run!.runId,
    payload: JSON.stringify({
      schemaVersion: 1, turnId: 'forged-turn', runId: mysql.run!.runId,
      actorUserId: 'user_bo_gao', message: '查看伪造来源',
      selection: { sourceId: 'other-project-source' },
      knowledgeSourceRevisions: [{
        sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId, documentRevision: 1,
      }],
      response: { kind: 'SUPPORTED_SCOPE', title: '伪造', body: ['伪造回复'] },
      createdAt: '2026-08-18T18:00:00.000Z',
    }),
  }), /唯一投影|不属于当前运行/);
});

test('Run持久化seam在CAS前拒绝目标合法但回答不是确定性投影的Turn', async () => {
  const { runtime, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'forged-answer:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'forged-answer:read'));
  await assert.rejects(runs.execute({
    type: 'RECORD_ASSISTANT_TURN', commandId: 'forged-answer:direct-run',
    expectedRevision: mysql.run!.revision, actor: { userId: 'user_bo_gao' },
    runId: mysql.run!.runId,
    payload: JSON.stringify({
      schemaVersion: 1, turnId: 'forged-answer', runId: mysql.run!.runId,
      actorUserId: 'user_bo_gao', message: '当前来源是什么',
      selection: {
        sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId,
      },
      knowledgeSourceRevisions: [{
        sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId, documentRevision: 1,
      }],
      response: {
        kind: 'SOURCE_EXPLANATION', title: '攻击者伪造结论', body: ['这个答案没有从当前Run投影。'], targets: [],
      },
      createdAt: '2026-08-18T18:00:00.000Z',
    }),
  }), /确定性投影/);
});

test('Run持久化seam即使回答不使用objectRef也拒绝伪造的选择目标', async () => {
  const { runtime, runs } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'forged-object:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'forged-object:read'));
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'forged-object:legitimate-turn',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '当前来源是什么',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
    },
  } as GuanyijiaWorkbenchCommand);
  const event = snapshot.run!.timeline.at(-1)!;
  const turn = JSON.parse(await runs.readEventPayload(snapshot.run!.runId, event.eventId)) as {
    turnId: string;
    selection: Record<string, unknown>;
  };
  turn.turnId = 'assistant-turn:forged-object';
  turn.selection.objectRef = { kind: 'ENTITY', objectId: 'attacker-cross-run-object' };
  await assert.rejects(runs.execute({
    type: 'RECORD_ASSISTANT_TURN', commandId: 'forged-object:direct-run',
    expectedRevision: snapshot.run!.revision, actor: { userId: 'user_bo_gao' },
    runId: snapshot.run!.runId, payload: JSON.stringify(turn),
  }), /影响对象目标不属于当前运行/);
});

test('同一Run内合法但属于另一Block的objectRef不能注入当前项回答', async () => {
  const { runtime } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'mismatched-object:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'mismatched-object:read'));
  const blocks = snapshot.current!.compilation!.blocks;
  const pair = blocks.flatMap((block) => blocks.flatMap((other) => {
    const foreign = other.affectedObjectRefs.find((reference) => !block.affectedObjectRefs.some((candidate) => (
      canonicalModelingJson(candidate) === canonicalModelingJson(reference)
    )));
    return foreign ? [{ block, foreign }] : [];
  }))[0];
  assert.ok(pair, 'fixture应包含分属不同Block的影响对象');
  await assert.rejects(runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'mismatched-object:ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '影响哪些对象',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      blockId: pair.block.blockId, objectRef: pair.foreign,
    },
  } as GuanyijiaWorkbenchCommand), /影响对象目标与当前Block不一致/);
});

test('Assistant selection必须保持timeline来源与Block章节的一致性', async () => {
  const { runtime } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'selection-coherence:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'selection-coherence:mysql'));
  const mysqlGenerated = snapshot.run!.timeline.find((event) => event.type === 'DOCUMENT_GENERATED')!;
  snapshot = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'selection-coherence:review-mysql',
  ));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'selection-coherence:github'));
  const block = snapshot.current!.compilation!.blocks[0]!;
  await assert.rejects(runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'selection-coherence:timeline',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '当前来源是什么',
    selection: {
      timelineItemId: mysqlGenerated.eventId,
      sourceId: 'guanyijia_github', documentId: snapshot.current!.document!.documentId,
    },
  } as GuanyijiaWorkbenchCommand), /时间线目标与当前来源不一致/);
  await assert.rejects(runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'selection-coherence:conflict-source',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '当前差异是什么',
    selection: { sourceId: 'guanyijia_mysql', conflictId: 'gyj-conflict-debt-schema' },
  } as GuanyijiaWorkbenchCommand), /冲突目标与当前来源不一致/);
  await assert.rejects(runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'selection-coherence:section',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '打开当前项',
    selection: {
      sourceId: 'guanyijia_github', documentId: snapshot.current!.document!.documentId,
      blockId: block.blockId, section: block.section === 'OVERVIEW' ? 'GOAL' : 'OVERVIEW',
    },
  } as GuanyijiaWorkbenchCommand), /章节目标与当前Block不一致/);
});

test('Run持久化seam拒绝同步重算Hunk与SHA但Before不来自持久化revision的Proposal', async () => {
  const { runtime, runs, story } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'forged-proposal:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'forged-proposal:read'));
  const persisted = mysql.current!.compilation!;
  const persistedBlock = persisted.blocks[0]!;
  const forgedCurrent = story.reviseCompilation({
    compilation: persisted,
    priorCompilations: [],
    changes: [{ blockId: persistedBlock.blockId, label: '攻击者伪造的当前事实' }],
  }).compilation;
  const forgedBefore = forgedCurrent.blocks[0]!;
  const forgedChange = { blockId: forgedBefore.blockId, label: '攻击者建议' };
  const forgedRevision = story.reviseCompilation({
    compilation: forgedCurrent, priorCompilations: [], changes: [forgedChange],
  });
  const diff = forgedRevision.diff;
  const block = diff.blockChanges[0]!;
  const assertion = diff.assertionChanges[0]!;
  const section = diff.sectionChanges[0]!;
  const preview = {
    block,
    assertion,
    section: { ...section, lines: diffSourceDocumentLines(section.before, section.after) },
    markdown: { ...diff.markdown, lines: diffSourceDocumentLines(diff.markdown.before, diff.markdown.after) },
    affectedConflicts: diff.affectedConflicts,
    affectedObjects: persistedBlock.affectedObjectRefs.map((reference) => `${reference.kind}:${reference.objectId}`),
  };
  const proposalCore = {
    runId: mysql.run!.runId, sourceId: 'guanyijia_mysql',
    documentId: mysql.current!.document!.documentId, documentRevision: 1,
    blockId: forgedBefore.blockId, change: forgedChange, beforeBlock: forgedBefore, preview,
  };
  const proposal = {
    schemaVersion: 1 as const,
    proposalId: 'assistant-proposal:forged-full-chain',
    ...proposalCore,
    previewSha256: `sha256:${sha256HexSync(canonicalModelingJson(proposalCore))}` as const,
    createdBy: 'user_bo_gao', createdAt: '2026-08-18T18:00:00.000Z',
  };
  const proposalPayload = JSON.stringify(proposal);
  const proposalRef = `sha256:${sha256HexSync(proposalPayload)}` as const;
  await assert.rejects(runs.execute({
    type: 'RECORD_ASSISTANT_TURN', commandId: 'forged-proposal:direct-run',
    expectedRevision: mysql.run!.revision, actor: { userId: 'user_bo_gao' },
    runId: mysql.run!.runId,
    proposalPayload,
    payload: JSON.stringify({
      schemaVersion: 1, turnId: 'assistant-turn:forged-full-chain', runId: mysql.run!.runId,
      actorUserId: 'user_bo_gao', message: '建议把当前项名称改为:攻击者建议',
      selection: {
        sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId,
        section: persistedBlock.section, blockId: persistedBlock.blockId,
      },
      knowledgeSourceRevisions: [{
        sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId, documentRevision: 1,
      }],
      response: {
        kind: 'PATCH_PREVIEW', title: `建议修改“${forgedBefore.label}”`,
        body: ['已从当前持久化 revision 生成真实 Block、Assertion、章节与 Markdown 差异。只有点击确认按钮后才会应用。'],
        proposalId: proposal.proposalId, proposalRef,
      },
      createdAt: '2026-08-18T18:00:00.000Z',
    }),
  }), /Before Block与持久化revision不一致/);
});

test('Run持久化seam在CAS前拒绝不存在的伪造确认revision', async () => {
  const { runtime, runs } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'forged-confirm:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'forged-confirm:read'));
  const block = snapshot.current!.compilation!.blocks[0]!;
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'forged-confirm:ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为:合法的修改建议',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);
  const turnEvent = snapshot.run!.timeline.at(-1)!;
  const turn = JSON.parse(await runs.readEventPayload(snapshot.run!.runId, turnEvent.eventId)) as {
    response: { proposalId: string; proposalRef: string };
  };
  const history = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  });
  const response = history.items.at(-1)!.response;
  if (response.kind !== 'PATCH_PREVIEW') assert.fail('应生成Proposal');
  const forgedDocumentId = 'attacker-document-r2';
  const confirmation = {
    schemaVersion: 1 as const, runId: snapshot.run!.runId, sourceId: 'guanyijia_mysql',
    proposalId: response.proposal.proposalId, proposalRef: turn.response.proposalRef,
    expectedPreviewSha256: response.proposal.previewSha256,
    beforeDocumentId: snapshot.current!.document!.documentId, beforeDocumentRevision: 1,
    documentId: forgedDocumentId, documentRevision: 2, blockId: block.blockId,
    actorUserId: 'user_bo_gao', confirmedAt: '2026-08-18T18:00:00.000Z',
  };
  await assert.rejects(runs.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'forged-confirm:direct-run',
    expectedRevision: snapshot.run!.revision, actor: { userId: 'user_bo_gao' },
    runId: snapshot.run!.runId, sourceId: 'guanyijia_mysql',
    proposalId: response.proposal.proposalId,
    expectedPreviewSha256: response.proposal.previewSha256,
    beforeDocumentId: snapshot.current!.document!.documentId, beforeDocumentRevision: 1,
    documentId: forgedDocumentId, documentRevision: 2,
    introducedConflictIds: [],
    diffSummary: { changedBlockIds: [block.blockId], changedSections: [block.section], affectedObjectIds: [] },
    payload: JSON.stringify(confirmation),
  }), /确认revision无法由持久化来源文档复算/);
  const persisted = await runs.read(snapshot.run!.runId);
  assert.equal(persisted?.revision, snapshot.run!.revision);
});

test('自由文本确认只记录边界回复，Viewer可看建议但只有Editor/Admin能明确应用', async () => {
  let access: ReviewAssistantAccess = { active: true, role: 'EDITOR' };
  const { runtime, sourceDocuments } = fixture(() => access);
  const started = await runtime.execute(command('START_RUN', 0, 'permission:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'permission:read:mysql'));
  const block = mysql.current!.compilation!.blocks[0]!;
  access = { active: true, role: 'VIEWER' };
  const boundary = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'permission:free-confirm',
    expectedRevision: mysql.run!.revision, actorUserId: 'user_viewer',
    message: '确认应用刚才的建议', selection: { documentId: mysql.current!.document!.documentId },
  } as GuanyijiaWorkbenchCommand);
  let history = await runtime.readAssistantHistory({ actorUserId: 'user_viewer', runId: boundary.run!.runId });
  assert.equal(history.items.at(-1)?.response.kind, 'BOUNDARY');
  assert.equal((await sourceDocuments.list('guanyijia_erp')).length, 1);
  const proposed = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'permission:proposal',
    expectedRevision: boundary.run!.revision, actorUserId: 'user_viewer',
    message: '建议把当前项名称改为：查看者建议名称',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId,
      blockId: block.blockId, section: block.section,
    },
  } as GuanyijiaWorkbenchCommand);
  history = await runtime.readAssistantHistory({ actorUserId: 'user_viewer', runId: proposed.run!.runId });
  const preview = history.items.at(-1)?.response;
  if (preview?.kind !== 'PATCH_PREVIEW') assert.fail('Viewer应可查看建议预览');
  await assert.rejects(runtime.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'permission:deny-confirm',
    expectedRevision: proposed.run!.revision, actorUserId: 'user_viewer',
    proposalId: preview.proposal.proposalId,
    expectedPreviewSha256: preview.proposal.previewSha256,
  } as GuanyijiaWorkbenchCommand), /只有编辑者或管理员/);
  assert.equal((await sourceDocuments.list('guanyijia_erp')).length, 1);
  access = { active: false, role: 'VIEWER' };
  await assert.rejects(runtime.read('user_removed'), /不是当前项目ACTIVE成员/);
});

test('审阅助手拒绝空输入和超过4000字符的消息', () => {
  assert.throws(() => normalizeReviewAssistantMessage(' \u3000 '), /消息不能为空/);
  assert.throws(() => normalizeReviewAssistantMessage('甲'.repeat(4001)), /最多4000/);
});

test('ASK命令把输入与确定性来源回答作为内容寻址Turn写入同一Run时间线', async () => {
  const { runtime, runs } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'assistant:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'assistant:read:mysql'));

  const asked = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT',
    commandId: 'assistant:ask:source',
    expectedRevision: mysql.run!.revision,
    actorUserId: 'user_bo_gao',
    message: '  当前来源的快照和读取范围是什么？ ',
    selection: { sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId },
  } as GuanyijiaWorkbenchCommand);

  assert.equal(asked.run!.timeline.at(-1)?.type, 'ASSISTANT_TURN_RECORDED');
  assert.equal(asked.run!.sources[0]?.status, 'DOCUMENT_READY');
  const history = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: asked.run!.runId,
  });
  assert.equal(history.items.length, 1);
  assert.equal(history.nextCursor, null);
  assert.equal(history.items[0]?.message, '当前来源的快照和读取范围是什么?');
  assert.equal(history.items[0]?.response.kind, 'SOURCE_EXPLANATION');
  const raw = await runs.readEventPayload(asked.run!.runId, asked.run!.timeline.at(-1)!.eventId);
  assert.doesNotMatch(raw, /## 1\.|markdown/iu);
});

test('修改建议生成当前持久化revision的真实Block Assertion与Markdown Diff且Turn只引用Proposal', async () => {
  const { runtime, runs, contentStore } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'proposal:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'proposal:read:mysql'));
  const block = mysql.current!.compilation!.blocks[0];
  assert.ok(block);

  const asked = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'proposal:ask',
    expectedRevision: mysql.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为：进销存单据主表',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);

  const history = await runtime.readAssistantHistory({ actorUserId: 'user_bo_gao', runId: asked.run!.runId });
  const response = history.items[0]?.response;
  assert.equal(response?.kind, 'PATCH_PREVIEW');
  if (response?.kind !== 'PATCH_PREVIEW') assert.fail('应返回真实Patch预览');
  assert.equal(response.proposal.documentId, mysql.current!.document!.documentId);
  assert.equal(response.proposal.documentRevision, 1);
  assert.equal(response.proposal.beforeBlock.label, block.label);
  assert.equal(response.proposal.preview.block.after.label, '进销存单据主表');
  assert.equal(response.proposal.preview.assertion.after.statement.startsWith('进销存单据主表：'), true);
  assert.equal(response.proposal.preview.markdown.lines.some((line) => line.type === 'REMOVED'), true);
  assert.equal(response.proposal.preview.markdown.lines.some((line) => line.type === 'ADDED'), true);
  assert.equal(history.items[0]?.proposalStatus, 'PENDING');
  const event = asked.run!.timeline.at(-1)!;
  const rawTurn = await runs.readEventPayload(asked.run!.runId, event.eventId);
  assert.doesNotMatch(rawTurn, /"preview"|"beforeBlock"|"change"/u);
  const turn = JSON.parse(rawTurn) as { response: { proposalRef: `sha256:${string}` } };
  assert.ok(await contentStore.get(turn.response.proposalRef));
  assert.equal(asked.run!.sources[0]?.status, 'DOCUMENT_READY');
});

test('取消Proposal会写入内容寻址Run审计，不修订文档且不能再确认', async () => {
  const { runtime, runs, sourceDocuments } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'cancel:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'cancel:read'));
  const documentId = snapshot.current!.document!.documentId;
  const block = snapshot.current!.compilation!.blocks[0]!;
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'cancel:proposal',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为：取消的建议',
    selection: {
      sourceId: 'guanyijia_mysql', documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);
  const proposed = await runtime.readAssistantHistory({ actorUserId: 'user_bo_gao', runId: snapshot.run!.runId });
  const response = proposed.items.at(-1)!.response;
  if (response.kind !== 'PATCH_PREVIEW') assert.fail('应生成待取消Proposal');

  const cancelled = await runtime.execute({
    type: 'CANCEL_ASSISTANT_PATCH', commandId: 'cancel:apply',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: response.proposal.proposalId,
  } as GuanyijiaWorkbenchCommand);
  assert.equal(cancelled.current!.document!.documentId, documentId);
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map(({ revision }) => revision), [1]);
  assert.equal(cancelled.run!.timeline.at(-1)?.type, 'ASSISTANT_PATCH_CANCELLED');
  const payload = JSON.parse(await runs.readEventPayload(
    cancelled.run!.runId, cancelled.run!.timeline.at(-1)!.eventId,
  )) as { proposalId: string; cancelledBy: string };
  assert.equal(payload.proposalId, response.proposal.proposalId);
  assert.equal(payload.cancelledBy, 'user_bo_gao');
  const history = await runtime.readAssistantHistory({ actorUserId: 'user_bo_gao', runId: cancelled.run!.runId });
  assert.equal(history.items.at(-1)?.proposalStatus, 'CANCELLED');
  assert.equal(history.pendingProposal, undefined);
  await assert.rejects(runtime.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'cancel:forbidden-confirm',
    expectedRevision: cancelled.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: response.proposal.proposalId,
    expectedPreviewSha256: response.proposal.previewSha256,
  } as GuanyijiaWorkbenchCommand), /最新一份未确认Proposal/u);
});

test('明确确认从Run内Proposal复算并以同一CAS相邻写入确认与DOCUMENT_REVISED', async () => {
  const { runtime, runs, sourceDocuments } = fixture();
  const started = await runtime.execute(command('START_RUN', 0, 'confirm:start'));
  const mysql = await runtime.execute(command('READ_NEXT_SOURCE', started.run!.revision, 'confirm:read:mysql'));
  const block = mysql.current!.compilation!.blocks[0]!;
  const asked = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'confirm:ask',
    expectedRevision: mysql.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为：已确认的单据事实',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: mysql.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);
  const pending = await runtime.readAssistantHistory({ actorUserId: 'user_bo_gao', runId: asked.run!.runId });
  const response = pending.items[0]!.response;
  if (response.kind !== 'PATCH_PREVIEW') assert.fail('应生成待确认Proposal');

  const confirmed = await runtime.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'confirm:apply',
    expectedRevision: asked.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: response.proposal.proposalId,
    expectedPreviewSha256: response.proposal.previewSha256,
  } as GuanyijiaWorkbenchCommand);

  assert.deepEqual(confirmed.run!.timeline.slice(-2).map((event) => event.type), [
    'ASSISTANT_PATCH_CONFIRMED', 'DOCUMENT_REVISED',
  ]);
  assert.equal(confirmed.run!.timeline.at(-2)?.createdAt, confirmed.run!.timeline.at(-1)?.createdAt);
  assert.equal(confirmed.current!.document!.revision, 2);
  assert.equal(confirmed.current!.compilation!.blocks[0]?.label, '已确认的单据事实');
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2]);
  const confirmation = JSON.parse(await runs.readEventPayload(
    confirmed.run!.runId, confirmed.run!.timeline.at(-2)!.eventId,
  )) as { proposalId: string; documentId: string; beforeDocumentId: string };
  assert.equal(confirmation.proposalId, response.proposal.proposalId);
  assert.equal(confirmation.documentId, confirmed.current!.document!.documentId);
  assert.equal(confirmation.beforeDocumentId, mysql.current!.document!.documentId);
  const history = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: confirmed.run!.runId,
  });
  assert.equal(history.items[0]?.proposalStatus, 'CONFIRMED');
  assert.deepEqual(confirmed.assistantFocusTarget, {
    kind: 'SOURCE_DOCUMENT', documentId: confirmed.current!.document!.documentId,
    section: block.section, blockId: block.blockId,
  });
});

test('助手历史对当前页已确认Proposal定向校验确认Artifact', async () => {
  const { runtime, runs, contentStore } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'confirmed-history:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'confirmed-history:read'));
  const block = snapshot.current!.compilation!.blocks[0]!;
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'confirmed-history:proposal',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为:必须校验确认正文',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);
  const pending = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  });
  const response = pending.items[0]!.response;
  if (response.kind !== 'PATCH_PREVIEW') assert.fail('应生成待确认Proposal');
  snapshot = await runtime.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'confirmed-history:confirm',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: response.proposal.proposalId,
    expectedPreviewSha256: response.proposal.previewSha256,
  } as GuanyijiaWorkbenchCommand);
  const confirmationRef = snapshot.run!.timeline.find((event) => (
    event.type === 'ASSISTANT_PATCH_CONFIRMED'
  ))!.payloadRef;
  const originalGet = contentStore.get.bind(contentStore);
  contentStore.get = async (ref) => ref === confirmationRef ? null : originalGet(ref);

  assert.ok(await runs.read(snapshot.run!.runId));
  await assert.rejects(runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  }), /确认.*内容不存在|确认.*校验和/);
});

test('来源、冲突、证据、影响对象与打开目标都只从当前Run事实投影', async () => {
  const { runtime } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'answers:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'answers:read:mysql'));
  const mysqlDocumentId = snapshot.current!.document!.documentId;
  const block = snapshot.current!.compilation!.blocks.find((candidate) => candidate.evidenceRefs.length)!;
  const ask = async (commandId: string, message: string, selection: Record<string, unknown>) => {
    snapshot = await runtime.execute({
      type: 'ASK_REVIEW_ASSISTANT', commandId,
      expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao', message, selection,
    } as GuanyijiaWorkbenchCommand);
    return (await runtime.readAssistantHistory({
      actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
    })).items.at(-1)!.response;
  };
  const evidence = await ask('answers:evidence', '这项依据是什么？', {
    sourceId: 'guanyijia_mysql', documentId: mysqlDocumentId,
    blockId: block.blockId, evidenceRef: block.evidenceRefs[0],
  });
  assert.equal(evidence.kind, 'EVIDENCE_EXPLANATION');
  const affected = await ask('answers:affected', '影响哪些对象？', {
    sourceId: 'guanyijia_mysql', documentId: mysqlDocumentId, blockId: block.blockId,
  });
  assert.equal(affected.kind, 'AFFECTED_OBJECTS');
  if (affected.kind === 'AFFECTED_OBJECTS') assert.ok(affected.targets.length <= 20);
  const opened = await ask('answers:open', '打开当前项', {
    sourceId: 'guanyijia_mysql', documentId: mysqlDocumentId,
    section: block.section, blockId: block.blockId,
  });
  assert.deepEqual(opened.kind === 'OPEN_TARGET' ? opened.target : null, {
    kind: 'SOURCE_DOCUMENT', documentId: mysqlDocumentId,
    section: block.section, blockId: block.blockId,
  });

  snapshot = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'answers:review:mysql',
  ));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'answers:read:github'));
  snapshot = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'answers:review:github',
  ));
  const conflict = await ask('answers:conflict', '当前差异是什么？', {
    conflictId: 'gyj-conflict-debt-schema', sourceId: 'guanyijia_github',
  });
  assert.equal(conflict.kind, 'CONFLICT_EXPLANATION');
  if (conflict.kind === 'CONFLICT_EXPLANATION') {
    assert.match(conflict.body.join('\n'), /管伊佳部署数据库/u);
    assert.match(conflict.body.join('\n'), /jshERP 源码/u);
  }
});

test('证据意图没有选中Block时以纯函数降级回复持久化且仍可重载', async () => {
  const { runtime, runs } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'evidence-fallback:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'evidence-fallback:read'));
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'evidence-fallback:ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '这项依据是什么？',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
    },
  } as GuanyijiaWorkbenchCommand);

  const history = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  });
  assert.equal(history.items.at(-1)?.response.kind, 'SUPPORTED_SCOPE');
  assert.ok(await runs.read(snapshot.run!.runId));
});

test('第二次助手确认在Run CAS后指针完成失败可以幂等恢复', async () => {
  const pointerStorage = new AssistantPointerFailureStorage();
  const { runtime, runs, sourceDocuments } = fixture(undefined, pointerStorage);
  let snapshot = await runtime.execute(command('START_RUN', 0, 'assistant-saga:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'assistant-saga:read'));
  const firstBlock = snapshot.current!.compilation!.blocks[0]!;
  const proposeAndRead = async (commandId: string, label: string) => {
    snapshot = await runtime.execute({
      type: 'ASK_REVIEW_ASSISTANT', commandId,
      expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
      message: `建议把当前项名称改为:${label}`,
      selection: {
        sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
        section: firstBlock.section, blockId: firstBlock.blockId,
      },
    } as GuanyijiaWorkbenchCommand);
    const response = (await runtime.readAssistantHistory({
      actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
    })).items.at(-1)!.response;
    if (response.kind !== 'PATCH_PREVIEW') assert.fail('应生成待确认Proposal');
    return response.proposal;
  };
  const first = await proposeAndRead('assistant-saga:ask:first', '第一次确认');
  snapshot = await runtime.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'assistant-saga:confirm:first',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: first.proposalId, expectedPreviewSha256: first.previewSha256,
  } as GuanyijiaWorkbenchCommand);
  const second = await proposeAndRead('assistant-saga:ask:second', '第二次确认');
  const secondConfirm = {
    type: 'CONFIRM_ASSISTANT_PATCH' as const, commandId: 'assistant-saga:confirm:second',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: second.proposalId, expectedPreviewSha256: second.previewSha256,
  };
  pointerStorage.failCompletionFor = secondConfirm.commandId;
  await assert.rejects(runtime.execute(secondConfirm), /模拟助手确认指针完成失败/);
  const committed = await runs.read(snapshot.run!.runId);
  assert.equal(committed?.timeline.filter((event) => event.type === 'ASSISTANT_PATCH_CONFIRMED').length, 2);

  const recovered = await runtime.execute(secondConfirm);
  assert.equal(recovered.run?.revision, committed?.revision);
  assert.equal(recovered.run?.timeline.filter((event) => event.type === 'ASSISTANT_PATCH_CONFIRMED').length, 2);
  assert.equal(recovered.current?.document?.revision, 3);
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2, 3]);
});

test('助手确认Run写入失败后即使插入ASK也能以当前revision恢复既有r2', async () => {
  const base = fixture();
  let failNextConfirm = true;
  const flakyRuns: typeof base.runs = {
    ...base.runs,
    async execute(runCommand) {
      if (failNextConfirm && runCommand.type === 'CONFIRM_ASSISTANT_PATCH') {
        failNextConfirm = false;
        throw new Error('模拟助手确认Run CAS失败');
      }
      return base.runs.execute(runCommand);
    },
  };
  const runtime = base.createRuntime(flakyRuns);
  let snapshot = await runtime.execute(command('START_RUN', 0, 'interleaved-saga:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'interleaved-saga:read'));
  const block = snapshot.current!.compilation!.blocks[0]!;
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'interleaved-saga:proposal',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为:可恢复的r2',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);
  const response = (await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  })).items.at(-1)!.response;
  if (response.kind !== 'PATCH_PREVIEW') assert.fail('应生成待确认Proposal');
  const confirm = {
    type: 'CONFIRM_ASSISTANT_PATCH' as const,
    commandId: 'interleaved-saga:confirm', expectedRevision: snapshot.run!.revision,
    actorUserId: 'user_bo_gao', proposalId: response.proposal.proposalId,
    expectedPreviewSha256: response.proposal.previewSha256,
  };
  await assert.rejects(runtime.execute(confirm), /模拟助手确认Run CAS失败/);
  assert.deepEqual((await base.sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2]);
  const unchanged = await base.runs.read(snapshot.run!.runId);
  assert.equal(unchanged?.revision, snapshot.run!.revision);
  await assert.rejects(runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'interleaved-saga:block-review',
  )), /未完成的助手修改/);
  await assert.rejects(runtime.execute({
    type: 'APPLY_CURRENT_BLOCK_CHANGE', commandId: 'interleaved-saga:block-manual-apply',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    change: { blockId: block.blockId, label: '不能越过pending的人工修改' },
  } as GuanyijiaWorkbenchCommand), /未完成的助手修改/);
  await assert.rejects(runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'interleaved-saga:block-new-proposal',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为:不应覆盖pending的建议',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand), /未完成的助手修改/);

  const interleaved = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'interleaved-saga:ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '当前来源是什么',
    selection: { sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId },
  } as GuanyijiaWorkbenchCommand);
  assert.equal(interleaved.run!.revision, snapshot.run!.revision + 1);

  const recovered = await runtime.execute(confirm);
  assert.equal(recovered.current!.document!.revision, 2);
  assert.equal(recovered.run!.timeline.filter((event) => event.type === 'ASSISTANT_PATCH_CONFIRMED').length, 1);
  assert.deepEqual((await base.sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2]);
});

test('ASK在Run提交后指针完成失败仍可用相同commandId幂等恢复', async () => {
  const pointerStorage = new AssistantPointerFailureStorage();
  let clock = 0;
  const { runtime, runs } = fixture(
    undefined,
    pointerStorage,
    () => `2026-08-18T18:00:${String(clock++).padStart(2, '0')}.000Z`,
  );
  let snapshot = await runtime.execute(command('START_RUN', 0, 'ask-saga:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'ask-saga:read'));
  const ask = {
    type: 'ASK_REVIEW_ASSISTANT' as const, commandId: 'ask-saga:ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '当前来源是什么',
    selection: { sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId },
  };
  pointerStorage.failCompletionFor = ask.commandId;
  await assert.rejects(runtime.execute(ask), /模拟助手确认指针完成失败/);
  const committed = await runs.read(snapshot.run!.runId);
  assert.equal(committed?.timeline.filter((event) => event.type === 'ASSISTANT_TURN_RECORDED').length, 1);
  const recovered = await runtime.execute(ask);
  assert.equal(recovered.run?.revision, committed?.revision);
  assert.equal(recovered.run?.timeline.filter((event) => event.type === 'ASSISTANT_TURN_RECORDED').length, 1);
});

test('ASK的Run CAS失败且别的命令胜出后不能把旧expectedRevision偷渡到新事实', async () => {
  const base = fixture();
  const competingRuntime = base.createRuntime();
  let injectWinner = true;
  let originalExpectedRevision = 0;
  const flakyRuns: typeof base.runs = {
    ...base.runs,
    async execute(runCommand) {
      if (injectWinner && runCommand.type === 'RECORD_ASSISTANT_TURN'
        && runCommand.commandId.includes('ask-cas:original')) {
        injectWinner = false;
        await competingRuntime.execute({
          type: 'ASK_REVIEW_ASSISTANT', commandId: 'ask-cas:winner',
          expectedRevision: originalExpectedRevision, actorUserId: 'user_bo_gao',
          message: '当前来源读取范围是什么',
          selection: { sourceId: 'guanyijia_mysql' },
        } as GuanyijiaWorkbenchCommand);
        throw new Error('模拟旧ASK的Run CAS失败');
      }
      return base.runs.execute(runCommand);
    },
  };
  const runtime = base.createRuntime(flakyRuns);
  let snapshot = await runtime.execute(command('START_RUN', 0, 'ask-cas:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'ask-cas:read'));
  originalExpectedRevision = snapshot.run!.revision;
  const original = {
    type: 'ASK_REVIEW_ASSISTANT' as const, commandId: 'ask-cas:original',
    expectedRevision: originalExpectedRevision, actorUserId: 'user_bo_gao',
    message: '当前来源是什么', selection: { sourceId: 'guanyijia_mysql' },
  };
  await assert.rejects(runtime.execute(original), /模拟旧ASK的Run CAS失败/);
  await assert.rejects(runtime.execute(original), /revision已变化/);
  const persisted = await base.runs.read(snapshot.run!.runId);
  assert.equal(persisted?.timeline.filter((event) => event.type === 'ASSISTANT_TURN_RECORDED').length, 1);
});

test('Assistant Turn不能重排到其事实来源文档生成之前', async () => {
  const { runtime, runs, metadataStorage } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'turn-order:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'turn-order:read'));
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'turn-order:ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '当前来源是什么', selection: { sourceId: 'guanyijia_mysql' },
  } as GuanyijiaWorkbenchCommand);
  const key = 'linguan:standardization-runs:v1';
  const state = JSON.parse(metadataStorage.getItem(key)!) as {
    runs: Array<{ runId: string; timeline: Array<{ type: string; sequence: number; eventId: string }> }>;
  };
  const run = state.runs.find((candidate) => candidate.runId === snapshot.run!.runId)!;
  const turnIndex = run.timeline.findIndex((event) => event.type === 'ASSISTANT_TURN_RECORDED');
  const [turn] = run.timeline.splice(turnIndex, 1);
  const sourceStart = run.timeline.findIndex((event) => event.type === 'SOURCE_READ_STARTED');
  run.timeline.splice(sourceStart, 0, turn!);
  run.timeline.forEach((event, index) => {
    event.sequence = index + 1;
    event.eventId = `${run.runId}:event:${index + 1}`;
  });
  metadataStorage.setItem(key, JSON.stringify(state));
  await assert.rejects(runs.read(run.runId), /Turn.*文档生成|事实投影.*事件时点/);
});

test('历史冲突回答在后续revision移除该冲突后仍按Turn时点事实可读', async () => {
  const { runtime } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'historical-conflict:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'historical-conflict:mysql'));
  snapshot = await runtime.execute(command(
    'COMPLETE_CURRENT_DOCUMENT_REVIEW', snapshot.run!.revision, 'historical-conflict:review-mysql',
  ));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'historical-conflict:github'));
  const debtBlock = snapshot.current!.compilation!.blocks.find((block) => (
    block.stableCode === 'schema.jsh_depot_head.debt_fields'
  ));
  assert.ok(debtBlock && typeof debtBlock.value === 'object' && debtBlock.value !== null);
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'historical-conflict:ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '当前差异是什么', selection: {
      sourceId: 'guanyijia_github', documentId: snapshot.current!.document!.documentId,
      conflictId: 'gyj-conflict-debt-schema',
    },
  } as GuanyijiaWorkbenchCommand);
  snapshot = await runtime.execute({
    type: 'APPLY_CURRENT_BLOCK_CHANGE', commandId: 'historical-conflict:remove-in-r2',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    change: {
      blockId: debtBlock.blockId,
      value: { ...debtBlock.value as Record<string, unknown>, normalized: 'SOURCE_SCHEMA_DEBT_REMOVED' },
    },
  } as GuanyijiaWorkbenchCommand);
  assert.deepEqual(snapshot.run!.sources[1]?.introducedConflictIds, []);
  const history = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  });
  assert.equal(history.items.at(-1)?.response.kind, 'CONFLICT_EXPLANATION');
});

test('助手历史首页只展开最近20条且不读取首页之外的完整Proposal', async () => {
  const { runtime, runs, contentStore } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'history-page:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'history-page:read'));
  const block = snapshot.current!.compilation!.blocks[0]!;
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'history-page:old-proposal',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为:首页之外的大Proposal',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);
  const oldProposalTurn = JSON.parse(await runs.readEventPayload(
    snapshot.run!.runId, snapshot.run!.timeline.at(-1)!.eventId,
  )) as { response: { proposalRef: `sha256:${string}` } };
  const oldProposal = JSON.parse((await contentStore.get(oldProposalTurn.response.proposalRef))!) as {
    proposalId: string;
    previewSha256: `sha256:${string}`;
  };
  snapshot = await runtime.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'history-page:confirm-old-proposal',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: oldProposal.proposalId, expectedPreviewSha256: oldProposal.previewSha256,
  } as GuanyijiaWorkbenchCommand);
  const oldConfirmationRef = snapshot.run!.timeline.at(-2)!.payloadRef;
  for (let index = 1; index <= 21; index += 1) {
    snapshot = await runtime.execute({
      type: 'ASK_REVIEW_ASSISTANT', commandId: `history-page:ask:${index}`,
      expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
      message: `当前来源读取范围是什么 ${index}`,
      selection: {
        sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      },
    } as GuanyijiaWorkbenchCommand);
  }
  const turnRefs = new Set(snapshot.run!.timeline.filter((event) => (
    event.type === 'ASSISTANT_TURN_RECORDED'
  )).map((event) => event.payloadRef));
  const originalGet = contentStore.get.bind(contentStore);
  const reads: string[] = [];
  contentStore.get = async (ref) => {
    reads.push(ref);
    return originalGet(ref);
  };
  const first = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  });
  assert.equal(first.items.length, 20);
  assert.ok(first.nextCursor);
  assert.equal(first.items[0]?.message.endsWith('2'), true);
  assert.equal(first.items.at(-1)?.message.endsWith('21'), true);
  assert.ok(reads.filter((ref) => turnRefs.has(ref as `sha256:${string}`)).length <= 20);
  assert.equal(reads.includes(oldProposalTurn.response.proposalRef), false);
  assert.equal(reads.includes(oldConfirmationRef), false);
  const second = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId, cursor: first.nextCursor!,
  });
  assert.equal(second.items.length, 2);
  assert.equal(second.items.at(-1)?.message.endsWith('1'), true);
  assert.equal(second.nextCursor, null);
  assert.equal(reads.includes(oldConfirmationRef), true);
});

test('当前PENDING Proposal即使落在最近20条之外也单独返回以保持唯一Primary', async () => {
  const { runtime } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'sticky-pending:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'sticky-pending:read'));
  const block = snapshot.current!.compilation!.blocks[0]!;
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'sticky-pending:proposal',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为:始终可确认的建议',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);
  for (let index = 1; index <= 21; index += 1) {
    snapshot = await runtime.execute({
      type: 'ASK_REVIEW_ASSISTANT', commandId: `sticky-pending:ask:${index}`,
      expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
      message: `当前来源是什么 ${index}`,
      selection: { sourceId: 'guanyijia_mysql' },
    } as GuanyijiaWorkbenchCommand);
  }
  const page = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  });
  assert.equal(page.items.length, 20);
  assert.equal(page.items.some((item) => item.response.kind === 'PATCH_PREVIEW'), false);
  assert.equal(page.pendingProposal?.response.kind, 'PATCH_PREVIEW');
  assert.equal(page.pendingProposal?.proposalStatus, 'PENDING');
});

test('同文档新Proposal取代旧Proposal且手工revision后的过期确认被拒绝', async () => {
  const { runtime, sourceDocuments } = fixture();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'stale-proposal:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'stale-proposal:read'));
  const block = snapshot.current!.compilation!.blocks[0]!;
  for (const [index, label] of ['第一份建议', '第二份建议'].entries()) {
    snapshot = await runtime.execute({
      type: 'ASK_REVIEW_ASSISTANT', commandId: `stale-proposal:ask:${index}`,
      expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
      message: `建议把当前项名称改为:${label}`,
      selection: {
        sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
        section: block.section, blockId: block.blockId,
      },
    } as GuanyijiaWorkbenchCommand);
  }
  const proposals = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  });
  assert.deepEqual(proposals.items.map((item) => item.proposalStatus), ['SUPERSEDED', 'PENDING']);
  const latest = proposals.items.at(-1)!.response;
  if (latest.kind !== 'PATCH_PREVIEW') assert.fail('应有最新Proposal');

  snapshot = await runtime.execute({
    type: 'APPLY_CURRENT_BLOCK_CHANGE', commandId: 'stale-proposal:manual-revise',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    change: { blockId: block.blockId, label: '人工已修订' },
  } as GuanyijiaWorkbenchCommand);
  await assert.rejects(runtime.execute({
    type: 'CONFIRM_ASSISTANT_PATCH', commandId: 'stale-proposal:confirm-old',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: latest.proposal.proposalId,
    expectedPreviewSha256: latest.proposal.previewSha256,
  } as GuanyijiaWorkbenchCommand), /revision已变化/);
  const history = await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  });
  assert.equal(history.items.at(-1)?.proposalStatus, 'SUPERSEDED');
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2]);
});

test('两个Workbench并发确认同一Proposal只有一个成功且只生成一份r2', async () => {
  const { runtime, createRuntime, runs, sourceDocuments } = fixture();
  const secondRuntime = createRuntime();
  let snapshot = await runtime.execute(command('START_RUN', 0, 'concurrent-confirm:start'));
  snapshot = await runtime.execute(command('READ_NEXT_SOURCE', snapshot.run!.revision, 'concurrent-confirm:read'));
  const block = snapshot.current!.compilation!.blocks[0]!;
  snapshot = await runtime.execute({
    type: 'ASK_REVIEW_ASSISTANT', commandId: 'concurrent-confirm:ask',
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    message: '建议把当前项名称改为:并发确认结果',
    selection: {
      sourceId: 'guanyijia_mysql', documentId: snapshot.current!.document!.documentId,
      section: block.section, blockId: block.blockId,
    },
  } as GuanyijiaWorkbenchCommand);
  const response = (await runtime.readAssistantHistory({
    actorUserId: 'user_bo_gao', runId: snapshot.run!.runId,
  })).items.at(-1)!.response;
  if (response.kind !== 'PATCH_PREVIEW') assert.fail('应生成Proposal');
  const base = {
    type: 'CONFIRM_ASSISTANT_PATCH' as const,
    expectedRevision: snapshot.run!.revision, actorUserId: 'user_bo_gao',
    proposalId: response.proposal.proposalId,
    expectedPreviewSha256: response.proposal.previewSha256,
  };
  const outcomes = await Promise.allSettled([
    runtime.execute({ ...base, commandId: 'concurrent-confirm:first' }),
    secondRuntime.execute({ ...base, commandId: 'concurrent-confirm:second' }),
  ]);
  assert.deepEqual(outcomes.map((result) => result.status).sort(), ['fulfilled', 'rejected']);
  const persisted = await runs.read(snapshot.run!.runId);
  assert.equal(persisted?.timeline.filter((event) => event.type === 'ASSISTANT_PATCH_CONFIRMED').length, 1);
  assert.deepEqual((await sourceDocuments.list('guanyijia_erp')).map((document) => document.revision), [1, 2]);
});
