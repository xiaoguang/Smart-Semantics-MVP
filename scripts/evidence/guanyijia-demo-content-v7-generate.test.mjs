import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { cp, lstat, mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { standardSectionOrder } from '../../src/features/modeling-document-bridge/standard-markdown.ts';

let generator;
let generatorLoadError;
try {
  generator = await import('./guanyijia-demo-content-v7-generate.mjs');
} catch (error) {
  generatorLoadError = error;
}

const moduleDirectory = dirname(fileURLToPath(import.meta.url));
const prototypeRoot = resolve(moduleDirectory, '../..');
const workspaceRoot = resolve(prototypeRoot, '..');
const v6Root = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v6-20260826',
);
const v7SnapshotId = 'guanyijia-demo-content-v7-20260827';

function api() {
  assert.ok(generator, `V7 generator is required (load failed: ${generatorLoadError?.message ?? 'unknown'})`);
  for (const name of ['loadV6NarrativeDescriptors', 'createV7Candidate', 'validateV7Candidate', 'freezeV7Snapshot', 'validateV7Snapshot']) {
    assert.equal(typeof generator[name], 'function', `V7 generator must export ${name}`);
  }
  return generator;
}

function sourceOutput(descriptor, suffix = '') {
  const narrativeFor = (heading, key) => {
    const coverage = key === 'UNRESOLVED' ? 'GAP' : 'PARTIAL';
    const gap = coverage === 'GAP'
      ? '资料缺口：缺少能够确认最终业务含义的材料，限制是不能据此生成正式规则或指标；下一步由对应责任人回到正式材料核对。'
      : '';
    return `${gap}${heading}围绕本来源已保存的材料说明对象、活动、字段与关系的建模边界；未保存的运行结果、制度确认和跨来源判断仍需另行核对。`;
  };
  return JSON.stringify({
    schemaVersion: 1,
    sourceId: descriptor.sourceId,
    sections: standardSectionOrder.map(({ key, heading }) => ({
      sectionId: key,
      coverage: key === 'UNRESOLVED' ? 'GAP' : 'PARTIAL',
      narrative: `${narrativeFor(heading, key)}${suffix}`,
      ...(key === 'UNRESOLVED' ? {
        gap: {
          missing: '能够确认最终业务含义的正式材料',
          limitation: '不能据此生成正式规则或指标',
          nextStep: '由对应责任人回到正式材料核对',
        },
      } : {}),
    })),
  });
}

function recordsFor(descriptors) {
  return descriptors.map((descriptor, index) => ({
    sourceId: descriptor.sourceId,
    sessionId: `chatgpt-session-v7-${index + 1}`,
    rawOutput: sourceOutput(descriptor),
  }));
}

function reviewFor(result, sourceId) {
  const review = result.reviews.find((candidate) => candidate.descriptor?.sourceId === sourceId
    || candidate.sourceId === sourceId);
  assert.ok(review, `candidate review must include ${sourceId}`);
  return review;
}

function issueClasses(review) {
  assert.ok(Array.isArray(review.issues), `${review.descriptor?.sourceId ?? review.sourceId} must persist issues`);
  return review.issues.map((issue) => issue.class);
}

function persistedReviewFor(result, sourceId) {
  const record = result.candidate.reviews.find((candidate) => candidate.sourceId === sourceId);
  assert.ok(record, `candidate must retain ${sourceId} input`);
  assert.ok(record.review, `${sourceId} candidate input must retain its review`);
  return record.review;
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function canonicalJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
}

