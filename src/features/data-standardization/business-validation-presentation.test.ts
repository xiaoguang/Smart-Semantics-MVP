import assert from 'node:assert/strict';
import test from 'node:test';
import { presentValidationIssue } from './business-validation-presentation.ts';

test('validation feedback gives readers a business label without exposing an internal check code', () => {
  assert.deepEqual(presentValidationIssue({
    severity: 'BLOCKER',
    checkCode: 'INVALID_TARGET_VALUE',
    message: '目标值必须大于零',
  }), {
    label: '需要修正',
    tone: 'error',
    message: '目标值必须大于零',
  });

  assert.deepEqual(presentValidationIssue({
    severity: 'WARNING',
    checkCode: 'TARGET_RATIO_OUT_OF_RANGE',
    message: '比率建议在 0 到 1 之间',
  }), {
    label: '请确认',
    tone: 'warning',
    message: '比率建议在 0 到 1 之间',
  });
});
