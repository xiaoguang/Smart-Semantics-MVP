import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

import { standardSectionOrder } from '../../src/features/modeling-document-bridge/standard-markdown.ts';
import {
  codexInvocationArgs,
  createV7Selection,
  freezeV7Snapshot,
  ImmutablePromptConstraints,
  loadV7SourceCandidate,
  loadV6NarrativeDescriptors,
  persistLegacyV7SourceIdentityRemediation,
  persistV7SourceCandidate,
  reprojectLegacyV2SourceIdentity,
  reprojectLegacyV7SourceCandidate,
  refineV7Prompt,
  validateV7Snapshot,
  validatePromptRevisionProposal,
  validateV7Selection,
} from './guanyijia-demo-content-v7-generate.mjs';

const moduleDirectory = dirname(fileURLToPath(import.meta.url));

function v2Output(descriptor) {
  return JSON.stringify({
    schemaVersion: 2,
    chapters: standardSectionOrder.map(({ key, heading }) => {
      const claims = descriptor.review.claims.filter((claim) => claim.sectionId === key);
      return {
        sectionId: key,
        introduction: `${heading}说明本来源能够支持的业务理解范围。`,
        items: claims
          .filter((claim) => claim.kind !== 'GAP')
          .map((claim) => ({
            title: claim.title,
            explanation: '当前资料保存了这一结论，可作为后续建模理解的依据；具体运行情况仍需结合后续资料确认。',
            boundary: claim.boundary ?? null,
            claimIds: [claim.claimId],
          })),
        gaps: claims
          .filter((claim) => claim.kind === 'GAP')
          .map((claim) => ({
            missing: claim.statement.replaceAll('GAP', '待补充资料'),
            impact: claim.boundary || '当前资料不足以确认正式业务规则。',
            nextStep: '补充正式业务资料并由对应负责人确认。',
            claimIds: [claim.claimId],
          })),
      };
    }),
  });
}

test('the strict V7 response schema requires every item property, represents an absent boundary as null, and excludes source identity', async () => {
  const schema = JSON.parse(await readFile(join(moduleDirectory, 'schemas/guanyijia-demo-content-v7-narrative.schema.json'), 'utf8'));
  const item = schema.properties.chapters.items.properties.items.items;
  assert.ok(item.required.includes('boundary'));
  assert.deepEqual(item.properties.boundary.type, ['string', 'null']);
  assert.equal(schema.required.includes('sourceId'), false);
  assert.equal(Object.hasOwn(schema.properties, 'sourceId'), false);
});

test('V7 blocks model-supplied source identity during ordinary validation but permits only the explicit legacy projection seam', async () => {
  const descriptors = await loadV6NarrativeDescriptors();
  const descriptor = descriptors[1];
  const raw = JSON.parse(v2Output(descriptor));
  raw.sourceId = 'github-v5';
  const candidateRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-legacy-source-identity-'));
  try {
    const ordinary = await persistV7SourceCandidate({
      descriptors,
      sourceId: descriptor.sourceId,
      sessionId: 'chatgpt-session-v7-source-identity-ordinary',
      rawOutput: JSON.stringify(raw),
      candidateRoot,
      generationRound: 1,
    });
    assert.equal(ordinary.review.acceptance, 'FATAL');
    assert.ok(ordinary.review.issues.some((issue) => issue.code === 'MODEL_SOURCE_IDENTITY_FORBIDDEN'));

    const reprojected = reprojectLegacyV2SourceIdentity({
      descriptor,
      rawOutput: JSON.stringify(raw),
    });
    assert.equal(reprojected.sourceId, descriptor.sourceId);
    assert.equal(reprojected.status, 'READY_WITH_WARNINGS');
    assert.ok(reprojected.issues.some((issue) => issue.code === 'LEGACY_MODEL_SOURCE_ID_DROPPED'));
    assert.equal(reprojected.chapters.length, 9);

    const persistedReprojection = await reprojectLegacyV7SourceCandidate({
      descriptors,
      sourceId: descriptor.sourceId,
      parentCandidateId: ordinary.candidateId,
      candidateRoot,
    });
    assert.equal(persistedReprojection.parentCandidateId, ordinary.candidateId);
    assert.equal(persistedReprojection.sourceId, descriptor.sourceId);
    assert.equal(persistedReprojection.status, 'READY_WITH_WARNINGS');
    assert.equal(persistedReprojection.parentRawOutputSha256, ordinary.receipt.rawOutputSha256);

    const remediation = await persistLegacyV7SourceIdentityRemediation({
      descriptors,
      sourceId: descriptor.sourceId,
      parentCandidateId: ordinary.candidateId,
      candidateRoot,
    });
    assert.equal(remediation.sourceId, descriptor.sourceId);
    assert.equal(remediation.parentCandidateId, ordinary.candidateId);
    assert.equal(remediation.review.acceptance, 'REVIEWABLE_WITH_WARNINGS');
    const loaded = await loadV7SourceCandidate({
      descriptors,
      candidateId: remediation.candidateId,
      candidateRoot,
    });
    assert.equal(loaded.candidateId, remediation.candidateId);
    assert.equal(loaded.compatibility.kind, 'DROP_LEGACY_MODEL_SOURCE_IDENTITY');
    assert.equal(loaded.review.acceptance, 'REVIEWABLE_WITH_WARNINGS');

    const candidates = { [descriptor.sourceId]: remediation.candidateId };
    for (const other of descriptors.filter((candidate) => candidate.sourceId !== descriptor.sourceId)) {
      const persisted = await persistV7SourceCandidate({
        descriptors,
        sourceId: other.sourceId,
        sessionId: `chatgpt-session-v7-source-identity-${other.sourceId}`,
        rawOutput: v2Output(other),
        candidateRoot,
        generationRound: 1,
      });
      candidates[other.sourceId] = persisted.candidateId;
    }
    const selection = await createV7Selection({ descriptors, candidates, candidateRoot });
    const selectionValidation = await validateV7Selection({ descriptors, selection, candidateRoot });
    assert.equal(selectionValidation.reviews.find((review) => review.descriptor.sourceId === descriptor.sourceId)?.acceptance, 'REVIEWABLE_WITH_WARNINGS');
  } finally {
    await rm(candidateRoot, { recursive: true, force: true });
  }
});

