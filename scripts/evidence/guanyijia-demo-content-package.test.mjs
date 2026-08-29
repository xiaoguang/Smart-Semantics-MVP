import assert from 'node:assert/strict';
import { cp, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { createHash } from 'node:crypto';
import { tmpdir } from 'node:os';
import {
  buildDemoContentPublication,
  buildV6MergedBaseline,
  packageV6SourceReview,
} from './guanyijia-demo-content-package.mjs';
import * as demoContentPackage from './guanyijia-demo-content-package.mjs';
import {
  createV7Selection,
  freezeV7Snapshot,
  loadV6NarrativeDescriptors,
  persistV7SourceCandidate,
} from './guanyijia-demo-content-v7-generate.mjs';

const prototypeRoot = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const snapshotRoot = join(
  prototypeRoot,
  '../modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v4-20260826',
);
const contentSnapshotDirectory = join(
  prototypeRoot,
  '../modeling-evidence/guanyijia/demo-content/snapshots',
);
const v5SnapshotId = 'guanyijia-demo-content-v5-20260826';
const v6SnapshotId = 'guanyijia-demo-content-v6-20260826';
const v7SnapshotId = 'guanyijia-demo-content-v7-20260827';

const formalSourceIdentities = [
  ['guanyijia_mysql', '20260813T032528Z-abb0502c7d79'],
  ['guanyijia_github', '20260813032126Z-5821d0ece9b1'],
  ['guanyijia_official_docs', 'gyjerp-official-docs-20260813T031656Z'],
  ['guanyijia_demo_policy', 'guanyijia-demo-policy-f6c6d209ffe3fd53'],
  ['guanyijia_semantica_demo', 'guanyijia-semantica-demo-ff948845dc5bd778'],
];

const standardSections = [
  ['OVERVIEW', '1. 文档说明'],
  ['GOAL', '2. 业务目标'],
  ['OBJECT', '3. 业务对象'],
  ['ACTIVITY', '4. 业务活动'],
  ['FIELD', '5. 字段与维度'],
  ['RELATION', '6. 对象关系'],
  ['METRIC', '7. 指标口径'],
  ['QUESTION', '8. 示例问题'],
  ['UNRESOLVED', '9. 待确认事项'],
];

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function canonicalJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
}

async function createV7PackageFixture() {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'guanyijia-demo-content-package-v7-'));
  for (const snapshotId of [v5SnapshotId, v6SnapshotId]) {
    await cp(join(contentSnapshotDirectory, snapshotId), join(fixtureRoot, snapshotId), { recursive: true });
  }
  const v7Root = join(fixtureRoot, v7SnapshotId);
  const v6Root = join(fixtureRoot, v6SnapshotId);
  const candidateRoot = join(fixtureRoot, 'candidates');
  const selectionPath = join(fixtureRoot, 'selection.json');
  const descriptors = await loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const candidates = {};
  for (const [index, descriptor] of descriptors.entries()) {
    const sections = standardSections.map(([sectionId]) => ({
      sectionId,
      narrative: `本测试章节说明${descriptor.readerLabel}中已保存资料可用于理解本章节的对象、活动和建模边界；未保存的材料不在此补造。`,
      ...((sectionId === 'UNRESOLVED' || !descriptor.review.claims.some((claim) => claim.sectionId === sectionId)) ? {
        gap: {
          missing: '能够确认最终业务含义的正式材料',
          limitation: '不能据此生成正式规则或指标',
          nextStep: '由对应责任人回到正式材料核对',
        },
      } : {}),
    }));
    const candidate = await persistV7SourceCandidate({
      descriptors,
      sourceId: descriptor.sourceId,
      sessionId: `chatgpt-package-fixture-${index + 1}`,
      rawOutput: `${JSON.stringify({ schemaVersion: 1, sourceId: descriptor.sourceId, sections }, null, 2)}\n`,
      candidateRoot,
    });
    candidates[candidate.sourceId] = candidate.candidateId;
  }
  await createV7Selection({ descriptors, candidateRoot, selectionPath, candidates });
  const manifest = await freezeV7Snapshot({
    sourceSnapshotRoot: v6Root,
    targetSnapshotRoot: v7Root,
    candidateRoot,
    selectionPath,
  });
  return { fixtureRoot, v7Root, manifest };
}

