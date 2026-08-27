import assert from 'node:assert/strict';
import test from 'node:test';
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import { createEvidenceFactory } from './runtime.ts';
import type {
  FrozenDemoEvidenceBundle,
  PublicationReceipt,
  PublishedRawManifestAttestation,
  Sha256,
  TrustedPublicationRegistry,
} from './types.ts';

const storyKey = 'guanyijia-five-source-v2';
const sourceId = 'guanyijia_mysql';
const snapshotId = 'mysql-snapshot-v2';
const observedEvidenceRef = 'evidence:observed-1';
const missingEvidenceRef = 'evidence:missing-fragment';
const generatedEvidenceRef = 'target:generated-policy';
const blockId = 'block:observed-object';
const markdownAnchor = 'markdown-anchor:object-observed';

type MutableFragment = FrozenDemoEvidenceBundle['evidenceFragments'][number] & Record<string, unknown>;
type PublicEvidenceBundle = FrozenDemoEvidenceBundle;
type MutablePublicBundle = PublicEvidenceBundle & Record<string, unknown>;

const publicationReceiptId = 'publication:guanyijia-evidence-v2:20260820-01';
const manifestRef = 'manifest:mysql:snapshot-v2';
const manifestSha256 = `sha256:${'b'.repeat(64)}` as Sha256;
const rawArtifactSha256 = `sha256:${'a'.repeat(64)}` as Sha256;
const redactionPolicyId = 'guanyijia-public-redaction';
const redactionPolicyVersion = 'guanyijia-public-redaction-v1';
const redactionPolicySha256 = `sha256:${'c'.repeat(64)}` as Sha256;
const sanitizerVersion = 'guanyijia-sanitizer-v1';
const approvalRef = 'approval:guanyijia-public-bundle-v2';
const approvedAt = '2026-08-20T00:00:00.000Z';

function bundleWithDigest(
  unsignedBundle: Omit<FrozenDemoEvidenceBundle, 'bundleSha256'>,
): FrozenDemoEvidenceBundle {
  return {
    ...unsignedBundle,
    bundleSha256: `sha256:${sha256HexSync(canonicalModelingJson(unsignedBundle))}`,
  };
}

function rawManifestAttestations(): PublishedRawManifestAttestation[] {
  return [{ manifestRef, sourceId, snapshotId, manifestSha256 }];
}

function rawManifestDigestFor(attestations: PublishedRawManifestAttestation[]): Sha256 {
  return `sha256:${sha256HexSync(canonicalModelingJson({
    schemaVersion: 1,
    manifests: attestations.map(({ manifestRef: currentManifestRef, sourceId: currentSourceId, snapshotId: currentSnapshotId, manifestSha256: currentManifestSha256 }) => ({
      manifestRef: currentManifestRef,
      sourceId: currentSourceId,
      snapshotId: currentSnapshotId,
      manifestSha256: currentManifestSha256,
    })),
  }))}`;
}

function locatorDigest(locator: unknown): Sha256 {
  return `sha256:${sha256HexSync(canonicalModelingJson(locator))}`;
}

function excerptDigest(excerpt: string): Sha256 {
  return `sha256:${sha256HexSync(excerpt)}`;
}

function buildBundleFixture(): FrozenDemoEvidenceBundle {
  const sourceIdentity = {
    sourceId,
    sourceName: '管伊佳部署数据库',
    sourceClass: 'REAL' as const,
    snapshotId,
    authority: 'PRIMARY' as const,
    lineageStatus: 'ROOT' as const,
    upstreamSourceIds: [],
  };
  const block = {
    blockId,
    section: 'OBJECT' as const,
    semanticKind: 'ENTITY' as const,
    stableCode: 'object:observed-customer',
    label: '观察到的往来单位对象',
    value: { normalized: 'CUSTOMER', text: '数据库中的往来单位对象。' },
    evidenceStatus: 'FACT' as const,
    evidenceRefs: [observedEvidenceRef],
    affectedObjectRefs: [],
  };
  const fragment = {
    evidenceRef: observedEvidenceRef,
    sourceId,
    snapshotId,
    evidenceKind: 'OBSERVED' as const,
    title: 'jsh_supplier 表定义',
    excerpt: 'CREATE TABLE `jsh_supplier` (...);',
    artifactRef: 'artifact:mysql-ddl:jsh_supplier',
    locator: { kind: 'SQL', schema: 'jsh_erp', object: 'jsh_supplier', symbol: 'TABLE' },
  } as unknown as FrozenDemoEvidenceBundle['evidenceFragments'][number];
  const markdown = `# 最小冻结编译\n\n<!-- ${markdownAnchor} -->\n\n观察到的往来单位对象。\n`;
  const compilation = {
    sourceId,
    sourceName: sourceIdentity.sourceName,
    sourceClass: sourceIdentity.sourceClass,
    snapshotId,
    authority: sourceIdentity.authority,
    readSummary: {
      summary: '用于验证证据引用闭合性的最小冻结编译。',
      objectCount: 1,
      evidenceCount: 1,
      objectCounts: { entities: 1 },
      versionRef: snapshotId,
      warnings: [],
    },
    sections: {
      OVERVIEW: '', GOAL: '', OBJECT: '观察到的往来单位对象', ACTIVITY: '',
      FIELD: '', RELATION: '', METRIC: '', QUESTION: '', UNRESOLVED: '',
    },
    blocks: [block],
    assertions: [{
      assertionId: blockId,
      section: 'OBJECT' as const,
      statement: '观察到的往来单位对象。',
      provenance: 'OBSERVED' as const,
      evidenceRefs: [observedEvidenceRef],
    }],
    markdown,
    markdownSha256: sha256HexSync(markdown),
    evidenceLocators: {
      [observedEvidenceRef]: { kind: 'SQL', schema: 'jsh_erp', object: 'jsh_supplier', symbol: 'TABLE' },
    },
    delta: { addedBlockIds: [blockId], changedBlockIds: [], addedGapIds: [] },
    introducedConflictIds: [],
    corroboratedConflictIds: [],
    lineageStatus: 'ROOT' as const,
    upstreamSourceIds: [],
  };

  return bundleWithDigest({
    schemaVersion: 1,
    storyKey,
    compilerVersion: 'evidence-factory-v1',
    publicationReceiptId,
    sourceIdentities: [sourceIdentity],
    compilations: [compilation],
    traceLinks: {
      [sourceId]: [{
        blockId,
        assertionId: blockId,
        section: 'OBJECT' as const,
        markdownAnchor,
        evidenceRefs: [observedEvidenceRef],
      }],
    },
    evidenceFragments: [fragment],
    generationRuns: [],
  } as unknown as Omit<FrozenDemoEvidenceBundle, 'bundleSha256'>);
}

