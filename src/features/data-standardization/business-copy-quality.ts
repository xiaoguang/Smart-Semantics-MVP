export type BusinessCopyRole = 'heading' | 'paragraph';

export type BusinessCopyEntry = {
  value: string;
  role: BusinessCopyRole;
  /** A heading must add information instead of echoing its body. */
  pairedValue?: string;
};

export type BusinessCopyIssue = {
  kind: 'MALFORMED_TEXT' | 'HTML_ENTITY' | 'TECHNICAL_TOKEN' | 'RAW_JSON' | 'LONG_PARAGRAPH' | 'DUPLICATE_HEADING' | 'DUPLICATE_COPY';
  value: string;
};

const technicalToken = /\b(?:sha256:|contentRef\b|sourceId\b|evidenceRef\b|artifactId\b|checkCode\b|FROZEN_FILE\b|DEFERRED\b|SOURCE_NATIVE\b|DEMO_AUTHORED\b|DERIVED_DEMO\b|Block\b|Assertion\b|Proposal\b|Locator\b|M4\b|Receipt\b|Catalog\b)|\[TRACE:/iu;
const htmlEntity = /&(?:amp|gt|lt|quot|#(?:39|34|\d+));/iu;
const malformedText = /\uFFFD|[\u0000-\u0008\u000B\u000C\u000E-\u001F]/u;
const rawJson = /^\s*\{\s*"[^"\n]+"\s*:/u;
const sentenceEnd = /[。！？!?]/gu;

function normalized(value: string) {
  return value.replace(/[\s\p{P}\p{S}]/gu, '').toLocaleLowerCase('zh-CN');
}

/**
 * Reviews only human-facing copy. Source code, SQL, DDL and structured
 * identifiers are intentionally outside this contract and stay immutable.
 */
export function collectBusinessCopyIssues(entries: readonly BusinessCopyEntry[]): BusinessCopyIssue[] {
  const issues: BusinessCopyIssue[] = [];
  const paragraphValues = new Set<string>();
  for (const entry of entries) {
    const value = entry.value.trim();
    const normalizedValue = normalized(value);
    if (entry.role === 'paragraph' && normalizedValue && paragraphValues.has(normalizedValue)) {
      issues.push({ kind: 'DUPLICATE_COPY', value });
    }
    if (entry.role === 'paragraph' && normalizedValue) paragraphValues.add(normalizedValue);
    if (malformedText.test(value)) issues.push({ kind: 'MALFORMED_TEXT', value });
    if (htmlEntity.test(value)) issues.push({ kind: 'HTML_ENTITY', value });
    if (rawJson.test(value)) issues.push({ kind: 'RAW_JSON', value });
    if (technicalToken.test(value)) issues.push({ kind: 'TECHNICAL_TOKEN', value });
    if (entry.role === 'paragraph' && (value.length > 140 || (value.match(sentenceEnd)?.length ?? 0) > 3)) {
      issues.push({ kind: 'LONG_PARAGRAPH', value });
    }
    if (entry.role === 'heading' && entry.pairedValue && normalized(value) === normalized(entry.pairedValue)) {
      issues.push({ kind: 'DUPLICATE_HEADING', value });
    }
  }
  return issues;
}

/** Fail closed before a damaged or implementation-heavy sentence reaches a business page. */
export function assertBusinessCopy(entries: readonly BusinessCopyEntry[]): void {
  const issues = collectBusinessCopyIssues(entries);
  if (issues.length) throw new Error(`业务文案校验失败：${issues.map((issue) => issue.kind).join('、')}`);
}

/** Map unexpected implementation errors to the one message a business reader can act on. */
export function presentBusinessError(error: unknown, fallback: string): string {
  const detail = error instanceof Error ? error.message : '';
  if (/保存|存储|quota|权限/iu.test(detail)) return '浏览器暂时无法保存本次操作，请检查存储权限后重试。';
  if (/正文|章节|证据.*读取|内容.*读取/iu.test(detail)) return '所需资料暂时无法读取，请重新打开当前资料后重试。';
  if (/revision|并发|另一.*标签|已更新/iu.test(detail)) return '内容已更新，请刷新当前步骤后重试。';
  if (/校验|digest|sha|checksum|fingerprint|contentRef/iu.test(detail)) return '内容完整性校验未通过，请重新打开当前资料后重试。';
  return fallback;
}
