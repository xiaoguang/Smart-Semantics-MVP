import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import test from 'node:test';

// Keep this import dynamic: the RED phase must fail because the public module
// does not exist yet. The generated asset is imported only after that seam.
const proposalModule = await import('./candidate-target-proposal.ts');
const generatedProposalModule = await import('./guanyijia-candidate-target.generated.ts');

const { readCandidateTargetProposal } = proposalModule;

type MutableTarget = {
  targetId: string;
  topic: string;
  title: string;
  statement: string;
  status: string;
  evidenceClass: string;
  citationRefs: string[];
  affectedClaimIds: string[];
};

type MutableProposal = {
  schemaVersion: number;
  kind: string;
  markdown: string;
  targets: MutableTarget[];
  generation: {
    provider: string;
    sessionReference: string;
    model: string;
    reasoningEffort: string;
    promptVersion: string;
    inputDigest: string;
    outputDigest: string;
  };
};

const generatedProposal = (
  generatedProposalModule as unknown as {
    generatedCandidateTargetProposal: MutableProposal;
  }
).generatedCandidateTargetProposal;

function targetFor(proposal: ReturnType<typeof readCandidateTargetProposal>, topic: string) {
  const target = proposal.targets.find((candidate) => candidate.topic === topic);
  assert.ok(target, `expected a target for ${topic}`);
  return target;
}

function mutableTargetFor(topic: string): MutableTarget {
  const target = generatedProposal.targets.find((candidate) => candidate.topic === topic);
  assert.ok(target, `expected a mutable target for ${topic}`);
  return target;
}

function digest(text: string): string {
  return `sha256:${createHash('sha256').update(text, 'utf8').digest('hex')}`;
}

function recomputeOutputDigest(proposal: MutableProposal): string {
  return digest(JSON.stringify({
    domain: 'guanyijia-candidate-target-proposal/output/v1',
    value: {
      schemaVersion: proposal.schemaVersion,
      kind: proposal.kind,
      markdown: proposal.markdown,
      targets: proposal.targets.map((target) => ({
        targetId: target.targetId,
        topic: target.topic,
        title: target.title,
        statement: target.statement,
        status: target.status,
        evidenceClass: target.evidenceClass,
        citationRefs: [...target.citationRefs],
        affectedClaimIds: [...target.affectedClaimIds],
      })),
      generation: {
        provider: proposal.generation.provider,
        sessionReference: proposal.generation.sessionReference,
        model: proposal.generation.model,
        reasoningEffort: proposal.generation.reasoningEffort,
        promptVersion: proposal.generation.promptVersion,
        inputDigest: proposal.generation.inputDigest,
      },
    },
  }));
}

test('returns exactly the negative-stock and document-status target candidates with public citations', () => {
  const proposal = readCandidateTargetProposal();

  assert.equal(proposal.schemaVersion, 1);
  assert.equal(proposal.kind, 'GUANYIJIA_GENERATED_TARGET_CANDIDATE');
  assert.deepEqual(
    proposal.targets.map((target) => target.topic),
    ['NEGATIVE_STOCK', 'DOCUMENT_STATUS'],
  );
  assert.equal(new Set(proposal.targets.map((target) => target.targetId)).size, 2);

  const negativeStock = targetFor(proposal, 'NEGATIVE_STOCK');
  assert.deepEqual([...negativeStock.citationRefs].sort(), [
    'github:jsh_system_config:minus_stock_flag',
    'mysql:jsh_system_config:minus_stock_flag',
  ].sort());
  assert.deepEqual([...negativeStock.affectedClaimIds].sort(), [
    'github-negative-stock-control',
    'mysql-negative-stock-control',
  ].sort());

  const documentStatus = targetFor(proposal, 'DOCUMENT_STATUS');
  assert.deepEqual([...documentStatus.citationRefs].sort(), [
    'gap:official:document_status_9',
    'github:migration:jsh_depot_head:historical_status',
    'mysql:jsh_depot_head:document_status',
  ].sort());
  assert.deepEqual([...documentStatus.affectedClaimIds].sort(), [
    'github-document-status-historical',
    'mysql-document-status-current',
    'official-document-status-9-gap',
  ].sort());
});

