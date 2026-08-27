import assert from 'node:assert/strict';
import test from 'node:test';

// The public projection seam is intentionally absent during RED.
const projectionModule = await import('./candidate-review-projection.ts');
const evidenceModule = await import('./guanyijia-candidate-evidence.generated.ts');
const targetModule = await import('./guanyijia-candidate-target.generated.ts');

type Projection = {
  topic: string;
  heading?: string;
  relation: string;
  relationLabel?: string;
  relationExplanation?: string;
  reviewGuidance?: string;
  evidence: Array<{ evidenceRef: string; evidenceClass: string }>;
  target?: {
    evidenceClass?: string;
    statusLabel?: string;
    markdown?: string;
    citationRefs?: string[];
  };
};

const candidateReviewForSource = projectionModule.candidateReviewForSource as (
  sourceId: string,
) => Projection[];
const candidateReviewForConflict = projectionModule.candidateReviewForConflict as (
  conflictId: string,
) => Projection | undefined;
const candidateReviewTopicForBlock = projectionModule.candidateReviewTopicForBlock as (
  blockId: string,
) => Projection | undefined;
const candidateReviewForSourceMapSelection = projectionModule.candidateReviewForSourceMapSelection as (
  input: { selected?: Projection; mappedBlock?: Projection },
) => Projection | undefined;

const generatedEvidence = (evidenceModule as unknown as {
  generatedCandidateEvidence: { fragments: Array<{ evidenceRef: string; excerpt: string }> };
}).generatedCandidateEvidence;
const generatedTarget = (targetModule as unknown as {
  generatedCandidateTargetProposal: { markdown: string };
}).generatedCandidateTargetProposal;

function topicFor(sourceId: string, topic: string) {
  return candidateReviewForSource(sourceId).find((projection) => projection.topic === topic);
}

test('projects the three sources with truthful evidence classes', () => {
  const mysqlNegativeStock = topicFor('guanyijia_mysql', 'NEGATIVE_STOCK');
  const mysqlStatus = topicFor('guanyijia_mysql', 'DOCUMENT_STATUS');
  assert.ok(mysqlNegativeStock?.evidence.some((evidence) => evidence.evidenceClass === 'OBSERVED'));
  assert.ok(mysqlStatus?.evidence.some((evidence) => evidence.evidenceRef.includes('document_status')));

  const github = candidateReviewForSource('guanyijia_github');
  assert.ok(github.some((projection) =>
    projection.evidence.some((evidence) => evidence.evidenceClass === 'FROZEN_RECORD')),
  );

  const officialStatus = topicFor('guanyijia_official_document', 'DOCUMENT_STATUS');
  assert.ok(officialStatus?.evidence.some((evidence) => evidence.evidenceClass === 'GAP'));

  const target = topicFor('guanyijia_demo_policy', 'NEGATIVE_STOCK')?.target;
  assert.equal(target?.evidenceClass, 'GENERATED_TARGET');
  assert.equal(target?.statusLabel, '待人工确认');
  assert.match(target?.markdown ?? '', /待人工确认/);
});