test('V7 legacy source identity remediation accepts only a completed legacy receipt with a bound started session', async () => {
  const descriptors = await loadV6NarrativeDescriptors();
  const descriptor = descriptors[0];
  const raw = JSON.parse(v2Output(descriptor));
  raw.sourceId = 'mysql-v5';
  const candidateRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-legacy-started-session-'));
  try {
    const parent = await persistV7SourceCandidate({
      descriptors,
      sourceId: descriptor.sourceId,
      sessionId: 'chatgpt-session-v7-legacy-started-session',
      rawOutput: JSON.stringify(raw),
      candidateRoot,
      generationRound: 1,
    });
    const receiptPath = join(candidateRoot, parent.candidateId, 'receipt.json');
    const receipt = JSON.parse(await readFile(receiptPath, 'utf8'));
    delete receipt.attempt;
    await writeFile(receiptPath, `${JSON.stringify(receipt, null, 2)}\n`, 'utf8');

    const projection = await reprojectLegacyV7SourceCandidate({
      descriptors,
      sourceId: descriptor.sourceId,
      parentCandidateId: parent.candidateId,
      candidateRoot,
    });
    assert.equal(projection.status, 'READY_WITH_WARNINGS');

    const rejectedReceipt = { ...receipt, attempt: { modelSessionStarted: false } };
    await writeFile(receiptPath, `${JSON.stringify(rejectedReceipt, null, 2)}\n`, 'utf8');
    await assert.rejects(
      reprojectLegacyV7SourceCandidate({
        descriptors,
        sourceId: descriptor.sourceId,
        parentCandidateId: parent.candidateId,
        candidateRoot,
      }),
      /completed started source candidate/u,
    );
  } finally {
    await rm(candidateRoot, { recursive: true, force: true });
  }
});

test('V7 schema v2 maps every frozen Claim and Gap exactly once and records an ideal candidate', async () => {
  const descriptors = await loadV6NarrativeDescriptors();
  const descriptor = descriptors[0];
  const candidateRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-governance-'));
  try {
    const persisted = await persistV7SourceCandidate({
      descriptors,
      sourceId: descriptor.sourceId,
      sessionId: 'chatgpt-session-v7-schema-two',
      rawOutput: v2Output(descriptor),
      candidateRoot,
      generationRound: 1,
    });

    assert.equal(persisted.review.status, 'READY', JSON.stringify(persisted.review.issues));
    assert.equal(persisted.review.acceptance, 'IDEAL');
    assert.equal(persisted.receipt.lineage.generationRound, 1);
    assert.equal(persisted.normalized.schemaVersion, 2);
  } finally {
    await rm(candidateRoot, { recursive: true, force: true });
  }
});