test('marks every target as generated and pending human confirmation, with an honest generation manifest', () => {
  const proposal = readCandidateTargetProposal();

  assert.ok(proposal.targets.every((target) => target.status === 'PENDING_HUMAN_CONFIRMATION'));
  assert.ok(proposal.targets.every((target) => target.evidenceClass === 'GENERATED_TARGET'));
  assert.ok(proposal.targets.every((target) => target.title.trim() && target.statement.trim()));

  assert.equal(proposal.generation.provider, 'CODEX_CHATGPT_SESSION');
  assert.equal(proposal.generation.model, 'gpt-5.6-luna');
  assert.equal(proposal.generation.reasoningEffort, 'xhigh');
  assert.equal(proposal.generation.promptVersion, 'guanyijia-candidate-target-v1');
  assert.equal(proposal.generation.sessionReference, 'codex-task:/root/real_evidence_task2_generation');
  assert.doesNotMatch(proposal.generation.sessionReference, /^(?:sess|run|chatcmpl)[-_]/i);
  assert.match(proposal.generation.inputDigest, /^sha256:[a-f0-9]{64}$/);
  assert.match(proposal.generation.outputDigest, /^sha256:[a-f0-9]{64}$/);
});

test('keeps target Markdown and labels truthful for proposed policy, status gap, and frozen sources', () => {
  const proposal = readCandidateTargetProposal();
  const display = [proposal.markdown, ...proposal.targets.flatMap((target) => [target.title, target.statement])].join('\n');

  assert.match(display, /候选目标制度/);
  assert.match(display, /待人工确认/);
  assert.match(display, /负库存/);
  assert.match(display, /目标(?:政策|制度)/);
  assert.match(display, /(?:不是|非|尚未落地|不代表).*(?:当前|现行).*(?:规则|制度|政策)|(?:当前|现行).*(?:规则|制度|政策).*(?:不是|尚未落地|不代表)/s);
  assert.match(display, /状态\s*9/);
  assert.match(display, /(?:未知|缺口|GAP)/i);
  assert.match(display, /(?:债务|欠款|debt)/i);
  assert.match(display, /结构.*(?:决策|分歧)|(?:决策|分歧).*结构/);
  assert.match(display, /MySQL 已保存快照/);
  assert.match(display, /GitHub 冻结结构化记录（未保留完整原文）/);

  for (const location of [
    'ddl/tables/jsh_system_config.sql:L12',
    'schema/tables/jsh_system_config.sql:L12',
    'ddl/tables/jsh_depot_head.sql:L24-L27',
    'migration-history/数据库更新记录:L1842-L1843',
    'ddl/tables/jsh_depot_head.sql:L27',
    'migration-history/数据库更新记录:L312',
    '完整官方文本未保留',
  ]) {
    assert.ok(display.includes(location), `missing readable citation location: ${location}`);
  }

  assert.doesNotMatch(
    display,
    /sha256:|Content Ref|FROZEN_FILE|\bREAL\b|\bPRIMARY\b|Tenant\s*153|sample|profile|\/Users\//i,
  );
});

test('returns a deep clone so proposal and nested generation data cannot be mutated through a read', () => {
  const first = readCandidateTargetProposal();
  const second = readCandidateTargetProposal();
  const expectedMarkdown = second.markdown;
  const expectedCitation = second.targets[0]!.citationRefs[0];
  const expectedDigest = second.generation.outputDigest;

  assert.notStrictEqual(first, second);
  assert.notStrictEqual(first.targets, second.targets);
  assert.notStrictEqual(first.targets[0], second.targets[0]);
  assert.notStrictEqual(first.generation, second.generation);

  (first as unknown as { markdown: string }).markdown = 'mutated test copy';
  (first.targets[0]!.citationRefs as string[])[0] = 'evidence:mutated';
  (first.generation as unknown as { outputDigest: string }).outputDigest = `sha256:${'0'.repeat(64)}`;

  const reread = readCandidateTargetProposal();
  assert.equal(reread.markdown, expectedMarkdown);
  assert.equal(reread.targets[0]!.citationRefs[0], expectedCitation);
  assert.equal(reread.generation.outputDigest, expectedDigest);
});

