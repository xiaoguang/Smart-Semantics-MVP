import type { ModelingDocumentArtifact, ModelingDocumentSection } from './types.ts';
import { sha256HexSync } from '../ai-modeling/sha256.ts';

export const standardSectionOrder: Array<{ key: ModelingDocumentSection; heading: string }> = [
  { key: 'OVERVIEW', heading: '1. 文档说明' },
  { key: 'GOAL', heading: '2. 业务目标' },
  { key: 'OBJECT', heading: '3. 业务对象' },
  { key: 'ACTIVITY', heading: '4. 业务活动' },
  { key: 'FIELD', heading: '5. 字段与维度' },
  { key: 'RELATION', heading: '6. 对象关系' },
  { key: 'METRIC', heading: '7. 指标口径' },
  { key: 'QUESTION', heading: '8. 示例问题' },
  { key: 'UNRESOLVED', heading: '9. 待确认事项' },
];

export function canonicalModelingJson(value: unknown): string {
  if (value === undefined) return 'undefined';
  if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) return `[${value.map(canonicalModelingJson).join(',')}]`;
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record).sort().map((key) => `${JSON.stringify(key)}:${canonicalModelingJson(record[key])}`).join(',')}}`;
  }
  throw new Error('建模文档包含不可序列化值');
}

export function modelingAssertionsSha256(assertions: ModelingDocumentArtifact['assertions']) {
  return sha256HexSync(canonicalModelingJson(assertions));
}

export function modelingDocumentSemanticPayloadSha256(payload: NonNullable<ModelingDocumentArtifact['semanticPayload']>['data']) {
  return sha256HexSync(canonicalModelingJson(payload));
}

export function renderStandardModelingMarkdown(input: Pick<ModelingDocumentArtifact, 'title' | 'projectId' | 'documentCode' | 'sourceBatch' | 'sections' | 'assertions' | 'semanticPayload'> & { artifactId: string }) {
  const frontmatter = [
    '---', 'schema: linguan.modeling-document/1', `artifactId: ${input.artifactId}`,
    `projectId: ${input.projectId}`, `documentCode: ${input.documentCode}`,
    ...(input.sourceBatch ? [`sourceBatch: ${input.sourceBatch.batchId}`, `sourceFingerprint: ${input.sourceBatch.fingerprint}`] : []),
    `assertionsSha256: ${modelingAssertionsSha256(input.assertions)}`,
    ...(input.semanticPayload ? [`semanticPayloadSha256: ${modelingDocumentSemanticPayloadSha256(input.semanticPayload.data)}`] : []),
    '---', '', `# ${input.title}`, '',
  ];
  const body = standardSectionOrder.flatMap(({ key, heading }) => [`## ${heading}`, '', input.sections[key].trim() || '暂无可确认内容。', '']);
  return [...frontmatter, ...body].join('\n').replace(/\n{3,}/g, '\n\n').trimEnd() + '\n';
}

export function assertStandardModelingMarkdown(content: string) {
  if (!content.trim()) throw new Error('标准建模文档不能为空');
  if (!/^---\nschema: linguan\.modeling-document\/1/m.test(content)) throw new Error('缺少标准建模文档契约');
  for (const section of standardSectionOrder) {
    if (!content.includes(`## ${section.heading}`)) throw new Error(`缺少必需章节：${section.heading}`);
  }
  if (new TextEncoder().encode(content).byteLength > 1024 * 1024) throw new Error('标准建模文档不得超过1 MiB');
}

export function parseNineSectionMarkdown(content: string) {
  if (!content.trim()) throw new Error('Markdown设计资料不能为空');
  if (new TextEncoder().encode(content).byteLength > 1024 * 1024) throw new Error('Markdown设计资料不得超过1 MiB');
  const sections = {} as ModelingDocumentArtifact['sections'];
  for (let index = 0; index < standardSectionOrder.length; index += 1) {
    const current = standardSectionOrder[index];
    const next = standardSectionOrder[index + 1];
    const start = content.indexOf(`## ${current.heading}`);
    if (start < 0) throw new Error(`缺少必需章节：${current.heading}`);
    const bodyStart = start + `## ${current.heading}`.length;
    const end = next ? content.indexOf(`## ${next.heading}`, bodyStart) : content.length;
    sections[current.key] = content.slice(bodyStart, end < 0 ? content.length : end).trim();
  }
  const title = content.match(/^#\s+(.+)$/m)?.[1]?.trim() || '未命名标准建模资料';
  return { title, sections };
}

function parseFrontmatter(content: string) {
  const match = /^---\n([\s\S]*?)\n---(?:\n|$)/.exec(content);
  if (!match) return null;
  const values: Record<string, string> = {};
  for (const line of match[1].split('\n')) {
    const separator = line.indexOf(':');
    if (separator < 1) continue;
    values[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
  }
  return values;
}

export function assertModelingDocumentIntegrity(artifact: ModelingDocumentArtifact) {
  if (sha256HexSync(artifact.markdown.content) !== artifact.markdown.sha256) {
    throw new Error('Markdown校验和不一致');
  }
  if (new TextEncoder().encode(artifact.markdown.content).byteLength !== artifact.markdown.byteLength) {
    throw new Error('Markdown字节长度不一致');
  }
  const parsed = parseNineSectionMarkdown(artifact.markdown.content);
  if (canonicalModelingJson(parsed.sections) !== canonicalModelingJson(artifact.sections)) {
    throw new Error('Artifact章节与冻结Markdown不一致');
  }
  if (parsed.title !== artifact.title) throw new Error('Artifact标题与冻结Markdown不一致');
  if (artifact.origin === 'MANUAL_UPLOAD') {
    if (artifact.assertions.length) throw new Error('人工Markdown不能携带未签名断言');
    return true;
  }
  const frontmatter = parseFrontmatter(artifact.markdown.content);
  if (!frontmatter || frontmatter.schema !== 'linguan.modeling-document/1') throw new Error('缺少标准建模文档契约');
  const expectedFields: Record<string, string | undefined> = {
    artifactId: artifact.artifactId,
    projectId: artifact.projectId,
    documentCode: artifact.documentCode,
    sourceBatch: artifact.sourceBatch?.batchId,
    sourceFingerprint: artifact.sourceBatch?.fingerprint,
    assertionsSha256: modelingAssertionsSha256(artifact.assertions),
    semanticPayloadSha256: artifact.semanticPayload
      ? modelingDocumentSemanticPayloadSha256(artifact.semanticPayload.data) : undefined,
  };
  for (const [field, expected] of Object.entries(expectedFields)) {
    if (expected !== undefined && frontmatter[field] !== expected) {
      if (field === 'assertionsSha256') throw new Error('Artifact断言与冻结Markdown不一致');
      if (field === 'semanticPayloadSha256') throw new Error('Artifact语义载荷与冻结Markdown不一致');
      throw new Error(`Artifact ${field} 与冻结Markdown不一致`);
    }
  }
  if (artifact.semanticPayload
    && modelingDocumentSemanticPayloadSha256(artifact.semanticPayload.data) !== artifact.semanticPayload.sha256) {
    throw new Error('Artifact语义载荷校验和不一致');
  }
  return true;
}