async function rewriteV7Manifest(fixture) {
  const manifest = JSON.parse(await readFile(join(fixture.v7Root, 'manifest.json'), 'utf8'));
  const unsignedManifest = {
    schemaVersion: manifest.schemaVersion,
    snapshotId: manifest.snapshotId,
    storyKey: manifest.storyKey,
    sources: manifest.sources,
    generationRuns: manifest.generationRuns,
    previousSnapshotId: manifest.previousSnapshotId,
  };
  manifest.contentSha256 = sha256(`${canonicalJson(unsignedManifest)}\n`);
  await writeFile(join(fixture.v7Root, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  await writeFile(
    join(fixture.v7Root, 'checksums.sha256'),
    `${manifest.sources.flatMap((source) => source.artifacts)
      .map((artifact) => `${artifact.sha256.slice(7)}  ${artifact.path}`)
      .join('\n')}\n`,
    'utf8',
  );
}

function v6FrozenReview(input = {}) {
  const sourceId = input.sourceId ?? 'guanyijia_mysql';
  const origin = input.origin ?? 'SNAPSHOT_REFERENCE';
  const sections = standardSections.map(([sectionId, heading], index) => {
    const evidenceRef = `${sourceId}:evidence:${index + 1}`;
    const claimId = `${sourceId}:claim:${index + 1}`;
    return { sectionId, heading, evidenceRef, claimId };
  });
  const markdown = [
    `# ${input.title ?? '数据库标准化文档'}`,
    '',
    ...sections.flatMap(({ heading, claimId }, index) => [
      `## ${heading}`,
      '',
      `### 第${index + 1}项说明`,
      '',
      `这是第${index + 1}章的可建模内容。`,
      '',
    ]),
  ].join('\n').trimEnd();
  const evidence = sections.map(({ evidenceRef }, index) => {
    const excerpt = `第${index + 1}章的冻结材料。`;
    return {
      evidenceRef,
      evidenceClass: origin,
      excerptKind: 'DOCUMENT_SECTION',
      title: `材料${index + 1}`,
      excerpt,
      locationLabel: '冻结资料',
      locationValue: `source.md:L${index + 1}-L${index + 1}`,
      excerptSha256: sha256(excerpt),
      artifactDigest: sha256(`artifact:${index + 1}`),
      affectedObjectRefs: [],
    };
  });
  const claims = sections.map(({ sectionId, heading, evidenceRef, claimId }, index) => ({
    claimId,
    section: index + 1,
    sectionId,
    sectionPurpose: heading,
    title: `第${index + 1}项说明`,
    statement: `这是第${index + 1}章的可建模内容。`,
    focusIdentifiers: [],
    markdownAnchor: `${sourceId}-anchor-${index + 1}`,
    evidenceRefs: [evidenceRef],
    affectedObjectRefs: [],
  }));
  return {
    schemaVersion: 1,
    documentKind: 'SOURCE',
    sourceId,
    snapshotId: input.snapshotId ?? `${sourceId}-content-v6`,
    contentOrigin: origin,
    title: input.title ?? '数据库标准化文档',
    coverageLabel: '9 章',
    coverage: {},
    standardSections: sections.map(({ sectionId, heading, claimId }, index) => ({
      sectionId,
      heading,
      purpose: `第${index + 1}章用途`,
      markdownAnchor: `${sourceId}-section-${index + 1}`,
      claimIds: [claimId],
    })),
    sections: Object.fromEntries(sections.map(({ sectionId }, index) => [sectionId, `第${index + 1}章内容。`])),
    markdown,
    markdownSha256: sha256(markdown),
    evidence,
    claims,
    traceLinks: claims.map((claim) => ({
      linePrefix: `### ${claim.title}`,
      markdownAnchor: claim.markdownAnchor,
      section: claim.section,
      sectionId: claim.sectionId,
      sectionPurpose: claim.sectionPurpose,
      claim: {
        title: claim.title,
        statement: claim.statement,
        focusIdentifiers: [],
      },
      evidenceRefs: claim.evidenceRefs,
    })),
  };
}

test('packages V6 frozen standardized reviews directly and preserves their explicit section and claim records', () => {
  const review = v6FrozenReview();
  const packaged = packageV6SourceReview({
    source: {
      sourceId: 'guanyijia_mysql',
      snapshotId: review.snapshotId,
      contentOrigin: 'SNAPSHOT_REFERENCE',
    },
    formalSnapshotId: '20260813T032528Z-abb0502c7d79',
    review,
    sourceType: 'MySQL 冻结逻辑快照',
    location: 'jsh_erp',
    scope: '30 张表',
    credentialLabel: '已配置',
  });

  assert.deepEqual(packaged.review.standardSections, review.standardSections);
  assert.deepEqual(packaged.review.claims, review.claims);
  assert.equal(packaged.review.markdown, review.markdown);
  assert.equal(packaged.review.snapshotId, '20260813T032528Z-abb0502c7d79');
  assert.equal(packaged.review.documentKind, 'SOURCE');
});

test('derives a clearly non-final nine-section V6 merged baseline from all five frozen source reviews', () => {
  const sources = [
    ['guanyijia_mysql', 'SNAPSHOT_REFERENCE', '数据库标准化文档'],
    ['guanyijia_github', 'SOURCE_NATIVE', '代码仓库标准化文档'],
    ['guanyijia_official_docs', 'DEMO_AUTHORED', '业务说明标准化文档'],
    ['guanyijia_demo_policy', 'DEMO_AUTHORED', '制度标准化文档'],
    ['guanyijia_semantica_demo', 'DERIVED_DEMO', '术语图标准化文档'],
  ].map(([sourceId, origin, title]) => packageV6SourceReview({
    source: { sourceId, snapshotId: `${sourceId}-content-v6`, contentOrigin: origin },
    formalSnapshotId: `${sourceId}-formal`,
    review: v6FrozenReview({ sourceId, origin, title }),
    sourceType: '冻结资料', location: 'local', scope: '演示范围', credentialLabel: '不需要',
  }));
  const merged = buildV6MergedBaseline(sources);

  assert.equal(merged.documentKind, 'MERGED');
  assert.deepEqual(
    [...merged.markdown.matchAll(/^##\s+(.+)$/gmu)].map((match) => match[1]),
    standardSections.map(([, heading]) => heading),
  );
  assert.match(merged.markdown, /待本次运行确认/u);
  assert.doesNotMatch(merged.markdown, /最终定版/u);
  assert.equal(merged.standardSections.length, 9);
});

test('includes each V7 source chapter narrative in the matching merged chapter without exposing source internals', () => {
  const sources = [
    ['guanyijia_mysql', 'SNAPSHOT_REFERENCE', '数据库结构资料'],
    ['guanyijia_github', 'SOURCE_NATIVE', '固定版本源码节选'],
    ['guanyijia_official_docs', 'DEMO_AUTHORED', '业务说明（演示编写资料）'],
    ['guanyijia_demo_policy', 'DEMO_AUTHORED', 'ERP 管理制度（演示制度草案）'],
    ['guanyijia_semantica_demo', 'DERIVED_DEMO', '企业术语图（派生内容）'],
  ].map(([sourceId, origin, title], sourceIndex) => {
    const review = v6FrozenReview({ sourceId, origin, title, snapshotId: `${sourceId}-content-v7` });
    review.sections.OBJECT = `第${sourceIndex + 1}份资料对业务对象的阅读说明：对象的职责、使用范围和本次资料边界。`;
    return packageV6SourceReview({
      source: { sourceId, snapshotId: `${sourceId}-content-v7`, contentOrigin: origin },
      formalSnapshotId: `${sourceId}-formal`,
      review,
      sourceType: '冻结资料', location: 'local', scope: '演示范围', credentialLabel: '不需要',
    });
  });

  const merged = buildV6MergedBaseline(sources, { contentSnapshotId: v7SnapshotId });

  const objectChapter = merged.markdown.split('## 3. 业务对象')[1].split('## 4. 业务活动')[0];
  for (const [index, source] of sources.entries()) {
    assert.match(objectChapter, new RegExp(`### ${source.review.title}`, 'u'));
    assert.match(objectChapter, new RegExp(`第${index + 1}份资料对业务对象的阅读说明`, 'u'));
  }
  assert.doesNotMatch(merged.markdown, /guanyijia_|SOURCE_NATIVE|DEMO_AUTHORED|DERIVED_DEMO/u);
});

test('packages a V7 rich browser projection and preserves its V6 then V5 immutable publication lineage', async (t) => {
  const fixture = await createV7PackageFixture();
  t.after(() => rm(fixture.fixtureRoot, { recursive: true, force: true }));

  const publication = await buildDemoContentPublication(fixture.v7Root);

  assert.equal(publication.contentSnapshotId, v7SnapshotId);
  assert.equal(publication.contentSha256, fixture.manifest.contentSha256);
  assert.deepEqual(
    publication.sources.map((source) => [source.sourceId, source.formalSnapshotId]),
    formalSourceIdentities,
  );
  assert.deepEqual(
    [
      publication.contentSnapshotId,
      publication.legacyPublications?.[0]?.contentSnapshotId,
      publication.legacyPublications?.[0]?.legacyPublications?.[0]?.contentSnapshotId,
    ],
    [v7SnapshotId, v6SnapshotId, v5SnapshotId],
  );
  assert.equal(publication.sources.every((source) => source.review.standardSections?.length === 9), true);
});

test('uses the approved V7 snapshot as the default active browser publication', async () => {
  const publication = await buildDemoContentPublication();

  assert.equal(publication.contentSnapshotId, v7SnapshotId);
  assert.deepEqual(
    publication.legacyPublications?.map((entry) => entry.contentSnapshotId),
    [v6SnapshotId],
  );
});

test('fails closed when the V6 browser publication drops a frozen claim, evidence, or trace reference', async () => {
  assert.equal(
    typeof demoContentPackage.validateV6PublicationCompleteness,
    'function',
    'the active V6 publication must expose its structural completeness gate',
  );
  const publication = await buildDemoContentPublication(join(contentSnapshotDirectory, v6SnapshotId));

  assert.doesNotThrow(() => demoContentPackage.validateV6PublicationCompleteness(publication));

  const missingClaim = structuredClone(publication);
  missingClaim.sources[0].review.claims.pop();
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(missingClaim),
    /V6.*claim|claim.*V6/i,
  );

  const missingEvidence = structuredClone(publication);
  missingEvidence.sources[1].review.evidence.pop();
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(missingEvidence),
    /V6.*evidence|evidence.*V6/i,
  );

  const missingTrace = structuredClone(publication);
  missingTrace.sources[4].review.traceLinks.pop();
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(missingTrace),
    /V6.*trace|trace.*V6/i,
  );
});

test('fails closed when V6 readable content, formal identities, or merged mappings drift without dropping IDs', async () => {
  const publication = await buildDemoContentPublication(join(contentSnapshotDirectory, v6SnapshotId));

  const changedClaimText = structuredClone(publication);
  changedClaimText.sources[0].review.claims[0].statement = '被错误裁剪后的说明';
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(changedClaimText),
    /V6.*claim|claim.*V6/i,
  );

  const changedEvidenceText = structuredClone(publication);
  changedEvidenceText.sources[1].review.evidence[0].excerpt = '被错误裁剪后的源码摘录';
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(changedEvidenceText),
    /V6.*evidence|evidence.*V6/i,
  );

  const changedFormalSnapshot = structuredClone(publication);
  changedFormalSnapshot.sources[0].formalSnapshotId = 'wrong-formal-snapshot';
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(changedFormalSnapshot),
    /V6.*source identity|source identity.*V6/i,
  );

  const changedMergedClaimMapping = structuredClone(publication);
  changedMergedClaimMapping.mergedDocument.claims[0].evidenceRefs = [];
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(changedMergedClaimMapping),
    /V6.*merged claim|merged claim.*V6/i,
  );

  const changedMergedEvidenceText = structuredClone(publication);
  changedMergedEvidenceText.mergedDocument.evidence[0].excerpt = '被错误裁剪后的合并材料';
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(changedMergedEvidenceText),
    /V6.*merged evidence|merged evidence.*V6/i,
  );

  const changedMergedTraceMapping = structuredClone(publication);
  changedMergedTraceMapping.mergedDocument.traceLinks[0].evidenceRefs = [];
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(changedMergedTraceMapping),
    /V6.*merged trace|merged trace.*V6/i,
  );
});

