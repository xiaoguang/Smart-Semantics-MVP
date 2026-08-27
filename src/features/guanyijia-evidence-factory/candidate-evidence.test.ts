import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import test from 'node:test';

const candidateEvidence = await import('./candidate-evidence.ts');
const { compareCandidateClaims, readCandidateEvidenceBundle } = candidateEvidence;
const generatedEvidence = await import('./guanyijia-candidate-evidence.generated.ts');

type MutableGeneratedBundle = {
  fragments: Array<{
    evidenceRef: string;
    excerpt: string;
    locationValue: string;
  }>;
  claims: Array<{
    claimId: string;
    normalizedValue: string;
  }>;
  relations: Array<{
    leftClaimId: string;
    rightClaimId: string;
    relation: string;
  }>;
};

type MutableGeneratedIntegrity = {
  fragments: Array<{
    evidenceRef: string;
    excerptDigest: string;
    locationValue: string;
  }>;
};

const generatedBundle = generatedEvidence.generatedCandidateEvidence as unknown as MutableGeneratedBundle;
const generatedIntegrity = generatedEvidence.generatedCandidateEvidenceIntegrity as unknown as MutableGeneratedIntegrity;

function digest(text: string): string {
  return `sha256:${createHash('sha256').update(text, 'utf8').digest('hex')}`;
}

type CandidateClaim = {
  claimId: string;
  subjectRef: string;
  predicate: string;
  normalizedValue: string;
  scope: string;
  effectiveTime?: string;
  evidenceClass: 'OBSERVED' | 'FROZEN_RECORD' | 'GAP';
  evidenceRefs: string[];
};

function claim(overrides: Partial<CandidateClaim> = {}): CandidateClaim {
  return {
    claimId: 'claim-default',
    subjectRef: 'guanyijia:system-config',
    predicate: 'negative-stock',
    normalizedValue: 'disabled',
    scope: 'guanyijia',
    effectiveTime: '2026-08-13',
    evidenceClass: 'OBSERVED',
    evidenceRefs: ['evidence:default'],
    ...overrides,
  };
}

test('identical observed claims in the same scope and time corroborate', () => {
  const mysqlClaim = claim({
    claimId: 'mysql-negative-stock',
    evidenceRefs: ['mysql:system-config:minus-stock-flag'],
  });
  const independentlyObservedClaim = claim({
    claimId: 'other-observed-negative-stock',
    evidenceRefs: ['mysql:another-saved-ddl:minus-stock-flag'],
  });

  assert.equal(compareCandidateClaims(mysqlClaim, independentlyObservedClaim), 'CORROBORATES');
});

test('a frozen structural record complements rather than independently corroborates an observed claim', () => {
  const observedClaim = claim({
    claimId: 'mysql-negative-stock',
    evidenceRefs: ['mysql:system-config:minus-stock-flag'],
  });
  const frozenRecordClaim = claim({
    claimId: 'github-negative-stock',
    evidenceClass: 'FROZEN_RECORD',
    evidenceRefs: ['github:configuration-record:minus-stock-flag'],
  });

  assert.equal(compareCandidateClaims(observedClaim, frozenRecordClaim), 'COMPLEMENTS');
});

test('debt absence in deployed MySQL conflicts with debt fields in GitHub', () => {
  const mysqlClaim = claim({
    claimId: 'mysql-debt-fields',
    subjectRef: 'guanyijia:jsh-depot-head',
    predicate: 'debt-fields',
    normalizedValue: 'absent',
    evidenceRefs: ['mysql:jsh_depot_head:columns'],
  });
  const githubClaim = claim({
    claimId: 'github-debt-fields',
    subjectRef: 'guanyijia:jsh-depot-head',
    predicate: 'debt-fields',
    normalizedValue: 'debt,last_debt',
    evidenceClass: 'FROZEN_RECORD',
    evidenceRefs: ['github:migration:debt-fields'],
  });

  assert.equal(compareCandidateClaims(mysqlClaim, githubClaim), 'CONFLICTS');
});

