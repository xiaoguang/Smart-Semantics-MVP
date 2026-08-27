/**
 * Keeps storage and historical delivery contracts out of the reviewer-facing
 * surface. The original error is retained by the command/runtime audit trail.
 */
export function businessErrorMessage(cause: unknown, fallback: string) {
  const detail = cause instanceof Error ? cause.message.trim() : '';
  if (!detail) return fallback;
  if (/两层语义变化非零|zero.?delta|semantic.*delta/i.test(detail)) {
    return '标准化结果需要重新生成后再定版。';
  }
  if (/未决定.*差异|conflict.*unresolved|unresolved.*conflict/i.test(detail)) {
    return '还有来源差异未决定，请返回处理差异。';
  }
  if (/sha|fingerprint|content.?ref|sourceId|artifact|revision|block|assertion|proposal|locator|receipt|catalog|\bM4\b/i.test(detail)) {
    return fallback;
  }
  return detail;
}