function validBundle(): PublicEvidenceBundle {
  const mutable = structuredClone(buildBundleFixture()) as MutablePublicBundle;
  const fragment = mutable.evidenceFragments[0] as MutableFragment;
  mutable.rawManifestDigest = rawManifestDigestFor(rawManifestAttestations());
  mutable.artifactProvenance = [{
    artifactRef: fragment.artifactRef,
    sourceId,
    snapshotId,
    rawArtifactSha256,
  }];
  const { bundleSha256: _bundleSha256, ...unsignedBundle } = mutable;
  return bundleWithDigest(unsignedBundle as unknown as Omit<FrozenDemoEvidenceBundle, 'bundleSha256'>) as PublicEvidenceBundle;
}

function publicationReceiptFor(bundle: PublicEvidenceBundle, overrides: Partial<PublicationReceipt> = {}): PublicationReceipt {
  const rawManifests = rawManifestAttestations();
  const artifactMemberships = bundle.artifactProvenance.map((provenance) => ({
    artifactRef: provenance.artifactRef,
    manifestRef,
    rawArtifactSha256: provenance.rawArtifactSha256,
  }));
  const fragmentApprovals = bundle.evidenceFragments
    .filter((fragment): fragment is Extract<MutableFragment, { evidenceKind: 'OBSERVED' }> => fragment.evidenceKind === 'OBSERVED')
    .map((fragment) => ({
      evidenceRef: fragment.evidenceRef,
      artifactRef: fragment.artifactRef,
      locatorSha256: locatorDigest(fragment.locator),
      excerptSha256: excerptDigest(fragment.excerpt),
    }));
  const redactionApproval = {
    decision: 'APPROVED' as const,
    scope: 'ENTIRE_PUBLIC_BUNDLE' as const,
    approvedBundleSha256: bundle.bundleSha256,
    policyId: redactionPolicyId,
    policyVersion: redactionPolicyVersion,
    policySha256: redactionPolicySha256,
    sanitizerVersion,
    approvalRef,
    approvedAt,
  };
  const unsignedReceipt = {
    schemaVersion: 1 as const,
    receiptId: publicationReceiptId,
    storyKey: bundle.storyKey,
    rawManifests,
    rawManifestDigest: rawManifestDigestFor(rawManifests),
    artifactMemberships,
    fragmentApprovals,
    publicBundleSha256: bundle.bundleSha256,
    redactionApproval,
  };
  const receiptSha256 = `sha256:${sha256HexSync(canonicalModelingJson(unsignedReceipt))}` as Sha256;
  return {
    ...unsignedReceipt,
    receiptSha256,
    ...overrides,
  };
}

function refreshReceiptDigest(receipt: PublicationReceipt): PublicationReceipt {
  const { receiptSha256: _receiptSha256, ...unsignedReceipt } = receipt;
  return {
    ...unsignedReceipt,
    receiptSha256: `sha256:${sha256HexSync(canonicalModelingJson(unsignedReceipt))}` as Sha256,
  };
}

function trustedPublicationRegistryFor(bundles: readonly PublicEvidenceBundle[]): TrustedPublicationRegistry {
  return {
    schemaVersion: 1,
    registryVersion: 'trusted-publications-v1',
    publications: bundles.map((bundle) => {
      const receipt = publicationReceiptFor(bundle);
      return {
        storyKey: bundle.storyKey,
        receiptId: receipt.receiptId,
        receiptSha256: receipt.receiptSha256,
        bundleSha256: bundle.bundleSha256,
        rawManifestDigest: bundle.rawManifestDigest,
        redactionPolicySha256,
      };
    }),
  };
}