async function rewriteSnapshotIntegrity(root, manifest) {
  const unsignedManifest = {
    schemaVersion: manifest.schemaVersion,
    snapshotId: manifest.snapshotId,
    storyKey: manifest.storyKey,
    sources: manifest.sources,
    generationRuns: manifest.generationRuns,
    ...(Object.hasOwn(manifest, 'previousSnapshotId')
      ? { previousSnapshotId: manifest.previousSnapshotId }
      : {}),
  };
  manifest.contentSha256 = sha256(`${canonicalJson(unsignedManifest)}\n`);
  await writeFile(join(root, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  const artifacts = manifest.sources.flatMap((source) => source.artifacts);
  await writeFile(
    join(root, 'checksums.sha256'),
    `${artifacts.map((entry) => `${entry.sha256.slice(7)}  ${entry.path}`).join('\n')}\n`,
    'utf8',
  );
}

async function freezeFixture(targetRoot) {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const candidateRoot = join(dirname(targetRoot), 'v7-candidates');
  const selectionPath = join(dirname(targetRoot), 'v7-selection.json');
  const persisted = await Promise.all(recordsFor(descriptors).map((record) => implementation.persistV7SourceCandidate({
    descriptors,
    sourceId: record.sourceId,
    sessionId: record.sessionId,
    rawOutput: record.rawOutput,
    candidateRoot,
  })));
  await implementation.createV7Selection({
    descriptors,
    candidateRoot,
    selectionPath,
    candidates: Object.fromEntries(persisted.map(({ sourceId, candidateId }) => [sourceId, candidateId])),
  });
  await implementation.freezeV7Snapshot({
    sourceSnapshotRoot: v6Root,
    targetSnapshotRoot: targetRoot,
    selectionPath,
    candidateRoot,
  });
}

test('V7 candidate binds exactly five new Luna xhigh sessions to the V6 frozen reviews', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const candidate = implementation.createV7Candidate(descriptors, recordsFor(descriptors));
  const validated = implementation.validateV7Candidate(candidate, descriptors);

  assert.equal(validated.reviews.length, 5);
  assert.deepEqual(
    validated.reviews.map(({ descriptor }) => descriptor.sourceId),
    descriptors.map((descriptor) => descriptor.sourceId),
  );
  assert.equal(new Set(validated.reviews.map(({ generation }) => generation.sessionId)).size, 5);
  for (const review of validated.reviews) {
    assert.equal(review.status, 'READY');
    assert.deepEqual(issueClasses(review), []);
    assert.equal(review.generation.provider, 'CODEX_CHATGPT_SESSION');
    assert.equal(review.generation.model, 'gpt-5.6-luna');
    assert.equal(review.generation.reasoningEffort, 'xhigh');
    assert.match(
      review.generation.promptVersion,
      /^guanyijia-v7-reader-candidate-[a-z_]+-5$/u,
      'the richer V7 contract must have its own prompt version for reproducible generation metadata',
    );
    assert.deepEqual(review.narratives.map((section) => section.sectionId), standardSectionOrder.map(({ key }) => key));
    assert.deepEqual(
      review.narratives.map((section) => section.coverage),
      [...Array(8).fill('PARTIAL'), 'GAP'],
      `${review.descriptor.sourceId} must preserve an explicit coverage status for each chapter`,
    );
    const gap = review.narratives.find((section) => section.coverage === 'GAP');
    assert.deepEqual(gap.gap, {
      missing: '能够确认最终业务含义的正式材料',
      limitation: '不能据此生成正式规则或指标',
      nextStep: '由对应责任人回到正式材料核对',
    });
    assert.match(gap.narrative, /缺少：能够确认最终业务含义的正式材料\n限制：不能据此生成正式规则或指标\n下一步：由对应责任人回到正式材料核对/u);
  }
});

test('V7 candidate normalizes known transport vocabulary and persists a READY_WITH_WARNINGS review', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const records = recordsFor(descriptors);
  const invalid = JSON.parse(records[1].rawOutput);
  invalid.sections[0].narrative = 'SOURCE_NATIVE 是内部传输标识，不能出现在人类阅读内容中。';
  records[1].rawOutput = JSON.stringify(invalid);
  const candidate = implementation.createV7Candidate(descriptors, records);

  const validated = implementation.validateV7Candidate(candidate, descriptors);
  const review = reviewFor(validated, 'guanyijia_github');
  assert.equal(review.status, 'READY_WITH_WARNINGS');
  assert.ok(issueClasses(review).includes('NORMALIZED'));
  assert.match(review.narratives[0].narrative, /固定版本源码节选/u);
  assert.match(persistedReviewFor(validated, 'guanyijia_github').issues.map((issue) => issue.class).join(','), /NORMALIZED/u);
  assert.match(validated.candidate.reviews.find((record) => record.sourceId === 'guanyijia_github').rawOutput, /SOURCE_NATIVE/u);
});

test('V7 candidate accepts a concise readable chapter', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const records = recordsFor(descriptors);
  const invalid = JSON.parse(records[0].rawOutput);
  invalid.sections[0].narrative = '本章说明当前冻结资料能够支持的建模范围。';
  records[0].rawOutput = JSON.stringify(invalid);
  const candidate = implementation.createV7Candidate(descriptors, records);

  const validated = implementation.validateV7Candidate(candidate, descriptors);
  const review = reviewFor(validated, 'guanyijia_mysql');
  assert.equal(review.status, 'READY');
  assert.deepEqual(review.issues, []);
  assert.deepEqual(persistedReviewFor(validated, 'guanyijia_mysql').issues, []);
});

test('V7 candidate normalizes unordered chapters and extra fields as READY_WITH_WARNINGS', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const records = recordsFor(descriptors);
  const parsed = JSON.parse(records[0].rawOutput);
  parsed.sections = [...parsed.sections].reverse().map((section) => ({
    ...section,
    extra: 'transport metadata that is not part of the narrative contract',
  }));
  records[0].rawOutput = JSON.stringify(parsed);
  const candidate = implementation.createV7Candidate(descriptors, records);

  const validated = implementation.validateV7Candidate(candidate, descriptors);
  const review = reviewFor(validated, 'guanyijia_mysql');
  assert.equal(review.status, 'READY_WITH_WARNINGS');
  assert.ok(issueClasses(review).includes('NORMALIZED'));
  assert.deepEqual(
    review.narratives.map((section) => section.sectionId),
    standardSectionOrder.map(({ key }) => key),
  );
  assert.equal(review.narratives.some((section) => Object.hasOwn(section, 'extra')), false);
});

