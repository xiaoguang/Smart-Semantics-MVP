import assert from 'node:assert/strict';
import test from 'node:test';
import {
  bindDemoContentRun,
  normalizeDemoContentSourceReview,
  readBoundDemoContentSourceReview,
  readDemoContentMergedBaseline,
  readDemoContentSourceReview,
  validateDemoContentRunBinding,
} from './demo-content-review.ts';
import { pinnedDemoContentPublication } from './pinned-demo-content.generated.ts';
import { sha256HexSync } from '../ai-modeling/sha256.ts';

const formalSources = [
  { sourceId: 'guanyijia_mysql', snapshotId: '20260813T032528Z-abb0502c7d79' },
  { sourceId: 'guanyijia_github', snapshotId: '20260813032126Z-5821d0ece9b1' },
  { sourceId: 'guanyijia_official_docs', snapshotId: 'gyjerp-official-docs-20260813T031656Z' },
  { sourceId: 'guanyijia_demo_policy', snapshotId: 'guanyijia-demo-policy-f6c6d209ffe3fd53' },
  { sourceId: 'guanyijia_semantica_demo', snapshotId: 'guanyijia-semantica-demo-ff948845dc5bd778' },
] as const;

const persistedV5Binding = {
  runId: 'persisted-v5-run',
  storyKey: 'guanyijia-five-source-v1',
  contentSnapshotId: 'guanyijia-demo-content-v5-20260826',
  contentSha256: 'sha256:2663cc64b19b5aa149ae0dfcea2a1917e9295ea490e0a59b422558cbcd009a2c',
  sourceBindings: [
    { sourceId: 'guanyijia_mysql', formalSnapshotId: '20260813T032528Z-abb0502c7d79', contentSourceSnapshotId: '20260813T032528Z-abb0502c7d79', origin: 'SNAPSHOT_REFERENCE' },
    { sourceId: 'guanyijia_github', formalSnapshotId: '20260813032126Z-5821d0ece9b1', contentSourceSnapshotId: 'github-b3ab269b05070d40', origin: 'SOURCE_NATIVE' },
    { sourceId: 'guanyijia_official_docs', formalSnapshotId: 'gyjerp-official-docs-20260813T031656Z', contentSourceSnapshotId: 'guanyijia-demo-official-v1', origin: 'DEMO_AUTHORED' },
    { sourceId: 'guanyijia_demo_policy', formalSnapshotId: 'guanyijia-demo-policy-f6c6d209ffe3fd53', contentSourceSnapshotId: 'guanyijia-demo-policy-content-v1', origin: 'DEMO_AUTHORED' },
    { sourceId: 'guanyijia_semantica_demo', formalSnapshotId: 'guanyijia-semantica-demo-ff948845dc5bd778', contentSourceSnapshotId: 'guanyijia-demo-terminology-v1', origin: 'DERIVED_DEMO' },
  ],
} as const;

const persistedV6Binding = {
  runId: 'persisted-v6-run',
  storyKey: 'guanyijia-five-source-v1',
  contentSnapshotId: 'guanyijia-demo-content-v6-20260826',
  contentSha256: 'sha256:6da765901357f4b0856e3b1c5aaab159aec75c895b60b1bb2ea11539997f8180',
  sourceBindings: [
    { sourceId: 'guanyijia_mysql', formalSnapshotId: '20260813T032528Z-abb0502c7d79', contentSourceSnapshotId: '20260813T032528Z-abb0502c7d79', origin: 'SNAPSHOT_REFERENCE' },
    { sourceId: 'guanyijia_github', formalSnapshotId: '20260813032126Z-5821d0ece9b1', contentSourceSnapshotId: 'github-b3ab269b05070d40', origin: 'SOURCE_NATIVE' },
    { sourceId: 'guanyijia_official_docs', formalSnapshotId: 'gyjerp-official-docs-20260813T031656Z', contentSourceSnapshotId: 'guanyijia-demo-official-v1', origin: 'DEMO_AUTHORED' },
    { sourceId: 'guanyijia_demo_policy', formalSnapshotId: 'guanyijia-demo-policy-f6c6d209ffe3fd53', contentSourceSnapshotId: 'guanyijia-demo-policy-content-v1', origin: 'DEMO_AUTHORED' },
    { sourceId: 'guanyijia_semantica_demo', formalSnapshotId: 'guanyijia-semantica-demo-ff948845dc5bd778', contentSourceSnapshotId: 'guanyijia-demo-terminology-v1', origin: 'DERIVED_DEMO' },
  ],
} as const;

