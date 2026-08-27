import assert from 'node:assert/strict';
import test from 'node:test';
import { presentCandidateEvidence, presentCandidateStatus } from './candidate-model-presentation.ts';

test('候选模型卡只向业务用户展示可读状态和来源材料数量', () => {
  assert.deepEqual(presentCandidateStatus('NEEDS_CONFIRMATION'), {
    label: '需要确认',
    color: 'orange',
  });
  assert.equal(presentCandidateEvidence(['mysql:table:jsh_account', 'github:mapper:account']), '已关联 2 处来源材料');
  assert.equal(presentCandidateEvidence([]), '尚未关联来源材料');
});
