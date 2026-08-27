import { createHash } from 'node:crypto';
import { standardSectionOrder } from '../../src/features/modeling-document-bridge/standard-markdown.ts';

/**
 * V7 is deliberately a publication transform, not another evidence capture.
 * It consumes one already-frozen V6 review and returns a new reader-oriented
 * value.  In particular, it never reads a source, calls a model, or changes
 * the formal source and snapshot identities carried by the review.
 */

const sourceProfiles = Object.freeze({
  guanyijia_mysql: {
    label: '已保存的数据库结构资料',
    title: '数据库结构资料审阅',
    introduction: '本资料只整理已保存的表定义和过程定义。字段、索引或过程体的出现只能说明结构或实现入口已被保存，不能证明实际配置、业务行分布、制度已经生效或指标已经确认。',
  },
  guanyijia_github: {
    label: '固定版本源码节选',
    title: '固定版本源码节选审阅',
    introduction: '本资料只整理固定版本中已经选定的源码节选。查询、字段和分支说明可供核对实现线索，但不能单独证明全部调用路径、部署配置、业务制度或正式指标公式。',
  },
  guanyijia_official_docs: {
    label: '业务说明（演示编写资料）',
    title: '业务说明审阅',
    introduction: '本资料是演示编写的业务说明，用于组织业务阅读和建模讨论；它不是官方原文，也不能据此认定当前生产实施、配置或已批准的制度。',
  },
  guanyijia_demo_policy: {
    label: 'ERP 管理制度（演示制度草案）',
    title: 'ERP 管理制度审阅',
    introduction: '本资料是待审阅的演示制度草案。它可用于讨论控制目标、责任和例外，但所有制度性表述均须经过责任人确认、批准和实施后才可能成为现行规则。',
  },
  guanyijia_semantica_demo: {
    label: '企业术语图（派生内容）',
    title: '企业术语图审阅',
    introduction: '本资料由演示资料派生，用来统一对象、活动和治理词汇。它不增加根证据，也不能替代正式来源对关系、字段、流程或指标的确认。',
  },
});

const chapterClaimTitles = Object.freeze({
  OVERVIEW: '资料范围与阅读边界',
  GOAL: '可支持的建模目标',
  OBJECT: '可整理的业务对象',
  ACTIVITY: '可核对的业务活动',
  FIELD: '字段与维度的阅读重点',
  RELATION: '关系线索与确认范围',
  METRIC: '指标候选与确认条件',
  QUESTION: '业务核对问题',
  UNRESOLVED: '资料缺口、限制与下一步',
});

const chapterPurpose = Object.freeze({
  OVERVIEW: '说明资料身份、选择范围、来源属性与不能推出的结论。',
  GOAL: '说明资料能够支持的业务理解或建模目标，并区分事实与候选。',
  OBJECT: '整理业务对象、对象粒度、标识及其责任范围。',
  ACTIVITY: '整理业务动作、状态变化、前后步骤与参与角色。',
  FIELD: '整理字段、维度、时间、状态、数量、金额及需要确认的语义。',
  RELATION: '整理对象间连接、上下游、基数候选以及无法由单一来源证明的关系。',
  METRIC: '只提出来源能够支持的指标候选、口径要素与阻断条件，不把实现细节直接当作正式公式。',
  QUESTION: '给出业务人员可直接核对的示例问题，问题能够回到本来源资料。',
  UNRESOLVED: '明确资料缺口、限制和下一步确认责任。',
});

const forbiddenHumanTokens = Object.freeze([
  'SOURCE_NATIVE',
  'DEMO_AUTHORED',
  'DERIVED_DEMO',
  'SNAPSHOT_REFERENCE',
  'sourceId',
  'snapshotId',
  'contentOrigin',
  '[TRACE:',
  '<!--',
  '&amp;gt;',
]);

function fail(message) {
  throw new Error(`Demo content V7 transform blocked: ${message}`);
}

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function clone(value) {
  return structuredClone(value);
}

function safeAnchorPart(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9]+/gu, '-').replace(/^-|-$/gu, '');
}

function sectionForClaim(claim) {
  const section = standardSectionOrder[Number(claim.section) - 1];
  if (!section) fail(`claim ${claim.claimId ?? '(unknown)'} has no canonical section`);
  return section;
}

/**
 * Remove transport vocabulary from reader-facing text.  The formal fields
 * retaining those values stay untouched in the returned object; only the
 * prose projection is changed.
 */