type MutablePublication = {
  contentSnapshotId: string;
  sources?: Array<{ review: MutableReview }>;
  mergedDocument?: MutableReview;
  legacyPublications?: MutablePublication[];
};

type MutableReview = {
  [key: string]: unknown;
  evidence: Array<{ evidenceRef: string; [key: string]: unknown }>;
  traceLinks: Array<{ [key: string]: unknown }>;
  standardSections?: Array<{ [key: string]: unknown; claimIds: string[] }>;
  claims?: Array<{ [key: string]: unknown }>;
  sections?: Record<string, string>;
  markdown: string;
  markdownSha256: string;
};

const mutablePublication = pinnedDemoContentPublication as unknown as MutablePublication;

function findV5Publication(): MutablePublication | undefined {
  if (mutablePublication.contentSnapshotId === 'guanyijia-demo-content-v5-20260826') return mutablePublication;
  const direct = mutablePublication.legacyPublications?.find(
    (publication) => publication.contentSnapshotId === 'guanyijia-demo-content-v5-20260826',
  );
  if (direct) return direct;
  return mutablePublication.legacyPublications?.find(
    (publication) => publication.contentSnapshotId === 'guanyijia-demo-content-v6-20260826',
  )?.legacyPublications?.find(
    (publication) => publication.contentSnapshotId === 'guanyijia-demo-content-v5-20260826',
  );
}

const strictSectionIds = [
  'OVERVIEW', 'GOAL', 'OBJECT', 'ACTIVITY', 'FIELD', 'RELATION', 'METRIC', 'QUESTION', 'UNRESOLVED',
] as const;
const strictSectionTitles = [
  '文档说明', '业务目标', '业务对象', '业务活动', '字段与维度', '对象关系', '指标口径', '示例问题', '待确认事项',
] as const;

function strictMarkdown(review: MutableReview): string {
  const sections = review.standardSections ?? [];
  const claims = review.claims ?? [];
  const claimsById = new Map(claims.map((claim) => [claim.claimId as string, claim]));
  const lines = [`# ${String(review.title)}`];
  for (const [index, section] of sections.entries()) {
    const sectionId = String(section.sectionId);
    lines.push('', `## ${index + 1}. ${String(section.heading).replace(/^\d+\.\s*/u, '')}`, '', review.sections?.[sectionId] ?? '章节说明。');
    for (const claimId of section.claimIds) {
      const claim = claimsById.get(claimId);
      if (!claim) throw new Error(`missing fixture claim ${claimId}`);
      lines.push('', `### ${String(claim.title)}`, '', String(claim.statement));
    }
  }
  return lines.join('\n').concat('\n');
}