test('V7 candidate records invalid JSON, missing sections, duplicate sections, and malformed GAP details as BLOCKED', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const cases = [
    ['invalid JSON', (payload) => payload, (records) => { records[0].rawOutput = '{not-json'; }],
    ['missing canonical section', (payload) => { payload.sections.pop(); return payload; }],
    ['duplicate canonical section', (payload) => {
      payload.sections[1].sectionId = payload.sections[0].sectionId;
      return payload;
    }],
    ['empty GAP field', (payload) => {
      payload.sections.at(-1).gap.nextStep = '';
      return payload;
    }],
  ];

  for (const [label, mutate, mutateRecords] of cases) {
    const records = recordsFor(descriptors);
    if (mutateRecords) {
      mutateRecords(records);
    } else {
      const payload = JSON.parse(records[0].rawOutput);
      records[0].rawOutput = JSON.stringify(mutate(payload));
    }
    const candidate = implementation.createV7Candidate(descriptors, records);
    const validated = implementation.validateV7Candidate(candidate, descriptors);
    const review = reviewFor(validated, 'guanyijia_mysql');
    assert.equal(review.status, 'BLOCKED', label);
    assert.ok(issueClasses(review).includes('CRITICAL'), label);
    assert.equal(persistedReviewFor(validated, 'guanyijia_mysql').issues.some((issue) => issue.class === 'CRITICAL'), true, label);
  }
});

test('V7 candidate turns base V6 identity drift into a persisted CRITICAL block', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const candidate = implementation.createV7Candidate(descriptors, recordsFor(descriptors));
  candidate.sourceContentSha256 = 'sha256:base-identity-drift';

  const validated = implementation.validateV7Candidate(candidate, descriptors);
  assert.equal(validated.reviews.every((review) => review.status === 'BLOCKED'), true);
  assert.equal(validated.reviews.every((review) => issueClasses(review).includes('CRITICAL')), true);
});

test('V7 batch accepts unordered source candidates and returns the canonical source order', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const records = recordsFor(descriptors);
  const unordered = [records[4], records[2], records[0], records[3], records[1]];

  const candidate = implementation.createV7Candidate(descriptors, unordered);
  assert.deepEqual(
    candidate.reviews.map((record) => record.sourceId),
    descriptors.map((descriptor) => descriptor.sourceId),
  );
  const validated = implementation.validateV7Candidate(candidate, descriptors);
  assert.deepEqual(
    validated.reviews.map((review) => review.descriptor.sourceId),
    descriptors.map((descriptor) => descriptor.sourceId),
  );
});

test('V7 freeze blocks a batch with four ready sources without creating a target or discarding ready inputs', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const records = recordsFor(descriptors);
  const readyInputs = new Map(records.slice(0, 4).map((record) => [record.sourceId, record.rawOutput]));
  records[4].rawOutput = '{not-json';
  const candidate = implementation.createV7Candidate(descriptors, records);
  const reviewed = implementation.validateV7Candidate(candidate, descriptors);
  const ready = reviewed.reviews.filter((review) => review.status === 'READY' || review.status === 'READY_WITH_WARNINGS');
  assert.equal(ready.length, 4);
  for (const [sourceId, rawOutput] of readyInputs) {
    assert.equal(reviewed.candidate.reviews.find((record) => record.sourceId === sourceId).rawOutput, rawOutput);
  }

  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-blocked-batch-'));
  const targetRoot = join(temporaryRoot, v7SnapshotId);
  const candidateRoot = join(temporaryRoot, 'v7-candidates');
  const selectionPath = join(temporaryRoot, 'v7-selection.json');
  try {
    const persisted = await Promise.all(records.map((record) => implementation.persistV7SourceCandidate({
      descriptors,
      sourceId: record.sourceId,
      sessionId: record.sessionId,
      rawOutput: record.rawOutput,
      candidateRoot,
    })));
    await implementation.createV7Selection({
      descriptors,
      candidateRoot,
      selectionPath,
      candidates: Object.fromEntries(persisted.map(({ sourceId, candidateId }) => [sourceId, candidateId])),
    });
    await assert.rejects(
      () => implementation.freezeV7Snapshot({
        sourceSnapshotRoot: v6Root,
        targetSnapshotRoot: targetRoot,
        selectionPath,
        candidateRoot,
      }),
      /blocked|ready|candidate/i,
    );
    await assert.rejects(() => lstat(targetRoot), (error) => error?.code === 'ENOENT');
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 candidate records a GAP chapter missing a readable next step as BLOCKED', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const records = recordsFor(descriptors);
  const invalid = JSON.parse(records[2].rawOutput);
  const unresolved = invalid.sections.find((section) => section.sectionId === 'UNRESOLVED');
  unresolved.narrative = '本章节存在尚未确认的事项，需要由相关人员回到正式材料核对。';
  unresolved.gap = {
    missing: '正式业务材料',
    limitation: '不能用于建模',
    nextStep: '',
  };
  records[2].rawOutput = JSON.stringify(invalid);
  const candidate = implementation.createV7Candidate(descriptors, records);

  const validated = implementation.validateV7Candidate(candidate, descriptors);
  const review = reviewFor(validated, 'guanyijia_official_docs');
  assert.equal(review.status, 'BLOCKED');
  assert.ok(issueClasses(review).includes('CRITICAL'));
});