test('enforces the exact admissible fragment contract for every source', () => {
  const expected: Record<string, Record<string, Array<[string, string]>>> = {
    guanyijia_mysql: {
      NEGATIVE_STOCK: [['mysql:jsh_system_config:minus_stock_flag', 'OBSERVED']],
      DEBT_FIELDS: [['mysql:jsh_depot_head:debt_fields_absent', 'OBSERVED']],
      DOCUMENT_STATUS: [['mysql:jsh_depot_head:document_status', 'OBSERVED']],
    },
    guanyijia_github: {
      NEGATIVE_STOCK: [['github:jsh_system_config:minus_stock_flag', 'FROZEN_RECORD']],
      DEBT_FIELDS: [['github:migration:jsh_depot_head:debt_fields', 'FROZEN_RECORD']],
      DOCUMENT_STATUS: [['github:migration:jsh_depot_head:historical_status', 'FROZEN_RECORD']],
    },
    guanyijia_official_document: {
      DOCUMENT_STATUS: [['gap:official:document_status_9', 'GAP']],
    },
  };

  for (const [sourceId, topics] of Object.entries(expected)) {
    const projections = candidateReviewForSource(sourceId);
    assert.deepEqual(
      projections.map((projection) => projection.topic),
      Object.keys(topics),
      `${sourceId} must expose only its admitted topics`,
    );
    for (const [topic, fragments] of Object.entries(topics)) {
      const projection = projections.find((candidate) => candidate.topic === topic);
      assert.ok(projection, `${sourceId}/${topic} projection must exist`);
      assert.deepEqual(
        projection!.evidence.map(({ evidenceRef, evidenceClass }) => [evidenceRef, evidenceClass]),
        fragments,
        `${sourceId}/${topic} must expose only admissible fragments/classes`,
      );
      assert.ok(projection!.evidence.every((evidence) => (
        evidence.evidenceClass !== 'GENERATED_TARGET' && evidence.evidenceClass !== 'DERIVED'
      )));
    }
  }

  // The compatibility spelling is still a narrow official GAP projection.
  assert.deepEqual(
    candidateReviewForSource('guanyijia_official_docs').flatMap((projection) => (
      projection.evidence.map(({ evidenceRef, evidenceClass }) => [evidenceRef, evidenceClass])
    )),
    [['gap:official:document_status_9', 'GAP']],
  );
});

test('demo policy exposes only proposal citations and pending target state', () => {
  const expected: Record<string, string[]> = {
    NEGATIVE_STOCK: [
      'mysql:jsh_system_config:minus_stock_flag',
      'github:jsh_system_config:minus_stock_flag',
    ],
    DOCUMENT_STATUS: [
      'mysql:jsh_depot_head:document_status',
      'github:migration:jsh_depot_head:historical_status',
      'gap:official:document_status_9',
    ],
  };
  for (const [topic, citationRefs] of Object.entries(expected)) {
    const projection = topicFor('guanyijia_demo_policy', topic);
    assert.ok(projection);
    assert.deepEqual(projection!.evidence.map((evidence) => evidence.evidenceRef), citationRefs);
    assert.deepEqual(projection!.target?.citationRefs, citationRefs);
    assert.equal(projection!.target?.evidenceClass, 'GENERATED_TARGET');
    assert.equal(projection!.target?.statusLabel, '待人工确认');
    assert.match(projection!.target?.markdown ?? '', /候选目标制度/);
  }
  assert.equal(topicFor('guanyijia_demo_policy', 'DEBT_FIELDS'), undefined);
  assert.equal(candidateReviewForConflict('gyj-conflict-debt-schema')?.target, undefined);
});

test('keeps the exact relation labels and explanations for all three topics', () => {
  const expected = {
    NEGATIVE_STOCK: {
      relation: 'COMPLEMENTS', relationLabel: '互补资料',
      explanation: '已部署 DDL 与冻结结构化记录都涉及 minus_stock_flag；后者不是第二份独立观察，只补充待审阅的来源上下文。',
    },
    DEBT_FIELDS: {
      relation: 'CONFLICTS', relationLabel: '结构差异',
      explanation: '已部署 DDL 的字段边界未见 debt 或 last_debt，而冻结迁移记录声明了这两个字段。',
    },
    DOCUMENT_STATUS: {
      relation: 'TEMPORAL_DRIFT', relationLabel: '不同版本记录不一致',
      explanation: '已部署快照记录 0/1/2/3/9；冻结历史迁移记录只描述 0/1/2，两个保留快照时刻不同。',
    },
  } as const;
  for (const [topic, contract] of Object.entries(expected)) {
    const projection = candidateReviewForConflict(
      topic === 'NEGATIVE_STOCK'
        ? 'gyj-conflict-negative-stock'
        : topic === 'DEBT_FIELDS' ? 'gyj-conflict-debt-schema' : 'gyj-conflict-status-nine',
    );
    assert.equal(projection?.topic, topic);
    assert.equal(projection?.relation, contract.relation);
    assert.equal(projection?.relationLabel, contract.relationLabel);
    assert.equal(projection?.relationExplanation, contract.explanation);
  }
});