function makeStrictV7Review(input: MutableReview, documentKind: 'SOURCE' | 'MERGED'): MutableReview {
  const evidence = [structuredClone(input.evidence[0])];
  const claimId = `strict-${documentKind.toLowerCase()}-overview`;
  const sectionPurpose = '说明已冻结资料的范围。';
  const claim = {
    claimId,
    section: 1,
    sectionId: 'OVERVIEW',
    sectionPurpose,
    title: '冻结资料范围',
    statement: '本结论来自已保存的固定资料。',
    focusIdentifiers: [],
    markdownAnchor: `strict-${documentKind.toLowerCase()}-overview-anchor`,
    evidenceRefs: [evidence[0]!.evidenceRef],
    affectedObjectRefs: [],
  };
  const review: MutableReview = {
    ...structuredClone(input),
    documentKind,
    evidence,
    claims: [claim],
    standardSections: strictSectionIds.map((sectionId, index) => ({
      sectionId,
      heading: `${index + 1}. ${strictSectionTitles[index]}`,
      purpose: index === 0 ? sectionPurpose : `说明${strictSectionTitles[index]}。`,
      markdownAnchor: `strict-${documentKind.toLowerCase()}-${sectionId.toLowerCase()}`,
      claimIds: index === 0 ? [claimId] : [],
    })),
    sections: Object.fromEntries(strictSectionIds.map((sectionId, index) => [
      sectionId,
      index === 0 ? '本章说明已保存资料的使用范围。' : `本章说明${strictSectionTitles[index]}。`,
    ])),
    traceLinks: [{
      linePrefix: '### 冻结资料范围',
      claimId,
      markdownAnchor: claim.markdownAnchor,
      section: claim.section,
      sectionId: claim.sectionId,
      sectionPurpose: claim.sectionPurpose,
      claim: {
        title: claim.title,
        statement: claim.statement,
        focusIdentifiers: claim.focusIdentifiers,
      },
      evidenceRefs: claim.evidenceRefs,
    }],
    markdown: '',
    markdownSha256: '',
  };
  review.markdown = strictMarkdown(review);
  review.markdownSha256 = `sha256:${sha256HexSync(review.markdown)}`;
  return review;
}

function withStrictV7Fixtures(action: (publication: MutablePublication) => void): void {
  const originalSources = mutablePublication.sources;
  const originalMerged = mutablePublication.mergedDocument;
  if (!originalSources?.[0]?.review) throw new Error('active publication must contain a MySQL review');
  const strictSource = makeStrictV7Review(originalSources[0].review, 'SOURCE');
  const strictMerged = makeStrictV7Review(originalSources[0].review, 'MERGED');
  strictMerged.sourceId = 'guanyijia_demo_content_merged_baseline';
  strictMerged.snapshotId = mutablePublication.contentSnapshotId;
  strictMerged.contentOrigin = 'DERIVED_DEMO';
  mutablePublication.sources = originalSources.map((source, index) => (
    index === 0 ? { ...source, review: strictSource } : source
  ));
  mutablePublication.mergedDocument = strictMerged;
  try {
    action(mutablePublication);
  } finally {
    mutablePublication.sources = originalSources;
    mutablePublication.mergedDocument = originalMerged;
  }
}

test('binds an exact five-source run to the frozen content publication', () => {
  const binding = bindDemoContentRun({
    runId: 'run-content-1',
    formalSources,
  });

  assert.equal(binding.contentSnapshotId, 'guanyijia-demo-content-v7-20260827');
  assert.equal(binding.sourceBindings.length, 5);
  assert.deepEqual(binding.sourceBindings.map((source) => source.sourceId), formalSources.map((source) => source.sourceId));
  assert.equal(binding.sourceBindings.find((source) => source.sourceId === 'guanyijia_github')?.origin, 'SOURCE_NATIVE');
});

test('refuses a content binding when a formal source snapshot does not match the story', () => {
  assert.throws(() => bindDemoContentRun({
    runId: 'run-content-2',
    formalSources: formalSources.map((source) => source.sourceId === 'guanyijia_github'
      ? { ...source, snapshotId: 'github-wrong' }
      : source),
  }), /冻结内容快照校验失败/u);
});

test('projects V7 GitHub source explanations and authored-material boundaries without a legacy template fallback', () => {
  const github = readDemoContentSourceReview({
    sourceId: 'guanyijia_github',
    snapshotId: '20260813032126Z-5821d0ece9b1',
  });
  const official = readDemoContentSourceReview({
    sourceId: 'guanyijia_official_docs',
    snapshotId: 'gyjerp-official-docs-20260813T031656Z',
  });

  assert.ok(github);
  assert.ok(official);
  assert.equal(github.contentOrigin, 'SOURCE_NATIVE');
  assert.equal(github.evidence.every((entry) => entry.excerptKind === 'EXACT_EXCERPT'), true);
  assert.match(github.markdown, /本章说明源码节选中可以确认的通用入口和配置定义/u);
  assert.match(github.markdown, /不能单独证明/u);
  assert.ok(github.markdown.split('\n').length >= 250);
  assert.ok(github.evidence.length >= 60);
  assert.ok(github.traceLinks.length >= 40);
  assert.doesNotMatch(github.markdown, /TRACE:|<!--|sourceId=|snapshotId=|[a-f0-9]{40}/iu);
  assert.doesNotMatch(github.markdown, /冻结扫描已识别|源码界面与工作流提供/u);
  assert.equal(official.contentOrigin, 'DEMO_AUTHORED');
  assert.match(official.markdown, /演示编写/u);
  assert.ok(official.evidence.length > 0);
  assert.ok(official.traceLinks.every((trace) => (
    trace.evidenceRefs.length > 0 && trace.markdownAnchor.length > 0
  )));
});

