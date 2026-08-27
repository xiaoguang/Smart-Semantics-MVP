import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { standardSectionOrder } from '../../src/features/modeling-document-bridge/standard-markdown.ts';

// This is intentionally a RED contract for the append-only V7 publication
// transform.  Loading the not-yet-created module is caught so the failure is
// an actionable assertion, rather than a test setup/import error.
let transformModule;
let transformLoadError;
try {
  transformModule = await import('./guanyijia-demo-content-v7-transform.mjs');
} catch (error) {
  transformLoadError = error;
}

const moduleDirectory = dirname(fileURLToPath(import.meta.url));
const prototypeRoot = resolve(moduleDirectory, '../..');
const workspaceRoot = resolve(prototypeRoot, '..');
const v6Root = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v6-20260826',
);
const reviewPaths = [
  'sources/mysql/standardized-review.json',
  'sources/github/standardized-review.json',
  'sources/official/standardized-review.json',
  'sources/policy/standardized-review.json',
  'sources/terminology/standardized-review.json',
];

const canonicalHeadings = standardSectionOrder.map(({ heading }) => heading);
const canonicalSectionIds = standardSectionOrder.map(({ key }) => key);

function requireTransformModule() {
  assert.ok(
    transformModule,
    `V7 transform module is required; create an append-only transform (load failed: ${transformLoadError?.message ?? 'unknown'})`,
  );
  assert.equal(
    typeof transformModule.transformV6Review,
    'function',
    'V7 transform must expose transformV6Review(review, options) without mutating the frozen input',
  );
  return transformModule;
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function headings(markdown, level) {
  const prefix = '#'.repeat(level);
  return [...markdown.matchAll(new RegExp(`^${prefix}\\s+(.+)$`, 'gmu'))].map((match) => match[1]);
}

function ids(values) {
  return [...new Set(values)].sort();
}

async function readV6Reviews() {
  return Promise.all(reviewPaths.map(async (relativePath) => {
    const bytes = await readFile(join(v6Root, relativePath));
    return {
      relativePath,
      bytes,
      review: JSON.parse(bytes.toString('utf8')),
    };
  }));
}

test('V7 transform keeps every V6 review source, claim identity, and evidence identity', async () => {
  const api = requireTransformModule();
  const inputs = await readV6Reviews();

  for (const { relativePath, review } of inputs) {
    const transformed = api.transformV6Review(review, {
      targetSnapshotId: 'guanyijia-demo-content-v7-20260827',
    });

    assert.notEqual(transformed, review, `${relativePath} must be returned as a new review value`);
    assert.equal(transformed.sourceId, review.sourceId);
    // A source review retains the formal source snapshot identity.  The V7
    // content snapshot identity belongs to the publication/manifest layer;
    // overloading this field would make the runtime unable to verify the
    // binding to the actual MySQL/GitHub/document snapshot.
    assert.equal(transformed.snapshotId, review.snapshotId);
    assert.deepEqual(ids(transformed.claims.map((claim) => claim.claimId)), ids(review.claims.map((claim) => claim.claimId)));
    assert.deepEqual(ids(transformed.evidence.map((entry) => entry.evidenceRef)), ids(review.evidence.map((entry) => entry.evidenceRef)));
    assert.deepEqual(
      transformed.claims.flatMap((claim) => claim.evidenceRefs).sort(),
      review.claims.flatMap((claim) => claim.evidenceRefs).sort(),
      `${relativePath} must preserve all claim-to-evidence links`,
    );
  }
});

test('V7 human Markdown has exactly the shared nine chapters and no duplicate H3 chapter headings', async () => {
  const api = requireTransformModule();
  const inputs = await readV6Reviews();

  for (const { relativePath, review } of inputs) {
    const transformed = api.transformV6Review(review, {
      targetSnapshotId: 'guanyijia-demo-content-v7-20260827',
    });
    assert.deepEqual(headings(transformed.markdown, 2), canonicalHeadings, `${relativePath} must retain the canonical H2 order`);
    assert.deepEqual(
      transformed.standardSections.map((section) => section.sectionId),
      canonicalSectionIds,
      `${relativePath} must retain explicit section IDs`,
    );
    assert.equal(transformed.standardSections.length, 9);
    assert.equal(
      canonicalHeadings.some((heading) => headings(transformed.markdown, 3).includes(heading)),
      false,
      `${relativePath} must not repeat a canonical H2 as an H3 claim title`,
    );
    assert.equal(
      transformed.claims.some((claim) => canonicalHeadings.includes(claim.title)),
      false,
      `${relativePath} claim titles must be meaningful and distinct from the chapter heading`,
    );
    assert.equal(
      transformed.standardSections.every((section) => section.claimIds.every((claimId) => transformed.claims.some((claim) => claim.claimId === claimId))),
      true,
      `${relativePath} section claim IDs must resolve`,
    );
  }
});

test('V7 human Markdown removes internal provenance labels while preserving V6 byte identity', async () => {
  const api = requireTransformModule();
  const inputs = await readV6Reviews();
  const forbidden = [
    'SOURCE_NATIVE',
    'DEMO_AUTHORED',
    'DERIVED_DEMO',
    'sourceId',
    'snapshotId',
    'contentOrigin',
    '[TRACE:',
    '<!--',
  ];

  for (const { relativePath, bytes, review } of inputs) {
    const beforeDigest = sha256(bytes);
    const transformed = api.transformV6Review(review, {
      targetSnapshotId: 'guanyijia-demo-content-v7-20260827',
    });
    assert.equal(sha256(bytes), beforeDigest, `${relativePath} V6 source bytes must remain unchanged`);
    assert.equal(forbidden.some((token) => transformed.markdown.includes(token)), false, `${relativePath} human Markdown leaks internal token`);
    assert.equal(transformed.markdown.includes('&amp;gt;'), false, `${relativePath} human Markdown leaks encoded HTML entity`);
  }
});

test('V7 inserts independently validated section narratives without changing claims or evidence', async () => {
  const api = requireTransformModule();
  const [{ review }] = await readV6Reviews();
  const sectionNarratives = Object.fromEntries(canonicalSectionIds.map((sectionId) => [
    sectionId,
    `这是 ${sectionId} 的补充阅读说明。它只解释已经保存的材料如何用于建模，不新增字段、规则或来源事实。`,
  ]));

  const transformed = api.transformV6Review(review, {
    targetSnapshotId: 'guanyijia-demo-content-v7-20260827',
    sectionNarratives,
  });

  for (const [sectionId, narrative] of Object.entries(sectionNarratives)) {
    assert.match(transformed.sections[sectionId], new RegExp(narrative.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&')));
  }
  assert.deepEqual(
    transformed.claims.flatMap((claim) => claim.evidenceRefs).sort(),
    review.claims.flatMap((claim) => claim.evidenceRefs).sort(),
    'narrative prose must not alter frozen claim-to-evidence links',
  );
});

test('V7 serializes every claim once while keeping section bodies limited to reader context', async () => {
  const api = requireTransformModule();
  const inputs = await readV6Reviews();

  for (const { relativePath, review } of inputs) {
    const transformed = api.transformV6Review(review, {
      targetSnapshotId: 'guanyijia-demo-content-v7-20260827',
    });

    for (const claim of transformed.claims) {
      const block = `### ${claim.title}\n\n${claim.statement}`;
      assert.equal(
        transformed.markdown.split(block).length - 1,
        1,
        `${relativePath} must serialize ${claim.claimId} once as its readable Markdown block`,
      );
      assert.equal(
        transformed.sections[claim.sectionId].includes(block),
        false,
        `${relativePath} section context must not duplicate ${claim.claimId}`,
      );
      assert.equal(
        transformed.traceLinks.some((trace) => (
          trace.claimId === claim.claimId && trace.markdownAnchor === claim.markdownAnchor
        )),
        true,
        `${relativePath} must retain a stable claim-to-anchor trace for ${claim.claimId}`,
      );
    }
  }
});

test('V7 transform preserves supplied reader prose without a length gate', async () => {
  const api = requireTransformModule();
  const [{ review }] = await readV6Reviews();
  const richOverview = '这是一段用于说明已保存材料如何支持后续建模阅读的补充说明。它只整理资料中已经明确的对象、活动、字段、关系和边界，不把技术线索延伸为未确认的生产规则。'.repeat(16);
  const sectionNarratives = Object.fromEntries(canonicalSectionIds.map((sectionId) => [
    sectionId,
    sectionId === 'OVERVIEW' ? richOverview : '补充阅读说明只解释当前已保存资料的范围与建模价值，不新增字段、规则或来源事实。',
  ]));

  const transformed = api.transformV6Review(review, {
    targetSnapshotId: 'guanyijia-demo-content-v7-20260827',
    sectionNarratives,
  });

  assert.match(transformed.sections.OVERVIEW, new RegExp(richOverview.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&')));
});

test('V7 puts the shared reading limitation only in the unresolved chapter', async () => {
  const api = requireTransformModule();
  const inputs = await readV6Reviews();

  for (const { relativePath, review } of inputs) {
    const transformed = api.transformV6Review(review, {
      targetSnapshotId: 'guanyijia-demo-content-v7-20260827',
    });
    const limitation = '资料缺口仍存在；限制是本章只能依据';
    assert.equal(
      (transformed.markdown.match(new RegExp(limitation, 'gu')) ?? []).length,
      1,
      `${relativePath} must not repeat the same limitation in every chapter`,
    );
    assert.match(
      transformed.sections.UNRESOLVED,
      new RegExp(limitation, 'u'),
      `${relativePath} must put the shared limitation in the unresolved chapter`,
    );
  }
});