test('current and historical status claims with different effective times drift temporally', () => {
  const currentClaim = claim({
    claimId: 'mysql-status-current',
    subjectRef: 'guanyijia:document-status',
    predicate: 'status-codes',
    normalizedValue: '0,1,2,3,9',
    effectiveTime: '2026-08-13',
    evidenceRefs: ['mysql:deployed-ddl:document-status'],
  });
  const historicalClaim = claim({
    claimId: 'github-status-historical',
    subjectRef: 'guanyijia:document-status',
    predicate: 'status-codes',
    normalizedValue: '0,1,2',
    effectiveTime: '2020-01-01',
    evidenceClass: 'FROZEN_RECORD',
    evidenceRefs: ['github:historical-record:document-status'],
  });

  assert.equal(compareCandidateClaims(currentClaim, historicalClaim), 'TEMPORAL_DRIFT');
});

test('subject or predicate mismatch takes precedence over scope and time differences', () => {
  const left = claim({
    subjectRef: 'guanyijia:system-config',
    predicate: 'negative-stock',
    scope: 'guanyijia:current',
    effectiveTime: '2026-08-13',
  });
  const right = claim({
    subjectRef: 'guanyijia:document-status',
    predicate: 'status-codes',
    scope: 'guanyijia:historical',
    effectiveTime: '2020-01-01',
  });

  assert.equal(compareCandidateClaims(left, right), 'COMPLEMENTS');
});

test('scope mismatch takes precedence when subject and predicate match', () => {
  const left = claim({
    subjectRef: 'guanyijia:system-config',
    predicate: 'negative-stock',
    scope: 'guanyijia:current',
    normalizedValue: 'enabled',
    effectiveTime: '2026-08-13',
  });
  const right = claim({
    subjectRef: 'guanyijia:system-config',
    predicate: 'negative-stock',
    scope: 'guanyijia:historical',
    normalizedValue: 'disabled',
    effectiveTime: '2020-01-01',
  });

  assert.equal(compareCandidateClaims(left, right), 'SCOPE_DIFFERENCE');
});

test('a claim without evidence or with GAP evidence is unsupported', () => {
  const gapClaim = claim({
    claimId: 'official-status-gap',
    subjectRef: 'guanyijia:official-document-status',
    predicate: 'status-9-semantics',
    normalizedValue: 'unknown',
    evidenceClass: 'GAP',
    evidenceRefs: [],
  });

  assert.equal(
    compareCandidateClaims(gapClaim, claim({ claimId: 'observed-status' })),
    'UNSUPPORTED',
  );
});

test('candidate bundle exposes safe evidence classes and each read is a clone', async () => {
  const first = await readCandidateEvidenceBundle();
  const second = await readCandidateEvidenceBundle();
  const firstFragments = first.fragments;
  const secondFragments = second.fragments;

  assert.deepEqual(
    [...new Set(firstFragments.map((fragment) => fragment.evidenceClass))].sort(),
    ['FROZEN_RECORD', 'GAP', 'OBSERVED'],
  );
  assert.ok(firstFragments.length > 0, 'the candidate bundle must expose user-facing fragments');
  assert.ok(firstFragments.every((fragment) =>
    !/sample|profile|Tenant 153|FROZEN_FILE|REAL|PRIMARY/i.test(fragment.excerpt)),
  );

  assert.notStrictEqual(first, second);
  assert.notStrictEqual(firstFragments[0], secondFragments[0]);
  const secondExcerpt = secondFragments[0]!.excerpt;
  firstFragments[0]!.excerpt = 'mutated test copy';
  assert.equal(secondFragments[0]!.excerpt, secondExcerpt);

  const reread = await readCandidateEvidenceBundle();
  assert.equal(reread.fragments[0]!.excerpt, secondExcerpt);
});