function trustedPublicationRegistryForReceipt(bundle: PublicEvidenceBundle, receipt: PublicationReceipt): TrustedPublicationRegistry {
  return {
    schemaVersion: 1,
    registryVersion: 'trusted-publications-v1',
    publications: [{
      storyKey: bundle.storyKey,
      receiptId: receipt.receiptId,
      receiptSha256: receipt.receiptSha256,
      bundleSha256: bundle.bundleSha256,
      rawManifestDigest: bundle.rawManifestDigest,
      redactionPolicySha256,
    }],
  };
}

function createTrustedFactory(
  bundles: readonly unknown[],
  trustedPublicationRegistry: TrustedPublicationRegistry = trustedPublicationRegistryFor(bundles as PublicEvidenceBundle[]),
  publicationReceipts: PublicationReceipt[] = (bundles as PublicEvidenceBundle[]).map((bundle) => publicationReceiptFor(bundle)),
) {
  return createEvidenceFactory({ bundles, publicationReceipts, trustedRegistry: trustedPublicationRegistry } as unknown as { bundles: readonly unknown[] });
}

function refreshPublicManifestDigest(bundle: MutablePublicBundle) {
  bundle.rawManifestDigest = rawManifestDigestFor(rawManifestAttestations());
}

function redigestBundle(bundle: MutablePublicBundle): PublicEvidenceBundle {
  const { bundleSha256: _bundleSha256, ...unsignedBundle } = bundle;
  return bundleWithDigest(unsignedBundle as unknown as Omit<FrozenDemoEvidenceBundle, 'bundleSha256'>) as PublicEvidenceBundle;
}

function mutatedPublicBundle(mutate: (bundle: MutablePublicBundle) => void): PublicEvidenceBundle {
  const mutable = structuredClone(validBundle()) as MutablePublicBundle;
  mutate(mutable);
  const { bundleSha256: _bundleSha256, ...unsignedBundle } = mutable;
  return bundleWithDigest(unsignedBundle as unknown as Omit<FrozenDemoEvidenceBundle, 'bundleSha256'>) as PublicEvidenceBundle;
}

function twoFragmentBundle(): PublicEvidenceBundle {
  const secondEvidenceRef = 'evidence:observed-2';
  const secondBlockId = 'block:observed-object-2';
  const secondMarkdownAnchor = 'markdown-anchor:object-observed-2';
  return mutatedPublicBundle((bundle) => {
    const compilation = bundle.compilations[0];
    const firstFragment = bundle.evidenceFragments[0] as MutableFragment;
    const firstLocator = { kind: 'FILE_LINES', path: 'src/example.ts', startLine: 10, endLine: 12 } as const;
    const secondLocator = { kind: 'FILE_LINES', path: 'src/example.ts', startLine: 30, endLine: 35 } as const;
    firstFragment.locator = firstLocator;
    compilation.evidenceLocators[observedEvidenceRef] = firstLocator;
    bundle.evidenceFragments.push({
      ...structuredClone(firstFragment),
      evidenceRef: secondEvidenceRef,
      title: 'jsh_supplier 表定义的另一处行范围',
      locator: secondLocator,
    } as unknown as FrozenDemoEvidenceBundle['evidenceFragments'][number]);

    const firstBlock = compilation.blocks[0];
    compilation.blocks.push({
      ...structuredClone(firstBlock),
      blockId: secondBlockId,
      stableCode: 'object:observed-customer-2',
      label: '观察到的往来单位对象（另一处行范围）',
      evidenceRefs: [secondEvidenceRef],
    });
    compilation.assertions.push({
      assertionId: secondBlockId,
      section: 'OBJECT',
      statement: '同一真实制品的另一处公开行范围支持该对象。',
      provenance: 'OBSERVED',
      evidenceRefs: [secondEvidenceRef],
    });
    compilation.evidenceLocators[secondEvidenceRef] = secondLocator;
    compilation.readSummary.objectCount = 2;
    compilation.readSummary.evidenceCount = 2;
    compilation.readSummary.objectCounts.entities = 2;
    compilation.markdown += `\n<!-- ${secondMarkdownAnchor} -->\n同一制品的另一处公开行范围。\n`;
    compilation.markdownSha256 = sha256HexSync(compilation.markdown);
    bundle.traceLinks[sourceId].push({
      blockId: secondBlockId,
      assertionId: secondBlockId,
      section: 'OBJECT',
      markdownAnchor: secondMarkdownAnchor,
      evidenceRefs: [secondEvidenceRef],
    });
    refreshPublicManifestDigest(bundle);
  });
}

function assertSnapshotRejected(
  bundle: FrozenDemoEvidenceBundle,
  message = /演示证据快照不可用/,
  trustedPublicationRegistry?: TrustedPublicationRegistry,
  publicationReceipts?: PublicationReceipt[],
) {
  const factory = createTrustedFactory([bundle], trustedPublicationRegistry, publicationReceipts);
  assert.throws(
    () => factory.readBundle({ storyKey }),
    (error: unknown) => {
      assert.match(String(error), message);
      return true;
    },
  );
}

function brokenBundle(): PublicEvidenceBundle {
  return mutatedPublicBundle((bundle) => {
    const compilation = bundle.compilations[0];
    compilation.blocks[0].evidenceRefs = [missingEvidenceRef];
    compilation.assertions[0].evidenceRefs = [missingEvidenceRef];
    bundle.traceLinks[sourceId][0].evidenceRefs = [missingEvidenceRef];
  });
}