test('V7 candidate records missing structured GAP details as BLOCKED even when prose mentions the three concepts', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const records = recordsFor(descriptors);
  const invalid = JSON.parse(records[0].rawOutput);
  delete invalid.sections.at(-1).gap;
  records[0].rawOutput = JSON.stringify(invalid);
  const candidate = implementation.createV7Candidate(descriptors, records);

  const validated = implementation.validateV7Candidate(candidate, descriptors);
  const review = reviewFor(validated, 'guanyijia_mysql');
  assert.equal(review.status, 'BLOCKED');
  assert.ok(issueClasses(review).includes('CRITICAL'));
});

test('V7 candidate accepts a source with concise GAP narratives when every GAP has structured details', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const sparseDescriptors = structuredClone(descriptors);
  sparseDescriptors[1].review.claims = [];
  sparseDescriptors[1].review.markdown = '';
  const records = recordsFor(sparseDescriptors);
  const githubOutput = JSON.parse(records[1].rawOutput);
  for (const section of githubOutput.sections) {
    delete section.coverage;
    section.gap = {
      missing: '可确认本章节业务范围的冻结结论',
      limitation: '不能将本章节内容作为完整建模依据',
      nextStep: '补充保存并准入该章节的来源结论',
    };
  }
  records[1].rawOutput = JSON.stringify(githubOutput);
  const candidate = implementation.createV7Candidate(sparseDescriptors, records);

  const validated = implementation.validateV7Candidate(candidate, sparseDescriptors);
  const review = reviewFor(validated, 'guanyijia_github');
  assert.equal(review.status, 'READY');
});