test('rejects a static proposal whose output digest is corrupted', () => {
  const original = generatedProposal.generation.outputDigest;
  try {
    generatedProposal.generation.outputDigest = `sha256:${original === `sha256:${'f'.repeat(64)}` ? 'e' : 'f'.repeat(64)}`;
    assert.throws(() => readCandidateTargetProposal());
  } finally {
    generatedProposal.generation.outputDigest = original;
  }
});

test('rejects a static proposal with an unknown citation', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK');
  const original = target.citationRefs;
  try {
    target.citationRefs = [...original, 'candidate:evidence-does-not-exist'];
    assert.throws(() => readCandidateTargetProposal(), /引用了未知证据/);
  } finally {
    target.citationRefs = original;
  }
});

test('rejects a document-status target that cites only the official GAP', () => {
  const target = mutableTargetFor('DOCUMENT_STATUS');
  const original = target.citationRefs;
  try {
    target.citationRefs = ['gap:official:document_status_9'];
    assert.throws(() => readCandidateTargetProposal(), /不能只引用 GAP/);
  } finally {
    target.citationRefs = original;
  }
});

test('rejects a static proposal with an unknown affected claim reference', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK');
  const original = target.affectedClaimIds;
  try {
    target.affectedClaimIds = [...original, 'claim:does-not-exist'];
    assert.throws(() => readCandidateTargetProposal());
  } finally {
    target.affectedClaimIds = original;
  }
});

test('rejects a static proposal with an existing affected claim from the wrong topic', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK');
  const original = target.affectedClaimIds;
  try {
    target.affectedClaimIds = ['mysql-document-status-current'];
    assert.throws(() => readCandidateTargetProposal(), /主张主题不一致/);
  } finally {
    target.affectedClaimIds = original;
  }
});

test('rejects a static proposal with a non-generated evidence class while status remains pending', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK');
  const originalEvidenceClass = target.evidenceClass;
  const originalStatus = target.status;
  try {
    target.evidenceClass = 'OBSERVED';
    assert.equal(target.status, 'PENDING_HUMAN_CONFIRMATION');
    assert.throws(() => readCandidateTargetProposal(), /evidenceClass.*GENERATED_TARGET/);
  } finally {
    target.evidenceClass = originalEvidenceClass;
    target.status = originalStatus;
  }
});

test('rejects a static proposal with a non-generated status or wrong topic', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK');
  const originalStatus = target.status;
  const originalTopic = target.topic;
  try {
    target.status = 'OBSERVED';
    assert.throws(() => readCandidateTargetProposal());

    target.status = originalStatus;
    target.topic = 'DEBT_FIELDS';
    assert.throws(() => readCandidateTargetProposal());
  } finally {
    target.status = originalStatus;
    target.topic = originalTopic;
  }
});

test('rejects a static proposal whose Markdown loses a required location declaration', () => {
  const original = generatedProposal.markdown;
  const originalOutputDigest = generatedProposal.generation.outputDigest;
  const requiredLocation = 'ddl/tables/jsh_depot_head.sql:L27';
  assert.ok(original.includes(requiredLocation));
  try {
    generatedProposal.markdown = original.replace(requiredLocation, 'ddl/tables/jsh_depot_head.sql:line-removed');
    generatedProposal.generation.outputDigest = recomputeOutputDigest(generatedProposal);
    assert.throws(() => readCandidateTargetProposal(), /Markdown 缺少公开证据声明.*mysql:jsh_depot_head:document_status/);
  } finally {
    generatedProposal.markdown = original;
    generatedProposal.generation.outputDigest = originalOutputDigest;
  }
});

