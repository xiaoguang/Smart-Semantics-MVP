import {
  ReviewWindowError,
  type ReviewWindowPage,
  type ReviewWindowSummary,
} from './index.ts';
import type { ContentReference } from '../source-documents/types.ts';
import type { ReviewWindowStream } from './index.ts';

export type VerifiedStreamRecoveryTarget = {
  stream: ReviewWindowStream;
  stableKey?: string;
  contentRef: ContentReference;
  expectedSha256: string;
};

type RecoverySummaryReader = {
  readPage(): Promise<ReviewWindowPage>;
  readContent(input: {
    contentRef: NonNullable<ReviewWindowSummary['contentRef']>;
    expectedSha256: string;
  }): Promise<unknown>;
};

type VerifiedContentTarget = Pick<VerifiedStreamRecoveryTarget, 'contentRef' | 'expectedSha256'>;

/**
 * Ordinary navigation may read a different healthy body while a stream still
 * carries an earlier integrity failure. Only acknowledge success when the
 * verified body is exactly the recorded failure target; otherwise the sole
 * explicit recovery action remains responsible for clearing the failure.
 */
export function acknowledgeVerifiedStreamTarget(input: {
  recordedTarget?: VerifiedStreamRecoveryTarget;
  verifiedTarget?: VerifiedContentTarget;
  onVerified(target: VerifiedStreamRecoveryTarget): void;
}): boolean {
  const { recordedTarget, verifiedTarget } = input;
  if (!recordedTarget || !verifiedTarget
    || recordedTarget.contentRef !== verifiedTarget.contentRef
    || recordedTarget.expectedSha256 !== verifiedTarget.expectedSha256) return false;
  input.onVerified(recordedTarget);
  return true;
}

/**
 * A summary read is only a recovery checkpoint after one body from that
 * stream has completed the same verified read used by production consumers.
 * This seam keeps retry code from clearing an aggregate failure on index
 * availability alone.
 */
export async function readAndVerifyReviewWindowPage(
  reader: RecoverySummaryReader,
  target: VerifiedContentTarget | undefined,
): Promise<ReviewWindowPage> {
  if (!target?.contentRef || !target.expectedSha256) {
    throw new ReviewWindowError({
      code: 'STORE_UNAVAILABLE',
      message: '审阅窗口恢复缺少原失败正文引用，不能使用摘要首项代替',
      recoveryAction: 'RETRY',
    });
  }
  const page = await reader.readPage();
  const targetListed = page.items.some((item) => item.contentRef === target.contentRef
    && item.contentSha256 === target.expectedSha256);
  if (!targetListed) {
    throw new ReviewWindowError({
      code: 'STORE_UNAVAILABLE',
      message: '审阅窗口恢复未找到原失败正文引用，不能验证当前摘要页',
      recoveryAction: 'RETRY',
    });
  }
  await reader.readContent({ contentRef: target.contentRef, expectedSha256: target.expectedSha256 });
  return page;
}

export async function recoverVerifiedStreamTarget(input: {
  target?: VerifiedStreamRecoveryTarget;
  readContent(target: Pick<VerifiedStreamRecoveryTarget, 'contentRef' | 'expectedSha256'>): Promise<unknown>;
  onVerified(target: VerifiedStreamRecoveryTarget): void;
}): Promise<VerifiedStreamRecoveryTarget> {
  const target = input.target;
  if (!target?.contentRef || !target.expectedSha256) {
    throw new ReviewWindowError({
      code: 'STORE_UNAVAILABLE',
      message: '审阅流恢复缺少已记录的正文引用，不能清除失败状态',
      recoveryAction: 'RETRY',
    });
  }
  await input.readContent({
    contentRef: target.contentRef,
    expectedSha256: target.expectedSha256,
  });
  input.onVerified(target);
  return target;
}