test('continues to read a persisted V5 binding after V6 becomes active', () => {
  const v5Binding = {
    runId: 'persisted-v5-run',
    storyKey: 'guanyijia-five-source-v1',
    contentSnapshotId: 'guanyijia-demo-content-v5-20260826',
    contentSha256: 'sha256:2663cc64b19b5aa149ae0dfcea2a1917e9295ea490e0a59b422558cbcd009a2c',
    sourceBindings: [
      { sourceId: 'guanyijia_mysql', formalSnapshotId: '20260813T032528Z-abb0502c7d79', contentSourceSnapshotId: '20260813T032528Z-abb0502c7d79', origin: 'SNAPSHOT_REFERENCE' },
      { sourceId: 'guanyijia_github', formalSnapshotId: '20260813032126Z-5821d0ece9b1', contentSourceSnapshotId: 'github-b3ab269b05070d40', origin: 'SOURCE_NATIVE' },
      { sourceId: 'guanyijia_official_docs', formalSnapshotId: 'gyjerp-official-docs-20260813T031656Z', contentSourceSnapshotId: 'guanyijia-demo-official-v1', origin: 'DEMO_AUTHORED' },
      { sourceId: 'guanyijia_demo_policy', formalSnapshotId: 'guanyijia-demo-policy-f6c6d209ffe3fd53', contentSourceSnapshotId: 'guanyijia-demo-policy-content-v1', origin: 'DEMO_AUTHORED' },
      { sourceId: 'guanyijia_semantica_demo', formalSnapshotId: 'guanyijia-semantica-demo-ff948845dc5bd778', contentSourceSnapshotId: 'guanyijia-demo-terminology-v1', origin: 'DERIVED_DEMO' },
    ],
  } as const;

  assert.equal(validateDemoContentRunBinding(v5Binding).contentSnapshotId, v5Binding.contentSnapshotId);
  assert.equal(readBoundDemoContentSourceReview({
    sourceId: 'guanyijia_mysql',
    snapshotId: '20260813T032528Z-abb0502c7d79',
    contentBinding: v5Binding,
  }), undefined);
  assert.match(readBoundDemoContentSourceReview({
    sourceId: 'guanyijia_github',
    snapshotId: '20260813032126Z-5821d0ece9b1',
    contentBinding: v5Binding,
  })?.markdown ?? '', /固定版本共 719 个文件/u);
});

test('publishes approved V7 as the rich active publication and retains V6 then V5 as its predecessors', () => {
  const publication = pinnedDemoContentPublication as unknown as {
    contentSnapshotId: string;
    legacyPublications?: Array<{
      contentSnapshotId: string;
      legacyPublications?: Array<{ contentSnapshotId: string }>;
    }>;
  };

  assert.equal(publication.contentSnapshotId, 'guanyijia-demo-content-v7-20260827');
  assert.deepEqual([
    publication.contentSnapshotId,
    publication.legacyPublications?.[0]?.contentSnapshotId,
    publication.legacyPublications?.[0]?.legacyPublications?.[0]?.contentSnapshotId,
  ], [
    'guanyijia-demo-content-v7-20260827',
    'guanyijia-demo-content-v6-20260826',
    'guanyijia-demo-content-v5-20260826',
  ]);

  const mysql = readDemoContentSourceReview(formalSources[0]);
  assert.ok(mysql, 'active V7 MySQL source must expose a review');
  assert.equal(mysql.standardSections?.length, 9, 'active V7 MySQL review must keep the rich nine-section shape');
  assert.ok((mysql.claims?.length ?? 0) > 0, 'active V7 MySQL review must keep rich claims');
});