test('maps the three demo conflict IDs to deterministic relations', () => {
  assert.equal(candidateReviewForConflict('gyj-conflict-negative-stock')?.relation, 'COMPLEMENTS');
  assert.equal(candidateReviewForConflict('gyj-conflict-debt-schema')?.relation, 'CONFLICTS');
  assert.equal(candidateReviewForConflict('gyj-conflict-status-nine')?.relation, 'TEMPORAL_DRIFT');
});

test('maps every known source, derived, and resolution block alias to its topic', () => {
  const aliases: Record<string, string[]> = {
    NEGATIVE_STOCK: [
      'rule.negative_stock',
      'gyj-block:rule.negative_stock',
      'derived.rule.negative_stock',
      'gyj-block:derived.rule.negative_stock',
      'resolution.gap.negative_stock_policy_not_implemented',
      'gyj-resolution-block:gyj-conflict-negative-stock:gap',
    ],
    DEBT_FIELDS: [
      'schema.jsh_depot_head.debt_fields',
      'gyj-block:schema.jsh_depot_head.debt_fields',
      'resolution.gap.debt_schema',
      'gyj-resolution-block:gyj-conflict-debt-schema:gap',
    ],
    DOCUMENT_STATUS: [
      'dimension.document_status.nine',
      'gyj-block:dimension.document_status.nine',
      'derived.dimension.document_status.nine',
      'gyj-block:derived.dimension.document_status.nine',
      'resolution.gap.status_nine_unconfirmed',
      'gyj-resolution-block:gyj-conflict-status-nine:gap',
    ],
  };
  for (const [topic, blockIds] of Object.entries(aliases)) {
    for (const blockId of blockIds) {
      assert.equal(
        candidateReviewTopicForBlock(blockId)?.topic,
        topic,
        `${blockId} must map to ${topic}`,
      );
    }
  }
});

test('selected candidate evidence wins over an older block mapping in Source Map', () => {
  const selected = topicFor('guanyijia_mysql', 'NEGATIVE_STOCK');
  const mappedBlock = candidateReviewTopicForBlock('schema.jsh_depot_head.debt_fields');
  assert.ok(selected);
  assert.ok(mappedBlock);
  assert.equal(
    candidateReviewForSourceMapSelection({ selected, mappedBlock })?.topic,
    'NEGATIVE_STOCK',
  );
  assert.equal(
    candidateReviewForSourceMapSelection({ mappedBlock })?.topic,
    'DEBT_FIELDS',
  );
});

test('returns empty or undefined for unknown projection keys', () => {
  assert.deepEqual(candidateReviewForSource('unknown-source'), []);
  assert.equal(candidateReviewForConflict('unknown-conflict'), undefined);
  assert.equal(candidateReviewTopicForBlock('unknown-block'), undefined);
});

test('returns independent projection clones', () => {
  const first = candidateReviewForConflict('gyj-conflict-negative-stock');
  const second = candidateReviewForConflict('gyj-conflict-negative-stock');
  assert.ok(first);
  assert.ok(second);
  assert.notStrictEqual(first, second);
  assert.notStrictEqual(first.evidence, second.evidence);
  first.evidence[0]!.evidenceRef = 'mutated';
  assert.notEqual(second.evidence[0]!.evidenceRef, 'mutated');
});

test('fails closed when frozen evidence or target assets are mutated', () => {
  const evidence = generatedEvidence.fragments.find((fragment) =>
    fragment.evidenceRef === 'mysql:jsh_system_config:minus_stock_flag');
  assert.ok(evidence);
  const originalExcerpt = evidence.excerpt;
  try {
    evidence.excerpt = `${originalExcerpt}\ntampered`;
    assert.throws(
      () => candidateReviewForConflict('gyj-conflict-negative-stock'),
      /候选证据快照不可用/,
    );
  } finally {
    evidence.excerpt = originalExcerpt;
  }

  const originalMarkdown = generatedTarget.markdown;
  try {
    generatedTarget.markdown = `${originalMarkdown}\ntampered`;
    assert.throws(
      () => candidateReviewForSource('guanyijia_demo_policy'),
      /候选目标提案不可用/,
    );
  } finally {
    generatedTarget.markdown = originalMarkdown;
  }
});