test('rejects static Markdown corruption that removes target and citation declarations', () => {
  const original = generatedProposal.markdown;
  try {
    generatedProposal.markdown = '这不是候选目标提案。';
    assert.throws(() => readCandidateTargetProposal());
  } finally {
    generatedProposal.markdown = original;
  }
});

test('rejects a static proposal whose input digest is corrupted', () => {
  const original = generatedProposal.generation.inputDigest;
  try {
    generatedProposal.generation.inputDigest = `sha256:${'0'.repeat(64)}`;
    assert.throws(() => readCandidateTargetProposal(), /inputDigest 不匹配/);
  } finally {
    generatedProposal.generation.inputDigest = original;
  }
});

test('rejects paired public-content and recomputed-output-digest mutation at the trusted asset pin', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK');
  const originalStatement = target.statement;
  const originalOutputDigest = generatedProposal.generation.outputDigest;
  try {
    target.statement = `${originalStatement} 这段内容不属于已冻结提案。`;
    generatedProposal.generation.outputDigest = recomputeOutputDigest(generatedProposal);
    assert.throws(
      () => readCandidateTargetProposal(),
      /受信任运行时完整性锚点不一致/,
    );
  } finally {
    target.statement = originalStatement;
    generatedProposal.generation.outputDigest = originalOutputDigest;
  }
});

test('rejects a static proposal with duplicate citation entries', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK');
  const original = target.citationRefs;
  try {
    target.citationRefs = [original[0]!, original[0]!];
    assert.throws(() => readCandidateTargetProposal(), /citationRefs 不能重复/);
  } finally {
    target.citationRefs = original;
  }
});

test('rejects a static target with an unknown field at the exact target shape guard', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK') as unknown as Record<string, unknown>;
  const unknownField = '__unexpected_target_field__';
  try {
    target[unknownField] = 'malformed';
    assert.throws(() => readCandidateTargetProposal(), /targets\[0\] 字段形状不匹配/);
  } finally {
    delete target[unknownField];
  }
});

test('rejects a static generation manifest with an unknown field at the exact generation shape guard', () => {
  const generation = generatedProposal.generation as unknown as Record<string, unknown>;
  const unknownField = '__unexpected_generation_field__';
  try {
    generation[unknownField] = 'malformed';
    assert.throws(() => readCandidateTargetProposal(), /generation 字段形状不匹配/);
  } finally {
    delete generation[unknownField];
  }
});

test('rejects a static proposal with an unknown top-level field at the exact shape guard', () => {
  const proposal = generatedProposal as unknown as Record<string, unknown>;
  const unknownField = '__unexpected_top_level_field__';
  try {
    proposal[unknownField] = 'malformed';
    assert.throws(() => readCandidateTargetProposal(), /generated proposal 字段形状不匹配/);
  } finally {
    delete proposal[unknownField];
  }
});

test('rejects duplicate target IDs while the proposal topics and target data remain valid', () => {
  const negativeStock = mutableTargetFor('NEGATIVE_STOCK');
  const documentStatus = mutableTargetFor('DOCUMENT_STATUS');
  const originalTargetId = documentStatus.targetId;
  try {
    documentStatus.targetId = negativeStock.targetId;
    assert.throws(() => readCandidateTargetProposal(), /targets targetId 不能重复/);
  } finally {
    documentStatus.targetId = originalTargetId;
  }
});

test('rejects duplicate affected claim IDs while target citations remain valid', () => {
  const target = mutableTargetFor('NEGATIVE_STOCK');
  const original = target.affectedClaimIds;
  try {
    target.affectedClaimIds = [original[0]!, original[0]!];
    assert.throws(() => readCandidateTargetProposal(), /affectedClaimIds 不能重复/);
  } finally {
    target.affectedClaimIds = original;
  }
});
