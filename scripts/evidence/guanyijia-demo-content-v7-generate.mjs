import { createHash } from 'node:crypto';
import { execFile as execFileCallback, spawn } from 'node:child_process';
import { cp, lstat, mkdir, mkdtemp, open, readFile, readdir, rename, rm, writeFile } from 'node:fs/promises';
import { homedir, tmpdir } from 'node:os';
import { basename, dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

import { standardSectionOrder } from '../../src/features/modeling-document-bridge/standard-markdown.ts';
import { validateDemoContentSnapshot } from './guanyijia-demo-content-snapshot.mjs';
import { transformV6Review } from './guanyijia-demo-content-v7-transform.mjs';

const execFile = promisify(execFileCallback);
const moduleDirectory = dirname(fileURLToPath(import.meta.url));
const prototypeRoot = resolve(moduleDirectory, '../..');
const workspaceRoot = resolve(prototypeRoot, '..');

export const V6_SNAPSHOT_ID = 'guanyijia-demo-content-v6-20260826';
export const V7_SNAPSHOT_ID = 'guanyijia-demo-content-v7-20260827';

const sourceOrder = Object.freeze([
  'guanyijia_mysql',
  'guanyijia_github',
  'guanyijia_official_docs',
  'guanyijia_demo_policy',
  'guanyijia_semantica_demo',
]);

const reviewPathBySource = Object.freeze({
  guanyijia_mysql: 'sources/mysql/standardized-review.json',
  guanyijia_github: 'sources/github/standardized-review.json',
  guanyijia_official_docs: 'sources/official/standardized-review.json',
  guanyijia_demo_policy: 'sources/policy/standardized-review.json',
  guanyijia_semantica_demo: 'sources/terminology/standardized-review.json',
});

const narrativePathBySource = Object.freeze({
  guanyijia_mysql: 'sources/mysql/v7-reader-narratives.json',
  guanyijia_github: 'sources/github/v7-reader-narratives.json',
  guanyijia_official_docs: 'sources/official/v7-reader-narratives.json',
  guanyijia_demo_policy: 'sources/policy/v7-reader-narratives.json',
  guanyijia_semantica_demo: 'sources/terminology/v7-reader-narratives.json',
});

const defaultSourceSnapshotRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v6-20260826',
);
const defaultTargetSnapshotRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/snapshots/guanyijia-demo-content-v7-20260827',
);
const defaultCandidateRoot = resolve(
  workspaceRoot,
  'modeling-evidence/guanyijia/demo-content/candidates/V7',
);
const schemaPath = resolve(moduleDirectory, 'schemas/guanyijia-demo-content-v7-narrative.schema.json');
const promptRevisionSchemaPath = resolve(moduleDirectory, 'schemas/guanyijia-demo-content-v7-prompt-revision.schema.json');

const forbiddenEnvironmentKeys = Object.freeze([
  'OPENAI_API_KEY',
  'CODEX_API_KEY',
  'CODEX_ACCESS_TOKEN',
  'OPENAI_BASE_URL',
  'AZURE_OPENAI_API_KEY',
  'AZURE_OPENAI_ENDPOINT',
]);

// Candidate validation has three deliberately small, stable tiers.  The
// persisted `class` field is an operational contract: callers may offer a
// normalized candidate for selection, must ask for a new source candidate for
// a BLOCKED one, and must never infer that a warning was silently ignored.
export const CandidateIssueClass = Object.freeze({
  NORMALIZED: 'NORMALIZED',
  CRITICAL: 'CRITICAL',
});

/** Product-content candidates are intentionally capped at two per source. */
export const ContentGenerationRound = Object.freeze({
  ONE: 1,
  TWO: 2,
});

export const CandidateAcceptance = Object.freeze({
  IDEAL: 'IDEAL',
  REVIEWABLE_WITH_WARNINGS: 'REVIEWABLE_WITH_WARNINGS',
  FATAL: 'FATAL',
});

const transportReplacement = Object.freeze({
  SOURCE_NATIVE: (descriptor) => descriptor.readerLabel,
  FROZEN_RECORD: () => '已保存资料',
  DEMO_AUTHORED: () => '演示编写资料',
  DERIVED_DEMO: () => '派生内容',
  SNAPSHOT_REFERENCE: () => '已保存资料引用',
  sourceId: () => '资料来源标识',
  snapshotId: () => '快照标识',
  contentOrigin: () => '内容性质',
  '[TRACE:': () => '追踪标记：',
  '<!--': () => '注释：',
  '&amp;gt;': () => '>',
  'sha256:': () => '摘要：',
  'GAP': () => '待补充资料',
});

function fail(message) {
  throw new Error(`Demo content V7 generation blocked: ${message}`);
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function canonicalJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
}

function exactMembers(actual, expected) {
  return actual.length === expected.length
    && new Set(actual).size === actual.length
    && expected.every((item) => actual.includes(item));
}

function isSameOrDescendant(root, candidate) {
  const pathFromRoot = relative(root, candidate);
  return pathFromRoot === ''
    || (!pathFromRoot.startsWith(`..${sep}`) && pathFromRoot !== '..' && !isAbsolute(pathFromRoot));
}

function assertDisjointSnapshotRoots(sourceSnapshotRoot, targetSnapshotRoot) {
  if (isSameOrDescendant(sourceSnapshotRoot, targetSnapshotRoot)
    || isSameOrDescendant(targetSnapshotRoot, sourceSnapshotRoot)) {
    fail('source and target snapshot roots overlap; V7 staging must be outside V6');
  }
}