function humanText(value) {
  return String(value ?? '')
    .replace(/<!--[\s\S]*?-->/gu, '')
    .replace(/\[TRACE:[^\]]*\]/giu, '')
    .replace(/&amp;gt;/giu, '>')
    .replace(/\bSOURCE_NATIVE\b/gu, '固定版本源码节选')
    .replace(/\bDEMO_AUTHORED\b/gu, '演示编写资料')
    .replace(/\bDERIVED_DEMO\b/gu, '派生内容')
    .replace(/\bSNAPSHOT_REFERENCE\b/gu, '资料范围说明')
    .replace(/\bGENERATED_TARGET\b/gu, '待审阅目标草案')
    .replace(/\bsourceId\s*(?:=|为|:|：)\s*[^\s，。；；]*/gu, '资料来源')
    .replace(/\bsnapshotId\s*(?:=|为|:|：)\s*[^\s，。；；]*/gu, '保存版本')
    .replace(/\bcontentOrigin\s*(?:=|为|:|：)?\s*/gu, '资料属性为')
    .replace(/\bsourceId\b/gu, '资料来源')
    .replace(/\bsnapshotId\b/gu, '保存版本')
    .replace(/\bcontentOrigin\b/gu, '资料属性')
    // Each GAP in V6 had different prose quality.  V7 uses one explicit
    // reader-facing form that always states the missing material, limitation,
    // and next action instead of implying a confirmed result.
    .replace(/\bGAP\s*[：:]/gu, '资料缺口：')
    .replace(/\r\n?/gu, '\n')
    .replace(/[ \t]+\n/gu, '\n')
    .replace(/\n{3,}/gu, '\n\n')
    .trim();
}

function sourceProfile(review) {
  const profile = sourceProfiles[review?.sourceId];
  if (!profile) fail(`unsupported formal source: ${review?.sourceId ?? '(missing)'}`);
  return profile;
}

function validateV6Review(review) {
  if (!review || typeof review !== 'object') fail('review must be an object');
  if (typeof review.sourceId !== 'string' || !review.sourceId) fail('review formal source identity is missing');
  if (typeof review.snapshotId !== 'string' || !review.snapshotId) fail('review formal snapshot identity is missing');
  if (!Array.isArray(review.claims) || !Array.isArray(review.evidence)) fail('review claims or evidence are missing');
  if (!review.claims.every((claim) => (
    claim && typeof claim.claimId === 'string' && Array.isArray(claim.evidenceRefs)
  ))) fail('review claim identity or evidence links are invalid');
  if (!review.evidence.every((entry) => entry && typeof entry.evidenceRef === 'string')) {
    fail('review evidence identity is invalid');
  }
}

function transformedClaim(claim, sourceId) {
  const section = sectionForClaim(claim);
  const genericChapterTitle = standardSectionOrder.some(({ heading }) => claim.title === heading);
  const title = humanText(genericChapterTitle ? chapterClaimTitles[section.key] : claim.title);
  const statement = humanText(claim.statement);
  return {
    ...claim,
    title,
    statement,
    sectionId: claim.sectionId ?? section.key,
    sectionPurpose: claim.sectionPurpose ?? chapterPurpose[section.key],
    markdownAnchor: claim.markdownAnchor ?? `v7-${safeAnchorPart(sourceId)}-${safeAnchorPart(claim.claimId)}`,
  };
}

function tableCell(value) {
  return humanText(value).replace(/\|/gu, '\\|').replace(/\n+/gu, '<br>');
}

function terminologyProjection(review) {
  const terms = new Map();
  const relations = new Map();
  for (const evidence of review.evidence) {
    if (evidence.excerptKind !== 'DERIVED_GRAPH' || typeof evidence.excerpt !== 'string') continue;
    let projection;
    try {
      projection = JSON.parse(evidence.excerpt);
    } catch {
      fail(`terminology evidence ${evidence.evidenceRef} is not readable JSON`);
    }
    for (const term of projection.terms ?? []) {
      if (term?.termId && !terms.has(term.termId)) terms.set(term.termId, term);
    }
    for (const relation of projection.relations ?? []) {
      if (relation?.relationId && !relations.has(relation.relationId)) relations.set(relation.relationId, relation);
    }
  }
  if (terms.size !== 56 || relations.size !== 80) {
    fail(`terminology projection must retain 56 terms and 80 relations; received ${terms.size} and ${relations.size}`);
  }
  return {
    terms: [...terms.values()].sort((left, right) => String(left.termId).localeCompare(String(right.termId))),
    relations: [...relations.values()].sort((left, right) => String(left.relationId).localeCompare(String(right.relationId))),
  };
}