test('returned bundle contains no forbidden public text and keeps disclosure labels honest', async () => {
  const bundle = await readCandidateEvidenceBundle();
  const publicBundleText = JSON.stringify(bundle);

  assert.doesNotMatch(
    publicBundleText,
    /sample|profile|Tenant 153|FROZEN_FILE|\bREAL\b|\bPRIMARY\b/i,
  );

  const frozenRecords = bundle.fragments.filter((fragment) => fragment.evidenceClass === 'FROZEN_RECORD');
  assert.ok(frozenRecords.length > 0, 'the bundle must retain frozen-record disclosures');
  assert.ok(frozenRecords.every((fragment) =>
    fragment.sourceName.includes('冻结结构化记录')
    && fragment.sourceName.includes('未保留完整原文'),
  ));

  const gaps = bundle.fragments.filter((fragment) => fragment.evidenceClass === 'GAP');
  assert.equal(gaps.length, 1);
  assert.match(gaps[0]!.excerpt, /完整官方文本未被保留/);
  assert.match(gaps[0]!.excerpt, /不以任何引文补全/);
});

test('rejects a post-import wrong locator even when its paired integrity record is changed', () => {
  const fragment = generatedBundle.fragments.find((candidate) => candidate.evidenceRef === 'mysql:jsh_system_config:minus_stock_flag');
  const integrity = generatedIntegrity.fragments.find((candidate) => candidate.evidenceRef === 'mysql:jsh_system_config:minus_stock_flag');
  assert.ok(fragment);
  assert.ok(integrity);
  const originalFragmentLocator = fragment.locationValue;
  const originalIntegrityLocator = integrity.locationValue;

  try {
    fragment.locationValue = 'ddl/tables/jsh_system_config.sql:L999';
    integrity.locationValue = 'ddl/tables/jsh_system_config.sql:L999';
    assert.throws(() => readCandidateEvidenceBundle(), /候选证据快照不可用/);
  } finally {
    fragment.locationValue = originalFragmentLocator;
    integrity.locationValue = originalIntegrityLocator;
  }
});

test('rejects a post-import excerpt corruption even when its paired digest is changed', () => {
  const fragment = generatedBundle.fragments.find((candidate) => candidate.evidenceRef === 'mysql:jsh_system_config:minus_stock_flag');
  const integrity = generatedIntegrity.fragments.find((candidate) => candidate.evidenceRef === 'mysql:jsh_system_config:minus_stock_flag');
  assert.ok(fragment);
  assert.ok(integrity);
  const originalExcerpt = fragment.excerpt;
  const originalExcerptDigest = integrity.excerptDigest;

  try {
    fragment.excerpt = `${originalExcerpt}\ncorrupted evidence`;
    integrity.excerptDigest = digest(fragment.excerpt);
    assert.throws(() => readCandidateEvidenceBundle(), /候选证据快照不可用/);
  } finally {
    fragment.excerpt = originalExcerpt;
    integrity.excerptDigest = originalExcerptDigest;
  }
});

test('rejects post-import semantic claim and relation changes', () => {
  const claimToMutate = generatedBundle.claims.find((candidate) => candidate.claimId === 'github-debt-fields-present');
  const relationToMutate = generatedBundle.relations.find((candidate) =>
    candidate.leftClaimId === 'mysql-debt-fields-absent'
    && candidate.rightClaimId === 'github-debt-fields-present',
  );
  assert.ok(claimToMutate);
  assert.ok(relationToMutate);
  const originalNormalizedValue = claimToMutate.normalizedValue;
  const originalRelation = relationToMutate.relation;

  try {
    claimToMutate.normalizedValue = 'absent';
    relationToMutate.relation = 'CORROBORATES';
    assert.throws(() => readCandidateEvidenceBundle(), /候选证据快照不可用/);
  } finally {
    claimToMutate.normalizedValue = originalNormalizedValue;
    relationToMutate.relation = originalRelation;
  }
});