test('resolves a persisted V6 binding to its exact rich review after V7 activation', () => {
  const validated = validateDemoContentRunBinding(persistedV6Binding);
  assert.equal(validated.contentSnapshotId, persistedV6Binding.contentSnapshotId);
  const mysql = readBoundDemoContentSourceReview({
    sourceId: 'guanyijia_mysql',
    snapshotId: '20260813T032528Z-abb0502c7d79',
    contentBinding: persistedV6Binding,
  });
  assert.ok(mysql);
  assert.equal(mysql.markdownSha256, 'sha256:fe65ab6622c315f1525dcb390fe8e9964f94227f384417188c75a22b373a49f6');
  assert.equal(mysql.standardSections?.length, 9);
});

test('fails closed for unknown snapshots and known snapshots with a bad content pin', () => {
  assert.throws(() => validateDemoContentRunBinding({
    ...persistedV6Binding,
    contentSnapshotId: 'guanyijia-demo-content-v99-20991231',
  } as unknown as Parameters<typeof validateDemoContentRunBinding>[0]), /冻结内容快照校验失败/u);
  assert.throws(() => validateDemoContentRunBinding({
    ...persistedV6Binding,
    contentSha256: `sha256:${'0'.repeat(64)}`,
  } as unknown as Parameters<typeof validateDemoContentRunBinding>[0]), /冻结内容快照校验失败/u);
});

test('fails closed when the V7 to V6 to V5 publication chain contains a duplicate', () => {
  const v5 = findV5Publication();
  assert.ok(v5, 'publication history must include V5');
  const original = v5.legacyPublications;
  v5.legacyPublications = [structuredClone(v5)];
  try {
    assert.throws(() => validateDemoContentRunBinding(persistedV5Binding), /冻结内容快照校验失败/u);
  } finally {
    v5.legacyPublications = original;
  }
});

test('fails closed when the append-only publication chain contains a cycle', () => {
  const v5 = findV5Publication();
  assert.ok(v5, 'publication history must include V5');
  const original = v5.legacyPublications;
  v5.legacyPublications = [mutablePublication];
  try {
    assert.throws(() => validateDemoContentRunBinding(persistedV5Binding), /冻结内容快照校验失败/u);
  } finally {
    v5.legacyPublications = original;
  }
});

test('normalizes V6 frozen section descriptors for the review runtime without losing their stable identities', () => {
  const markdown = '# V6 审阅文档\n\n## 1. 文档说明\n\n内容。';
  const excerpt = '冻结资料。';
  const normalized = normalizeDemoContentSourceReview({
    schemaVersion: 1,
    documentKind: 'SOURCE',
    sourceId: 'guanyijia_mysql',
    snapshotId: '20260813T032528Z-abb0502c7d79',
    contentOrigin: 'SNAPSHOT_REFERENCE',
    title: '数据库审阅文档',
    coverageLabel: '9 章',
    markdown,
    markdownSha256: 'sha256:23d543e6e76f4be0bd9ed32b842e57f3dc8bea7059fab70d629c1a1617fc89cc',
    standardSections: [{
      sectionId: 'OVERVIEW',
      heading: '1. 文档说明',
      purpose: '说明资料范围',
      markdownAnchor: 'overview',
      claimIds: ['mysql:overview'],
    }],
    evidence: [{
      evidenceRef: 'mysql:overview:evidence', evidenceClass: 'OBSERVED', excerptKind: 'EXACT_EXCERPT',
      title: '数据库快照', excerpt, locationLabel: '快照', locationValue: 'ddl.sql:L1-L1',
      excerptSha256: 'sha256:7b37f0d31b87701f6bd779d3ce668c995f68b5ac77e2839d37ee18148bb25fa7',
      artifactDigest: 'sha256:5c95e3242f5f41012468ee366227d5e7ec3612c5ac29feae919bd281efe19ab5',
      affectedObjectRefs: [],
    }],
    claims: [{
      claimId: 'mysql:overview', section: 1, sectionId: 'OVERVIEW', sectionPurpose: '说明资料范围',
      title: '审阅范围', statement: '资料来自冻结快照。', focusIdentifiers: [],
      markdownAnchor: 'overview', evidenceRefs: ['mysql:overview:evidence'], affectedObjectRefs: [],
    }],
    traceLinks: [{
      linePrefix: '审阅范围', markdownAnchor: 'overview', section: 1, sectionId: 'OVERVIEW',
      sectionPurpose: '说明资料范围', claim: { title: '审阅范围', statement: '资料来自冻结快照。', focusIdentifiers: [] },
      evidenceRefs: ['mysql:overview:evidence'],
    }],
  });

  assert.deepEqual(normalized.standardSections, [{
    index: 1,
    sectionId: 'OVERVIEW',
    title: '文档说明',
    purpose: '说明资料范围',
    markdownAnchor: 'overview',
    claimIds: ['mysql:overview'],
  }]);
  assert.equal(normalized.claims?.[0]?.sectionId, 'OVERVIEW');
});