test('readBundle rejects an observed block trace that refers to a missing EvidenceFragment', () => {
  const factory = createTrustedFactory([brokenBundle()]);

  assert.throws(
    () => factory.readBundle({ storyKey }),
    (error: unknown) => {
      assert.match(String(error), /evidence fragment|证据片段/i);
      assert.match(String(error), new RegExp(missingEvidenceRef.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
      return true;
    },
  );
});

test('readBundle requires every compilation block and assertion to be trace-covered', () => {
  assertSnapshotRejected(mutatedPublicBundle((bundle) => {
    bundle.traceLinks[sourceId] = [];
  }), /trace|覆盖|block|assertion/i);
});

test('readBundle requires each trace Markdown anchor to occur in its compilation Markdown', () => {
  assertSnapshotRejected(mutatedPublicBundle((bundle) => {
    bundle.traceLinks[sourceId][0].markdownAnchor = 'markdown-anchor:not-present';
  }), /anchor|Markdown|锚点/i);
});

test('readBundle rejects FACT evidence that cites GENERATED_TARGET and observed fragments without raw provenance', () => {
  assertSnapshotRejected(mutatedPublicBundle((bundle) => {
    const compilation = bundle.compilations[0];
    compilation.blocks[0].evidenceStatus = 'FACT';
    compilation.blocks[0].evidenceRefs = [generatedEvidenceRef];
    compilation.assertions[0].provenance = 'OBSERVED';
    compilation.assertions[0].evidenceRefs = [generatedEvidenceRef];
    bundle.traceLinks[sourceId][0].evidenceRefs = [generatedEvidenceRef];
    bundle.evidenceFragments = [{
      evidenceRef: generatedEvidenceRef,
      sourceId: '',
      snapshotId: '',
      evidenceKind: 'GENERATED_TARGET',
      title: '待确认的生成目标',
      excerpt: '这是目标提案，不是观察到的生产事实。',
      locator: { kind: 'GENERATED_TARGET', confirmationStatus: 'PENDING_HUMAN_CONFIRMATION' },
    } as unknown as FrozenDemoEvidenceBundle['evidenceFragments'][number]];
  }), /generated|target|FACT|OBSERVED|生成|观察/i);

  assertSnapshotRejected(mutatedPublicBundle((bundle) => {
    const fragment = bundle.evidenceFragments[0] as MutableFragment;
    fragment.artifactRef = '';
    fragment.locator = {};
  }), /artifactRef|locator|artifact.*raw|原始.*artifact|定位/i);
});

test('readBundle rejects non-generated INFERENCE/INFERRED traces with no evidence references', () => {
  assertSnapshotRejected(mutatedPublicBundle((bundle) => {
    const compilation = bundle.compilations[0];
    compilation.blocks[0].evidenceStatus = 'INFERENCE';
    compilation.blocks[0].evidenceRefs = [];
    compilation.assertions[0].provenance = 'INFERRED';
    compilation.assertions[0].evidenceRefs = [];
    bundle.traceLinks[sourceId][0].evidenceRefs = [];
  }), /INFERENCE|INFERRED|trace.*evidence|evidenceRefs|引用.*证据/i);
});

test('readBundle rejects a GENERATED_TARGET fragment from supporting a USER_CONFIRMED assertion', () => {
  assertSnapshotRejected(mutatedPublicBundle((bundle) => {
    const compilation = bundle.compilations[0];
    compilation.blocks[0].evidenceStatus = 'INFERENCE';
    compilation.blocks[0].evidenceRefs = [generatedEvidenceRef];
    compilation.assertions[0].provenance = 'USER_CONFIRMED';
    compilation.assertions[0].evidenceRefs = [generatedEvidenceRef];
    bundle.traceLinks[sourceId][0].evidenceRefs = [generatedEvidenceRef];
    bundle.evidenceFragments = [{
      evidenceRef: generatedEvidenceRef,
      evidenceKind: 'GENERATED_TARGET',
      title: '待确认的生成目标',
      excerpt: '这是目标提案，不是观察到的生产事实。',
      locator: { kind: 'GENERATED_TARGET', confirmationStatus: 'PENDING_HUMAN_CONFIRMATION' },
    } as unknown as FrozenDemoEvidenceBundle['evidenceFragments'][number]];
  }), /generated|target|confirmed|确认/i);
});

test('readBundle rejects a digest-valid private rawRow field on an observed Fragment', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const fragment = candidate.evidenceFragments[0] as MutableFragment;
    fragment.rawRow = { id: 153, supplier_name: 'private tenant data' };
  }), /rawRow|raw artifact|私有原始|unknown.*rawRow|未知.*rawRow/i);
});

test('readBundle rejects a digest-valid private rawRow nested in readSummary', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const summary = candidate.compilations[0].readSummary as unknown as Record<string, unknown>;
    summary.rawRow = { id: 153, supplier_name: 'private tenant data' };
  }), /rawRow|readSummary|summary.*rawRow|readSummary.*未知/i);
});

