import assert from 'node:assert/strict';
import test from 'node:test';
import { guanyijiaFrozenModelingArtifact } from '../modeling-document-bridge/guanyijia-modeling-baseline.ts';
import { compileModelingDocument } from '../modeling-document-bridge/compile-modeling-document.ts';
import {
  assertModelingEligibilityProjection,
  projectModelingEligibility,
} from './modeling-eligibility.ts';

test('缺口和待确认内容保留在交付投影中，但不会成为建模候选', () => {
  const projection = projectModelingEligibility(guanyijiaFrozenModelingArtifact);

  assertModelingEligibilityProjection(projection, guanyijiaFrozenModelingArtifact);
  assert.ok(projection.conclusions.some((item) => item.eligibility === 'EXCLUDED_GAP'));
  assert.ok(projection.conclusions.some((item) => item.eligibility === 'EXCLUDED_UNCERTAIN'));

  const compiled = compileModelingDocument(guanyijiaFrozenModelingArtifact, { eligibility: projection });
  assert.equal(compiled.items.some((item) => item.kind === 'PENDING_ASSET'), false);
  assert.equal(compiled.items.some((item) => projection.excludedCandidateIds.includes(item.candidateId)), false);
  assert.ok(compiled.items.length > 0);
});

test('资格投影被篡改时失败关闭，而不是把缺口送进建模', () => {
  const projection = projectModelingEligibility(guanyijiaFrozenModelingArtifact);
  const tampered = structuredClone(projection);
  tampered.excludedCandidateIds = [];

  assert.throws(
    () => assertModelingEligibilityProjection(tampered, guanyijiaFrozenModelingArtifact),
    /排除清单/u,
  );
});