test('V7 freeze is append-only and writes reader-oriented reviews without mutating V6', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-freeze-test-'));
  const targetRoot = join(temporaryRoot, v7SnapshotId);
  const candidateRoot = join(temporaryRoot, 'v7-candidates');
  const selectionPath = join(temporaryRoot, 'v7-selection.json');
  const v6ManifestBefore = await readFile(join(v6Root, 'manifest.json'), 'utf8');
  try {
    const persisted = await persistCandidateFixtures(implementation, descriptors, candidateRoot);
    await implementation.createV7Selection({
      descriptors,
      candidateRoot,
      selectionPath,
      candidates: Object.fromEntries(persisted.map(({ sourceId, candidateId }) => [sourceId, candidateId])),
    });
    const manifest = await implementation.freezeV7Snapshot({
      sourceSnapshotRoot: v6Root,
      targetSnapshotRoot: targetRoot,
      selectionPath,
      candidateRoot,
    });
    assert.equal(manifest.snapshotId, v7SnapshotId);
    assert.equal(manifest.previousSnapshotId, 'guanyijia-demo-content-v6-20260826');
    const validated = await implementation.validateV7Snapshot(targetRoot, { sourceSnapshotRoot: v6Root });
    assert.equal(validated.snapshotId, v7SnapshotId);
    const review = JSON.parse(await readFile(join(targetRoot, 'sources/github/standardized-review.json'), 'utf8'));
    assert.equal(review.markdown.includes('## 1. 文档说明'), true);
    assert.equal(review.markdown.includes('SOURCE_NATIVE'), false);
    assert.equal(await readFile(join(v6Root, 'manifest.json'), 'utf8'), v6ManifestBefore);
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 freeze rejects a target nested inside V6 before copying and leaves V6 untouched', async () => {
  const implementation = api();
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-overlap-test-'));
  const sourceCopy = join(temporaryRoot, 'v6-copy');
  const nestedTarget = join(sourceCopy, 'nested-v7-target');
  await cp(v6Root, sourceCopy, { recursive: true });
  const v6ManifestBefore = await readFile(join(sourceCopy, 'manifest.json'), 'utf8');
  try {
    const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: sourceCopy });
    const candidate = implementation.createV7Candidate(descriptors, recordsFor(descriptors));

    await assert.rejects(
      () => implementation.freezeV7Snapshot({
        sourceSnapshotRoot: sourceCopy,
        targetSnapshotRoot: nestedTarget,
        candidate,
      }),
      /source and target snapshot roots overlap/i,
    );
    await assert.rejects(() => lstat(nestedTarget), (error) => error?.code === 'ENOENT');
    assert.equal(await readFile(join(sourceCopy, 'manifest.json'), 'utf8'), v6ManifestBefore);
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 validation rejects a mutated inherited V6 artifact even when manifest integrity is rewritten', async () => {
  const implementation = api();
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-lineage-test-'));
  const targetRoot = join(temporaryRoot, v7SnapshotId);
  const inheritedPath = 'sources/official/系统边界、基础资料与角色.md';
  try {
    await freezeFixture(targetRoot);
    const original = await readFile(join(targetRoot, inheritedPath), 'utf8');
    await writeFile(join(targetRoot, inheritedPath), `${original}\n被篡改的继承资料。\n`, 'utf8');
    const manifest = JSON.parse(await readFile(join(targetRoot, 'manifest.json'), 'utf8'));
    const source = manifest.sources.find((entry) => entry.sourceId === 'guanyijia_official_docs');
    const artifact = source.artifacts.find((entry) => entry.path === inheritedPath);
    artifact.sha256 = sha256(await readFile(join(targetRoot, inheritedPath)));
    await rewriteSnapshotIntegrity(targetRoot, manifest);

    await assert.rejects(
      () => implementation.validateV7Snapshot(targetRoot, { sourceSnapshotRoot: v6Root }),
      /lineage|inherited|V6 artifact/i,
    );
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 validation rejects an injected artifact even when manifest integrity is rewritten', async () => {
  const implementation = api();
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-injected-artifact-test-'));
  const targetRoot = join(temporaryRoot, v7SnapshotId);
  const injectedPath = 'sources/github/injected-v7-artifact.json';
  try {
    await freezeFixture(targetRoot);
    await writeFile(join(targetRoot, injectedPath), '{"injected":true}\n', 'utf8');
    const manifest = JSON.parse(await readFile(join(targetRoot, 'manifest.json'), 'utf8'));
    const source = manifest.sources.find((entry) => entry.sourceId === 'guanyijia_github');
    source.artifacts.push({
      path: injectedPath,
      mediaType: 'application/json',
      sha256: sha256(await readFile(join(targetRoot, injectedPath))),
    });
    await rewriteSnapshotIntegrity(targetRoot, manifest);

    await assert.rejects(
      () => implementation.validateV7Snapshot(targetRoot, { sourceSnapshotRoot: v6Root }),
      /lineage|injected|unexpected artifact/i,
    );
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

function selectionApi() {
  const implementation = api();
  for (const name of ['persistV7SourceCandidate', 'createV7Selection', 'validateV7Selection']) {
    assert.equal(typeof implementation[name], 'function', `V7 generator must export ${name}`);
  }
  return implementation;
}

async function persistCandidateFixtures(implementation, descriptors, candidateRoot) {
  return Promise.all(descriptors.map((descriptor, index) => implementation.persistV7SourceCandidate({
    descriptors,
    sourceId: descriptor.sourceId,
    sessionId: `chatgpt-session-v7-selection-${index + 1}`,
    rawOutput: sourceOutput(descriptor),
    candidateRoot,
  })));
}

test('V7 selection is keyed by source ID and does not depend on candidate array position', async () => {
  const implementation = selectionApi();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-source-keyed-selection-'));
  try {
    const persisted = await persistCandidateFixtures(implementation, descriptors, temporaryRoot);
    const candidates = Object.fromEntries(
      [...persisted].reverse().map(({ sourceId, candidateId }) => [sourceId, candidateId]),
    );

    const selection = await implementation.createV7Selection({
      descriptors,
      candidateRoot: temporaryRoot,
      candidates,
    });

    assert.deepEqual(
      Object.keys(selection.candidates).sort(),
      descriptors.map(({ sourceId }) => sourceId).sort(),
      'the persisted selection must expose one candidate ID for each source ID',
    );
    assert.deepEqual(
      await implementation.validateV7Selection({
        descriptors,
        candidateRoot: temporaryRoot,
        selection,
      }).then(({ reviews }) => reviews.map((review) => review.descriptor.sourceId)),
      descriptors.map(({ sourceId }) => sourceId),
      'selection validation must restore canonical source order from source keys',
    );
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 candidate command assignments are source keyed and maintenance scripts use explicit source and selection commands', async () => {
  const implementation = selectionApi();
  assert.equal(typeof implementation.parseV7SourceCandidateAssignments, 'function');
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const candidateIds = Object.fromEntries(descriptors.map((descriptor, index) => [
    descriptor.sourceId,
    `v7-${descriptor.sourceId}-candidate-${String(index + 1).padStart(8, '0')}`,
  ]));
  const assignments = [...descriptors]
    .reverse()
    .map((descriptor) => `${descriptor.sourceId}=${candidateIds[descriptor.sourceId]}`);

  assert.deepEqual(
    implementation.parseV7SourceCandidateAssignments(assignments),
    candidateIds,
    'CLI selections must resolve candidates by source identity rather than positional order',
  );
  assert.throws(
    () => implementation.parseV7SourceCandidateAssignments([...assignments, assignments[0]]),
    /exactly one|duplicate|canonical/i,
  );

  const packageJson = JSON.parse(await readFile(join(prototypeRoot, 'package.json'), 'utf8'));
  assert.match(packageJson.scripts['content:candidates:generate'] ?? '', /--source$/u);
  assert.match(packageJson.scripts['content:candidates:check'] ?? '', /--selection$/u);
  assert.match(
    packageJson.scripts['evidence:guanyijia:content:candidates:generate'] ?? '',
    /--source$/u,
    'the explicit V7 candidate-generation maintenance command must be exposed',
  );
  assert.match(
    packageJson.scripts['evidence:guanyijia:content:candidates:check'] ?? '',
    /--selection$/u,
    'the explicit V7 candidate-selection validation command must be exposed',
  );
  assert.match(
    packageJson.scripts['evidence:guanyijia:content:candidates:selection'] ?? '',
    /--create-selection$/u,
    'the explicit source-keyed selection command must be exposed',
  );
  assert.match(
    packageJson.scripts['evidence:guanyijia:content:freeze'] ?? '',
    /--freeze$/u,
    'freeze must require an explicit --selection argument rather than bake a positional legacy flag into npm',
  );
  assert.doesNotMatch(
    packageJson.scripts['evidence:guanyijia:content:freeze'] ?? '',
    /--freeze-selection/u,
  );
  assert.deepEqual(
    implementation.parseV7FreezeArguments(['--selection', '/private/tmp/v7-selection.json']),
    { selectionPath: '/private/tmp/v7-selection.json' },
  );
  assert.deepEqual(
    implementation.parseV7FreezeArguments([
      '--selection',
      '/private/tmp/v7-selection.json',
      '--target',
      '/private/tmp/v7-snapshot',
    ]),
    {
      selectionPath: '/private/tmp/v7-selection.json',
      targetSnapshotRoot: '/private/tmp/v7-snapshot',
    },
  );
  assert.throws(
    () => implementation.parseV7FreezeArguments(['/private/tmp/v7-selection.json']),
    /--selection/i,
  );
});

test('V7 coverage is derived from admitted descriptor claims instead of model coverage labels', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const derivedDescriptors = structuredClone(descriptors);
  derivedDescriptors[0].review.claims = derivedDescriptors[0].review.claims
    .filter((claim) => claim.sectionId !== 'OVERVIEW');
  const records = recordsFor(derivedDescriptors);
  const output = JSON.parse(records[0].rawOutput);
  for (const section of output.sections) section.coverage = 'SUPPORTED';
  output.sections.find((section) => section.sectionId === 'GOAL').coverage = 'not-a-real-coverage';
  output.sections.find((section) => section.sectionId === 'OVERVIEW').gap = {
    missing: '已准入的概览结论',
    limitation: '不能将本章节作为完整建模背景',
    nextStep: '补充概览章节的冻结结论后重新生成',
  };
  records[0].rawOutput = JSON.stringify(output);

  const candidate = implementation.createV7Candidate(derivedDescriptors, records);
  const validated = implementation.validateV7Candidate(candidate, derivedDescriptors);
  const review = reviewFor(validated, 'guanyijia_mysql');

  assert.equal(review.status, 'READY_WITH_WARNINGS');
  assert.ok(issueClasses(review).includes('NORMALIZED'));
  assert.equal(review.narratives.find((section) => section.sectionId === 'OVERVIEW').coverage, 'GAP');
  assert.equal(review.narratives.find((section) => section.sectionId === 'GOAL').coverage, 'PARTIAL');
  assert.equal(review.narratives.find((section) => section.sectionId === 'UNRESOLVED').coverage, 'GAP');
});

test('V7 drops a gap object attached to a non-GAP chapter as a normalized warning', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const records = recordsFor(descriptors);
  const output = JSON.parse(records[0].rawOutput);
  output.sections[0].gap = {
    missing: '不应附着在已支持章节的字段',
    limitation: '不应改变章节覆盖状态',
    nextStep: '删除该字段后继续审阅',
  };
  records[0].rawOutput = JSON.stringify(output);

  const validated = implementation.validateV7Candidate(
    implementation.createV7Candidate(descriptors, records),
    descriptors,
  );
  const review = reviewFor(validated, 'guanyijia_mysql');

  assert.equal(review.status, 'READY_WITH_WARNINGS');
  assert.ok(issueClasses(review).includes('NORMALIZED'));
  const overview = review.narratives.find((section) => section.sectionId === 'OVERVIEW');
  assert.equal(Object.hasOwn(overview, 'gap'), false);
});

test('V7 missing snapshot validation reports an actionable V7 snapshot error', async () => {
  const implementation = api();
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-missing-snapshot-'));
  const missingRoot = join(temporaryRoot, 'does-not-exist');
  try {
    await assert.rejects(
      () => implementation.validateV7Snapshot(missingRoot),
      /V7.*snapshot.*(?:missing|unavailable|not found)|(?:missing|unavailable|not found).*V7.*snapshot/i,
    );
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 public freeze rejects an in-memory candidate or candidatePath without a persisted selection', async () => {
  const implementation = api();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const candidate = implementation.createV7Candidate(descriptors, recordsFor(descriptors));
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-selection-gate-'));
  const candidatePath = join(temporaryRoot, 'candidate.json');
  await writeFile(candidatePath, `${JSON.stringify(candidate)}\n`, 'utf8');
  try {
    for (const [label, bypass] of [
      ['in-memory candidate', { candidate }],
      ['candidate path', { candidatePath }],
    ]) {
      const targetRoot = join(temporaryRoot, label.replaceAll(' ', '-'));
      await assert.rejects(
        () => implementation.freezeV7Snapshot({
          sourceSnapshotRoot: v6Root,
          targetSnapshotRoot: targetRoot,
          ...bypass,
        }),
        /persisted.*selection|selection.*required|explicit.*selection/i,
        `${label} must not bypass persisted selection validation`,
      );
      await assert.rejects(() => lstat(targetRoot), (error) => error?.code === 'ENOENT');
    }
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

async function writableCodexStateFixture(implementation, temporaryRoot) {
  assert.equal(
    typeof implementation.preflightCodexStateWriteCapability,
    'function',
    'V7 generator must export preflightCodexStateWriteCapability',
  );
  const stateDirectoryPath = join(temporaryRoot, 'codex-state');
  const stateFilePath = join(stateDirectoryPath, 'state_5.sqlite');
  await mkdir(stateDirectoryPath, { recursive: true });
  await writeFile(stateFilePath, 'unchanged-state-bytes', 'utf8');
  return {
    stateDirectoryPath,
    stateFilePath,
    run: () => implementation.preflightCodexStateWriteCapability({
      stateDirectoryPath,
      stateFilePath,
    }),
  };
}

test('V7 records a passed state write preflight before one successful Codex generation call', async () => {
  const implementation = selectionApi();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-state-preflight-success-'));
  const candidateRoot = join(temporaryRoot, 'candidates');
  const state = await writableCodexStateFixture(implementation, temporaryRoot);
  let prepareCalls = 0;
  let generationCalls = 0;
  try {
    const generated = await implementation.regenerateV7SourceCandidate({
      descriptors,
      sourceId: 'guanyijia_mysql',
      candidateRoot,
      codexStatePreflight: state.run,
      codexSession: {
        async prepare() { prepareCalls += 1; },
        async generate({ descriptor }) {
          generationCalls += 1;
          return {
            sessionId: 'chatgpt-session-v7-preflight-success',
            rawOutput: sourceOutput(descriptor),
          };
        },
      },
    });

    assert.equal(prepareCalls, 1);
    assert.equal(generationCalls, 1);
    assert.equal(generated.receipt.outcome, 'COMPLETED');
    assert.deepEqual(generated.receipt.stateWritePreflight, {
      schemaVersion: 1,
      status: 'PASSED',
      stateDirectoryPath: state.stateDirectoryPath,
      stateFilePath: state.stateFilePath,
    });
    assert.equal(await readFile(state.stateFilePath, 'utf8'), 'unchanged-state-bytes');
    assert.deepEqual(await readdir(state.stateDirectoryPath), ['state_5.sqlite']);
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 blocks before prepare when the Codex state file is not writable and never retries generation', async () => {
  const implementation = selectionApi();
  assert.equal(typeof implementation.regenerateV7SourceCandidate, 'function');
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-state-preflight-blocked-'));
  const candidateRoot = join(temporaryRoot, 'candidates');
  const stateDirectoryPath = join(temporaryRoot, 'codex-state');
  const stateFilePath = join(stateDirectoryPath, 'state_5.sqlite');
  await mkdir(stateFilePath, { recursive: true });
  let prepareCalls = 0;
  let generationCalls = 0;
  let preflightCalls = 0;
  try {
    const blocked = await implementation.regenerateV7SourceCandidate({
      descriptors,
      sourceId: 'guanyijia_github',
      candidateRoot,
      codexStatePreflight: async () => {
        preflightCalls += 1;
        return implementation.preflightCodexStateWriteCapability({
          stateDirectoryPath,
          stateFilePath,
        });
      },
      codexSession: {
        async prepare() { prepareCalls += 1; },
        async generate() { generationCalls += 1; },
      },
    });

    assert.equal(preflightCalls, 1);
    assert.equal(prepareCalls, 0, 'login/session preparation must not run after a blocked state preflight');
    assert.equal(generationCalls, 0, 'generation must not run or retry after a blocked state preflight');
    assert.equal(blocked.receipt.outcome, 'FAILED');
    assert.equal(blocked.review.status, 'BLOCKED');
    assert.equal(blocked.receipt.stateWritePreflight.status, 'BLOCKED');
    assert.equal(blocked.receipt.stateWritePreflight.stateDirectoryPath, stateDirectoryPath);
    assert.equal(blocked.receipt.stateWritePreflight.stateFilePath, stateFilePath);
    assert.equal(blocked.receipt.stateWritePreflight.failure.capability, 'STATE_FILE_WRITE');
    assert.equal(blocked.receipt.stateWritePreflight.failure.path, stateFilePath);
    assert.match(blocked.receipt.failure.message, /Codex state write preflight.*state_5\.sqlite/u);
    assert.equal(await readFile(join(candidateRoot, blocked.candidateId, 'raw-output.txt'), 'utf8'), '');
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 state directory preflight failure is deterministic and omits transient probe names', async () => {
  const implementation = selectionApi();
  assert.equal(typeof implementation.preflightCodexStateWriteCapability, 'function');
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-state-preflight-deterministic-'));
  const missingStateDirectoryPath = join(temporaryRoot, 'missing-codex-state');
  try {
    const first = await implementation.preflightCodexStateWriteCapability({
      stateDirectoryPath: missingStateDirectoryPath,
      stateFilePath: null,
    });
    const second = await implementation.preflightCodexStateWriteCapability({
      stateDirectoryPath: missingStateDirectoryPath,
      stateFilePath: null,
    });

    assert.deepEqual(first, second);
    assert.equal(first.status, 'BLOCKED');
    assert.equal(first.failure.capability, 'STATE_DIRECTORY_WRITE');
    assert.equal(first.failure.path, missingStateDirectoryPath);
    assert.doesNotMatch(first.failure.message, /\.guanyijia-v7-state-preflight-/u);
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 persists a failed Codex source call as a blocked candidate so other selections remain recoverable', async () => {
  const implementation = selectionApi();
  assert.equal(typeof implementation.regenerateV7SourceCandidate, 'function');
  assert.equal(typeof implementation.loadV7SourceCandidate, 'function');
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-failed-source-candidate-'));
  const candidateRoot = join(temporaryRoot, 'candidates');
  const state = await writableCodexStateFixture(implementation, temporaryRoot);
  try {
    const failed = await implementation.regenerateV7SourceCandidate({
      descriptors,
      sourceId: 'guanyijia_github',
      candidateRoot,
      codexStatePreflight: state.run,
      codexSession: {
        async prepare() {},
        async generate() {
          throw new Error('simulated Codex nonzero exit');
        },
      },
    });

    assert.equal(failed.sourceId, 'guanyijia_github');
    assert.equal(failed.review.status, 'BLOCKED');
    assert.ok(failed.review.issues.some((issue) => issue.code === 'CODEX_SESSION_FAILED'));
    assert.equal(failed.receipt.outcome, 'FAILED');
    assert.equal(failed.receipt.stateWritePreflight.status, 'PASSED');
    assert.equal(
      failed.receipt.attempt.modelSessionStarted,
      false,
      'a Codex process error without thread.started must not consume a content round',
    );
    assert.match(failed.receipt.failure.message, /simulated Codex nonzero exit/u);

    const reloaded = await implementation.loadV7SourceCandidate({
      descriptors,
      candidateRoot,
      candidateId: failed.candidateId,
    });
    assert.equal(reloaded.review.status, 'BLOCKED');
    assert.ok(reloaded.review.issues.some((issue) => issue.code === 'CODEX_SESSION_FAILED'));
    assert.equal(await readFile(join(candidateRoot, failed.candidateId, 'raw-output.txt'), 'utf8'), '');
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 records a failed source call as consuming a round only when Codex reported thread.started', async () => {
  const implementation = selectionApi();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-started-source-failure-'));
  const candidateRoot = join(temporaryRoot, 'candidates');
  const state = await writableCodexStateFixture(implementation, temporaryRoot);
  try {
    const failure = new Error('simulated Codex failure after thread.started');
    failure.sessionId = 'chatgpt-session-v7-started-then-failed';
    const failed = await implementation.regenerateV7SourceCandidate({
      descriptors,
      sourceId: 'guanyijia_github',
      candidateRoot,
      codexStatePreflight: state.run,
      codexSession: {
        async prepare() {},
        async generate() { throw failure; },
      },
    });

    assert.equal(failed.receipt.outcome, 'FAILED');
    assert.equal(failed.receipt.attempt.modelSessionStarted, true);
    assert.equal(failed.receipt.generation.sessionId, 'chatgpt-session-v7-started-then-failed');
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 persists a failed ChatGPT login preflight as a blocked source candidate', async () => {
  const implementation = selectionApi();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-failed-preflight-candidate-'));
  const candidateRoot = join(temporaryRoot, 'candidates');
  const state = await writableCodexStateFixture(implementation, temporaryRoot);
  try {
    const failed = await implementation.regenerateV7SourceCandidate({
      descriptors,
      sourceId: 'guanyijia_mysql',
      candidateRoot,
      codexStatePreflight: state.run,
      codexSession: {
        async prepare() {
          throw new Error('simulated ChatGPT login preflight failure');
        },
        async generate() {
          throw new Error('generate must not run after failed preflight');
        },
      },
    });

    assert.equal(failed.sourceId, 'guanyijia_mysql');
    assert.equal(failed.receipt.outcome, 'FAILED');
    assert.equal(failed.review.status, 'BLOCKED');
    assert.equal(failed.receipt.stateWritePreflight.status, 'PASSED');
    assert.match(failed.receipt.failure.message, /simulated ChatGPT login preflight failure/u);
    assert.equal(await readFile(join(candidateRoot, failed.candidateId, 'raw-output.txt'), 'utf8'), '');
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 writes an explicit source-keyed selection atomically without leaving a staging directory', async () => {
  const implementation = selectionApi();
  const descriptors = await implementation.loadV6NarrativeDescriptors({ sourceSnapshotRoot: v6Root });
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-atomic-selection-'));
  const candidateRoot = join(temporaryRoot, 'candidates');
  const selectionPath = join(temporaryRoot, 'selection.json');
  try {
    const persisted = await persistCandidateFixtures(implementation, descriptors, candidateRoot);
    const selection = await implementation.createV7Selection({
      descriptors,
      candidateRoot,
      selectionPath,
      candidates: Object.fromEntries(persisted.map(({ sourceId, candidateId }) => [sourceId, candidateId])),
    });

    assert.deepEqual(JSON.parse(await readFile(selectionPath, 'utf8')).candidates, selection.candidates);
    assert.equal(
      (await readdir(temporaryRoot)).some((entry) => entry.includes('.selection.json.staging-')),
      false,
      'the sibling staging directory must be cleaned after atomic publication',
    );
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});