test('readBundle rejects a digest-valid private rawRow nested in block.value', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const block = candidate.compilations[0].blocks[0] as unknown as Record<string, unknown>;
    block.value = {
      ...(block.value as Record<string, unknown>),
      rawRow: { id: 153, supplier_name: 'private tenant data' },
    };
  }), /rawRow|block.*value|value.*rawRow/i);
});

test('readBundle rejects an explicit raw payload embedded in a Fragment excerpt', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const fragment = candidate.evidenceFragments[0] as MutableFragment;
    fragment.excerpt = '{"tenantId":153,"rawRow":{"supplier_name":"private tenant data"}}';
  }), /rawRow|tenantId|supplier_name|敏感.*载荷|原始.*摘录/i);
});

test('readBundle rejects GENERATED_TARGET fragments carrying source identity fields', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const compilation = candidate.compilations[0];
    compilation.blocks[0].evidenceStatus = 'GAP';
    compilation.blocks[0].evidenceRefs = [generatedEvidenceRef];
    compilation.assertions[0].provenance = 'INFERRED';
    compilation.assertions[0].evidenceRefs = [generatedEvidenceRef];
    candidate.traceLinks[sourceId][0].evidenceRefs = [generatedEvidenceRef];
    candidate.evidenceFragments = [{
      evidenceRef: generatedEvidenceRef,
      sourceId,
      snapshotId,
      evidenceKind: 'GENERATED_TARGET',
      title: '待确认的生成目标',
      excerpt: '这是目标提案，不是观察到的生产事实。',
      locator: { kind: 'GENERATED_TARGET', confirmationStatus: 'PENDING_HUMAN_CONFIRMATION' },
    } as unknown as FrozenDemoEvidenceBundle['evidenceFragments'][number]];
  }), /GENERATED_TARGET|sourceId|snapshotId|generated.*source|target.*source|生成.*来源/i);
});

test('readBundle rejects a GENERATED_TARGET trace that is not an explicit GAP', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const compilation = candidate.compilations[0];
    compilation.blocks[0].evidenceStatus = 'INFERENCE';
    compilation.blocks[0].evidenceRefs = [generatedEvidenceRef];
    compilation.assertions[0].provenance = 'INFERRED';
    compilation.assertions[0].evidenceRefs = [generatedEvidenceRef];
    candidate.traceLinks[sourceId][0].evidenceRefs = [generatedEvidenceRef];
    candidate.evidenceFragments = [{
      evidenceRef: generatedEvidenceRef,
      evidenceKind: 'GENERATED_TARGET',
      title: '待确认的生成目标',
      excerpt: '这是目标提案，不是观察到的生产事实。',
      locator: { kind: 'GENERATED_TARGET', confirmationStatus: 'PENDING_HUMAN_CONFIRMATION' },
    } as unknown as FrozenDemoEvidenceBundle['evidenceFragments'][number]];
  }), /generated|target|gap|GAP|待确认/i);
});

test('readBundle rejects an observed artifactRef that has no manifest/artifact provenance closure', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const fragment = candidate.evidenceFragments[0] as MutableFragment;
    fragment.artifactRef = 'artifact:does-not-exist';
  }), /artifactRef|artifact.*provenance|manifest.*artifact|rawArtifactSha256|制品.*引用/i);
});

test('readBundle accepts a Bundle with a closed public raw manifest and artifact provenance index', () => {
  const bundle = validBundle();
  const read = createTrustedFactory([bundle]).readBundle({ storyKey }) as PublicEvidenceBundle;

  assert.equal(read.rawManifestDigest, bundle.rawManifestDigest);
  assert.deepEqual(read.artifactProvenance, bundle.artifactProvenance);
});

test('readBundle rejects unknown top-level private fields without weakening public provenance strictness', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    candidate.rawRow = { id: 153, supplier_name: 'private tenant data' };
  }), /rawRow|未知|private|私有/i);
});

test('readBundle rejects an artifactRef that is absent from the public provenance index', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const fragment = candidate.evidenceFragments[0] as MutableFragment;
    fragment.artifactRef = 'artifact:does-not-exist';
  }), /artifactProvenance|artifactRef|provenance.*artifact|artifact.*provenance|制品.*来源|索引.*制品/i);
});

test('readBundle rejects public artifact provenance with a mismatched sourceId', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    candidate.artifactProvenance[0].sourceId = 'guanyijia_other_source';
  }), /artifactProvenance|artifact.*source|provenance.*source|来源.*制品|来源.*artifact/i);
});

test('readBundle rejects public artifact provenance with a mismatched snapshotId', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    candidate.artifactProvenance[0].snapshotId = 'mysql-snapshot-wrong';
  }), /artifactProvenance|artifact.*snapshot|provenance.*snapshot|快照.*制品|快照.*artifact/i);
});

test('readBundle rejects public artifact provenance with a mismatched raw artifact digest', () => {
  const valid = validBundle();
  const mutated = mutatedPublicBundle((candidate) => {
    candidate.artifactProvenance[0].rawArtifactSha256 = `sha256:${'f'.repeat(64)}`;
  });
  assertSnapshotRejected(
    mutated,
    /BUNDLE_DIGEST_MISMATCH|artifactProvenance|rawArtifactSha256|artifact.*digest|provenance.*digest|raw.*manifest|artifact.*membership|原始.*摘要|制品.*摘要|制品.*成员/i,
    trustedPublicationRegistryFor([valid]),
    [publicationReceiptFor(valid)],
  );
});