test('fails closed when a rich V6 review section changes while its claim and evidence IDs remain intact', async (t) => {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'guanyijia-demo-content-package-v6-section-'));
  t.after(() => rm(fixtureRoot, { recursive: true, force: true }));
  const fixtureSnapshotRoot = join(fixtureRoot, v6SnapshotId);
  await cp(join(contentSnapshotDirectory, v5SnapshotId), join(fixtureRoot, v5SnapshotId), { recursive: true });
  await cp(join(contentSnapshotDirectory, v6SnapshotId), fixtureSnapshotRoot, { recursive: true });

  const publication = await buildDemoContentPublication(fixtureSnapshotRoot);
  const changed = structuredClone(publication);
  const review = changed.sources[0].review;
  assert.equal(typeof review.sections?.OVERVIEW, 'string');
  const claimIds = review.claims.map((claim) => claim.claimId);
  const evidenceRefs = review.evidence.map((evidence) => evidence.evidenceRef);
  review.sections.OVERVIEW = `${review.sections.OVERVIEW}\n\n被错误裁剪的章节内容。`;

  assert.deepEqual(review.claims.map((claim) => claim.claimId), claimIds);
  assert.deepEqual(review.evidence.map((evidence) => evidence.evidenceRef), evidenceRefs);
  assert.throws(
    () => demoContentPackage.validateV6PublicationCompleteness(changed, { snapshotRoot: fixtureSnapshotRoot }),
    /V6.*section|section.*V6|V6.*review/i,
  );
});