test('a fatal Round 1 can receive one persisted Sol Ultra addendum that binds its exact findings', async () => {
  const descriptors = await loadV6NarrativeDescriptors();
  const descriptor = descriptors[0];
  const candidateRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-sol-revision-'));
  const preflight = {
    schemaVersion: 1,
    status: 'PASSED',
    stateDirectoryPath: '/private/tmp/codex-state',
    stateFilePath: null,
  };
  try {
    const parent = await persistV7SourceCandidate({
      descriptors,
      sourceId: descriptor.sourceId,
      sessionId: 'chatgpt-session-v7-fatal-parent',
      rawOutput: '{not json}',
      candidateRoot,
      generationRound: 1,
    });
    const findingIds = parent.review.issues
      .filter((issue) => issue.class === 'CRITICAL')
      .map((issue) => issue.code);
    let invocation;
    const revision = await refineV7Prompt({
      descriptors,
      sourceId: descriptor.sourceId,
      parentCandidateId: parent.candidateId,
      findingIds,
      candidateRoot,
      codexStatePreflight: async () => preflight,
      codexSession: {
        async prepare() {},
        async generate(input) {
          invocation = input.invocation;
          return {
            sessionId: 'chatgpt-session-v7-sol-revision',
            rawOutput: JSON.stringify({
              schemaVersion: 1,
              sourceId: descriptor.sourceId,
              basePromptVersion: parent.generation.promptVersion,
              parentCandidateId: parent.candidateId,
              fatalFindingIds: findingIds,
              correctiveDirectives: [{
                findingIds,
                instruction: '严格按 JSON Schema v2 输出九章，并逐一映射全部冻结 Claim 和资料缺口。',
                reason: '修复初稿无法解析的问题。',
              }],
              preservedConstraints: ImmutablePromptConstraints,
              nonGoals: ['不增加新的业务事实或证据。'],
            }),
          };
        },
      },
    });
    assert.ok(invocation.includes('gpt-5.6-sol'));
    assert.ok(invocation.includes('model_reasoning_effort="ultra"'));
    assert.equal(revision.proposal.parentCandidateId, parent.candidateId);
    assert.equal(revision.receipt.generation.model, 'gpt-5.6-sol');
    assert.equal(revision.receipt.generation.reasoningEffort, 'ultra');
    const roundTwo = await persistV7SourceCandidate({
      descriptors,
      sourceId: descriptor.sourceId,
      sessionId: 'chatgpt-session-v7-round-two',
      rawOutput: v2Output(descriptor),
      candidateRoot,
      generationRound: 2,
      parentCandidateId: parent.candidateId,
      promptRevision: revision.proposal,
    });
    assert.equal(roundTwo.review.acceptance, 'IDEAL');
    assert.deepEqual(roundTwo.receipt.lineage, {
      generationRound: 2,
      parentCandidateId: parent.candidateId,
      promptRevisionId: revision.proposal.promptRevisionId,
    });
  } finally {
    await rm(candidateRoot, { recursive: true, force: true });
  }
});

test('V7 freezes only the deterministic schema v2 reader structure and preserves it in the append-only snapshot', async () => {
  const descriptors = await loadV6NarrativeDescriptors();
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-schema-two-freeze-'));
  const candidateRoot = join(temporaryRoot, 'candidates');
  const selectionPath = join(temporaryRoot, 'selection.json');
  const targetSnapshotRoot = join(temporaryRoot, 'guanyijia-demo-content-v7-20260827');
  try {
    const persisted = [];
    for (const descriptor of descriptors) {
      persisted.push(await persistV7SourceCandidate({
        descriptors,
        sourceId: descriptor.sourceId,
        sessionId: `chatgpt-session-v7-schema-freeze-${persisted.length + 1}`,
        rawOutput: v2Output(descriptor),
        candidateRoot,
        generationRound: 1,
      }));
    }
    await createV7Selection({
      descriptors,
      candidateRoot,
      selectionPath,
      candidates: Object.fromEntries(persisted.map(({ sourceId, candidateId }) => [sourceId, candidateId])),
    });
    await freezeV7Snapshot({ candidateRoot, selectionPath, targetSnapshotRoot });
    const validated = await validateV7Snapshot(targetSnapshotRoot);
    assert.equal(validated.snapshotId, 'guanyijia-demo-content-v7-20260827');
    const artifact = JSON.parse(await readFile(join(targetSnapshotRoot, 'sources/mysql/v7-reader-narratives.json'), 'utf8'));
    assert.equal(artifact.schemaVersion, 2);
    assert.equal(artifact.chapters.length, 9);
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});

test('V7 rejects a third content candidate and a Round 2 candidate without its verified lineage', async () => {
  const descriptors = await loadV6NarrativeDescriptors();
  const descriptor = descriptors[0];
  const candidateRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-governance-'));
  try {
    await assert.rejects(
      () => persistV7SourceCandidate({
        descriptors,
        sourceId: descriptor.sourceId,
        sessionId: 'chatgpt-session-v7-round-three',
        rawOutput: v2Output(descriptor),
        candidateRoot,
        generationRound: 3,
      }),
      /Round 3|two content candidates/u,
    );
    await assert.rejects(
      () => persistV7SourceCandidate({
        descriptors,
        sourceId: descriptor.sourceId,
        sessionId: 'chatgpt-session-v7-round-two',
        rawOutput: v2Output(descriptor),
        candidateRoot,
        generationRound: 2,
      }),
      /Round 2.*parent|prompt revision/u,
    );
  } finally {
    await rm(candidateRoot, { recursive: true, force: true });
  }
});

test('V7 uses Luna xhigh for content and only accepts Sol revisions that preserve immutable constraints', () => {
  assert.ok(codexInvocationArgs('/private/tmp/v7-reader.json').includes('model_reasoning_effort="xhigh"'));
  assert.equal(typeof validatePromptRevisionProposal, 'function');
  assert.throws(
    () => validatePromptRevisionProposal({
      schemaVersion: 1,
      sourceId: 'guanyijia_mysql',
      basePromptVersion: 'guanyijia-v7-reader-guanyijia_mysql-5',
      parentCandidateId: 'v7-guanyijia_mysql-parent-candidate',
      fatalFindingIds: ['MISSING_CLAIM'],
      correctiveDirectives: [],
      preservedConstraints: [],
      nonGoals: [],
    }),
    /fatal finding|immutable|corrective directive/u,
  );
});