test('readBundle exposes only the restricted normalized/text shape for a public block value', () => {
  const read = createTrustedFactory([validBundle()]).readBundle({ storyKey });
  const value = read.compilations[0].blocks[0].value as Record<string, unknown>;

  assert.deepEqual(Object.keys(value).sort(), ['normalized', 'text']);
  assert.equal(value.normalized, 'CUSTOMER');
  assert.equal(value.text, '数据库中的往来单位对象。');
});

test('readBundle rejects a compilation block without its public label', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const block = candidate.compilations[0].blocks[0] as unknown as Record<string, unknown>;
    delete block.label;
  }), /label|block.*label|block.*标签/i);
});

test('readBundle rejects a compilation with an incorrect markdownSha256', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    candidate.compilations[0].markdownSha256 = sha256HexSync('tampered markdown');
  }), /markdown|sha|校验/i);
});

test('readBundle rejects a compilation whose sections are not all nine required sections', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    const sections = candidate.compilations[0].sections as unknown as Record<string, unknown>;
    delete sections.UNRESOLVED;
  }), /section|sections|章节|UNRESOLVED/i);
});

test('readBundle rejects an empty readSummary object', () => {
  assertSnapshotRejected(mutatedPublicBundle((candidate) => {
    candidate.compilations[0].readSummary = {} as typeof candidate.compilations[0]['readSummary'];
  }), /readSummary|summary.*object|objectCount|摘要.*结构/i);
});

test('readBundle maps a null Bundle to an evidence-snapshot error instead of TypeError', () => {
  const factory = createEvidenceFactory({
    bundles: [null as unknown as FrozenDemoEvidenceBundle],
    publicationReceipts: [],
    trustedRegistry: { schemaVersion: 1, registryVersion: 'trusted-publications-v1', publications: [] },
  } as unknown as { bundles: readonly unknown[] });

  assert.throws(
    () => factory.readBundle({ storyKey }),
    (error: unknown) => {
      assert.match(String(error), /evidenceFragments|fragment.*数组|证据片段.*数组/);
      assert.doesNotMatch(String(error), /^TypeError:/);
      return true;
    },
  );
});

test('readBundle rejects UPSTREAM_MISSING source identity lineage', () => {
  assertSnapshotRejected(mutatedPublicBundle((bundle) => {
    bundle.sourceIdentities[0].lineageStatus = 'UPSTREAM_MISSING' as never;
    bundle.compilations[0].lineageStatus = 'UPSTREAM_MISSING';
  }), /lineage|UPSTREAM_MISSING|来源|上游/i);
});

test('readFragment fails with an evidence-snapshot error when the fragment collection is corrupt', () => {
  const bundle = mutatedPublicBundle((candidate) => {
    candidate.evidenceFragments = null as unknown as MutablePublicBundle['evidenceFragments'];
  });
  const valid = validBundle();
  const factory = createTrustedFactory(
    [bundle],
    trustedPublicationRegistryFor([valid]),
    [publicationReceiptFor(valid)],
  );

  assert.throws(
    () => factory.readFragment({ evidenceRef: observedEvidenceRef }),
    (error: unknown) => {
      assert.match(String(error), /演示证据快照不可用/);
      assert.doesNotMatch(String(error), /^TypeError:/);
      return true;
    },
  );
});

test('readFragment accepts a valid FILE_LINES observed locator without a symbol', () => {
  const fileLinesLocator = { kind: 'FILE_LINES', path: 'src/example.ts', startLine: 10, endLine: 12 };
  const bundle = mutatedPublicBundle((candidate) => {
    const fragment = candidate.evidenceFragments[0] as MutableFragment;
    fragment.locator = fileLinesLocator;
    candidate.compilations[0].evidenceLocators[observedEvidenceRef] = fileLinesLocator;
    refreshPublicManifestDigest(candidate);
  });
  const factory = createTrustedFactory([bundle]);

  const fragment = factory.readFragment({ evidenceRef: observedEvidenceRef });
  assert.equal(fragment.evidenceKind, 'OBSERVED');
  assert.deepEqual(fragment.locator, fileLinesLocator);
  assert.equal('symbol' in fragment.locator, false);
});

test('readFragment returns an isolated clone', () => {
  const factory = createTrustedFactory([validBundle()]);
  const first = factory.readFragment({ evidenceRef: observedEvidenceRef });
  first.excerpt = 'caller mutation';

  const second = factory.readFragment({ evidenceRef: observedEvidenceRef });
  assert.equal(second.excerpt, 'CREATE TABLE `jsh_supplier` (...);');
});

test('readBundle accepts a Bundle only when its trusted publication receipt exactly matches', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);
  const read = createTrustedFactory([bundle], registry).readBundle({ storyKey }) as PublicEvidenceBundle;
  const receipt = publicationReceiptFor(bundle);

  assert.equal(read.storyKey, storyKey);
  assert.equal(read.publicationReceiptId, publicationReceiptId);
  assert.equal(registry.publications.length, 1);
  assert.deepEqual(registry.publications[0], {
    storyKey,
    receiptId: receipt.receiptId,
    receiptSha256: receipt.receiptSha256,
    bundleSha256: bundle.bundleSha256,
    rawManifestDigest: bundle.rawManifestDigest,
    redactionPolicySha256,
  });
});