test('fails closed when a V6 review section map loses or gains canonical content without changing claim or evidence IDs', async (t) => {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'guanyijia-demo-content-package-v6-section-shape-'));
  t.after(() => rm(fixtureRoot, { recursive: true, force: true }));
  const fixtureSnapshotRoot = join(fixtureRoot, v6SnapshotId);
  await cp(join(contentSnapshotDirectory, v5SnapshotId), join(fixtureRoot, v5SnapshotId), { recursive: true });
  await cp(join(contentSnapshotDirectory, v6SnapshotId), fixtureSnapshotRoot, { recursive: true });

  const publication = await buildDemoContentPublication(fixtureSnapshotRoot);
  const source = publication.sources[0];
  const claimIds = source.review.claims.map((claim) => claim.claimId);
  const evidenceRefs = source.review.evidence.map((evidence) => evidence.evidenceRef);
  const assertRejectsSectionShape = (label, mutate) => {
    const changed = structuredClone(publication);
    const review = changed.sources[0].review;
    mutate(review.sections);
    assert.deepEqual(review.claims.map((claim) => claim.claimId), claimIds, label);
    assert.deepEqual(review.evidence.map((evidence) => evidence.evidenceRef), evidenceRefs, label);
    assert.throws(
      () => demoContentPackage.validateV6PublicationCompleteness(changed, { snapshotRoot: fixtureSnapshotRoot }),
      /V6.*section|section.*V6|V6.*review/i,
      label,
    );
  };

  assertRejectsSectionShape('missing canonical section key', (sections) => {
    delete sections.OVERVIEW;
  });
  assertRejectsSectionShape('extra section key', (sections) => {
    sections.EXTRA = sections.OVERVIEW;
  });
  assertRejectsSectionShape('cleared canonical section value', (sections) => {
    sections.OVERVIEW = undefined;
  });
});