test('fails closed when a V7 source trace does not map one-to-one to its claim', () => {
  withStrictV7Fixtures((publication) => {
    const review = publication.sources?.[0]?.review;
    assert.ok(review);
    const trace = review.traceLinks[0]!;
    trace.claimId = 'forged-claim-id';

    assert.throws(() => bindDemoContentRun({
      runId: 'strict-source-trace-mismatch',
      formalSources,
    }), /冻结内容快照校验失败/u);
  });
});

test('fails closed when a V7 source trace changes its anchor, section, evidence, or cardinality', () => {
  withStrictV7Fixtures((publication) => {
    const review = publication.sources?.[0]?.review;
    assert.ok(review);
    const original = structuredClone(review.traceLinks);
    const invalidTraces = [
      [{ ...original[0], markdownAnchor: 'forged-anchor' }],
      [{ ...original[0], section: 2, sectionId: 'GOAL' }],
      [{ ...original[0], evidenceRefs: [] }],
      [...original, structuredClone(original[0])],
    ];

    for (const traceLinks of invalidTraces) {
      review.traceLinks = traceLinks;
      assert.throws(() => bindDemoContentRun({
        runId: `strict-source-trace-${traceLinks.length}`,
        formalSources,
      }), /冻结内容快照校验失败/u);
    }
  });
});

test('accepts a V7 stable trace without legacy display-text metadata', () => {
  withStrictV7Fixtures((publication) => {
    const review = publication.sources?.[0]?.review;
    assert.ok(review);
    const trace = review.traceLinks[0]!;
    delete trace.claim;
    trace.linePrefix = 'stable V7 transport identity';

    assert.doesNotThrow(() => bindDemoContentRun({
      runId: 'strict-source-stable-trace',
      formalSources,
    }));
  });
});

test('fails closed when a V7 source Markdown duplicates a claim despite a matching hash', () => {
  withStrictV7Fixtures((publication) => {
    const review = publication.sources?.[0]?.review;
    assert.ok(review);
    const claim = review.claims?.[0];
    assert.ok(claim);
    review.markdown = `${review.markdown}\n### ${String(claim.title)}\n\n${String(claim.statement)}\n`;
    review.markdownSha256 = `sha256:${sha256HexSync(review.markdown)}`;

    assert.throws(() => bindDemoContentRun({
      runId: 'strict-source-duplicate-markdown-claim',
      formalSources,
    }), /冻结内容快照校验失败/u);
  });
});

test('fails closed when a V7 merged baseline duplicates a claim', () => {
  withStrictV7Fixtures((publication) => {
    const merged = publication.mergedDocument;
    assert.ok(merged);
    const claim = structuredClone(merged.claims?.[0]);
    assert.ok(claim);
    merged.claims = [...(merged.claims ?? []), claim];
    merged.standardSections?.[0]?.claimIds.push(String(claim.claimId));

    assert.throws(() => readDemoContentMergedBaseline(), /冻结内容快照校验失败/u);
  });
});