async function readJson(path) {
  try {
    return JSON.parse(await readFile(path, 'utf8'));
  } catch (error) {
    fail(`cannot read JSON ${path}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

async function assertDoesNotExist(path, label) {
  try {
    await lstat(path);
    fail(`${label} already exists: ${path}`);
  } catch (error) {
    if (error?.message?.startsWith('Demo content V7 generation blocked:')) throw error;
    if (error?.code !== 'ENOENT') throw error;
  }
}

async function artifact(root, path, mediaType) {
  return {
    path,
    mediaType,
    sha256: sha256(await readFile(join(root, path))),
  };
}

function promptVersionFor(sourceId) {
  return `guanyijia-v7-reader-candidate-${sourceId}-5`;
}

function narrativePrompt(descriptor, promptRevision) {
  const sections = standardSectionOrder.map(({ key, heading }) => ({
    sectionId: key,
    heading,
    claims: descriptor.review.claims
      .filter((claim) => claim.sectionId === key)
      .map((claim) => ({
        claimId: claim.claimId,
        kind: claim.kind,
        title: claim.title,
        statement: claim.statement,
        boundary: claim.boundary,
      })),
  }));
  const revision = promptRevision
    ? [
      '本轮受限修订指令（只处理列出的已发现问题；不得改变以下不可变事实边界）：',
      ...promptRevision.correctiveDirectives.map((directive) => `- ${directive.instruction}`),
      `不得做的事：${promptRevision.nonGoals.join('；')}`,
    ].join('\n')
    : '';
  return [
    '角色：你是中文 ERP 业务说明书编辑，不是数据库或代码清单生成器。只输出符合给定 JSON Schema v2 的 JSON；不要代码围栏或额外文字。',
    '事实边界：只能使用输入提供的冻结 Claim、Boundary 和 Evidence 摘要。不得新增字段、代码、文件、行号、运行结果、制度事实、跨来源结论或业务数据。不知道的内容必须写入 gaps 的缺少／影响／下一步。',
    '读者：业务负责人、产品经理和数据建模人员。先解释“是什么、为什么重要、如何使用、有哪些边界”，再由系统连接技术依据。使用自然、直接、短句中文；避免字段倾倒、模板空话和机械重复。',
    '结构：九章必须完整且按输入顺序输出。每个非 GAP Claim 必须且只能放入本章一个 items[].claimIds；每个 GAP Claim 必须且只能放入本章一个 gaps[].claimIds。不要把 claimId、sourceId、snapshot、SHA、文件位置、行号、英文 GAP 或证据引用写入人类文本。',
    '输出职责：模型只能提供 introduction、items 的 title/explanation/boundary（没有边界时写 null）和 gaps 的 missing/impact/nextStep。技术依据、章节标题、表格、Markdown、来源身份、正式差异和用户已保存修改由确定性程序处理。',
    '来源边界：MySQL 的结构存在不代表实际配置、业务行或制度生效；GitHub 实现线索不代表生产部署；业务说明不能冒充官方原文；ERP 制度草案不能冒充已批准制度；企业术语图只能佐证词义，不构成新的根证据或冲突。',
    `本次单一来源类别：${descriptor.readerLabel}。`,
    revision,
    `冻结 Claim 输入（仅能基于这些内容撰写）：${JSON.stringify(sections)}`,
  ].join('\n\n');
}

function normalizeHumanText(value, descriptor, sectionId, field, issues) {
  if (typeof value !== 'string') {
    fail(`${field} must be a string: ${descriptor.sourceId} / ${sectionId}`);
  }
  let normalized = value
    .replace(/\r\n?/gu, '\n')
    .replace(/[ \t]+\n/gu, '\n')
    .replace(/[ \t]{2,}/gu, ' ')
    .replace(/\n{3,}/gu, '\n\n')
    .trim();
  if (normalized !== value) {
    issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'WHITESPACE_NORMALIZED', `${sectionId} ${field} whitespace was normalized`));
  }
  for (const [token, replace] of Object.entries(transportReplacement)) {
    if (normalized.includes(token)) {
      normalized = normalized.replaceAll(token, replace(descriptor));
      issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'TRANSPORT_VOCABULARY_NORMALIZED', `${sectionId} ${field} contained ${token}`));
    }
  }
  if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\ufffd]/u.test(normalized)) {
    fail(`${field} contains unreadable characters: ${descriptor.sourceId} / ${sectionId}`);
  }
  return normalized;
}

function normalizeNarrative(value, descriptor, sectionId, issues) {
  const narrative = normalizeHumanText(value, descriptor, sectionId, 'narrative', issues);
  if (!narrative) fail(`narrative must not be empty: ${descriptor.sourceId} / ${sectionId}`);
  return narrative;
}

function normalizeGapDetail(value, field, descriptor, sectionId, issues) {
  const detail = normalizeHumanText(value, descriptor, sectionId, `gap.${field}`, issues);
  if (!detail) {
    fail(`structured GAP details must include a readable ${field}: ${descriptor.sourceId} / ${sectionId}`);
  }
  return detail;
}

function normalizeGapDetails(value, descriptor, sectionId, issues) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).length !== 3
    || !['missing', 'limitation', 'nextStep'].every((field) => Object.hasOwn(value, field))) {
    fail(`structured GAP details must include missing, limitation, and nextStep: ${descriptor.sourceId} / ${sectionId}`);
  }
  return {
    missing: normalizeGapDetail(value.missing, 'missing', descriptor, sectionId, issues),
    limitation: normalizeGapDetail(value.limitation, 'limitation', descriptor, sectionId, issues),
    nextStep: normalizeGapDetail(value.nextStep, 'nextStep', descriptor, sectionId, issues),
  };
}

function composeGapNarrative(narrative, gap) {
  return `${narrative}\n\n缺少：${gap.missing}\n限制：${gap.limitation}\n下一步：${gap.nextStep}`;
}

function candidateIssue(issueClass, code, message) {
  return { class: issueClass, code, message };
}

function normalizedIssueList(issues) {
  const seen = new Set();
  return issues.filter((issue) => {
    const key = canonicalJson(issue);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function hasAdmittedClaims(descriptor, sectionId) {
  return descriptor.review.claims.some((claim) => claim.sectionId === sectionId
    && claim.kind !== 'GAP'
    && Array.isArray(claim.evidenceRefs)
    && claim.evidenceRefs.length > 0);
}

function derivedCoverage(descriptor, sectionId) {
  return sectionId === 'UNRESOLVED' || !hasAdmittedClaims(descriptor, sectionId)
    ? 'GAP'
    : 'PARTIAL';
}

function normalizeV1Output(rawOutput, descriptor) {
  const issues = [];
  let parsed;
  try {
    parsed = JSON.parse(rawOutput);
  } catch {
    return {
      issues: [candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_JSON', `${descriptor.sourceId} reader narrative output is not JSON`)],
      status: 'BLOCKED',
    };
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)
    || parsed.schemaVersion !== 1 || parsed.sourceId !== descriptor.sourceId || !Array.isArray(parsed.sections)) {
    return {
      issues: [candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_SHAPE', `${descriptor.sourceId} reader narrative output has an invalid shape`)],
      status: 'BLOCKED',
    };
  }
  const outputExtras = Object.keys(parsed).filter((key) => !['schemaVersion', 'sourceId', 'sections'].includes(key));
  if (outputExtras.length) issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'EXTRA_OUTPUT_FIELDS_DROPPED', `dropped output fields: ${outputExtras.sort().join(', ')}`));
  const expectedSections = standardSectionOrder.map(({ key }) => key);
  if (parsed.sections.length !== expectedSections.length || parsed.sections.some((section) => !section || typeof section !== 'object' || Array.isArray(section))) {
    return {
      issues: [...issues, candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_SECTION_SET', `${descriptor.sourceId} reader narrative must include exactly the canonical nine sections`)],
      status: 'BLOCKED',
    };
  }
  const sectionById = new Map(parsed.sections.map((section) => [section.sectionId, section]));
  if (!exactMembers(parsed.sections.map((section) => section.sectionId), expectedSections)) {
    return {
      issues: [...issues, candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_SECTION_SET', `${descriptor.sourceId} reader narrative has missing or duplicate canonical sections`)],
      status: 'BLOCKED',
    };
  }
  if (parsed.sections.map((section) => section.sectionId).join('\n') !== expectedSections.join('\n')) {
    issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'SECTION_ORDER_NORMALIZED', 'canonical section order was restored'));
  }
  try {
    const narratives = expectedSections.map((sectionId) => {
      const section = sectionById.get(sectionId);
      const sectionExtras = Object.keys(section).filter((key) => !['sectionId', 'coverage', 'narrative', 'gap'].includes(key));
      if (sectionExtras.length) issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'EXTRA_SECTION_FIELDS_DROPPED', `${sectionId} dropped fields: ${sectionExtras.sort().join(', ')}`));
      const coverage = derivedCoverage(descriptor, sectionId);
      if (Object.hasOwn(section, 'coverage') && section.coverage !== coverage) {
        issues.push(candidateIssue(
          CandidateIssueClass.NORMALIZED,
          'MODEL_COVERAGE_NORMALIZED',
          `${sectionId} model coverage was replaced with locally derived ${coverage}`,
        ));
      }
      const narrative = normalizeNarrative(section.narrative, descriptor, sectionId, issues);
    if (coverage !== 'GAP') {
      if (Object.hasOwn(section, 'gap')) {
          issues.push(candidateIssue(
            CandidateIssueClass.NORMALIZED,
            'NON_GAP_DETAILS_DROPPED',
            `${sectionId} structured GAP details were removed because frozen claims derive PARTIAL coverage`,
          ));
      }
      return {
          sectionId,
        coverage,
        narrative,
      };
    }
      const gap = normalizeGapDetails(section.gap, descriptor, sectionId, issues);
    return {
        sectionId,
      coverage,
      gap,
      narrative: composeGapNarrative(narrative, gap),
    };
    });
    return {
      issues: normalizedIssueList(issues),
      narratives,
      status: issues.length ? 'READY_WITH_WARNINGS' : 'READY',
    };
  } catch (error) {
    return {
      issues: normalizedIssueList([...issues, candidateIssue(
        CandidateIssueClass.CRITICAL,
        'INVALID_CONTENT',
        error instanceof Error ? error.message : String(error),
      )]),
      status: 'BLOCKED',
    };
  }
}

function normalizedHumanText(value, descriptor, sectionId, field, issues) {
  const text = normalizeHumanText(value, descriptor, sectionId, field, issues);
  if (!text) fail(`${field} must not be empty: ${descriptor.sourceId} / ${sectionId}`);
  if (/\b(?:sourceId|snapshotId|ContentReference|sha256|GAP)\b/iu.test(text)) {
    fail(`${field} contains an internal transport token: ${descriptor.sourceId} / ${sectionId}`);
  }
  return text;
}

function expectedClaimMap(descriptor) {
  return new Map(descriptor.review.claims.map((claim) => [claim.claimId, claim]));
}

function assertClaimIds(value, field, descriptor, sectionId) {
  if (!Array.isArray(value) || !value.length || value.some((claimId) => typeof claimId !== 'string' || !claimId)) {
    fail(`${field} must contain one or more claim IDs: ${descriptor.sourceId} / ${sectionId}`);
  }
  if (new Set(value).size !== value.length) {
    fail(`${field} must not repeat a claim ID: ${descriptor.sourceId} / ${sectionId}`);
  }
  return [...value];
}

function chapterNarrative(chapter) {
  const itemText = chapter.items.map((item) => [
    `**${item.title}**`,
    item.explanation,
    ...(item.boundary ? [`边界：${item.boundary}`] : []),
  ].join('\n\n'));
  const gapText = chapter.gaps.map((gap, index) => [
    `**待补充资料 ${index + 1}**`,
    `缺少：${gap.missing}`,
    `影响：${gap.impact}`,
    `下一步：${gap.nextStep}`,
  ].join('\n\n'));
  return [chapter.introduction, ...itemText, ...gapText].filter(Boolean).join('\n\n');
}

function normalizeV2Output(parsed, descriptor) {
  const issues = [];
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)
    || parsed.schemaVersion !== 2 || parsed.sourceId !== descriptor.sourceId || !Array.isArray(parsed.chapters)) {
    return {
      issues: [candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_SHAPE', `${descriptor.sourceId} reader candidate must use schema v2`)],
      status: 'BLOCKED',
    };
  }
  const outputExtras = Object.keys(parsed).filter((key) => !['schemaVersion', 'sourceId', 'chapters'].includes(key));
  if (outputExtras.length) issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'EXTRA_OUTPUT_FIELDS_DROPPED', `dropped output fields: ${outputExtras.sort().join(', ')}`));
  const expectedSections = standardSectionOrder.map(({ key }) => key);
  if (parsed.chapters.length !== expectedSections.length || parsed.chapters.some((chapter) => !chapter || typeof chapter !== 'object' || Array.isArray(chapter))) {
    return {
      issues: [...issues, candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_SECTION_SET', `${descriptor.sourceId} reader candidate must include exactly the canonical nine chapters`)],
      status: 'BLOCKED',
    };
  }
  const chapterById = new Map(parsed.chapters.map((chapter) => [chapter.sectionId, chapter]));
  if (!exactMembers(parsed.chapters.map((chapter) => chapter.sectionId), expectedSections)) {
    return {
      issues: [...issues, candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_SECTION_SET', `${descriptor.sourceId} reader candidate has missing or duplicate canonical chapters`)],
      status: 'BLOCKED',
    };
  }
  if (parsed.chapters.map((chapter) => chapter.sectionId).join('\n') !== expectedSections.join('\n')) {
    issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'SECTION_ORDER_NORMALIZED', 'canonical chapter order was restored'));
  }
  try {
    const claimMap = expectedClaimMap(descriptor);
    const seen = new Map();
    const chapters = expectedSections.map((sectionId) => {
      const rawChapter = chapterById.get(sectionId);
      const chapterExtras = Object.keys(rawChapter).filter((key) => !['sectionId', 'introduction', 'items', 'gaps'].includes(key));
      if (chapterExtras.length) issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'EXTRA_CHAPTER_FIELDS_DROPPED', `${sectionId} dropped fields: ${chapterExtras.sort().join(', ')}`));
      if (!Array.isArray(rawChapter.items) || !Array.isArray(rawChapter.gaps)) {
        fail(`items and gaps must be arrays: ${descriptor.sourceId} / ${sectionId}`);
      }
      const introduction = normalizedHumanText(rawChapter.introduction, descriptor, sectionId, 'introduction', issues);
      const items = rawChapter.items.map((item, itemIndex) => {
        if (!item || typeof item !== 'object' || Array.isArray(item)) fail(`item must be an object: ${descriptor.sourceId} / ${sectionId}`);
        const extras = Object.keys(item).filter((key) => !['title', 'explanation', 'boundary', 'claimIds'].includes(key));
        if (extras.length) issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'EXTRA_ITEM_FIELDS_DROPPED', `${sectionId} item ${itemIndex + 1} dropped fields: ${extras.sort().join(', ')}`));
        if (!Object.hasOwn(item, 'boundary') || (item.boundary !== null && typeof item.boundary !== 'string')) {
          fail(`item.boundary must be a string or null: ${descriptor.sourceId} / ${sectionId}`);
        }
        const claimIds = assertClaimIds(item.claimIds, 'item.claimIds', descriptor, sectionId);
        for (const claimId of claimIds) {
          const claim = claimMap.get(claimId);
          if (!claim || claim.sectionId !== sectionId || claim.kind === 'GAP') fail(`item claims must be known non-GAP claims in the same chapter: ${descriptor.sourceId} / ${sectionId}`);
          if (seen.has(claimId)) fail(`Claim is mapped more than once: ${claimId}`);
          seen.set(claimId, 'item');
        }
        return {
          title: normalizedHumanText(item.title, descriptor, sectionId, 'item.title', issues),
          explanation: normalizedHumanText(item.explanation, descriptor, sectionId, 'item.explanation', issues),
          ...(item.boundary === null ? {} : { boundary: normalizedHumanText(item.boundary, descriptor, sectionId, 'item.boundary', issues) }),
          claimIds,
        };
      });
      const gaps = rawChapter.gaps.map((gap, gapIndex) => {
        if (!gap || typeof gap !== 'object' || Array.isArray(gap)) fail(`gap must be an object: ${descriptor.sourceId} / ${sectionId}`);
        const extras = Object.keys(gap).filter((key) => !['missing', 'impact', 'nextStep', 'claimIds'].includes(key));
        if (extras.length) issues.push(candidateIssue(CandidateIssueClass.NORMALIZED, 'EXTRA_GAP_FIELDS_DROPPED', `${sectionId} gap ${gapIndex + 1} dropped fields: ${extras.sort().join(', ')}`));
        const claimIds = assertClaimIds(gap.claimIds, 'gap.claimIds', descriptor, sectionId);
        for (const claimId of claimIds) {
          const claim = claimMap.get(claimId);
          if (!claim || claim.sectionId !== sectionId || claim.kind !== 'GAP') fail(`gap claims must be known GAP claims in the same chapter: ${descriptor.sourceId} / ${sectionId}`);
          if (seen.has(claimId)) fail(`Claim is mapped more than once: ${claimId}`);
          seen.set(claimId, 'gap');
        }
        return {
          missing: normalizedHumanText(gap.missing, descriptor, sectionId, 'gap.missing', issues),
          impact: normalizedHumanText(gap.impact, descriptor, sectionId, 'gap.impact', issues),
          nextStep: normalizedHumanText(gap.nextStep, descriptor, sectionId, 'gap.nextStep', issues),
          claimIds,
        };
      });
      return { sectionId, introduction, items, gaps };
    });
    for (const [claimId, claim] of claimMap) {
      const expectedKind = claim.kind === 'GAP' ? 'gap' : 'item';
      if (seen.get(claimId) !== expectedKind) fail(`Claim is missing or mapped to the wrong reader structure: ${claimId}`);
    }
    const narratives = chapters.map((chapter) => ({
      sectionId: chapter.sectionId,
      coverage: derivedCoverage(descriptor, chapter.sectionId),
      narrative: chapterNarrative(chapter),
    }));
    return {
      issues: normalizedIssueList(issues),
      chapters,
      narratives,
      status: issues.length ? 'READY_WITH_WARNINGS' : 'READY',
    };
  } catch (error) {
    return {
      issues: normalizedIssueList([...issues, candidateIssue(
        CandidateIssueClass.CRITICAL,
        'INVALID_CONTENT',
        error instanceof Error ? error.message : String(error),
      )]),
      status: 'BLOCKED',
    };
  }
}

function normalizeOutput(rawOutput, descriptor) {
  let parsed;
  try {
    parsed = JSON.parse(rawOutput);
  } catch {
    return {
      issues: [candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_JSON', `${descriptor.sourceId} reader narrative output is not JSON`)],
      status: 'BLOCKED',
    };
  }
  return parsed?.schemaVersion === 2
    ? normalizeV2Output(parsed, descriptor)
    : normalizeV1Output(rawOutput, descriptor);
}

function narrativeRecord(narratives) {
  return Object.fromEntries(narratives.map((section) => [section.sectionId, section.narrative]));
}

export const ImmutablePromptConstraints = Object.freeze([
  '只能使用冻结 Claim、Boundary 和 Evidence 摘要',
  '九章完整且顺序固定',
  'Claim 和 Gap 必须可追溯且不得跨来源',
  '不得生成文件路径、行号、证据引用、来源身份或 SHA',
  '不得扩大事实范围或修改用户已保存决定',
]);

function promptRevisionId(proposal) {
  return `v7-prompt-revision-${sha256(canonicalJson({
    sourceId: proposal.sourceId,
    basePromptVersion: proposal.basePromptVersion,
    parentCandidateId: proposal.parentCandidateId,
    fatalFindingIds: proposal.fatalFindingIds,
    correctiveDirectives: proposal.correctiveDirectives,
    preservedConstraints: proposal.preservedConstraints,
    nonGoals: proposal.nonGoals,
  })).slice(7, 31)}`;
}

/** Validate a bounded Sol Ultra addendum before it can be paired with Luna Round 2. */
export function validatePromptRevisionProposal(value) {
  if (!value || value.schemaVersion !== 1 || !sourceOrder.includes(value.sourceId)
    || typeof value.basePromptVersion !== 'string' || !value.basePromptVersion
    || typeof value.parentCandidateId !== 'string' || !value.parentCandidateId
    || !Array.isArray(value.fatalFindingIds) || !value.fatalFindingIds.length
    || new Set(value.fatalFindingIds).size !== value.fatalFindingIds.length
    || value.fatalFindingIds.some((findingId) => typeof findingId !== 'string' || !findingId)
    || !Array.isArray(value.correctiveDirectives) || !value.correctiveDirectives.length
    || !Array.isArray(value.preservedConstraints) || !Array.isArray(value.nonGoals)) {
    fail('prompt revision must bind one source, parent candidate, fatal findings and corrective directives');
  }
  const directiveFindingIds = new Set();
  for (const directive of value.correctiveDirectives) {
    if (!directive || !Array.isArray(directive.findingIds) || !directive.findingIds.length
      || directive.findingIds.some((findingId) => typeof findingId !== 'string' || !findingId)
      || typeof directive.instruction !== 'string' || !directive.instruction.trim()
      || typeof directive.reason !== 'string' || !directive.reason.trim()) {
      fail('every prompt corrective directive must name findings, instruction and reason');
    }
    directive.findingIds.forEach((findingId) => directiveFindingIds.add(findingId));
    if (/忽略|绕过|删除.*(?:Claim|证据|九章|边界)|新增.*事实|编造/iu.test(directive.instruction)) {
      fail('prompt revision may not weaken immutable fact, Claim, evidence or chapter constraints');
    }
  }
  if (value.fatalFindingIds.some((findingId) => !directiveFindingIds.has(findingId))) {
    fail('prompt revision must contain a corrective directive for every fatal finding');
  }
  if (!exactMembers(value.preservedConstraints, ImmutablePromptConstraints)
    || value.nonGoals.some((item) => typeof item !== 'string' || !item.trim())) {
    fail('prompt revision must preserve every immutable constraint and declare readable non-goals');
  }
  return {
    schemaVersion: 1,
    sourceId: value.sourceId,
    basePromptVersion: value.basePromptVersion,
    parentCandidateId: value.parentCandidateId,
    fatalFindingIds: [...value.fatalFindingIds],
    correctiveDirectives: value.correctiveDirectives.map((directive) => ({
      findingIds: [...directive.findingIds],
      instruction: directive.instruction.trim(),
      reason: directive.reason.trim(),
    })),
    preservedConstraints: [...value.preservedConstraints],
    nonGoals: [...value.nonGoals].map((item) => item.trim()),
    promptRevisionId: value.promptRevisionId ?? promptRevisionId(value),
  };
}

function contentLineage(record, descriptor) {
  const generationRound = record?.generationRound ?? ContentGenerationRound.ONE;
  if (!Object.values(ContentGenerationRound).includes(generationRound)) {
    fail('a source may produce at most two content candidates; Round 3 is forbidden');
  }
  if (generationRound === ContentGenerationRound.ONE) {
    if (record?.parentCandidateId || record?.promptRevision || record?.promptRevisionId) {
      fail('Round 1 cannot reference a parent candidate or prompt revision');
    }
    return { generationRound };
  }
  if (typeof record?.parentCandidateId !== 'string' || !record.parentCandidateId) {
    fail('Round 2 requires its Round 1 parent candidate');
  }
  if (!record.promptRevision) fail('Round 2 requires a validated Sol prompt revision');
  const promptRevision = validatePromptRevisionProposal(record.promptRevision);
  if (promptRevision.sourceId !== descriptor.sourceId
    || promptRevision.parentCandidateId !== record.parentCandidateId
    || promptRevision.basePromptVersion !== promptVersionFor(descriptor.sourceId)) {
    fail('Round 2 prompt revision must bind the same source, parent candidate and base prompt version');
  }
  return {
    generationRound,
    parentCandidateId: record.parentCandidateId,
    promptRevisionId: promptRevision.promptRevisionId,
    promptRevision,
  };
}

function generationFor(descriptor, record) {
  const lineage = contentLineage(record, descriptor);
  const prompt = narrativePrompt(descriptor, lineage.promptRevision);
  return {
    sourceId: descriptor.sourceId,
    provider: 'CODEX_CHATGPT_SESSION',
    model: 'gpt-5.6-luna',
    reasoningEffort: 'xhigh',
    sessionId: record.sessionId,
    promptVersion: promptVersionFor(descriptor.sourceId),
    inputDigest: sha256(prompt),
    outputDigest: sha256(record.rawOutput),
    lineage: {
      generationRound: lineage.generationRound,
      ...(lineage.parentCandidateId ? { parentCandidateId: lineage.parentCandidateId } : {}),
      ...(lineage.promptRevisionId ? { promptRevisionId: lineage.promptRevisionId } : {}),
    },
    ...(lineage.promptRevision ? { promptRevision: lineage.promptRevision } : {}),
  };
}

function validateGeneration(generation, descriptor, rawOutput, sessionIds) {
  const expected = generationFor(descriptor, {
    sessionId: generation?.sessionId,
    rawOutput,
    generationRound: generation?.lineage?.generationRound,
    parentCandidateId: generation?.lineage?.parentCandidateId,
    ...(generation?.lineage?.promptRevisionId
      ? { promptRevision: generation?.promptRevision }
      : {}),
  });
  if (!generation || canonicalJson(generation) !== canonicalJson(expected)
    || !generation.sessionId || sessionIds.has(generation.sessionId)) {
    fail(`${descriptor.sourceId} generation metadata is not a distinct ChatGPT Luna xhigh session`);
  }
  sessionIds.add(generation.sessionId);
  return structuredClone(generation);
}

/**
 * Load exactly one already-frozen V6 review per source.  The descriptors are
 * intentionally internal maintenance input; the reader never loads this
 * function at runtime.
 */
export async function loadV6NarrativeDescriptors(input = {}) {
  const sourceSnapshotRoot = resolve(input.sourceSnapshotRoot ?? defaultSourceSnapshotRoot);
  const validated = await validateDemoContentSnapshot(sourceSnapshotRoot);
  const manifest = await readJson(join(sourceSnapshotRoot, 'manifest.json'));
  if (validated.snapshotId !== V6_SNAPSHOT_ID || manifest.snapshotId !== V6_SNAPSHOT_ID
    || !exactMembers(manifest.sources.map((source) => source.sourceId), sourceOrder)) {
    fail('V7 must start from the exact frozen V6 five-source snapshot');
  }
  const sourceById = new Map(manifest.sources.map((source) => [source.sourceId, source]));
  return Promise.all(sourceOrder.map(async (sourceId) => {
    const source = sourceById.get(sourceId);
    const reviewPath = reviewPathBySource[sourceId];
    if (!source || !reviewPath || !source.artifacts.some((entry) => entry.path === reviewPath)) {
      fail(`V6 review artifact is missing: ${sourceId}`);
    }
    const review = await readJson(join(sourceSnapshotRoot, reviewPath));
    if (review.sourceId !== sourceId || review.snapshotId !== source.snapshotId
      || !Array.isArray(review.claims) || !Array.isArray(review.evidence)) {
      fail(`V6 review identity is invalid: ${sourceId}`);
    }
    return {
      sourceId,
      sourceSnapshotId: source.snapshotId,
      sourceContentSha256: manifest.contentSha256,
      source,
      reviewPath,
      narrativePath: narrativePathBySource[sourceId],
      review,
      readerLabel: {
        guanyijia_mysql: '已保存的数据库结构资料',
        guanyijia_github: '固定版本源码节选',
        guanyijia_official_docs: '业务说明（演示编写资料）',
        guanyijia_demo_policy: 'ERP 管理制度（演示制度草案）',
        guanyijia_semantica_demo: '企业术语图（派生内容）',
      }[sourceId],
    };
  }));
}

/** Create a no-side-effect V7 candidate from five recorded ChatGPT outputs. */
export function createV7Candidate(descriptors, records) {
  if (!Array.isArray(descriptors) || !Array.isArray(records) || descriptors.length !== sourceOrder.length
    || records.length !== sourceOrder.length) {
    fail('candidate requires exactly five V6 descriptors and five reader outputs');
  }
  if (descriptors.map((descriptor) => descriptor?.sourceId).join('\n') !== sourceOrder.join('\n')) {
    fail('candidate descriptors must be in the canonical V6 source order');
  }
  const sourceContentSha256 = descriptors[0]?.sourceContentSha256;
  const recordBySource = new Map(records.map((record) => [record?.sourceId, record]));
  if (!exactMembers(records.map((record) => record?.sourceId), sourceOrder)) {
    fail('candidate requires exactly one raw reader output for every V6 source');
  }
  const reviews = descriptors.map((descriptor) => {
    const record = recordBySource.get(descriptor.sourceId);
    if (!descriptor || record?.sourceId !== descriptor.sourceId
      || typeof record.sessionId !== 'string' || !record.sessionId
      || typeof record.rawOutput !== 'string' || !record.rawOutput.trim()) {
      fail(`candidate raw reader output is invalid: ${descriptor?.sourceId ?? 'unknown'}`);
    }
    return {
      sourceId: record.sourceId,
      ...(typeof record.candidateId === 'string' ? { candidateId: record.candidateId } : {}),
      generation: generationFor(descriptor, record),
      rawOutput: record.rawOutput,
    };
  });
  return {
    schemaVersion: 1,
    snapshotId: V7_SNAPSHOT_ID,
    previousSnapshotId: V6_SNAPSHOT_ID,
    sourceContentSha256,
    reviews,
  };
}

function validateSourceCandidateRecord(record, descriptor, { sourceIdentityDrift, sessionIds }) {
  if (record?.failure) {
    const message = typeof record.failure.message === 'string' && record.failure.message.trim()
      ? record.failure.message.trim()
      : 'Codex session failed before a readable reader narrative was saved';
    const issues = [candidateIssue(
      CandidateIssueClass.CRITICAL,
      'CODEX_SESSION_FAILED',
      `${descriptor.sourceId} Codex session failed: ${message}`,
    )];
    if (sourceIdentityDrift) {
      issues.push(candidateIssue(CandidateIssueClass.CRITICAL, 'V6_IDENTITY_DRIFT', 'candidate selection does not bind the current V6 content digest'));
    }
    return {
      candidateId: record.candidateId,
      descriptor,
      generation: record.generation,
      rawOutput: record.rawOutput,
      status: 'BLOCKED',
      acceptance: CandidateAcceptance.FATAL,
      issues: normalizedIssueList(issues),
    };
  }
  const initial = !record || typeof record.rawOutput !== 'string'
    ? {
      issues: [candidateIssue(CandidateIssueClass.CRITICAL, 'MISSING_SOURCE_CANDIDATE', `${descriptor.sourceId} candidate raw output is missing`)],
      status: 'BLOCKED',
    }
    : normalizeOutput(record.rawOutput, descriptor);
  const issues = [...initial.issues];
  let generation;
  if (record?.rawOutput && record?.generation) {
    try {
      generation = validateGeneration(record.generation, descriptor, record.rawOutput, sessionIds);
    } catch (error) {
      issues.push(candidateIssue(
        CandidateIssueClass.CRITICAL,
        'INVALID_RECEIPT',
        error instanceof Error ? error.message : String(error),
      ));
    }
  } else {
    issues.push(candidateIssue(CandidateIssueClass.CRITICAL, 'INVALID_RECEIPT', `${descriptor.sourceId} candidate generation receipt is missing`));
  }
  if (sourceIdentityDrift) {
    issues.push(candidateIssue(CandidateIssueClass.CRITICAL, 'V6_IDENTITY_DRIFT', 'candidate selection does not bind the current V6 content digest'));
  }
  const normalizedIssues = normalizedIssueList(issues);
  const status = normalizedIssues.some((issue) => issue.class === CandidateIssueClass.CRITICAL)
    ? 'BLOCKED'
    : normalizedIssues.length ? 'READY_WITH_WARNINGS' : 'READY';
  const acceptance = status === 'BLOCKED'
    ? CandidateAcceptance.FATAL
    : status === 'READY_WITH_WARNINGS'
      ? CandidateAcceptance.REVIEWABLE_WITH_WARNINGS
      : CandidateAcceptance.IDEAL;
  return {
    candidateId: record?.candidateId,
    descriptor,
    generation,
    rawOutput: record?.rawOutput,
    chapters: initial.chapters,
    narratives: initial.narratives,
    normalized: initial.narratives ? {
      schemaVersion: initial.chapters ? 2 : 1,
      sourceId: descriptor.sourceId,
      sourceSnapshotId: descriptor.sourceSnapshotId,
      ...(initial.chapters ? { chapters: initial.chapters } : { sections: initial.narratives }),
    } : undefined,
    status,
    acceptance,
    issues: normalizedIssues,
  };
}

export function validateV7Candidate(candidate, descriptors) {
  if (!Array.isArray(descriptors) || descriptors.length !== sourceOrder.length
    || descriptors.map((descriptor) => descriptor?.sourceId).join('\n') !== sourceOrder.join('\n')) {
    fail('V7 candidate validation requires the exact canonical V6 descriptors');
  }
  const candidateShapeIsValid = candidate && candidate.schemaVersion === 1
    && candidate.snapshotId === V7_SNAPSHOT_ID
    && candidate.previousSnapshotId === V6_SNAPSHOT_ID
    && Array.isArray(candidate.reviews)
    && candidate.reviews.length === sourceOrder.length
    && exactMembers(candidate.reviews.map((record) => record?.sourceId), sourceOrder);
  const recordBySource = candidateShapeIsValid
    ? new Map(candidate.reviews.map((record) => [record.sourceId, record]))
    : new Map();
  const sourceIdentityDrift = !candidateShapeIsValid
    || candidate.sourceContentSha256 !== descriptors[0]?.sourceContentSha256
    || descriptors.some((descriptor) => descriptor.sourceContentSha256 !== descriptors[0].sourceContentSha256);
  const sessionIds = new Set();
  const initialReviews = descriptors.map((descriptor) => {
    const record = recordBySource.get(descriptor.sourceId);
    return validateSourceCandidateRecord(record, descriptor, { sourceIdentityDrift, sessionIds });
  });
  const reviews = initialReviews;
  const persistedCandidate = {
    ...(candidateShapeIsValid ? structuredClone(candidate) : {
      schemaVersion: 1,
      snapshotId: V7_SNAPSHOT_ID,
      previousSnapshotId: V6_SNAPSHOT_ID,
      sourceContentSha256: candidate?.sourceContentSha256,
      reviews: sourceOrder.map((sourceId) => ({ sourceId })),
    }),
    reviews: reviews.map((review) => ({
      ...recordBySource.get(review.descriptor.sourceId),
      sourceId: review.descriptor.sourceId,
      ...(review.candidateId ? { candidateId: review.candidateId } : {}),
      review: {
        schemaVersion: 1,
        sourceId: review.descriptor.sourceId,
        status: review.status,
        acceptance: review.acceptance,
        issues: review.issues,
        ...(review.normalized ? { normalized: review.normalized } : {}),
      },
    })),
  };
  return { candidate: persistedCandidate, reviews };
}

async function selectedCandidateValue(input, descriptors) {
  if (typeof input.selectionPath !== 'string' || !input.selectionPath) {
    fail('freeze requires a persisted explicit V7 selection path');
  }
  return validateV7Selection({
    selectionPath: input.selectionPath,
    candidateRoot: input.candidateRoot,
    descriptors,
  });
}

function sanitizedEnvironment() {
  for (const key of forbiddenEnvironmentKeys) {
    if (process.env[key]) fail(`forbidden API credential is present: ${key}`);
  }
  const environment = { ...process.env };
  for (const key of forbiddenEnvironmentKeys) delete environment[key];
  return environment;
}

function defaultCodexStateDirectoryPath() {
  return resolve(process.env.CODEX_HOME || join(homedir(), '.codex'));
}

function stateDatabaseVersion(path) {
  const match = basename(path).match(/^state(?:_(\d+))?\.sqlite$/u);
  return match ? Number(match[1] ?? 0) : -1;
}

function blockedStateWritePreflight(stateDirectoryPath, stateFilePath, capability, path, error) {
  const errorCode = typeof error?.code === 'string' && error.code ? error.code : null;
  const syscall = typeof error?.syscall === 'string' && error.syscall ? error.syscall : null;
  return {
    schemaVersion: 1,
    status: 'BLOCKED',
    stateDirectoryPath,
    stateFilePath,
    failure: {
      capability,
      path,
      message: errorCode || syscall
        ? `${errorCode ?? 'ERROR'}: ${syscall ?? 'filesystem operation'} failed`
        : failureMessage(error),
    },
  };
}

function validateStateWritePreflight(value) {
  const validBase = value?.schemaVersion === 1
    && (value.status === 'PASSED' || value.status === 'BLOCKED')
    && typeof value.stateDirectoryPath === 'string'
    && isAbsolute(value.stateDirectoryPath)
    && (value.stateFilePath === null
      || (typeof value.stateFilePath === 'string' && isAbsolute(value.stateFilePath)));
  const validFailure = value?.status === 'PASSED'
    ? value.failure === undefined
    : value.failure
      && (value.failure.capability === 'STATE_DIRECTORY_WRITE'
        || value.failure.capability === 'STATE_FILE_WRITE')
      && typeof value.failure.path === 'string'
      && isAbsolute(value.failure.path)
      && typeof value.failure.message === 'string'
      && value.failure.message.trim();
  if (!validBase || !validFailure) fail('Codex state write preflight returned an invalid result');
  return structuredClone(value);
}

/**
 * Verify the local capability needed by both `codex login status` and the
 * generation subprocess without mutating an existing Codex database.  The
 * transient probe is removed before this function returns; receipts retain
 * only the stable state directory/database paths, never its random name.
 */
export async function preflightCodexStateWriteCapability(input = {}) {
  const stateDirectoryPath = resolve(input.stateDirectoryPath ?? defaultCodexStateDirectoryPath());
  let stateFilePath = input.stateFilePath === null
    ? null
    : typeof input.stateFilePath === 'string'
      ? resolve(input.stateFilePath)
      : undefined;
  if (stateFilePath === undefined) {
    try {
      const stateFiles = (await readdir(stateDirectoryPath))
        .filter((entry) => /^state(?:_\d+)?\.sqlite$/u.test(entry))
        .sort((left, right) => stateDatabaseVersion(right) - stateDatabaseVersion(left)
          || right.localeCompare(left, 'en'));
      stateFilePath = stateFiles.length ? join(stateDirectoryPath, stateFiles[0]) : null;
    } catch (error) {
      return blockedStateWritePreflight(
        stateDirectoryPath,
        null,
        'STATE_DIRECTORY_WRITE',
        stateDirectoryPath,
        error,
      );
    }
  }

  let probeDirectoryPath;
  try {
    probeDirectoryPath = await mkdtemp(join(stateDirectoryPath, '.guanyijia-v7-state-preflight-'));
    await writeFile(join(probeDirectoryPath, 'write-probe'), 'codex-state-write-preflight\n', {
      encoding: 'utf8',
      flag: 'wx',
    });
    await rm(probeDirectoryPath, { recursive: true });
    probeDirectoryPath = undefined;
  } catch (error) {
    if (probeDirectoryPath) await rm(probeDirectoryPath, { recursive: true, force: true }).catch(() => {});
    return blockedStateWritePreflight(
      stateDirectoryPath,
      stateFilePath,
      'STATE_DIRECTORY_WRITE',
      stateDirectoryPath,
      error,
    );
  }

  if (stateFilePath) {
    let stateFile;
    try {
      stateFile = await open(stateFilePath, 'r+');
      await stateFile.close();
      stateFile = undefined;
    } catch (error) {
      if (stateFile) await stateFile.close().catch(() => {});
      return blockedStateWritePreflight(
        stateDirectoryPath,
        stateFilePath,
        'STATE_FILE_WRITE',
        stateFilePath,
        error,
      );
    }
  }

  return {
    schemaVersion: 1,
    status: 'PASSED',
    stateDirectoryPath,
    stateFilePath,
  };
}

async function assertChatGptLogin(environment) {
  const status = await execFile('codex', ['login', 'status'], {
    cwd: prototypeRoot,
    env: environment,
    encoding: 'utf8',
  });
  if (!`${status.stdout}\n${status.stderr}`.includes('Logged in using ChatGPT')) {
    fail('a logged-in ChatGPT Codex session is required; API authentication is not allowed');
  }
}

function optionalSessionIdFromJsonLines(value) {
  for (const line of value.split('\n')) {
    try {
      const event = JSON.parse(line);
      if (event.type === 'thread.started' && typeof event.thread_id === 'string' && event.thread_id) {
        return event.thread_id;
      }
    } catch { /* Non-JSON diagnostics are not session records. */ }
  }
  return undefined;
}

function sessionIdFromJsonLines(value) {
  return optionalSessionIdFromJsonLines(value) ?? fail('Codex did not return a ChatGPT session ID');
}

export function codexInvocationArgs(outputPath, input = {}) {
  const model = input.model ?? 'gpt-5.6-luna';
  const reasoningEffort = input.reasoningEffort ?? 'xhigh';
  const outputSchemaPath = input.outputSchemaPath ?? schemaPath;
  return [
    'exec',
    '--ephemeral',
    '--ignore-user-config',
    '-m', model,
    '-c', `model_reasoning_effort="${reasoningEffort}"`,
    '-s', 'read-only',
    '--output-schema', outputSchemaPath,
    '--output-last-message', outputPath,
    '--json',
    '-',
  ];
}

function runCodex(prompt, environment, outputPath, invocation) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn('codex', invocation ?? codexInvocationArgs(outputPath), {
      cwd: prototypeRoot,
      env: environment,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk.toString('utf8'); });
    child.stderr.on('data', (chunk) => { stderr += chunk.toString('utf8'); });
    child.once('error', rejectRun);
    child.once('close', (code) => {
      if (code === 0) {
        resolveRun({ stdout, stderr });
        return;
      }
      const failure = new Error(
        `Codex V7 generation exited with ${code}: ${[stderr.trim(), stdout.trim()].filter(Boolean).join('\n')}`,
      );
      const sessionId = optionalSessionIdFromJsonLines(stdout);
      if (sessionId) failure.sessionId = sessionId;
      rejectRun(failure);
    });
    child.stdin.end(prompt, 'utf8');
  });
}

function createCodexSessionAdapter() {
  const environment = sanitizedEnvironment();
  return {
    async prepare() {
      await assertChatGptLogin(environment);
    },
    async generate({ prompt, outputPath, invocation }) {
      const { stdout } = await runCodex(prompt, environment, outputPath, invocation);
      return { sessionId: sessionIdFromJsonLines(stdout), rawOutput: await readFile(outputPath, 'utf8') };
    },
  };
}

function assertCandidateId(candidateId) {
  if (typeof candidateId !== 'string' || !/^[a-z0-9][a-z0-9._-]{10,127}$/u.test(candidateId)) {
    fail('candidate ID must use lower-case letters, digits, dots, underscores, or hyphens');
  }
  return candidateId;
}

/**
 * Parse the public CLI's explicit `sourceId=candidateId` arguments.  The
 * source key is the only ordering authority: callers cannot accidentally
 * select a valid candidate for the wrong source by reordering five values.
 */
export function parseV7SourceCandidateAssignments(assignments) {
  if (!Array.isArray(assignments) || assignments.length !== sourceOrder.length) {
    fail('selection requires exactly one sourceId=candidateId assignment for every canonical source');
  }
  const candidates = {};
  for (const assignment of assignments) {
    if (typeof assignment !== 'string') fail('selection assignment must use sourceId=candidateId');
    const separator = assignment.indexOf('=');
    if (separator <= 0 || separator !== assignment.lastIndexOf('=')) {
      fail('selection assignment must use sourceId=candidateId');
    }
    const sourceId = assignment.slice(0, separator);
    const candidateId = assignment.slice(separator + 1);
    if (!sourceOrder.includes(sourceId)) fail(`selection assignment has an unknown source: ${sourceId}`);
    if (Object.hasOwn(candidates, sourceId)) fail(`selection assignment repeats source: ${sourceId}`);
    candidates[sourceId] = assertCandidateId(candidateId);
  }
  if (!exactMembers(Object.keys(candidates), sourceOrder)) {
    fail('selection assignments must include every canonical source exactly once');
  }
  return Object.fromEntries(sourceOrder.map((sourceId) => [sourceId, candidates[sourceId]]));
}

/** Parse the one explicit Sol refinement request for a fatal Round 1 candidate. */
export function parseV7PromptRefinementArguments(args) {
  if (!Array.isArray(args) || args.length < 3) {
    fail('prompt refinement requires <source-id> <parent-candidate-id> <fatal-finding-id>...');
  }
  const [sourceId, parentCandidateId, ...findingIds] = args;
  if (!sourceOrder.includes(sourceId) || !assertCandidateId(parentCandidateId)
    || findingIds.some((findingId) => typeof findingId !== 'string' || !findingId.trim())) {
    fail('prompt refinement requires <source-id> <parent-candidate-id> <fatal-finding-id>...');
  }
  return { sourceId, parentCandidateId, findingIds };
}

/** Parse the only allowable product-content replacement after a Sol revision. */
export function parseV7RoundTwoArguments(args) {
  if (!Array.isArray(args) || args.length !== 3) {
    fail('Round 2 requires <source-id> <parent-candidate-id> <prompt-revision-id>');
  }
  const [sourceId, parentCandidateId, promptRevisionId] = args;
  if (!sourceOrder.includes(sourceId) || !assertCandidateId(parentCandidateId)
    || !assertCandidateId(promptRevisionId)) {
    fail('Round 2 requires <source-id> <parent-candidate-id> <prompt-revision-id>');
  }
  return { sourceId, parentCandidateId, promptRevisionId };
}

/**
 * Parse the public freeze contract. The selection is intentionally named on
 * the command line: a freeze must never infer or silently discover a
 * candidate batch from the filesystem.
 */
export function parseV7FreezeArguments(args) {
  if (!Array.isArray(args) || (args.length !== 2 && args.length !== 4) || args[0] !== '--selection') {
    fail('freeze requires --selection <selection.json>');
  }
  const selectionPath = args[1];
  if (typeof selectionPath !== 'string' || !selectionPath) {
    fail('freeze requires --selection <selection.json>');
  }
  if (args.length === 2) return { selectionPath };
  if (args[2] !== '--target' || typeof args[3] !== 'string' || !args[3]) {
    fail('freeze accepts only --selection <selection.json> [--target <snapshot-root>]');
  }
  return { selectionPath, targetSnapshotRoot: args[3] };
}

function sourceCandidateId(descriptor, generation, rawOutput) {
  const digest = sha256(canonicalJson({
    sourceId: descriptor.sourceId,
    sourceSnapshotId: descriptor.sourceSnapshotId,
    sourceContentSha256: descriptor.sourceContentSha256,
    generation,
    rawOutput,
  })).slice(7, 31);
  return `v7-${descriptor.sourceId}-${digest}`;
}

function candidatePaths(candidateRoot, candidateId) {
  const root = resolve(candidateRoot);
  const id = assertCandidateId(candidateId);
  const candidateRootPath = resolve(root, id);
  if (!isSameOrDescendant(root, candidateRootPath) || candidateRootPath === root) {
    fail('candidate path escapes the V7 candidate root');
  }
  return {
    root,
    candidateRootPath,
    rawOutputPath: join(candidateRootPath, 'raw-output.txt'),
    receiptPath: join(candidateRootPath, 'receipt.json'),
    normalizedPath: join(candidateRootPath, 'normalized.json'),
    reviewPath: join(candidateRootPath, 'review-report.json'),
    renderedReviewPath: join(candidateRootPath, 'rendered-review.md'),
  };
}

function candidateReceipt(candidateId, descriptor, generation, rawOutput, failure, stateWritePreflight, modelSessionStarted) {
  return {
    schemaVersion: 2,
    candidateId,
    snapshotId: V7_SNAPSHOT_ID,
    previousSnapshotId: V6_SNAPSHOT_ID,
    sourceId: descriptor.sourceId,
    sourceSnapshotId: descriptor.sourceSnapshotId,
    sourceContentSha256: descriptor.sourceContentSha256,
    generation,
    lineage: generation.lineage,
    attempt: { modelSessionStarted: Boolean(modelSessionStarted) },
    rawOutputSha256: sha256(rawOutput),
    outcome: failure ? 'FAILED' : 'COMPLETED',
    ...(stateWritePreflight
      ? { stateWritePreflight: validateStateWritePreflight(stateWritePreflight) }
      : {}),
    ...(failure ? { failure: { message: failure.message } } : {}),
  };
}

function candidateReviewArtifact(candidateId, sourceReview) {
  return {
    schemaVersion: sourceReview.normalized?.schemaVersion ?? 2,
    candidateId,
    sourceId: sourceReview.descriptor.sourceId,
    status: sourceReview.status,
    acceptance: sourceReview.acceptance,
    issues: sourceReview.issues,
    ...(sourceReview.normalized ? { normalized: sourceReview.normalized } : {}),
  };
}

function candidateNormalizedArtifact(candidateId, sourceReview) {
  return {
    schemaVersion: sourceReview.normalized?.schemaVersion ?? 2,
    candidateId,
    sourceId: sourceReview.descriptor.sourceId,
    status: sourceReview.status,
    acceptance: sourceReview.acceptance,
    ...(sourceReview.chapters ? { chapters: sourceReview.chapters } : { sections: sourceReview.narratives ?? null }),
  };
}

function renderedReviewMarkdown(descriptor, sourceReview) {
  if (!sourceReview.narratives) return '# V7 阅读候选\n\n候选未通过结构化校验，不能提供阅读版。\n';
  return [
    `# ${descriptor.readerLabel}审阅候选`,
    '本阅读版由冻结资料上的结构化候选确定性排版；技术依据由页面按结论展开。',
    ...standardSectionOrder.map(({ key, heading }) => {
      const section = sourceReview.narratives.find((item) => item.sectionId === key);
      return `## ${heading}\n\n${section?.narrative ?? '本章没有可用阅读内容。'}`;
    }),
  ].join('\n\n');
}

async function writeSourceCandidateArtifacts(candidateRoot, candidateId, descriptor, record, sourceReview) {
  const paths = candidatePaths(candidateRoot, candidateId);
  await assertDoesNotExist(paths.candidateRootPath, 'source candidate');
  await mkdir(paths.root, { recursive: true });
  const stage = await mkdtemp(join(paths.root, `.${candidateId}.staging-`));
  let published = false;
  try {
    const receipt = candidateReceipt(
      candidateId,
      descriptor,
      record.generation,
      record.rawOutput,
      record.failure,
      record.stateWritePreflight,
      record.modelSessionStarted,
    );
    const review = candidateReviewArtifact(candidateId, sourceReview);
    const normalized = candidateNormalizedArtifact(candidateId, sourceReview);
    const renderedReview = renderedReviewMarkdown(descriptor, sourceReview);
    await writeFile(join(stage, 'raw-output.txt'), record.rawOutput, { encoding: 'utf8', flag: 'wx' });
    await writeFile(join(stage, 'receipt.json'), `${JSON.stringify(receipt, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
    await writeFile(join(stage, 'normalized.json'), `${JSON.stringify(normalized, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
    await writeFile(join(stage, 'review-report.json'), `${JSON.stringify(review, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
    await writeFile(join(stage, 'rendered-review.md'), renderedReview, { encoding: 'utf8', flag: 'wx' });
    await rename(stage, paths.candidateRootPath);
    published = true;
    return { receipt, normalized, review };
  } finally {
    if (!published) await rm(stage, { recursive: true, force: true });
  }
}

/**
 * Persist one immutable source candidate.  It intentionally writes a complete
 * four-artifact record even when validation is BLOCKED, so a maintainer can
 * select the other four candidates and explicitly regenerate only this source.
 */
export async function persistV7SourceCandidate(input = {}) {
  const descriptors = input.descriptors ?? await loadV6NarrativeDescriptors(input);
  const descriptor = descriptors.find((candidate) => candidate.sourceId === input.sourceId);
  if (!descriptor) fail('a canonical V7 source ID is required for candidate persistence');
  if (typeof input.sessionId !== 'string' || !input.sessionId || typeof input.rawOutput !== 'string') {
    fail(`${descriptor.sourceId} source candidate requires a session ID and raw output`);
  }
  const rawRecord = {
    sourceId: descriptor.sourceId,
    sessionId: input.sessionId,
    rawOutput: input.rawOutput,
    ...(input.generationRound === undefined ? {} : { generationRound: input.generationRound }),
    ...(input.parentCandidateId ? { parentCandidateId: input.parentCandidateId } : {}),
    ...(input.promptRevision ? { promptRevision: input.promptRevision } : {}),
    modelSessionStarted: input.modelSessionStarted ?? true,
  };
  const lineage = contentLineage(rawRecord, descriptor);
  await verifyRoundTwoLineage({ ...input, descriptors }, descriptor, lineage);
  const generation = generationFor(descriptor, rawRecord);
  const candidateId = input.candidateId ?? sourceCandidateId(descriptor, generation, input.rawOutput);
  const record = {
    ...rawRecord,
    candidateId,
    generation,
    ...(input.stateWritePreflight
      ? { stateWritePreflight: validateStateWritePreflight(input.stateWritePreflight) }
      : {}),
  };
  const sourceReview = validateSourceCandidateRecord(record, descriptor, {
    sourceIdentityDrift: false,
    sessionIds: new Set(),
  });
  const artifacts = await writeSourceCandidateArtifacts(
    input.candidateRoot ?? defaultCandidateRoot,
    candidateId,
    descriptor,
    record,
    sourceReview,
  );
  return {
    candidateId,
    sourceId: descriptor.sourceId,
    generation,
    rawOutput: input.rawOutput,
    receipt: artifacts.receipt,
    normalized: artifacts.normalized,
    review: artifacts.review,
  };
}

function failureMessage(error) {
  const value = error instanceof Error ? error.message : String(error ?? 'unknown Codex session failure');
  const normalized = value
    .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/gu, ' ')
    .replace(/\s+/gu, ' ')
    .trim();
  return (normalized || 'Codex session failed before a readable reader narrative was saved').slice(0, 2000);
}

/**
 * Persist a failed explicit Codex source call as an immutable blocked
 * candidate.  This keeps the receipt/report for diagnosis and allows the
 * other four persisted candidates to remain available for a later explicit
 * replacement; a failed candidate can never pass selection or freeze.
 */
export async function persistV7FailedSourceCandidate(input = {}) {
  const descriptors = input.descriptors ?? await loadV6NarrativeDescriptors(input);
  const descriptor = descriptors.find((candidate) => candidate.sourceId === input.sourceId);
  if (!descriptor) fail('a canonical V7 source ID is required for failed candidate persistence');
  const rawOutput = typeof input.rawOutput === 'string' ? input.rawOutput : '';
  const rawRecord = {
    sourceId: descriptor.sourceId,
    ...(typeof input.sessionId === 'string' && input.sessionId ? { sessionId: input.sessionId } : {}),
    rawOutput,
    ...(input.generationRound === undefined ? {} : { generationRound: input.generationRound }),
    ...(input.parentCandidateId ? { parentCandidateId: input.parentCandidateId } : {}),
    ...(input.promptRevision ? { promptRevision: input.promptRevision } : {}),
    modelSessionStarted: input.modelSessionStarted ?? Boolean(input.sessionId),
  };
  const generation = generationFor(descriptor, rawRecord);
  const failure = { message: failureMessage(input.failure) };
  const candidateId = input.candidateId ?? sourceCandidateId(descriptor, generation, rawOutput);
  const record = {
    ...rawRecord,
    candidateId,
    generation,
    failure,
    ...(input.stateWritePreflight
      ? { stateWritePreflight: validateStateWritePreflight(input.stateWritePreflight) }
      : {}),
  };
  const sourceReview = validateSourceCandidateRecord(record, descriptor, {
    sourceIdentityDrift: false,
    sessionIds: new Set(),
  });
  const artifacts = await writeSourceCandidateArtifacts(
    input.candidateRoot ?? defaultCandidateRoot,
    candidateId,
    descriptor,
    record,
    sourceReview,
  );
  return {
    candidateId,
    sourceId: descriptor.sourceId,
    generation,
    rawOutput,
    failure,
    receipt: artifacts.receipt,
    normalized: artifacts.normalized,
    review: artifacts.review,
  };
}

/**
 * The only model-calling V7 command: one explicitly named source, one call,
 * and no retry.  Re-running it is itself the maintainer's explicit decision.
 */
export async function regenerateV7SourceCandidate(input = {}) {
  if (!sourceOrder.includes(input.sourceId)) fail('a canonical source ID is required for explicit V7 regeneration');
  const descriptors = await loadV6NarrativeDescriptors(input);
  const descriptor = descriptors.find((candidate) => candidate.sourceId === input.sourceId);
  const codexSession = input.codexSession ?? createCodexSessionAdapter();
  const codexStatePreflight = input.codexStatePreflight ?? preflightCodexStateWriteCapability;
  if (!codexSession || typeof codexSession.prepare !== 'function' || typeof codexSession.generate !== 'function') {
    fail('ChatGPT session adapter is invalid');
  }
  if (typeof codexStatePreflight !== 'function') fail('Codex state write preflight is invalid');
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-source-call-'));
  let stateWritePreflight;
  let modelSessionStarted = false;
  try {
    const outputPath = join(temporaryRoot, `${descriptor.sourceId}.json`);
    try {
      try {
        stateWritePreflight = validateStateWritePreflight(await codexStatePreflight());
      } catch (error) {
        stateWritePreflight = blockedStateWritePreflight(
          defaultCodexStateDirectoryPath(),
          null,
          'STATE_DIRECTORY_WRITE',
          defaultCodexStateDirectoryPath(),
          error,
        );
      }
      if (stateWritePreflight.status === 'BLOCKED') {
        throw new Error(
          `Codex state write preflight blocked ${stateWritePreflight.failure.capability}`
          + ` at ${stateWritePreflight.failure.path}: ${stateWritePreflight.failure.message}`,
        );
      }
      // Login preparation is not a model content attempt. A candidate round
      // starts only when Codex reports thread.started; a pre-start process
      // failure remains an immutable diagnostic and does not consume a round.
      await codexSession.prepare();
      const result = await codexSession.generate({
        descriptor,
        prompt: narrativePrompt(descriptor, input.promptRevision),
        outputPath,
      });
      if (!result || typeof result.sessionId !== 'string' || typeof result.rawOutput !== 'string') {
        fail(`${descriptor.sourceId} ChatGPT session returned an invalid result`);
      }
      modelSessionStarted = true;
      return persistV7SourceCandidate({
        ...input,
        descriptors,
        sourceId: descriptor.sourceId,
        sessionId: result.sessionId,
        rawOutput: result.rawOutput,
        stateWritePreflight,
        modelSessionStarted,
      });
    } catch (error) {
      const rawOutput = await readFile(outputPath, 'utf8').catch(() => '');
      const sessionId = typeof error?.sessionId === 'string' && error.sessionId
        ? error.sessionId
        : undefined;
      return persistV7FailedSourceCandidate({
        ...input,
        descriptors,
        sourceId: descriptor.sourceId,
        rawOutput,
        failure: error,
        stateWritePreflight,
        ...(sessionId ? { sessionId } : {}),
        modelSessionStarted: modelSessionStarted || Boolean(sessionId),
      });
    }
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
}

export async function loadV7SourceCandidate(input = {}) {
  const candidateRoot = input.candidateRoot ?? defaultCandidateRoot;
  const candidateId = assertCandidateId(input.candidateId);
  const descriptors = input.descriptors ?? await loadV6NarrativeDescriptors(input);
  const paths = candidatePaths(candidateRoot, candidateId);
  const [rawOutput, receipt, normalized, persistedReview] = await Promise.all([
    readFile(paths.rawOutputPath, 'utf8').catch((error) => fail(`candidate raw output is unavailable: ${candidateId} (${error.code ?? error.message})`)),
    readJson(paths.receiptPath),
    readJson(paths.normalizedPath),
    readJson(paths.reviewPath),
  ]);
  const descriptor = descriptors.find((candidate) => candidate.sourceId === receipt?.sourceId);
  if (!descriptor) fail(`candidate receipt has an unknown source: ${candidateId}`);
  const record = {
    candidateId,
    sourceId: descriptor.sourceId,
    generation: receipt.generation,
    rawOutput,
    ...(receipt.attempt ? { modelSessionStarted: receipt.attempt.modelSessionStarted } : {}),
    ...(receipt.stateWritePreflight ? { stateWritePreflight: receipt.stateWritePreflight } : {}),
    ...(receipt.outcome === 'FAILED' ? { failure: receipt.failure } : {}),
  };
  const expectedReceipt = candidateReceipt(
    candidateId,
    descriptor,
    record.generation,
    rawOutput,
    record.failure,
    record.stateWritePreflight,
    record.modelSessionStarted,
  );
  const legacyExpectedReceipt = structuredClone(expectedReceipt);
  delete legacyExpectedReceipt.attempt;
  if (canonicalJson(receipt) !== canonicalJson(expectedReceipt)
    && canonicalJson(receipt) !== canonicalJson(legacyExpectedReceipt)) {
    fail(`candidate receipt does not bind its raw output and V6 identity: ${candidateId}`);
  }
  const review = validateSourceCandidateRecord(record, descriptor, {
    sourceIdentityDrift: false,
    sessionIds: new Set(),
  });
  const expectedNormalized = candidateNormalizedArtifact(candidateId, review);
  const expectedReview = candidateReviewArtifact(candidateId, review);
  if (canonicalJson(normalized) !== canonicalJson(expectedNormalized)) {
    fail(`candidate normalized artifact drifted from raw output: ${candidateId}`);
  }
  if (canonicalJson(persistedReview) !== canonicalJson(expectedReview)) {
    fail(`candidate review report drifted from raw output: ${candidateId}`);
  }
  return { ...record, receipt, normalized, review: expectedReview };
}

function promptRevisionPaths(candidateRoot, revisionId) {
  const root = resolve(candidateRoot, 'prompt-revisions');
  const id = assertCandidateId(revisionId);
  const revisionRoot = resolve(root, id);
  if (!isSameOrDescendant(root, revisionRoot) || revisionRoot === root) {
    fail('prompt revision path escapes the V7 candidate root');
  }
  return {
    root,
    revisionRoot,
    rawOutputPath: join(revisionRoot, 'raw-output.txt'),
    receiptPath: join(revisionRoot, 'receipt.json'),
    normalizedPath: join(revisionRoot, 'normalized.json'),
    reviewPath: join(revisionRoot, 'review-report.json'),
  };
}

function promptRevisionPrompt(descriptor, parent, fatalFindingIds) {
  return [
    '角色：你是生成质量的提示词诊断员。只输出符合 JSON Schema 的 JSON，不要生成产品正文、Markdown 或代码围栏。',
    '你的任务是为一次失败的 Luna 阅读候选写最小 corrective addendum。每条致命 finding 都必须有可执行指令和原因。',
    `不可变核心（必须逐条原样保留）：${JSON.stringify(ImmutablePromptConstraints)}`,
    '不得加入输入没有的新事实、证据、文件、行号、来源身份或业务结论；不得删除或弱化不可变核心；不得建议绕过 Claim、Gap、Schema 或证据验证。',
    `来源：${descriptor.sourceId}。父候选：${parent.candidateId}。基础提示词：${parent.generation.promptVersion}。`,
    `必须修复的 finding：${JSON.stringify(fatalFindingIds)}`,
    `父候选审查问题：${JSON.stringify(parent.review.issues.filter((issue) => fatalFindingIds.includes(issue.code)))}`,
    `父候选原始输出：${parent.rawOutput}`,
  ].join('\n\n');
}

/**
 * Ask Sol Ultra for a bounded corrective addendum. This operation produces no
 * product content and is only callable for a persisted fatal Round 1 source.
 */
export async function refineV7Prompt(input = {}) {
  if (!sourceOrder.includes(input.sourceId) || typeof input.parentCandidateId !== 'string' || !input.parentCandidateId) {
    fail('prompt refinement requires one canonical source and its persisted Round 1 parent candidate');
  }
  if (!Array.isArray(input.findingIds) || !input.findingIds.length) {
    fail('prompt refinement requires explicit fatal finding IDs');
  }
  const descriptors = input.descriptors ?? await loadV6NarrativeDescriptors(input);
  const descriptor = descriptors.find((candidate) => candidate.sourceId === input.sourceId);
  const candidateRoot = input.candidateRoot ?? defaultCandidateRoot;
  const parent = await loadV7SourceCandidate({
    candidateRoot,
    candidateId: input.parentCandidateId,
    descriptors,
  });
  if (parent.sourceId !== descriptor.sourceId || parent.receipt.lineage?.generationRound !== ContentGenerationRound.ONE
    || parent.review.acceptance !== CandidateAcceptance.FATAL) {
    fail('prompt refinement requires a fatal Round 1 candidate from the same source');
  }
  const expectedFindingIds = parent.review.issues
    .filter((issue) => issue.class === CandidateIssueClass.CRITICAL)
    .map((issue) => issue.code);
  if (!exactMembers(input.findingIds, expectedFindingIds)) {
    fail('prompt refinement must receive every fatal finding from the parent candidate');
  }
  const codexSession = input.codexSession ?? createCodexSessionAdapter();
  const codexStatePreflight = input.codexStatePreflight ?? preflightCodexStateWriteCapability;
  if (!codexSession || typeof codexSession.prepare !== 'function' || typeof codexSession.generate !== 'function'
    || typeof codexStatePreflight !== 'function') {
    fail('prompt refinement requires valid ChatGPT and state-preflight adapters');
  }
  const stateWritePreflight = validateStateWritePreflight(await codexStatePreflight());
  if (stateWritePreflight.status === 'BLOCKED') {
    fail(`Sol prompt refinement preflight is blocked at ${stateWritePreflight.failure.path}`);
  }
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'guanyijia-v7-prompt-revision-'));
  try {
    const outputPath = join(temporaryRoot, `${descriptor.sourceId}.json`);
    await codexSession.prepare();
    const prompt = promptRevisionPrompt(descriptor, parent, input.findingIds);
    const result = await codexSession.generate({
      descriptor,
      prompt,
      outputPath,
      invocation: codexInvocationArgs(outputPath, {
        model: 'gpt-5.6-sol',
        reasoningEffort: 'ultra',
        outputSchemaPath: promptRevisionSchemaPath,
      }),
    });
    if (!result || typeof result.sessionId !== 'string' || typeof result.rawOutput !== 'string') {
      fail('Sol prompt refinement returned an invalid ChatGPT session result');
    }
    let proposal;
    try {
      proposal = validatePromptRevisionProposal(JSON.parse(result.rawOutput));
    } catch (error) {
      fail(`Sol prompt refinement output is invalid: ${error instanceof Error ? error.message : String(error)}`);
    }
    if (proposal.sourceId !== descriptor.sourceId || proposal.parentCandidateId !== parent.candidateId
      || proposal.basePromptVersion !== parent.generation.promptVersion
      || !exactMembers(proposal.fatalFindingIds, input.findingIds)) {
      fail('Sol prompt refinement output does not bind the required source, parent, prompt or fatal findings');
    }
    const paths = promptRevisionPaths(candidateRoot, proposal.promptRevisionId);
    await assertDoesNotExist(paths.revisionRoot, 'prompt revision');
    await mkdir(paths.root, { recursive: true });
    const stage = await mkdtemp(join(paths.root, `.${proposal.promptRevisionId}.staging-`));
    let published = false;
    try {
      const generation = {
        provider: 'CODEX_CHATGPT_SESSION',
        model: 'gpt-5.6-sol',
        reasoningEffort: 'ultra',
        sessionId: result.sessionId,
        promptVersion: `${parent.generation.promptVersion}-sol-revision-1`,
        inputDigest: sha256(prompt),
        outputDigest: sha256(result.rawOutput),
      };
      const receipt = {
        schemaVersion: 1,
        promptRevisionId: proposal.promptRevisionId,
        sourceId: descriptor.sourceId,
        parentCandidateId: parent.candidateId,
        sourceContentSha256: descriptor.sourceContentSha256,
        generation,
        stateWritePreflight,
      };
      await writeFile(join(stage, 'raw-output.txt'), result.rawOutput, { encoding: 'utf8', flag: 'wx' });
      await writeFile(join(stage, 'normalized.json'), `${JSON.stringify(proposal, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
      await writeFile(join(stage, 'review-report.json'), `${JSON.stringify({ schemaVersion: 1, promptRevisionId: proposal.promptRevisionId, status: 'READY', issues: [] }, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
      await writeFile(join(stage, 'receipt.json'), `${JSON.stringify(receipt, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
      await rename(stage, paths.revisionRoot);
      published = true;
      return { proposal, receipt };
    } finally {
      if (!published) await rm(stage, { recursive: true, force: true });
    }
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
}

/** Read and verify one immutable Sol prompt revision before a Round 2 call. */
export async function loadV7PromptRevision(input = {}) {
  if (typeof input.promptRevisionId !== 'string' || !input.promptRevisionId) {
    fail('prompt revision ID is required');
  }
  const paths = promptRevisionPaths(input.candidateRoot ?? defaultCandidateRoot, input.promptRevisionId);
  const [receipt, normalized, review] = await Promise.all([
    readJson(paths.receiptPath),
    readJson(paths.normalizedPath),
    readJson(paths.reviewPath),
  ]);
  const proposal = validatePromptRevisionProposal(normalized);
  if (receipt?.schemaVersion !== 1
    || receipt?.promptRevisionId !== proposal.promptRevisionId
    || receipt?.sourceId !== proposal.sourceId
    || receipt?.parentCandidateId !== proposal.parentCandidateId
    || receipt?.generation?.model !== 'gpt-5.6-sol'
    || receipt?.generation?.reasoningEffort !== 'ultra'
    || review?.schemaVersion !== 1
    || review?.promptRevisionId !== proposal.promptRevisionId
    || review?.status !== 'READY') {
    fail(`prompt revision is not a verified Sol Ultra receipt: ${input.promptRevisionId}`);
  }
  return { proposal, receipt, review };
}

async function verifyRoundTwoLineage(input, descriptor, lineage) {
  if (lineage.generationRound !== ContentGenerationRound.TWO) return;
  const candidateRoot = input.candidateRoot ?? defaultCandidateRoot;
  const parent = await loadV7SourceCandidate({
    candidateRoot,
    candidateId: lineage.parentCandidateId,
    descriptors: input.descriptors,
  });
  if (parent.sourceId !== descriptor.sourceId
    || parent.receipt.sourceContentSha256 !== descriptor.sourceContentSha256
    || parent.receipt.lineage?.generationRound !== ContentGenerationRound.ONE) {
    fail('Round 2 parent candidate must use the same source and frozen V6 input digest');
  }
  const paths = promptRevisionPaths(candidateRoot, lineage.promptRevisionId);
  const [receipt, normalized] = await Promise.all([
    readJson(paths.receiptPath),
    readJson(paths.normalizedPath),
  ]);
  const proposal = validatePromptRevisionProposal(normalized);
  if (receipt?.promptRevisionId !== lineage.promptRevisionId
    || receipt?.sourceId !== descriptor.sourceId
    || receipt?.parentCandidateId !== lineage.parentCandidateId
    || receipt?.sourceContentSha256 !== descriptor.sourceContentSha256
    || receipt?.generation?.model !== 'gpt-5.6-sol'
    || receipt?.generation?.reasoningEffort !== 'ultra'
    || canonicalJson(proposal) !== canonicalJson(lineage.promptRevision)) {
    fail('Round 2 must bind one verified Sol Ultra prompt revision for the same source and frozen input');
  }
}

function selectionShape(selection) {
  return selection && selection.schemaVersion === 1
    && selection.snapshotId === V7_SNAPSHOT_ID
    && selection.previousSnapshotId === V6_SNAPSHOT_ID
    && typeof selection.sourceContentSha256 === 'string'
    && selection.candidates
    && typeof selection.candidates === 'object'
    && !Array.isArray(selection.candidates)
    && exactMembers(Object.keys(selection.candidates), sourceOrder)
    && new Set(Object.values(selection.candidates)).size === sourceOrder.length
    && Object.values(selection.candidates).every((candidateId) => (
      typeof candidateId === 'string' && /^[a-z0-9][a-z0-9._-]{10,127}$/u.test(candidateId)
    ));
}

async function selectionRecords(selection, descriptors, candidateRoot) {
  if (!selectionShape(selection)) {
    fail('selection must bind the V6 digest and exactly one candidate ID for every canonical source');
  }
  if (selection.sourceContentSha256 !== descriptors[0]?.sourceContentSha256) {
    fail('selection V6 digest drifted from the currently frozen V6 snapshot');
  }
  const loaded = await Promise.all(sourceOrder.map(async (sourceId) => {
    const candidate = await loadV7SourceCandidate({
      candidateRoot,
      candidateId: selection.candidates[sourceId],
      descriptors,
    });
    if (candidate.sourceId !== sourceId) {
      fail(`selection candidate does not belong to ${sourceId}: ${selection.candidates[sourceId]}`);
    }
    return candidate;
  }));
  return loaded;
}

async function writeSelectionAtomically(selectionPath, selection) {
  await assertDoesNotExist(selectionPath, 'selection');
  const selectionDirectory = dirname(selectionPath);
  await mkdir(selectionDirectory, { recursive: true });
  const stage = await mkdtemp(join(selectionDirectory, `.${basename(selectionPath)}.staging-`));
  let published = false;
  try {
    const stagedSelectionPath = join(stage, basename(selectionPath));
    await writeFile(stagedSelectionPath, `${JSON.stringify(selection, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
    await assertDoesNotExist(selectionPath, 'selection');
    await rename(stagedSelectionPath, selectionPath);
    published = true;
  } finally {
    await rm(stage, { recursive: true, force: true });
    if (!published) {
      // The final destination has never been overwritten.  `stage` is always
      // cleaned so a failed maintenance run cannot be mistaken for a saved
      // source selection.
    }
  }
}

/** Build and optionally write an immutable selection; it does not freeze. */
export async function createV7Selection(input = {}) {
  const descriptors = input.descriptors ?? await loadV6NarrativeDescriptors(input);
  if (!input.candidates || typeof input.candidates !== 'object' || Array.isArray(input.candidates)
    || !exactMembers(Object.keys(input.candidates), sourceOrder)) {
    fail('selection requires a source-keyed candidate map for every canonical source');
  }
  const proposed = {
    schemaVersion: 1,
    snapshotId: V7_SNAPSHOT_ID,
    previousSnapshotId: V6_SNAPSHOT_ID,
    sourceContentSha256: descriptors[0].sourceContentSha256,
    candidates: Object.fromEntries(sourceOrder.map((sourceId) => [sourceId, input.candidates[sourceId]])),
  };
  const records = await selectionRecords(proposed, descriptors, input.candidateRoot ?? defaultCandidateRoot);
  const selection = {
    ...proposed,
    candidates: Object.fromEntries(records.map((record) => [record.sourceId, record.candidateId])),
  };
  if (input.selectionPath !== undefined) {
    if (typeof input.selectionPath !== 'string' || !input.selectionPath) fail('selection path must be an explicit file path');
    const selectionPath = resolve(input.selectionPath);
    await writeSelectionAtomically(selectionPath, selection);
  }
  return selection;
}

/** Prevalidate all persisted candidates referenced by an explicit selection. */
export async function validateV7Selection(input = {}) {
  const descriptors = input.descriptors ?? await loadV6NarrativeDescriptors(input);
  const selection = input.selection ?? await readJson(resolve(input.selectionPath ?? fail('selection path is required')));
  const records = await selectionRecords(selection, descriptors, input.candidateRoot ?? defaultCandidateRoot);
  const candidate = {
    schemaVersion: 1,
    snapshotId: V7_SNAPSHOT_ID,
    previousSnapshotId: V6_SNAPSHOT_ID,
    sourceContentSha256: selection.sourceContentSha256,
    reviews: records.map((record) => ({
      candidateId: record.candidateId,
      sourceId: record.sourceId,
      generation: record.generation,
      rawOutput: record.rawOutput,
      ...(record.failure ? { failure: record.failure } : {}),
    })),
  };
  const validated = validateV7Candidate(candidate, descriptors);
  for (const review of validated.reviews) {
    const persisted = records.find((record) => record.sourceId === review.descriptor.sourceId)?.review;
    const expected = candidateReviewArtifact(review.candidateId, review);
    if (canonicalJson(persisted) !== canonicalJson(expected)) {
      fail(`selection candidate review does not match aggregate V7 validation: ${review.candidateId}`);
    }
  }
  return { selection: structuredClone(selection), ...validated };
}

function replaceArtifact(artifacts, replacement) {
  const matches = artifacts.filter((entry) => entry.path === replacement.path);
  if (matches.length !== 1) fail(`expected exactly one artifact to replace: ${replacement.path}`);
  return artifacts.map((entry) => entry.path === replacement.path ? replacement : entry);
}

function exactArtifactPaths(artifacts, expectedPaths, sourceId) {
  const paths = artifacts.map((entry) => entry?.path);
  if (!exactMembers(paths, expectedPaths)) {
    fail(`V7 artifact lineage has an unexpected, missing, or duplicate artifact: ${sourceId}`);
  }
}

function sourceIdentity(source) {
  const { artifacts, ...identity } = source ?? {};
  return identity;
}

async function assertV7ArtifactLineage({ snapshotRoot, sourceSnapshotRoot, manifest, v6Manifest }) {
  if (!Array.isArray(manifest.sources) || !Array.isArray(v6Manifest.sources)
    || !exactMembers(manifest.sources.map((source) => source?.sourceId), sourceOrder)
    || !exactMembers(v6Manifest.sources.map((source) => source?.sourceId), sourceOrder)
    || manifest.sources.map((source) => source.sourceId).join('\n') !== v6Manifest.sources.map((source) => source.sourceId).join('\n')) {
    fail('V7 source lineage membership or order is invalid');
  }

  const v6Sources = new Map(v6Manifest.sources.map((source) => [source.sourceId, source]));
  for (const source of manifest.sources) {
    const sourceId = source.sourceId;
    const previous = v6Sources.get(sourceId);
    const reviewPath = reviewPathBySource[sourceId];
    const narrativePath = narrativePathBySource[sourceId];
    if (!previous || !reviewPath || !narrativePath
      || canonicalJson(sourceIdentity(source)) !== canonicalJson(sourceIdentity(previous))) {
      fail(`V7 source identity drifted from V6: ${sourceId}`);
    }
    if (!Array.isArray(source.artifacts) || !Array.isArray(previous.artifacts)) {
      fail(`V7 source artifact declarations are invalid: ${sourceId}`);
    }

    const previousPaths = previous.artifacts.map((entry) => entry.path);
    if (!source.artifacts.some((entry) => entry?.path === narrativePath)) {
      fail(`V7 reader narrative artifact is missing: ${sourceId}`);
    }
    exactArtifactPaths(source.artifacts, [...previousPaths, narrativePath], sourceId);
    const artifactsByPath = new Map(source.artifacts.map((entry) => [entry.path, entry]));
    const previousByPath = new Map(previous.artifacts.map((entry) => [entry.path, entry]));

    for (const [path, inherited] of previousByPath) {
      const current = artifactsByPath.get(path);
      if (!current || current.mediaType !== inherited.mediaType) {
        fail(`V7 inherited artifact declaration drifted from V6: ${path}`);
      }
      if (path === reviewPath) continue;
      if (current.sha256 !== inherited.sha256) {
        fail(`V7 inherited artifact hash drifted from V6: ${path}`);
      }
      const [inheritedBytes, currentBytes] = await Promise.all([
        readFile(join(sourceSnapshotRoot, path)),
        readFile(join(snapshotRoot, path)),
      ]);
      if (!inheritedBytes.equals(currentBytes)) {
        fail(`V7 inherited artifact bytes drifted from V6: ${path}`);
      }
    }

    const narrative = artifactsByPath.get(narrativePath);
    if (!narrative || narrative.mediaType !== 'application/json' || previousByPath.has(narrativePath)) {
      fail(`V7 reader narrative artifact lineage is invalid: ${sourceId}`);
    }
  }
}

export async function freezeV7Snapshot(input = {}) {
  const sourceSnapshotRoot = resolve(input.sourceSnapshotRoot ?? defaultSourceSnapshotRoot);
  const targetSnapshotRoot = resolve(input.targetSnapshotRoot ?? defaultTargetSnapshotRoot);
  assertDisjointSnapshotRoots(sourceSnapshotRoot, targetSnapshotRoot);
  await assertDoesNotExist(targetSnapshotRoot, 'append-only V7 snapshot');
  const descriptors = await loadV6NarrativeDescriptors({ sourceSnapshotRoot });
  const validatedCandidate = await selectedCandidateValue(input, descriptors);
  if (validatedCandidate.reviews.some((review) => review.status === 'BLOCKED')) {
    fail('candidate selection contains blocked source reviews; retain the ready candidates and explicitly regenerate only blocked sources');
  }
  const stage = await mkdtemp(join(dirname(targetSnapshotRoot), `.${basename(targetSnapshotRoot)}.staging-`));
  let published = false;
  try {
    await cp(sourceSnapshotRoot, stage, { recursive: true, force: false, errorOnExist: false });
    const v6Manifest = await readJson(join(stage, 'manifest.json'));
    const transformedBySource = new Map();
    for (const item of validatedCandidate.reviews) {
      const narratives = narrativeRecord(item.narratives);
      const transformed = transformV6Review(item.descriptor.review, {
        targetSnapshotId: V7_SNAPSHOT_ID,
        sectionNarratives: narratives,
      });
      transformed.generationManifest = item.generation;
      transformedBySource.set(item.descriptor.sourceId, {
        ...item,
        chapters: item.chapters,
        narrativeSections: item.narratives,
        narratives,
        transformed,
      });
    }
    const sources = [];
    for (const source of v6Manifest.sources) {
      const transformed = transformedBySource.get(source.sourceId);
      if (!transformed) fail(`candidate did not transform source: ${source.sourceId}`);
      const reviewPath = reviewPathBySource[source.sourceId];
      const narrativePath = narrativePathBySource[source.sourceId];
      await writeFile(join(stage, reviewPath), `${JSON.stringify(transformed.transformed, null, 2)}\n`, 'utf8');
      const narrativeArtifact = {
        schemaVersion: transformed.chapters ? 2 : 1,
        sourceId: source.sourceId,
        sourceSnapshotId: source.snapshotId,
        sections: transformed.narrativeSections,
        ...(transformed.chapters ? { chapters: transformed.chapters } : {}),
        generation: transformed.generation,
        rawOutput: transformed.rawOutput,
      };
      await writeFile(join(stage, narrativePath), `${JSON.stringify(narrativeArtifact, null, 2)}\n`, 'utf8');
      const withReview = replaceArtifact(source.artifacts, await artifact(stage, reviewPath, 'application/json'));
      if (withReview.some((entry) => entry.path === narrativePath)) {
        fail(`V7 reader narrative artifact already exists: ${narrativePath}`);
      }
      sources.push({
        ...source,
        artifacts: [...withReview, await artifact(stage, narrativePath, 'application/json')],
      });
    }
    const generationRuns = validatedCandidate.reviews.map((item) => item.generation);
    const unsignedManifest = {
      schemaVersion: 1,
      snapshotId: V7_SNAPSHOT_ID,
      storyKey: v6Manifest.storyKey,
      sources,
      generationRuns,
      previousSnapshotId: V6_SNAPSHOT_ID,
    };
    const manifest = {
      ...unsignedManifest,
      contentSha256: sha256(`${canonicalJson(unsignedManifest)}\n`),
    };
    await writeFile(join(stage, 'generation-manifest.json'), `${JSON.stringify({
      schemaVersion: 1,
      snapshotId: V7_SNAPSHOT_ID,
      runs: generationRuns,
    }, null, 2)}\n`, 'utf8');
    const artifacts = sources.flatMap((source) => source.artifacts);
    await writeFile(join(stage, 'checksums.sha256'), `${artifacts.map((entry) => `${entry.sha256.slice(7)}  ${entry.path}`).join('\n')}\n`, 'utf8');
    await writeFile(join(stage, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
    await validateV7Snapshot(stage, { sourceSnapshotRoot });
    await rename(stage, targetSnapshotRoot);
    published = true;
    return manifest;
  } finally {
    if (!published) await rm(stage, { recursive: true, force: true });
  }
}

export async function validateV7Snapshot(root, input = {}) {
  const snapshotRoot = resolve(root);
  const sourceSnapshotRoot = resolve(input.sourceSnapshotRoot ?? defaultSourceSnapshotRoot);
  try {
    const stats = await lstat(snapshotRoot);
    if (!stats.isDirectory()) fail(`V7 snapshot is unavailable: ${snapshotRoot}`);
  } catch (error) {
    if (error?.message?.startsWith('Demo content V7 generation blocked:')) throw error;
    if (error?.code === 'ENOENT') fail(`V7 snapshot is missing or unavailable: ${snapshotRoot}`);
    throw error;
  }
  const base = await validateDemoContentSnapshot(snapshotRoot);
  const manifest = await readJson(join(snapshotRoot, 'manifest.json'));
  if (manifest.snapshotId !== V7_SNAPSHOT_ID) {
    fail(`V7 snapshot ID is invalid; expected ${V7_SNAPSHOT_ID}`);
  }
  if (manifest.previousSnapshotId !== V6_SNAPSHOT_ID) {
    fail(`V7 snapshot lineage must name the exact V6 predecessor ${V6_SNAPSHOT_ID}`);
  }
  if (!Array.isArray(manifest.generationRuns)
    || !exactMembers(manifest.generationRuns.map((run) => run?.sourceId), sourceOrder)) {
    fail('V7 snapshot generation membership is invalid');
  }
  const descriptors = await loadV6NarrativeDescriptors({ sourceSnapshotRoot });
  const v6Manifest = await readJson(join(sourceSnapshotRoot, 'manifest.json'));
  await assertV7ArtifactLineage({
    snapshotRoot,
    sourceSnapshotRoot,
    manifest,
    v6Manifest,
  });
  const generationBySource = new Map(manifest.generationRuns.map((generation) => [generation.sourceId, generation]));
  const generationSessionIds = new Set();
  const reviews = [];
  for (const descriptor of descriptors) {
    const source = manifest.sources.find((candidate) => candidate.sourceId === descriptor.sourceId);
    const reviewPath = descriptor.reviewPath;
    const narrativePath = descriptor.narrativePath;
    if (!source?.artifacts.some((entry) => entry.path === reviewPath)
      || !source.artifacts.some((entry) => entry.path === narrativePath)) {
      fail(`V7 source artifacts are missing: ${descriptor.sourceId}`);
    }
    const narrativeArtifact = await readJson(join(snapshotRoot, narrativePath));
    if (typeof narrativeArtifact.rawOutput !== 'string') {
      fail(`V7 raw reader output is missing: ${descriptor.sourceId}`);
    }
    const normalizedOutput = normalizeOutput(narrativeArtifact.rawOutput, descriptor);
    if (normalizedOutput.status === 'BLOCKED' || !normalizedOutput.narratives) {
      fail(`V7 raw reader output is blocked: ${descriptor.sourceId}`);
    }
    const narratives = normalizedOutput.narratives;
    if (canonicalJson(narrativeArtifact.sections) !== canonicalJson(narratives)) {
      fail(`V7 normalized reader narrative sections drifted from raw output: ${descriptor.sourceId}`);
    }
    if (normalizedOutput.chapters && canonicalJson(narrativeArtifact.chapters) !== canonicalJson(normalizedOutput.chapters)) {
      fail(`V7 structured reader chapters drifted from raw output: ${descriptor.sourceId}`);
    }
    const generation = validateGeneration(
      narrativeArtifact.generation,
      descriptor,
      narrativeArtifact.rawOutput,
      generationSessionIds,
    );
    if (canonicalJson(narrativeArtifact.generation) !== canonicalJson(generationBySource.get(descriptor.sourceId))) {
      fail(`V7 narrative generation does not match manifest: ${descriptor.sourceId}`);
    }
    const expected = transformV6Review(descriptor.review, {
      targetSnapshotId: V7_SNAPSHOT_ID,
      sectionNarratives: narrativeRecord(narratives),
    });
    expected.generationManifest = generationBySource.get(descriptor.sourceId);
    const actual = await readJson(join(snapshotRoot, reviewPath));
    if (canonicalJson(actual) !== canonicalJson(expected)) {
      fail(`V7 transformed review is not deterministic: ${descriptor.sourceId}`);
    }
    reviews.push(actual);
  }
  return { ...base, reviews };
}

async function main() {
  const [command, ...args] = process.argv.slice(2);
  if ((command === '--source' || command === '--regenerate-source') && (args.length === 1 || args.length === 2)) {
    const candidate = await regenerateV7SourceCandidate({ sourceId: args[0], ...(args[1] ? { candidateRoot: args[1] } : {}) });
    process.stdout.write(`${JSON.stringify({ candidateId: candidate.candidateId, sourceId: candidate.sourceId, status: candidate.review.status }, null, 2)}\n`);
    return;
  }
  if (command === '--refine-prompt') {
    const refinement = await refineV7Prompt(parseV7PromptRefinementArguments(args));
    process.stdout.write(`${JSON.stringify({ promptRevisionId: refinement.proposal.promptRevisionId, sourceId: refinement.proposal.sourceId, status: 'READY' }, null, 2)}\n`);
    return;
  }
  if (command === '--source-round-two') {
    const roundTwo = parseV7RoundTwoArguments(args);
    const revision = await loadV7PromptRevision({ promptRevisionId: roundTwo.promptRevisionId });
    const candidate = await regenerateV7SourceCandidate({
      sourceId: roundTwo.sourceId,
      generationRound: ContentGenerationRound.TWO,
      parentCandidateId: roundTwo.parentCandidateId,
      promptRevision: revision.proposal,
    });
    process.stdout.write(`${JSON.stringify({ candidateId: candidate.candidateId, sourceId: candidate.sourceId, status: candidate.review.status }, null, 2)}\n`);
    return;
  }
  if (command === '--check-source-candidate' && (args.length === 1 || args.length === 2)) {
    const candidate = await loadV7SourceCandidate({ candidateId: args[0], ...(args[1] ? { candidateRoot: args[1] } : {}) });
    process.stdout.write(`${JSON.stringify({ candidateId: candidate.candidateId, sourceId: candidate.sourceId, status: candidate.review.status }, null, 2)}\n`);
    return;
  }
  if (command === '--create-selection' && args.length === sourceOrder.length + 1) {
    const selection = await createV7Selection({
      selectionPath: args[0],
      candidates: parseV7SourceCandidateAssignments(args.slice(1)),
    });
    process.stdout.write(`${JSON.stringify({ snapshotId: selection.snapshotId, candidates: selection.candidates }, null, 2)}\n`);
    return;
  }
  if ((command === '--selection' || command === '--from-selection' || command === '--check-selection') && args.length === 1) {
    const result = await validateV7Selection({ selectionPath: args[0] });
    process.stdout.write(`${JSON.stringify({ snapshotId: result.selection.snapshotId, sourceReviews: result.reviews.map((review) => ({ sourceId: review.descriptor.sourceId, status: review.status })) }, null, 2)}\n`);
    return;
  }
  if (command === '--freeze') {
    const manifest = await freezeV7Snapshot(parseV7FreezeArguments(args));
    process.stdout.write(`${JSON.stringify({ snapshotId: manifest.snapshotId, contentSha256: manifest.contentSha256 }, null, 2)}\n`);
    return;
  }
  if (command === '--freeze-selection' && (args.length === 1 || args.length === 2)) {
    const manifest = await freezeV7Snapshot({ selectionPath: args[0], ...(args[1] ? { targetSnapshotRoot: args[1] } : {}) });
    process.stdout.write(`${JSON.stringify({ snapshotId: manifest.snapshotId, contentSha256: manifest.contentSha256 }, null, 2)}\n`);
    return;
  }
  if (command === '--check' && args.length === 1) {
    const result = await validateV7Snapshot(args[0]);
    process.stdout.write(`${JSON.stringify({ snapshotId: result.snapshotId, sourceReviews: result.reviews.length }, null, 2)}\n`);
    return;
  }
  fail('Usage: node scripts/evidence/guanyijia-demo-content-v7-generate.mjs --source <source-id> [candidate-root] | --refine-prompt <source-id> <parent-candidate-id> <fatal-finding-id>... | --source-round-two <source-id> <parent-candidate-id> <prompt-revision-id> | --create-selection <selection.json> <sourceId=candidateId> ×5 | --selection <selection.json> | --freeze --selection <selection.json> [--target <snapshot-root>] | --check <snapshot-root>');
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