test('readBundle rejects an anonymous Bundle without a trusted publication registry receipt', () => {
  const bundle = validBundle();
  assertSnapshotRejected(bundle, /publication|receipt|registry|发布|回执|登记/i, {
    schemaVersion: 1,
    registryVersion: 'trusted-publications-v1',
    publications: [],
  });
});

test('readBundle rejects a publication receipt with a missing or wrong redaction policy', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);
  const missingPolicy = refreshReceiptDigest(structuredClone(publicationReceiptFor(bundle)));
  delete (missingPolicy.redactionApproval as unknown as Record<string, unknown>).policyVersion;
  assertSnapshotRejected(bundle, /redaction|policy|publication|receipt|脱敏|发布|回执/i, registry, [missingPolicy]);

  const wrongPolicy = structuredClone(publicationReceiptFor(bundle));
  wrongPolicy.redactionApproval.policyVersion = 'guanyijia-public-redaction-v0';
  assertSnapshotRejected(bundle, /redaction|policy|publication|receipt|脱敏|发布|回执/i, registry, [refreshReceiptDigest(wrongPolicy)]);

  const wrongPolicyHash = structuredClone(publicationReceiptFor(bundle));
  wrongPolicyHash.redactionApproval.policySha256 = `sha256:${'f'.repeat(64)}`;
  assertSnapshotRejected(bundle, /redaction|policy|publication|receipt|脱敏|发布|回执/i, registry, [refreshReceiptDigest(wrongPolicyHash)]);
});

test('readBundle rejects an unapproved publication receipt or wrong receipt identity', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);
  const unapproved = structuredClone(publicationReceiptFor(bundle));
  unapproved.redactionApproval.decision = 'PENDING' as never;
  assertSnapshotRejected(bundle, /approved|decision|receipt|publication|批准|回执/i, registry, [refreshReceiptDigest(unapproved)]);

  const wrongReceiptId = structuredClone(publicationReceiptFor(bundle));
  wrongReceiptId.receiptId = 'publication:other-story:v9';
  assertSnapshotRejected(bundle, /receipt|publication|registry|回执|登记/i, registry, [refreshReceiptDigest(wrongReceiptId)]);
});

test('readBundle rejects a registered Bundle whose publication receipt is missing', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);

  assertSnapshotRejected(bundle, /PUBLICATION_RECEIPT_MISSING|missing.*receipt|receipt.*missing|缺少.*回执|找不到.*回执/i, registry, []);
});

test('readBundle rejects a digest-valid rewritten public Bundle kept under its old trusted receipt', () => {
  const original = validBundle();
  const oldRegistry = trustedPublicationRegistryFor([original]);
  const rewritten = mutatedPublicBundle((candidate) => {
    const fragment = candidate.evidenceFragments[0] as MutableFragment;
    fragment.excerpt = 'CREATE TABLE `jsh_supplier` (...); -- rewritten public projection';
    candidate.artifactProvenance[0].rawArtifactSha256 = `sha256:${'d'.repeat(64)}`;
    refreshPublicManifestDigest(candidate);
  });

  assertSnapshotRejected(rewritten, /publication|receipt|registry|trusted|发布|回执|登记/i, oldRegistry);
});

test('readBundle rejects a pseudo-raw string that bypasses content regex when no trusted receipt covers it', () => {
  const original = validBundle();
  const oldRegistry = trustedPublicationRegistryFor([original]);
  const pseudoRaw = mutatedPublicBundle((candidate) => {
    const fragment = candidate.evidenceFragments[0] as MutableFragment;
    fragment.excerpt = 'supplier_name=private tenant data; tenant 153; raw capture bypass';
    candidate.artifactProvenance[0].rawArtifactSha256 = `sha256:${'e'.repeat(64)}`;
    refreshPublicManifestDigest(candidate);
  });

  assertSnapshotRejected(pseudoRaw, /publication|receipt|registry|trusted|发布|回执|登记/i, oldRegistry);
});

test('readBundle accepts two distinct locators from one observed artifact receipt', () => {
  const bundle = twoFragmentBundle();
  const read = createTrustedFactory([bundle]).readBundle({ storyKey }) as PublicEvidenceBundle;

  assert.equal(read.artifactProvenance.length, 1);
  assert.equal(read.evidenceFragments.filter((fragment) => fragment.evidenceKind === 'OBSERVED').length, 2);
  assert.deepEqual(read.compilations[0].evidenceLocators[observedEvidenceRef], {
    kind: 'FILE_LINES', path: 'src/example.ts', startLine: 10, endLine: 12,
  });
  assert.deepEqual(read.compilations[0].evidenceLocators['evidence:observed-2'], {
    kind: 'FILE_LINES', path: 'src/example.ts', startLine: 30, endLine: 35,
  });
});