function terminologyTables(review, sectionId) {
  const { terms, relations } = terminologyProjection(review);
  if (sectionId === 'OBJECT') {
    return [
      '术语表（完整保留 56 个术语）：',
      '',
      '| 术语 | 领域 | 定义 | 别名 |',
      '| --- | --- | --- | --- |',
      ...terms.map((term) => [
        tableCell(term.name),
        tableCell(term.domain),
        tableCell(term.definition),
        tableCell((term.aliases ?? []).join('、')),
      ].join(' | ').replace(/^/u, '| ').concat(' |')),
    ].join('\n');
  }
  if (sectionId === 'RELATION') {
    return [
      '关系表（完整保留 80 条关系）：',
      '',
      '| 主语 | 关系 | 宾语 | 说明 |',
      '| --- | --- | --- | --- |',
      ...relations.map((relation) => [
        tableCell(relation.subjectName),
        tableCell(relation.predicate),
        tableCell(relation.objectName),
        tableCell(relation.description),
      ].join(' | ').replace(/^/u, '| ').concat(' |')),
    ].join('\n');
  }
  return '';
}

function authoredSourceMaterial(review, claims, sectionId) {
  if (!['guanyijia_official_docs', 'guanyijia_demo_policy'].includes(review.sourceId)) return '';
  const evidenceByRef = new Map(review.evidence.map((evidence) => [evidence.evidenceRef, evidence]));
  const visibleEvidence = claims
    .filter((claim) => claim.sectionId === sectionId)
    .flatMap((claim) => claim.evidenceRefs)
    .map((evidenceRef) => evidenceByRef.get(evidenceRef))
    .filter((evidence, index, values) => evidence && values.indexOf(evidence) === index);
  if (!visibleEvidence.length) return '';
  return [
    '本章纳入的演示资料原文摘录：',
    '',
    ...visibleEvidence.flatMap((evidence) => {
      const excerpt = humanText(evidence.excerpt)
        .replace(/^##\s+[^\n]+\n+/u, '')
        .trim();
      return [`- **${humanText(evidence.title)}**：${excerpt}`, ''];
    }),
  ].join('\n').trim();
}

function readerGap(section, profile) {
  return `GAP：资料缺口仍存在；限制是本章只能依据${profile.label}所保存的内容阅读，不能把候选解释当作已确认事实；下一步由相应业务、财务、库存或技术责任人回到正式资料核对并确认。`;
}

function normalizedSectionNarratives(value) {
  if (value === undefined) return {};
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    fail('section narratives must be a record when supplied');
  }
  const expected = standardSectionOrder.map((section) => section.key);
  const keys = Object.keys(value);
  if (keys.length !== expected.length || keys.some((key) => !expected.includes(key))) {
    fail('section narratives must cover the exact canonical nine sections');
  }
  return Object.fromEntries(expected.map((sectionId) => {
    const narrative = humanText(value[sectionId]);
    // Publication preserves validated human text apart from transport cleanup.
    // A nonempty, readable chapter is the only content prerequisite here.
    if (!narrative || forbiddenHumanTokens.some((token) => narrative.includes(token))) {
      fail(`section narrative is not readable: ${sectionId}`);
    }
    return [sectionId, narrative];
  }));
}

function sectionBody(review, claims, section, profile, narratives) {
  // Section bodies are reader context only.  Claims are serialized once by
  // `renderMarkdown` below, from their stable identity and section mapping.
  // Keeping the two apart makes a reviewer revision independent of the
  // frozen title/statement typography and prevents the reading view from
  // rendering every conclusion twice.
  const lines = [
    `资料标签：${profile.label}。${chapterPurpose[section.key]}`,
    '',
    ...(narratives[section.key] ? [narratives[section.key], ''] : []),
  ];
  if (review.sourceId === 'guanyijia_semantica_demo') {
    const tables = terminologyTables(review, section.key);
    if (tables) lines.push(tables, '');
  }
  const authored = authoredSourceMaterial(review, claims, section.key);
  if (authored) lines.push(authored, '');
  // A document-wide limitation belongs in the dedicated unresolved chapter.
  // Repeating it in every chapter made a rich document read like a template
  // while adding no decision value for the reader.
  if (section.key === 'UNRESOLVED') lines.push(readerGap(section, profile));
  return lines.join('\n').replace(/\n{3,}/gu, '\n\n').trim();
}

function standardSectionsFor(review, claims) {
  const existingById = new Map((review.standardSections ?? []).map((section) => [section.sectionId, section]));
  return standardSectionOrder.map((section) => ({
    ...clone(existingById.get(section.key) ?? {}),
    sectionId: section.key,
    heading: section.heading,
    purpose: existingById.get(section.key)?.purpose ?? chapterPurpose[section.key],
    markdownAnchor: existingById.get(section.key)?.markdownAnchor
      ?? `v7-${safeAnchorPart(review.sourceId)}-section-${section.key.toLowerCase()}`,
    claimIds: claims.filter((claim) => claim.sectionId === section.key).map((claim) => claim.claimId),
  }));
}