test('package check rejects a V7 snapshot that omits a source-specific reader narrative artifact', async (t) => {
  const fixture = await createV7PackageFixture();
  t.after(() => rm(fixture.fixtureRoot, { recursive: true, force: true }));

  const manifest = JSON.parse(await readFile(join(fixture.v7Root, 'manifest.json'), 'utf8'));
  const source = manifest.sources.find((entry) => entry.sourceId === 'guanyijia_github');
  const narrative = source.artifacts.find((artifact) => artifact.path.endsWith('/v7-reader-narratives.json'));
  assert.ok(narrative);
  source.artifacts = source.artifacts.filter((artifact) => artifact !== narrative);
  // Keep the bytes on disk: the package layer must require the V7-specific
  // manifest declaration rather than relying only on generic snapshot hashes.
  await writeFile(join(fixture.v7Root, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  await rewriteV7Manifest(fixture);

  await assert.rejects(
    () => buildDemoContentPublication(fixture.v7Root),
    /V7.*narrative|reader narrative|narrative artifact/i,
  );
});

test('package check rejects a V7 snapshot whose sealed predecessor is not the exact V6 publication', async (t) => {
  const fixture = await createV7PackageFixture();
  t.after(() => rm(fixture.fixtureRoot, { recursive: true, force: true }));

  const manifest = JSON.parse(await readFile(join(fixture.v7Root, 'manifest.json'), 'utf8'));
  manifest.previousSnapshotId = v5SnapshotId;
  await writeFile(join(fixture.v7Root, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  await rewriteV7Manifest(fixture);

  await assert.rejects(
    () => buildDemoContentPublication(fixture.v7Root),
    /previous.*V6|V6.*lineage|lineage.*V6/i,
  );
});

test('package entry rejects a semantically invalid V7 reader narrative even when generic hashes are valid', async (t) => {
  const fixture = await createV7PackageFixture();
  t.after(() => rm(fixture.fixtureRoot, { recursive: true, force: true }));

  const source = fixture.manifest.sources.find((entry) => entry.sourceId === 'guanyijia_github');
  const narrative = source.artifacts.find((artifact) => artifact.path.endsWith('/v7-reader-narratives.json'));
  assert.ok(narrative);
  const narrativePath = join(fixture.v7Root, narrative.path);
  const parsed = JSON.parse(await readFile(narrativePath, 'utf8'));
  // Keep a valid JSON artifact and rewrite all generic integrity fields. The
  // package entry must still validate the V7-specific reader contract rather
  // than accepting whatever generic snapshot validation can hash.
  parsed.sections = [];
  const narrativeText = `${JSON.stringify(parsed, null, 2)}\n`;
  await writeFile(narrativePath, narrativeText, 'utf8');
  const manifest = JSON.parse(await readFile(join(fixture.v7Root, 'manifest.json'), 'utf8'));
  const manifestSource = manifest.sources.find((entry) => entry.sourceId === 'guanyijia_github');
  const manifestNarrative = manifestSource.artifacts.find((artifact) => artifact.path === narrative.path);
  manifestNarrative.sha256 = sha256(narrativeText);
  await writeFile(join(fixture.v7Root, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  await rewriteV7Manifest(fixture);

  await assert.rejects(
    () => buildDemoContentPublication(fixture.v7Root),
    /V7.*narrative|reader narrative|narrative.*source|source.*narrative/i,
  );
});

test('packages only story excerpts while retaining all five frozen content sources', async () => {
  const publication = await buildDemoContentPublication(snapshotRoot);

  assert.equal(publication.contentSnapshotId, 'guanyijia-demo-content-v4-20260826');
  assert.equal(publication.sources.length, 5);
  const github = publication.sources.find((source) => source.sourceId === 'guanyijia_github');
  assert.ok(github);
  assert.equal(github.origin, 'SOURCE_NATIVE');
  assert.equal(github.sourceFileCount, 719);
  assert.ok(github.review.evidence.length >= 10);
  assert.ok(github.review.evidence.length < github.sourceFileCount);
  assert.ok(github.review.evidence.every((entry) => entry.locationValue.includes(':L')));

  const official = publication.sources.find((source) => source.sourceId === 'guanyijia_official_docs');
  assert.ok(official);
  assert.equal(official.origin, 'DEMO_AUTHORED');
  assert.match(official.review.markdown, /演示编写资料：用于说明本次审阅的业务语境/);
});

test('packages a reviewer-facing document without transport markers, empty chapters, or whole-document evidence', async () => {
  const publication = await buildDemoContentPublication(snapshotRoot);

  for (const source of publication.sources) {
    assert.doesNotMatch(source.review.markdown, /\[TRACE:|<!--|[a-f0-9]{40}/iu, source.sourceId);
    assert.doesNotMatch(source.review.markdown, /本来源未按此目录单独编写内容|本次代码节选未覆盖该目录/iu, source.sourceId);
    assert.ok(source.review.traceLinks.every((trace) => !source.review.markdown.includes(trace.linePrefix)), source.sourceId);
  }

  const github = publication.sources.find((source) => source.sourceId === 'guanyijia_github');
  assert.ok(github);
  assert.match(github.review.markdown, /固定版本源码节选/u);
  assert.doesNotMatch(github.review.markdown, /固定提交 `|719 个冻结文件/u);
  assert.ok(github.review.traceLinks.every((trace) => trace.markdownAnchor.startsWith('github-')));

  const official = publication.sources.find((source) => source.sourceId === 'guanyijia_official_docs');
  assert.ok(official);
  assert.ok(official.review.evidence.every((entry) => entry.excerpt.length < 900));
  assert.ok(official.review.evidence.every((entry) => /:L\d+-L\d+$/u.test(entry.locationValue)));
});

test('packages explicit readable claims with an anchor, purpose, and every supporting material', async () => {
  const publication = await buildDemoContentPublication(snapshotRoot);

  for (const source of publication.sources.filter((entry) => entry.sourceId !== 'guanyijia_mysql')) {
    const evidenceRefs = new Set(source.review.evidence.map((entry) => entry.evidenceRef));
    for (const trace of source.review.traceLinks) {
      assert.ok(trace.sectionId, `${source.sourceId} trace must declare its section identity`);
      assert.ok(trace.sectionPurpose, `${source.sourceId} trace must declare its business purpose`);
      assert.ok(trace.markdownAnchor, `${source.sourceId} trace must declare its Markdown anchor`);
      assert.ok(trace.claim.title && trace.claim.statement, `${source.sourceId} trace must declare a readable claim`);
      assert.notEqual(trace.claim.title, trace.claim.statement, `${source.sourceId} trace title cannot repeat its statement`);
      assert.ok(trace.evidenceRefs.length > 0, `${source.sourceId} trace must retain evidence`);
      assert.ok(trace.evidenceRefs.every((evidenceRef) => evidenceRefs.has(evidenceRef)), `${source.sourceId} trace evidence must exist`);
    }
    assert.doesNotMatch(source.review.markdown, /&(?:amp;)?(?:gt|lt);/iu, `${source.sourceId} must not leak HTML entities`);
  }
});