test('readBundle rejects a changed Fragment locator even when Bundle and receipt digests are recomputed', () => {
  const original = validBundle();
  const oldRegistry = trustedPublicationRegistryFor([original]);
  const changed = mutatedPublicBundle((candidate) => {
    const fragment = candidate.evidenceFragments[0] as MutableFragment;
    const locator = { kind: 'FILE_LINES', path: 'src/example.ts', startLine: 99, endLine: 101 };
    fragment.locator = locator;
    candidate.compilations[0].evidenceLocators[observedEvidenceRef] = locator;
    refreshPublicManifestDigest(candidate);
  });

  assertSnapshotRejected(changed, /fragment|locator|approval|publication|receipt|回执|定位/i, oldRegistry);
});

test('readBundle rejects a receipt whose artifact membership is not closed by the Bundle provenance', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);
  const receipt = structuredClone(publicationReceiptFor(bundle));
  receipt.artifactMemberships[0].rawArtifactSha256 = `sha256:${'f'.repeat(64)}`;

  assertSnapshotRejected(bundle, /artifact|membership|provenance|receipt|制品|来源|回执/i, registry, [refreshReceiptDigest(receipt)]);
});

test('readBundle rejects a receipt whose raw-manifest attestation is not closed by its digest', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);
  const receipt = structuredClone(publicationReceiptFor(bundle));
  receipt.rawManifests[0].manifestSha256 = `sha256:${'f'.repeat(64)}`;
  receipt.rawManifestDigest = rawManifestDigestFor(receipt.rawManifests);

  assertSnapshotRejected(bundle, /manifest|attestation|rawManifestDigest|receipt|清单|回执/i, registry, [refreshReceiptDigest(receipt)]);
});

test('readBundle rejects two raw-manifest attestations that reuse one manifestRef with conflicting identities', () => {
  const mutableBundle = structuredClone(validBundle()) as MutablePublicBundle;
  const conflictingAttestations: PublishedRawManifestAttestation[] = [
    {
      manifestRef,
      sourceId: 'guanyijia_aaa',
      snapshotId: 'mysql-snapshot-v1',
      manifestSha256: `sha256:${'d'.repeat(64)}`,
    },
    {
      manifestRef,
      sourceId,
      snapshotId,
      manifestSha256,
    },
  ];
  mutableBundle.rawManifestDigest = rawManifestDigestFor(conflictingAttestations);
  const bundle = redigestBundle(mutableBundle);
  const receipt = structuredClone(publicationReceiptFor(bundle));
  receipt.rawManifests = conflictingAttestations;
  receipt.rawManifestDigest = rawManifestDigestFor(conflictingAttestations);
  receipt.publicBundleSha256 = bundle.bundleSha256;
  receipt.redactionApproval.approvedBundleSha256 = bundle.bundleSha256;
  const candidateReceipt = refreshReceiptDigest(receipt);
  const candidateRegistry = trustedPublicationRegistryForReceipt(bundle, candidateReceipt);

  assertSnapshotRejected(
    bundle,
    /manifestRef|duplicate|ambiguous|manifest.*唯一|manifest.*重复|清单.*唯一|清单.*重复/i,
    candidateRegistry,
    [candidateReceipt],
  );
});

test('readBundle rejects a receipt with an unclosed or mismatched Fragment approval', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);
  const receipt = structuredClone(publicationReceiptFor(bundle));
  receipt.fragmentApprovals[0].excerptSha256 = `sha256:${'f'.repeat(64)}`;

  assertSnapshotRejected(bundle, /fragment|approval|excerpt|摘要|批准|回执/i, registry, [refreshReceiptDigest(receipt)]);
});

test('readBundle rejects replaying a publication receipt from another story', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);
  const receipt = structuredClone(publicationReceiptFor(bundle));
  receipt.storyKey = 'other-story';
  receipt.receiptId = 'publication:other-story:20260820-01';

  assertSnapshotRejected(bundle, /story|receipt|publication|replay|故事|回执|发布/i, registry, [refreshReceiptDigest(receipt)]);
});

test('readBundle rejects duplicate trusted publication receipts', () => {
  const bundle = validBundle();
  const registry = trustedPublicationRegistryFor([bundle]);
  registry.publications.push(structuredClone(registry.publications[0]));

  assertSnapshotRejected(bundle, /duplicate|receipt|registry|重复|回执|登记/i, registry);
});

test('readBundle rejects an observed fragment that is not closed by the unique artifact receipt', () => {
  const bundle = twoFragmentBundle() as MutablePublicBundle;
  const second = bundle.evidenceFragments[1] as MutableFragment;
  second.artifactRef = 'artifact:missing-receipt';
  refreshPublicManifestDigest(bundle);
  const { bundleSha256: _bundleSha256, ...unsignedBundle } = bundle;
  const unclosed = bundleWithDigest(unsignedBundle as unknown as Omit<FrozenDemoEvidenceBundle, 'bundleSha256'>) as PublicEvidenceBundle;

  assertSnapshotRejected(unclosed, /artifact|provenance|manifest|receipt|closure|制品|来源|回执|闭合/i);
});

test('readBundle rejects a tampered Bundle digest', () => {
  const bundle = validBundle() as MutablePublicBundle;
  bundle.bundleSha256 = `sha256:${'0'.repeat(64)}`;
  const factory = createTrustedFactory([bundle]);

  assert.throws(
    () => factory.readBundle({ storyKey }),
    (error: unknown) => {
      assert.match(String(error), /Bundle.*校验|bundleSha256|tampered|sha256/i);
      return true;
    },
  );
});