function traceLinksFor(claims) {
  return claims.map((claim) => ({
    // The visible heading may later be revised, but this stable claim ID and
    // anchor remain the actual revision seam.
    linePrefix: `### ${claim.title}`,
    claimId: claim.claimId,
    markdownAnchor: claim.markdownAnchor,
    section: claim.section,
    sectionId: claim.sectionId,
    sectionPurpose: claim.sectionPurpose,
    claim: {
      title: claim.title,
      statement: claim.statement,
      focusIdentifiers: [...(claim.focusIdentifiers ?? [])],
    },
    evidenceRefs: [...claim.evidenceRefs],
  }));
}

function renderMarkdown(review, profile, bodies, claims) {
  return [
    `# ${profile.title}`,
    '',
    `资料标签：${profile.label}。${profile.introduction}`,
    '',
    ...standardSectionOrder.flatMap((section) => {
      const sectionClaims = claims.filter((claim) => claim.sectionId === section.key);
      return [
        `## ${section.heading}`,
        '',
        bodies[section.key],
        '',
        ...sectionClaims.flatMap((claim) => [
          `### ${claim.title}`,
          '',
          claim.statement,
          '',
        ]),
      ];
    }),
  ].join('\n').replace(/\n{3,}/gu, '\n\n').trimEnd() + '\n';
}

function assertReaderProjection(review) {
  const headings = [...review.markdown.matchAll(/^##\s+(.+)$/gmu)].map((match) => match[1]);
  const expectedHeadings = standardSectionOrder.map((section) => section.heading);
  if (headings.length !== expectedHeadings.length || headings.some((heading, index) => heading !== expectedHeadings[index])) {
    fail('human Markdown must have exactly the canonical nine chapter headings');
  }
  if (forbiddenHumanTokens.some((token) => review.markdown.includes(token))) {
    fail('human Markdown still contains an internal provenance token');
  }
  if (/^###\s+(?:\d+\.\s*)?(?:文档说明|业务目标|业务对象|业务活动|字段与维度|对象关系|指标口径|示例问题|待确认事项)$/gmu.test(review.markdown)) {
    fail('human Markdown repeats a canonical chapter as an H3 title');
  }
  if (review.standardSections.length !== standardSectionOrder.length
    || review.standardSections.some((section, index) => section.sectionId !== standardSectionOrder[index].key
      || section.heading !== standardSectionOrder[index].heading)) {
    fail('standard section contract drifted');
  }
  const claimIds = new Set(review.claims.map((claim) => claim.claimId));
  if (review.standardSections.some((section) => section.claimIds.some((claimId) => !claimIds.has(claimId)))) {
    fail('standard section claim link does not resolve');
  }
  for (const claim of review.claims) {
    const block = `### ${claim.title}\n\n${claim.statement}`;
    if (review.markdown.split(block).length !== 2) {
      fail(`claim must serialize exactly once: ${claim.claimId}`);
    }
    if (review.sections?.[claim.sectionId]?.includes(block)) {
      fail(`section reader context must not duplicate claim: ${claim.claimId}`);
    }
    if (!review.traceLinks.some((trace) => (
      trace.claimId === claim.claimId && trace.markdownAnchor === claim.markdownAnchor
    ))) {
      fail(`claim trace must retain stable identity: ${claim.claimId}`);
    }
  }
}

/**
 * Re-project one frozen V6 source review for the V7 publication layer.
 * `targetSnapshotId` deliberately does not replace `review.snapshotId`: the
 * latter is the formal identity the runtime verifies against its source.
 */
export function transformV6Review(review, { targetSnapshotId, sectionNarratives } = {}) {
  if (targetSnapshotId !== undefined && (typeof targetSnapshotId !== 'string' || !targetSnapshotId.trim())) {
    fail('targetSnapshotId must be a non-empty publication identity when supplied');
  }
  validateV6Review(review);
  const profile = sourceProfile(review);
  const narratives = normalizedSectionNarratives(sectionNarratives);
  const result = clone(review);
  const claims = result.claims.map((claim) => transformedClaim(claim, result.sourceId));
  const bodies = Object.fromEntries(standardSectionOrder.map((section) => [
    section.key,
    sectionBody(result, claims, section, profile, narratives),
  ]));

  result.title = profile.title;
  result.claims = claims;
  result.standardSections = standardSectionsFor(result, claims);
  result.sections = bodies;
  result.traceLinks = traceLinksFor(claims);
  result.markdown = renderMarkdown(result, profile, bodies, claims);
  result.markdownSha256 = sha256(result.markdown);

  assertReaderProjection(result);
  return result;
}
