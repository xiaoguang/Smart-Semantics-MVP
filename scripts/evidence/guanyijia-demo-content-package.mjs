import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateDemoContentSnapshot } from './guanyijia-demo-content-snapshot.mjs';
import { validateV6Snapshot } from './guanyijia-demo-content-v6-generate.mjs';
import { V6_SNAPSHOT_ID as validatedV6SnapshotId, validateV7Snapshot } from './guanyijia-demo-content-v7-generate.mjs';

const V5_SNAPSHOT_ID = 'guanyijia-demo-content-v5-20260826';
const V6_SNAPSHOT_ID = 'guanyijia-demo-content-v6-20260826';
const V7_SNAPSHOT_ID = 'guanyijia-demo-content-v7-20260827';
const V6_CONTENT_SHA256 = 'sha256:6da765901357f4b0856e3b1c5aaab159aec75c895b60b1bb2ea11539997f8180';
const V6_COLLECTION_BASELINE = Object.freeze({
  claims: 115,
  evidence: 140,
  gaps: 8,
  mysqlTables: 30,
  mysqlProcedures: 6,
  githubClaims: 43,
  githubEvidence: 69,
  terminologyTerms: 56,
  terminologyRelations: 80,
});

const prototypeRoot = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
const canonicalV6SnapshotRoot = join(
  prototypeRoot,
  `../modeling-evidence/guanyijia/demo-content/snapshots/${V6_SNAPSHOT_ID}`,
);
const defaultSnapshotRoot = canonicalV6SnapshotRoot;
const defaultOutput = join(
  prototypeRoot,
  'src/features/guanyijia-demo-content/pinned-demo-content.generated.ts',
);

const formalSnapshotIds = Object.freeze({
  guanyijia_mysql: '20260813T032528Z-abb0502c7d79',
  guanyijia_github: '20260813032126Z-5821d0ece9b1',
  guanyijia_official_docs: 'gyjerp-official-docs-20260813T031656Z',
  guanyijia_demo_policy: 'guanyijia-demo-policy-f6c6d209ffe3fd53',
  guanyijia_semantica_demo: 'guanyijia-semantica-demo-ff948845dc5bd778',
});

const standardSectionOrder = Object.freeze([
  ['OVERVIEW', '1. 文档说明'],
  ['GOAL', '2. 业务目标'],
  ['OBJECT', '3. 业务对象'],
  ['ACTIVITY', '4. 业务活动'],
  ['FIELD', '5. 字段与维度'],
  ['RELATION', '6. 对象关系'],
  ['METRIC', '7. 指标口径'],
  ['QUESTION', '8. 示例问题'],
  ['UNRESOLVED', '9. 待确认事项'],
]);

const v6ReviewPathBySource = Object.freeze({
  guanyijia_mysql: 'sources/mysql/standardized-review.json',
  guanyijia_github: 'sources/github/standardized-review.json',
  guanyijia_official_docs: 'sources/official/standardized-review.json',
  guanyijia_demo_policy: 'sources/policy/standardized-review.json',
  guanyijia_semantica_demo: 'sources/terminology/standardized-review.json',
});

let cachedV6CompletenessBaseline;

const reviewSectionsBySource = Object.freeze({
  guanyijia_github: [
    '系统配置与权限入口',
    '单据状态与字段演进',
    '序列号与批号读取',
    '采购与销售查询',
  ],
  guanyijia_official_docs: [
    '系统边界与基础资料',
    '采购、销售与库存流程',
    '财务、状态与时间口径',
  ],
  guanyijia_demo_policy: [
    '基础资料与权限',
    '采购、销售与退货',
    '库存与负库存',
    '财务结算与欠款',
    '状态与统计时间',
  ],
  guanyijia_semantica_demo: ['术语与业务关系'],
});

const githubV5SectionNames = Object.freeze([
  '系统结构与部署入口',
  '租户隔离、权限与组织',
  '商品、客户、供应商等主数据',
  '采购、入库与采购退货',
  '销售、出库与销售退货',
  '库存、负库存、批号与序列号',
  '财务、账户、订金与欠款',
  '单据状态、审核与迁移记录',
  '查询、报表口径与资料边界',
]);

const githubSelectors = [
  {
    id: 'github:service:minus-stock-flag', section: 1,
    title: '负库存开关的读取实现',
    summary: '服务读取并返回负库存配置值；该片段不说明统一制度或最终拦截规则。',
    path: 'jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java',
    needle: 'public boolean getMinusStockFlag() throws Exception {', before: 0, after: 10,
    affectedObjectRefs: ['service:SystemConfigService.getMinusStockFlag', 'field:minus_stock_flag'],
  },
  {
    id: 'github:service:depot-head-minus-stock', section: 1,
    title: '单据状态处理读取负库存开关',
    summary: '库存相关代码读取负库存配置；该片段本身不证明最终校验结果。',
    path: 'jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java',
    needle: 'boolean minusStockFlag = systemConfigService.getMinusStockFlag();', before: 4, after: 8,
    affectedObjectRefs: ['service:DepotHeadService', 'field:minus_stock_flag', 'table:jsh_depot_head'],
  },
  {
    id: 'github:migration:jsh-depot-head-historical-status', section: 2,
    title: '早期单据状态迁移记录',
    summary: '迁移记录中的早期状态说明只列出0、1、2，适合与当前部署结构作时间差异比较。',
    path: 'jshERP-boot/docs/数据库更新记录-首次安装请勿使用.txt',
    needle: "alter table jsh_depot_head change Status Status varchar(1) DEFAULT '0' COMMENT '状态，0未审核、1已审核、2已转采购|销售';", before: 0, after: 0,
    affectedObjectRefs: ['table:jsh_depot_head', 'field:status'],
  },
  {
    id: 'github:migration:jsh-depot-head-debt-fields', section: 2,
    title: '欠款字段迁移记录',
    summary: '迁移记录向单据主表增加本次欠款和最终欠款字段，可与当前部署DDL逐项比对。',
    path: 'jshERP-boot/docs/数据库更新记录-首次安装请勿使用.txt',
    needle: "alter table jsh_depot_head add debt decimal(24,6) DEFAULT NULL COMMENT '本次欠款' after deposit;", before: 0, after: 1,
    affectedObjectRefs: ['table:jsh_depot_head', 'field:debt', 'field:last_debt'],
  },
  {
    id: 'github:tenancy:tenant-config-filter', section: 1,
    title: '租户表过滤实现入口',
    summary: '代码包含按表名判断租户过滤的入口；完整访问范围仍需结合其他代码确认。',
    path: 'jshERP-boot/src/main/java/com/jsh/erp/config/TenantConfig.java',
    needle: 'public boolean doTableFilter(String tableName) {', before: 4, after: 20,
    affectedObjectRefs: ['config:TenantConfig', 'rule:tenant-table-filter'],
  },
  {
    id: 'github:service:serial-number', section: 3,
    title: '序列号服务查询',
    summary: '源码包含按标识读取序列号记录的方法；不据此推断完整追踪流程。',
    path: 'jshERP-boot/src/main/java/com/jsh/erp/service/SerialNumberService.java',
    needle: 'public SerialNumber getSerialNumber(long id)throws Exception {', before: 0, after: 8,
    affectedObjectRefs: ['service:SerialNumberService.getSerialNumber', 'entity:SerialNumber'],
  },
  {
    id: 'github:service:batch-number', section: 3,
    title: '批号库存明细查询',
    summary: '源码包含按商品扩展和批号读取库存明细的方法；不据此推断完整库存历史。',
    path: 'jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemService.java',
    needle: 'public DepotItem getDepotItemByBatchNumber(Long materialExtendId, String batchNumber) {', before: 0, after: 6,
    affectedObjectRefs: ['service:DepotItemService.getDepotItemByBatchNumber', 'table:jsh_depot_item'],
  },
  {
    id: 'github:mapper:buy-or-sale-number', section: 4,
    title: '采购销售数量汇总SQL',
    summary: '查询实现按单据明细汇总采购或销售数量；筛选和聚合不等于完整指标口径。',
    path: 'jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml',
    needle: '<select id="buyOrSaleNumber" resultType="java.math.BigDecimal">', before: 0, after: 34,
    affectedObjectRefs: ['mapper:buyOrSaleNumber', 'table:jsh_depot_item'],
  },
  {
    id: 'github:mapper:buy-or-sale-total', section: 4,
    title: '采购销售金额汇总SQL',
    summary: '查询实现按单据明细汇总采购或销售金额；最终统计范围仍需业务资料确认。',
    path: 'jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml',
    needle: '<select id="buyOrSalePrice" resultType="java.math.BigDecimal">', before: 0, after: 34,
    affectedObjectRefs: ['mapper:buyOrSaleTotal', 'table:jsh_depot_item'],
  },
  {
    id: 'github:schema:system-config-minus-stock', section: 1,
    title: '系统配置中的负库存字段',
    summary: '固定建库脚本定义负库存、以销定购和多级审核等配置字段；字段定义不等于运行效果。',
    path: 'jshERP-boot/docs/jsh_erp.sql',
    needle: '`minus_stock_flag` varchar(1)', before: 3, after: 5,
    affectedObjectRefs: ['table:jsh_system_config', 'field:minus_stock_flag'],
  },
  {
    id: 'github:service:document-status-nine', section: 2,
    title: '状态9的代码显示分支',
    summary: '代码分支将状态值 9 显示为“审核中”；片段不足以确定其正式业务含义。',
    path: 'jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java',
    needle: 'case "9":', before: 3, after: 3,
    affectedObjectRefs: ['service:DepotHeadService', 'field:status'],
  },
  {
    id: 'github:config:purchase-by-sale', section: 1,
    title: '以销定购配置字段',
    summary: '建库脚本定义以销定购开关；是否被采购流程读取仍需额外代码确认。',
    path: 'jshERP-boot/docs/jsh_erp.sql',
    needle: '`purchase_by_sale_flag` varchar(1)', before: 1, after: 4,
    affectedObjectRefs: ['table:jsh_system_config', 'field:purchase_by_sale_flag'],
  },
];

function sha256(value) {
  return `sha256:${createHash('sha256').update(value).digest('hex')}`;
}

function lineCount(value) {
  const normalized = value.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  return normalized.length === 0 ? 0 : normalized.endsWith('\n')
    ? normalized.slice(0, -1).split('\n').length
    : normalized.split('\n').length;
}

function sourceTitle(markdown) {
  const title = /^#\s+(.+)$/mu.exec(markdown)?.[1]?.trim();
  if (!title) throw new Error('Demo content package failed: Markdown title is missing');
  return title;
}

function markdownChunks(path, markdown) {
  const lines = markdown.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
  const headings = lines.flatMap((line, index) => /^##\s+(.+)$/u.test(line) ? [index] : []);
  const prefaceEnd = headings[0] ?? lines.length;
  const preface = lines.slice(1, prefaceEnd)
    .filter((line) => !/^<!--.*-->$/u.test(line.trim()))
    .join('\n')
    .trim();
  const result = preface ? [{
    path,
    heading: sourceTitle(markdown),
    content: preface,
    startLine: 2,
    endLine: prefaceEnd,
  }] : [];
  return [...result, ...headings.map((start, index) => {
    const end = index + 1 < headings.length ? headings[index + 1] - 1 : lines.length - 1;
    const content = lines.slice(start + 1, end + 1).join('\n').trim();
    return {
      path,
      heading: (lines[start] ?? '').replace(/^##\s+/u, '').trim(),
      content,
      startLine: start + 1,
      endLine: end + 1,
    };
  })].filter((chunk) => chunk.content.length > 0);
}

function singleSentence(value) {
  const normalized = value
    .replace(/^[-*]\s+/gmu, '')
    .replace(/\*\*/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  const end = normalized.search(/[。！？]/u);
  const sentence = end >= 0 ? normalized.slice(0, end + 1) : normalized;
  return sentence.length <= 140 ? sentence : `${sentence.slice(0, 137).trimEnd()}…`;
}

function documentSectionTitle(sourceId, index) {
  const title = reviewSectionsBySource[sourceId]?.[index];
  if (!title) throw new Error(`Demo content package failed: missing explicit section for ${sourceId}`);
  return title;
}

function artifactFor(source, path) {
  const artifact = source.artifacts.find((entry) => entry.path === path);
  if (!artifact) throw new Error(`Demo content package failed: missing manifest artifact ${path}`);
  return artifact;
}

function clone(value) {
  return structuredClone(value);
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function readJsonSync(path, label) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch (error) {
    throw new Error(`Demo content package failed: cannot read V6 ${label}: ${error.message}`);
  }
}

function exactSet(values, label) {
  if (!Array.isArray(values)) {
    throw new Error(`Demo content package failed: V6 ${label} is not an array`);
  }
  const set = new Set(values);
  if (set.size !== values.length) {
    throw new Error(`Demo content package failed: V6 ${label} contains duplicates`);
  }
  return set;
}

function assertExactV6Set(label, actualValues, expectedValues) {
  const actual = exactSet(actualValues, label);
  const expected = exactSet(expectedValues, `baseline ${label}`);
  if (actual.size !== expected.size || [...actual].some((value) => !expected.has(value))) {
    throw new Error(`Demo content package failed: V6 ${label} changed`);
  }
}

function stableJson(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`;
}

function v6ClaimIdentity(claim) {
  return stableJson(claim);
}

function v6EvidenceIdentity(evidence) {
  return stableJson(evidence);
}

function v6TraceIdentity(trace) {
  return stableJson(trace);
}

function v6SectionIdentity(section) {
  return stableJson(section);
}

function v6ReaderSectionsIdentity(sections) {
  if (!sections || typeof sections !== 'object' || Array.isArray(sections)) return undefined;
  return stableJson(standardSectionOrder.map(([sectionId]) => [sectionId, sections[sectionId]]));
}

function assertExactV6Sequence(label, actualValues, expectedValues) {
  if (!Array.isArray(actualValues) || !Array.isArray(expectedValues)
    || actualValues.length !== expectedValues.length
    || actualValues.some((value, index) => value !== expectedValues[index])) {
    throw new Error(`Demo content package failed: V6 ${label} changed`);
  }
}

function assertExactV6ReaderSections(label, actualSections, expectedIdentity) {
  if (!actualSections || typeof actualSections !== 'object' || Array.isArray(actualSections)) {
    throw new Error(`Demo content package failed: V6 ${label} changed`);
  }
  const expectedKeys = standardSectionOrder.map(([sectionId]) => sectionId).sort();
  const actualKeys = Object.keys(actualSections).sort();
  if (actualKeys.length !== expectedKeys.length
    || actualKeys.some((key, index) => key !== expectedKeys[index])
    || v6ReaderSectionsIdentity(actualSections) !== expectedIdentity) {
    throw new Error(`Demo content package failed: V6 ${label} changed`);
  }
}

function readV6CompletenessBaseline() {
  if (cachedV6CompletenessBaseline) return cachedV6CompletenessBaseline;

  const manifest = readJsonSync(join(canonicalV6SnapshotRoot, 'manifest.json'), 'manifest');
  const sourceOrder = Object.keys(formalSnapshotIds);
  const reviews = Object.fromEntries(sourceOrder.map((sourceId) => [sourceId, readJsonSync(
    join(canonicalV6SnapshotRoot, v6ReviewPathBySource[sourceId]),
    `${sourceId} review`,
  )]));
  const sources = Object.fromEntries(sourceOrder.map((sourceId) => {
    const review = reviews[sourceId];
    return [sourceId, {
      snapshotId: review.snapshotId,
      formalSnapshotId: formalSnapshotIds[sourceId],
      contentOrigin: review.contentOrigin,
      markdownSha256: review.markdownSha256,
      sections: review.standardSections.map(v6SectionIdentity),
      readerSections: v6ReaderSectionsIdentity(review.sections),
      claims: review.claims.map(v6ClaimIdentity),
      evidence: review.evidence.map(v6EvidenceIdentity),
      traces: review.traceLinks.map(v6TraceIdentity),
      gaps: review.claims.filter((claim) => claim.kind === 'GAP').map((claim) => claim.claimId),
    }];
  }));
  const graph = readJsonSync(join(canonicalV6SnapshotRoot, 'sources/terminology/graph.json'), 'terminology graph');
  const merged = buildV6MergedBaseline(sourceOrder.map((sourceId) => ({
    sourceId,
    review: reviews[sourceId],
  })), { contentSnapshotId: V6_SNAPSHOT_ID });

  cachedV6CompletenessBaseline = {
    contentSha256: V6_CONTENT_SHA256,
    sourceOrder,
    sources,
    graph: {
      terms: graph.terms.map(stableJson),
      relations: graph.relations.map(stableJson),
    },
    merged: {
      claims: merged.claims.map(v6ClaimIdentity),
      evidence: merged.evidence.map(v6EvidenceIdentity),
      traces: merged.traceLinks.map(v6TraceIdentity),
      sections: merged.standardSections.map(v6SectionIdentity),
      markdownSha256: merged.markdownSha256,
      gapCount: sourceOrder.reduce((count, sourceId) => count + sources[sourceId].gaps.length, 0),
    },
  };
  if (manifest.contentSha256 !== V6_CONTENT_SHA256
    || cachedV6CompletenessBaseline.merged.claims.length !== V6_COLLECTION_BASELINE.claims
    || cachedV6CompletenessBaseline.merged.evidence.length !== V6_COLLECTION_BASELINE.evidence
    || cachedV6CompletenessBaseline.merged.gapCount !== V6_COLLECTION_BASELINE.gaps
    || graph.terms.length !== V6_COLLECTION_BASELINE.terminologyTerms
    || graph.relations.length !== V6_COLLECTION_BASELINE.terminologyRelations) {
    throw new Error('Demo content package failed: frozen V6 completeness baseline changed');
  }
  return cachedV6CompletenessBaseline;
}

/**
 * Rejects a browser projection that silently loses any frozen V6 object.
 * This is deliberately a collection gate, not a prose-size gate: it compares
 * source identities, nine sections, claim/evidence/trace identities and the
 * terminology graph's term/relation identities without judging text length.
 */
export function validateV6PublicationCompleteness(publication, input = {}) {
  const baseline = readV6CompletenessBaseline();
  if (!publication || publication.contentSnapshotId !== V6_SNAPSHOT_ID
    || publication.contentSha256 !== baseline.contentSha256) {
    throw new Error('Demo content package failed: V6 active publication identity changed');
  }
  const sources = publication.sources;
  if (!Array.isArray(sources) || sources.length !== baseline.sourceOrder.length) {
    throw new Error('Demo content package failed: V6 source collection changed');
  }
  assertExactV6Set('source order', sources.map((source) => source?.sourceId), baseline.sourceOrder);
  if (sources.some((source, index) => source?.sourceId !== baseline.sourceOrder[index])) {
    throw new Error('Demo content package failed: V6 source order changed');
  }

  for (const sourceId of baseline.sourceOrder) {
    const source = sources.find((entry) => entry.sourceId === sourceId);
    const expected = baseline.sources[sourceId];
    const review = source?.review;
    if (!source || source.contentSourceSnapshotId !== expected.snapshotId
      || source.formalSnapshotId !== expected.formalSnapshotId
      || source.origin !== expected.contentOrigin
      || review?.sourceId !== sourceId
      || review.snapshotId !== expected.formalSnapshotId
      || review.contentOrigin !== expected.contentOrigin
      || review.markdownSha256 !== expected.markdownSha256
      || sha256(review.markdown ?? '') !== expected.markdownSha256) {
      throw new Error(`Demo content package failed: V6 ${sourceId} source identity changed`);
    }
    assertExactV6Sequence(`${sourceId} section`, review.standardSections?.map(v6SectionIdentity), expected.sections);
    assertExactV6ReaderSections(`${sourceId} rich review section`, review.sections, expected.readerSections);
    assertExactV6Set(`${sourceId} claim`, review.claims?.map(v6ClaimIdentity), expected.claims);
    assertExactV6Set(`${sourceId} evidence`, review.evidence?.map(v6EvidenceIdentity), expected.evidence);
    assertExactV6Set(`${sourceId} trace`, review.traceLinks?.map(v6TraceIdentity), expected.traces);

    if (sourceId === 'guanyijia_mysql') {
      const tableClaims = review.claims.filter((claim) => claim.claimId.startsWith('v6-mysql-table-'));
      const procedureClaims = review.claims.filter((claim) => claim.claimId.startsWith('v6-mysql-procedure-'));
      if (tableClaims.length !== V6_COLLECTION_BASELINE.mysqlTables
        || procedureClaims.length !== V6_COLLECTION_BASELINE.mysqlProcedures) {
        throw new Error('Demo content package failed: V6 MySQL table or procedure coverage changed');
      }
    }
    if (sourceId === 'guanyijia_github'
      && (review.claims.length !== V6_COLLECTION_BASELINE.githubClaims
        || review.evidence.length !== V6_COLLECTION_BASELINE.githubEvidence)) {
      throw new Error('Demo content package failed: V6 GitHub claim or evidence coverage changed');
    }
  }

  const merged = publication.mergedDocument;
  if (!merged || !Array.isArray(merged.claims) || !Array.isArray(merged.evidence)
    || !Array.isArray(merged.traceLinks) || !Array.isArray(merged.standardSections)) {
    throw new Error('Demo content package failed: V6 merged document is incomplete');
  }
  if (merged.snapshotId !== V6_SNAPSHOT_ID || merged.contentOrigin !== 'DERIVED_DEMO'
    || merged.markdownSha256 !== baseline.merged.markdownSha256
    || sha256(merged.markdown ?? '') !== baseline.merged.markdownSha256) {
    throw new Error('Demo content package failed: V6 merged document identity changed');
  }
  assertExactV6Set('merged claim', merged.claims.map(v6ClaimIdentity), baseline.merged.claims);
  assertExactV6Set('merged evidence', merged.evidence.map(v6EvidenceIdentity), baseline.merged.evidence);
  assertExactV6Set('merged trace', merged.traceLinks.map(v6TraceIdentity), baseline.merged.traces);
  assertExactV6Sequence('merged section', merged.standardSections.map(v6SectionIdentity), baseline.merged.sections);
  const mergedGapCount = merged.claims.filter((claim) => claim.kind === 'GAP').length;
  if (merged.claims.length !== V6_COLLECTION_BASELINE.claims
    || merged.evidence.length !== V6_COLLECTION_BASELINE.evidence
    || mergedGapCount !== V6_COLLECTION_BASELINE.gaps) {
    throw new Error('Demo content package failed: V6 merged GAP collection changed');
  }

  const graphRoot = input.snapshotRoot ?? canonicalV6SnapshotRoot;
  const graph = readJsonSync(join(graphRoot, 'sources/terminology/graph.json'), 'terminology graph');
  assertExactV6Set('terminology term', graph.terms?.map(stableJson), baseline.graph.terms);
  assertExactV6Set('terminology relation', graph.relations?.map(stableJson), baseline.graph.relations);
  if (graph.terms?.length !== V6_COLLECTION_BASELINE.terminologyTerms
    || graph.relations?.length !== V6_COLLECTION_BASELINE.terminologyRelations) {
    throw new Error('Demo content package failed: V6 terminology baseline is incomplete');
  }
}

function expectedV6Sections() {
  return standardSectionOrder.map(([sectionId, heading]) => ({ sectionId, heading }));
}

function richSnapshotVersion(snapshotId) {
  const match = /^guanyijia-demo-content-v(\d+)-/u.exec(snapshotId ?? '');
  return match ? Number(match[1]) : undefined;
}

function isRichSnapshot(snapshotId) {
  const version = richSnapshotVersion(snapshotId);
  return Number.isInteger(version) && version >= 6;
}

function previousPublicationSnapshotId(manifest) {
  if (typeof manifest.previousSnapshotId === 'string' && manifest.previousSnapshotId) {
    return manifest.previousSnapshotId;
  }
  // V6 predates the manifest lineage field. Its browser projection already
  // promised persisted V5 runs an exact historical publication, so retain
  // that compatibility edge while newer rich snapshots follow their sealed
  // manifest lineage.
  return manifest.snapshotId === V6_SNAPSHOT_ID ? V5_SNAPSHOT_ID : undefined;
}

/**
 * Validates the sealed, human-readable V6 source review as a direct package
 * input.  This deliberately does not rebuild claims from legacy selectors or
 * from nearby Markdown lines: the review owns its explicit sections, claims
 * and evidence references.
 */
function validateV6FrozenReview({ source, review }) {
  if (!review || review.schemaVersion !== 1 || review.documentKind !== 'SOURCE'
    || review.sourceId !== source.sourceId || review.snapshotId !== source.snapshotId
    || review.contentOrigin !== source.contentOrigin
    || !isNonEmptyString(review.title) || !isNonEmptyString(review.coverageLabel)
    || !isNonEmptyString(review.markdown) || sha256(review.markdown) !== review.markdownSha256
    || !Array.isArray(review.standardSections) || !Array.isArray(review.claims)
    || !Array.isArray(review.evidence) || !Array.isArray(review.traceLinks)) {
    throw new Error(`Demo content package failed: invalid V6 source review for ${source.sourceId}`);
  }

  const expectedSections = expectedV6Sections();
  if (review.standardSections.length !== expectedSections.length
    || review.standardSections.some((section, index) => (
      section?.sectionId !== expectedSections[index].sectionId
      || section.heading !== expectedSections[index].heading
      || !isNonEmptyString(section.purpose)
      || !isNonEmptyString(section.markdownAnchor)
      || !Array.isArray(section.claimIds)
    ))) {
    throw new Error(`Demo content package failed: invalid V6 standard sections for ${source.sourceId}`);
  }

  const sectionAnchorIds = new Set(review.standardSections.map((section) => section.markdownAnchor));
  if (sectionAnchorIds.size !== review.standardSections.length) {
    throw new Error(`Demo content package failed: duplicate V6 section anchor for ${source.sourceId}`);
  }
  const evidenceByRef = new Map(review.evidence.map((entry) => [entry?.evidenceRef, entry]));
  if (evidenceByRef.size !== review.evidence.length
    || review.evidence.some((entry) => !isNonEmptyString(entry?.evidenceRef)
      || !isNonEmptyString(entry.excerpt)
      || sha256(entry.excerpt) !== entry.excerptSha256)) {
    throw new Error(`Demo content package failed: invalid V6 evidence for ${source.sourceId}`);
  }
  const claimById = new Map(review.claims.map((claim) => [claim?.claimId, claim]));
  if (claimById.size !== review.claims.length
    || review.claims.some((claim) => !isNonEmptyString(claim?.claimId)
      || !isNonEmptyString(claim.sectionId)
      || !Number.isInteger(claim.section)
      || !isNonEmptyString(claim.title)
      || !isNonEmptyString(claim.statement)
      || !isNonEmptyString(claim.markdownAnchor)
      || !Array.isArray(claim.evidenceRefs)
      || claim.evidenceRefs.length === 0
      || claim.evidenceRefs.some((evidenceRef) => !evidenceByRef.has(evidenceRef)))) {
    throw new Error(`Demo content package failed: invalid V6 claim for ${source.sourceId}`);
  }

  const expectedClaimIds = new Set();
  for (const [index, section] of review.standardSections.entries()) {
    const expected = expectedSections[index];
    const seenClaimIds = new Set(section.claimIds);
    if (seenClaimIds.size !== section.claimIds.length
      || section.claimIds.some((claimId) => {
        const claim = claimById.get(claimId);
        return !claim || claim.sectionId !== expected.sectionId || claim.section !== index + 1;
      })) {
      throw new Error(`Demo content package failed: V6 claim-to-section mismatch for ${source.sourceId}`);
    }
    for (const claimId of section.claimIds) {
      if (expectedClaimIds.has(claimId)) {
        throw new Error(`Demo content package failed: duplicate V6 section claim for ${source.sourceId}`);
      }
      expectedClaimIds.add(claimId);
    }
  }
  if (expectedClaimIds.size !== claimById.size) {
    throw new Error(`Demo content package failed: unassigned V6 claim for ${source.sourceId}`);
  }

  const traceAnchors = new Set(review.traceLinks.map((trace) => trace?.markdownAnchor));
  if (traceAnchors.size !== review.traceLinks.length
    || review.traceLinks.some((trace) => !isNonEmptyString(trace?.markdownAnchor)
      || !isNonEmptyString(trace.linePrefix)
      || !Array.isArray(trace.evidenceRefs)
      || trace.evidenceRefs.some((evidenceRef) => !evidenceByRef.has(evidenceRef)))) {
    throw new Error(`Demo content package failed: invalid V6 trace links for ${source.sourceId}`);
  }
}

/**
 * Converts one V6 frozen review to browser-safe publication data.  The rich
 * source review is deliberately copied as-is (apart from the formal snapshot
 * identity required by the workbench), rather than re-derived from a legacy
 * content adapter.
 */
export function packageV6SourceReview({
  source,
  formalSnapshotId,
  review,
  sourceFileCount = 0,
  sourceType,
  location,
  scope,
  credentialLabel,
}) {
  validateV6FrozenReview({ source, review });
  const packagedReview = clone(review);
  packagedReview.snapshotId = formalSnapshotId;
  return {
    sourceId: source.sourceId,
    formalSnapshotId,
    contentSourceSnapshotId: source.snapshotId,
    origin: source.contentOrigin,
    sourceFileCount,
    sourceType,
    location,
    scope,
    credentialLabel,
    review: packagedReview,
  };
}

function mergedClaim(source, claim) {
  return {
    ...clone(claim),
    claimId: `merged:${source.sourceId}:${claim.claimId}`,
    sourceClaimId: claim.claimId,
    sourceId: source.sourceId,
    sourceTitle: source.review.title,
    markdownAnchor: `merged:${source.sourceId}:${claim.markdownAnchor}`,
  };
}

const readerFacingInternalToken = /\[TRACE:|<!--|\b(?:SOURCE_NATIVE|DEMO_AUTHORED|DERIVED_DEMO|SNAPSHOT_REFERENCE|sourceId|snapshotId|contentOrigin)\b|sha256:/iu;

function v7ReaderSectionBody(source, sectionId) {
  const body = source.review?.sections?.[sectionId];
  if (!isNonEmptyString(body)) {
    throw new Error(`Demo content package failed: V7 reader section is missing for ${source.sourceId}/${sectionId}`);
  }
  const readable = body.trim();
  if (readerFacingInternalToken.test(readable)) {
    throw new Error(`Demo content package failed: V7 reader section exposes internal text for ${source.sourceId}/${sectionId}`);
  }
  return readable;
}

function v7MergedSectionSourceBody(source, section, sourceClaims) {
  const readerBody = v7ReaderSectionBody(source, section.sectionId);
  const claimLines = sourceClaims.flatMap((claim) => [
    `- **${claim.title}**：${claim.statement}`,
    '',
  ]);
  return [
    `### ${source.review.title}`,
    '',
    readerBody,
    '',
    ...(claimLines.length > 0 ? ['本源可核对结论：', '', ...claimLines] : []),
  ];
}

/**
 * Provides a deterministic, clearly non-final document baseline for the five
 * frozen V6 source reviews. It is a read-only cross-source starting point,
 * never a result of a live standardization run or a formal V1 document.
 */
export function buildV6MergedBaseline(sources, input = {}) {
  if (!Array.isArray(sources) || sources.length !== Object.keys(formalSnapshotIds).length) {
    throw new Error('Demo content package failed: V6 merged baseline requires five sources');
  }
  const expectedIds = Object.keys(formalSnapshotIds);
  if (sources.some((source, index) => source?.sourceId !== expectedIds[index]
    || source.review?.documentKind !== 'SOURCE')) {
    throw new Error('Demo content package failed: V6 merged baseline source order mismatch');
  }

  const claims = sources.flatMap((source) => source.review.claims.map((claim) => mergedClaim(source, claim)));
  const evidence = sources.flatMap((source) => source.review.evidence.map((entry) => ({
    ...clone(entry),
    sourceId: source.sourceId,
  })));
  const traceLinks = claims.map((claim) => ({
    linePrefix: `merged:${claim.claimId}`,
    markdownAnchor: claim.markdownAnchor,
    section: claim.section,
    sectionId: claim.sectionId,
    sectionPurpose: claim.sectionPurpose,
    claim: {
      title: claim.title,
      statement: claim.statement,
      focusIdentifiers: clone(claim.focusIdentifiers ?? []),
    },
    evidenceRefs: clone(claim.evidenceRefs),
  }));
  const standardSections = expectedV6Sections().map(({ sectionId, heading }, index) => {
    const sectionClaims = claims.filter((claim) => claim.sectionId === sectionId);
    return {
      sectionId,
      heading,
      purpose: `汇总五份冻结资料中与${heading.replace(/^\d+\.\s*/u, '')}相关的内容，待本次运行确认。`,
      markdownAnchor: `merged-${sectionId.toLowerCase()}`,
      claimIds: sectionClaims.map((claim) => claim.claimId),
      section: index + 1,
    };
  });
  const includeReaderNarratives = richSnapshotVersion(input.contentSnapshotId ?? V6_SNAPSHOT_ID) >= 7;
  const markdown = [
    '# 多源标准化文档基线（待本次运行确认）',
    '',
    '> 本文汇总五份已冻结阅读资料，供本次资料整理和后续建模参考；它不是正式发布文档，也不会改变正式模型。',
    '',
    ...standardSections.flatMap((section) => {
      const sectionClaims = claims.filter((claim) => claim.sectionId === section.sectionId);
      const bySource = sources.map((source) => ({
        source,
        claims: sectionClaims.filter((claim) => claim.sourceId === source.sourceId),
      })).filter((entry) => includeReaderNarratives || entry.claims.length > 0);
      return [
        `## ${section.heading}`,
        '',
        ...bySource.flatMap(({ source, claims: sourceClaims }) => [
          ...(includeReaderNarratives
            ? v7MergedSectionSourceBody(source, section, sourceClaims)
            : [
              `### ${source.review.title}`,
              '',
              ...sourceClaims.flatMap((claim) => [`- **${claim.title}**：${claim.statement}`, '']),
            ]),
        ]),
      ];
    }),
  ].join('\n').trimEnd();
  return {
    schemaVersion: 1,
    documentKind: 'MERGED',
    sourceId: 'guanyijia_demo_content_merged_baseline',
    snapshotId: input.contentSnapshotId ?? V6_SNAPSHOT_ID,
    contentOrigin: 'DERIVED_DEMO',
    title: '多源标准化文档基线（待本次运行确认）',
    coverageLabel: '五份冻结资料 · 九章对齐',
    standardSections,
    markdown,
    markdownSha256: sha256(markdown),
    evidence,
    claims,
    traceLinks,
  };
}

function sourceReview({ source, formalSnapshotId, title, coverageLabel, markdown, evidence, traceLinks, sourceFileCount = 0, sourceType, location, scope, credentialLabel }) {
  return {
    sourceId: source.sourceId,
    formalSnapshotId,
    contentSourceSnapshotId: source.snapshotId,
    origin: source.contentOrigin,
    sourceFileCount,
    sourceType,
    location,
    scope,
    credentialLabel,
    review: {
      schemaVersion: 1,
      sourceId: source.sourceId,
      snapshotId: formalSnapshotId,
      title,
      coverageLabel,
      markdown,
      markdownSha256: sha256(markdown),
      evidence,
      traceLinks,
    },
  };
}

function authoredReview({ root, source, formalSnapshotId, title, coverageLabel, paths, sourceType, location, scope }) {
  return Promise.all(paths.map(async (path) => ({
    path,
    content: await readFile(join(root, path), 'utf8'),
    artifact: artifactFor(source, path),
  }))).then((documents) => {
    const chunks = documents.flatMap((document, documentIndex) => markdownChunks(document.path, document.content)
      .map((chunk) => ({ ...chunk, documentIndex, artifact: document.artifact })));
    if (chunks.length === 0) throw new Error(`Demo content package failed: authored source ${source.sourceId} has no sections`);
    const evidence = [];
    const traceLinks = [];
    const sections = new Map();
    for (const entry of chunks) {
      const section = entry.documentIndex + 1;
      const sectionTitle = documentSectionTitle(source.sourceId, entry.documentIndex);
      const sectionEntries = sections.get(section) ?? [];
      const sequence = sectionEntries.length + 1;
      const anchor = `${source.sourceId}-document-${section}-section-${sequence}`;
      const evidenceRef = `${source.sourceId}:document:${section}:section:${sequence}`;
      const excerpt = entry.content;
      evidence.push({
        evidenceRef,
        evidenceClass: source.contentOrigin,
        excerptKind: 'DOCUMENT_SECTION',
        title: entry.heading,
        excerpt,
        locationLabel: source.contentOrigin === 'DEMO_AUTHORED' ? '演示资料章节' : '派生资料章节',
        locationValue: `${entry.path}:L${entry.startLine}-L${entry.endLine}`,
        excerptSha256: sha256(excerpt),
        artifactDigest: entry.artifact.sha256,
        affectedObjectRefs: [`document:${source.sourceId}:${section}`],
      });
      traceLinks.push({
        linePrefix: `trace:${source.sourceId}:${section}:${sequence}`,
        markdownAnchor: anchor,
        section,
        sectionId: `${source.sourceId}:section:${section}`,
        sectionPurpose: sectionTitle,
        claim: { title: entry.heading, statement: singleSentence(entry.content), focusIdentifiers: [] },
        evidenceRefs: [evidenceRef],
      });
      sectionEntries.push(entry);
      sections.set(section, sectionEntries);
    }
    const renderedSections = [...sections.entries()].sort(([left], [right]) => left - right)
      .map(([section, entries]) => [
        `## ${section}. ${documentSectionTitle(source.sourceId, section - 1)}`,
        '',
        ...entries.flatMap((entry) => [`### ${entry.heading}`, '', entry.content, '']),
      ].join('\n').trimEnd());
    const header = [
      `# ${title}`,
      '',
      `> ${source.contentOrigin === 'DEMO_AUTHORED' ? '演示编写资料：用于说明本次审阅的业务语境，不作为外部原文或现行制度。' : '派生术语资料：用于统一阅读，不作为独立事实。'}`,
      '',
    ].join('\n');
    return sourceReview({
      source,
      formalSnapshotId,
      title,
      coverageLabel,
      markdown: `${header}${renderedSections.join('\n\n')}`,
      evidence,
      traceLinks,
      sourceType,
      location,
      scope,
      credentialLabel: '不需要',
    });
  });
}

async function githubReview(root, source, formalSnapshotId) {
  const repository = JSON.parse(await readFile(join(root, 'sources/github/repository.json'), 'utf8'));
  const index = (await readFile(join(root, 'sources/github/file-index.jsonl'), 'utf8')).trim().split('\n').filter(Boolean).map(JSON.parse);
  const indexByPath = new Map(index.map((entry) => [entry.path, entry]));
  const evidence = [];
  const traceLinks = [];
  const sections = Array.from({ length: reviewSectionsBySource.guanyijia_github.length }, () => []);
  for (const selector of githubSelectors) {
    const entry = indexByPath.get(selector.path);
    if (!entry || entry.textEncoding !== 'UTF-8') throw new Error(`Demo content package failed: GitHub source index is unavailable for ${selector.path}`);
    const content = await readFile(join(root, 'sources/github/source', selector.path), 'utf8');
    if (sha256(content) !== entry.sha256 || lineCount(content) !== entry.lineCount) {
      throw new Error(`Demo content package failed: GitHub file digest mismatch for ${selector.path}`);
    }
    const lines = content.replace(/\r\n/g, '\n').split('\n');
    const matchingIndexes = lines.flatMap((line, index) => line.includes(selector.needle) ? [index] : []);
    if (matchingIndexes.length !== 1) throw new Error(`Demo content package failed: GitHub story selector is ambiguous for ${selector.id}`);
    const start = Math.max(0, matchingIndexes[0] - selector.before);
    const end = Math.min(lines.length - 1, matchingIndexes[0] + selector.after);
    const excerpt = lines.slice(start, end + 1).join('\n');
    const evidenceRef = selector.id;
    evidence.push({
      evidenceRef,
      evidenceClass: 'SOURCE_NATIVE',
      excerptKind: 'EXACT_EXCERPT',
      title: selector.title,
      excerpt,
      locationLabel: '固定GitHub提交源码',
      locationValue: `${selector.path}:L${start + 1}-L${end + 1}`,
      excerptSha256: sha256(excerpt),
      artifactDigest: entry.sha256,
      affectedObjectRefs: selector.affectedObjectRefs,
    });
    const anchor = `github-story-${evidence.length}`;
    traceLinks.push({
      linePrefix: `trace:github:${evidence.length}`,
      markdownAnchor: anchor,
      section: selector.section,
      sectionId: `guanyijia_github:section:${selector.section}`,
      sectionPurpose: reviewSectionsBySource.guanyijia_github[selector.section - 1],
      claim: { title: selector.title, statement: selector.summary, focusIdentifiers: [] },
      evidenceRefs: [evidenceRef],
    });
    sections[selector.section - 1].push(`- **${selector.title}**：${selector.summary}`);
  }
  const markdown = [
    '# 代码仓库审阅',
    '',
    '> 固定版本源码节选：每条结论均可打开对应的已保存行段；未选入的代码不在本次审阅范围内。',
    '',
    ...sections.flatMap((entries, index) => entries.length ? [
      `## ${index + 1}. ${reviewSectionsBySource.guanyijia_github[index]}`,
      '',
      entries.join('\n\n'),
      '',
    ] : []),
  ].join('\n\n');
  return sourceReview({
    source,
    formalSnapshotId,
    title: '代码仓库审阅',
    coverageLabel: `${evidence.length} 条固定版本源码节选`,
    markdown,
    evidence,
    traceLinks,
    sourceFileCount: repository.trackedFileCount,
    sourceType: 'GitHub 固定版本源码',
    location: 'jishenghua/jshERP',
    scope: `本次审阅相关的${evidence.length}条行段`,
    credentialLabel: '不需要',
  });
}

async function githubV5Review(root, source, formalSnapshotId) {
  const repository = JSON.parse(await readFile(join(root, 'sources/github/repository.json'), 'utf8'));
  const index = (await readFile(join(root, 'sources/github/file-index.jsonl'), 'utf8'))
    .trim().split('\n').filter(Boolean).map(JSON.parse);
  const indexByPath = new Map(index.map((entry) => [entry.path, entry]));
  const review = JSON.parse(await readFile(join(root, 'sources/github/review-claims.json'), 'utf8'));
  const evidence = await Promise.all(review.evidence.map(async (record) => {
    const indexEntry = indexByPath.get(record.path);
    if (!indexEntry || indexEntry.textEncoding !== 'UTF-8') {
      throw new Error(`Demo content package failed: V5 source index is unavailable for ${record.path}`);
    }
    const content = await readFile(join(root, 'sources/github/source', record.path), 'utf8');
    if (sha256(content) !== indexEntry.sha256 || lineCount(content) !== indexEntry.lineCount) {
      throw new Error(`Demo content package failed: V5 source digest mismatch for ${record.path}`);
    }
    const lines = content.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
    const excerpt = lines.slice(record.startLine - 1, record.endLine).join('\n');
    if (sha256(excerpt) !== record.excerptSha256) {
      throw new Error(`Demo content package failed: V5 source excerpt mismatch for ${record.path}`);
    }
    return {
      evidenceRef: record.evidenceRef,
      evidenceClass: 'SOURCE_NATIVE',
      excerptKind: 'EXACT_EXCERPT',
      title: record.title,
      excerpt,
      locationLabel: '固定版本源码节选',
      locationValue: `${record.path}:L${record.startLine}-L${record.endLine}`,
      excerptSha256: record.excerptSha256,
      artifactDigest: record.artifactDigest,
      affectedObjectRefs: [],
    };
  }));
  const sectionClaims = githubV5SectionNames.map((_, sectionIndex) => review.claims
    .filter((claim) => claim.section === sectionIndex + 1));
  const traceLinks = review.claims.map((claim) => ({
    linePrefix: `trace:github:v5:${claim.claimId}`,
    markdownAnchor: `github-v5-${claim.claimId}`,
    section: claim.section,
    sectionId: `guanyijia_github:section:${claim.section}`,
    sectionPurpose: githubV5SectionNames[claim.section - 1],
    claim: {
      title: claim.title,
      statement: claim.statement,
      focusIdentifiers: claim.focusIdentifiers,
    },
    evidenceRefs: claim.evidenceRefs,
  }));
  const markdown = [
    '# 代码仓库审阅',
    '',
    `> 固定版本共 ${repository.trackedFileCount} 个文件；本次选择性审阅 ${review.selectedFileCount} 个与业务流程直接相关的文件。未选入的代码不据此作出结论。`,
    '',
    ...sectionClaims.flatMap((claims, index) => claims.length === 0 ? [] : [
      `## ${index + 1}. ${githubV5SectionNames[index]}`,
      '',
      ...claims.flatMap((claim) => [
        `### ${claim.title}`,
        '',
        claim.statement,
        '',
        `审阅关注：${claim.reviewFocus}`,
        '',
        `资料边界：${claim.boundary}`,
        '',
      ]),
    ]),
  ].join('\n').trimEnd();
  return sourceReview({
    source,
    formalSnapshotId,
    title: '代码仓库审阅',
    coverageLabel: `${review.claims.length} 项结论 · ${review.selectedFileCount} 个文件`,
    markdown,
    evidence,
    traceLinks,
    sourceFileCount: repository.trackedFileCount,
    sourceType: '固定版本源码节选',
    location: 'jishenghua/jshERP',
    scope: `${review.selectedFileCount}个与业务流程直接相关的文件`,
    credentialLabel: '不需要',
  });
}

async function semanticaReview(root, source, formalSnapshotId) {
  const documentPath = 'sources/terminology/术语图说明.md';
  const graphPath = 'sources/terminology/graph.json';
  const document = await readFile(join(root, documentPath), 'utf8');
  const graphText = await readFile(join(root, graphPath), 'utf8');
  const graph = JSON.parse(graphText);
  const documentArtifact = artifactFor(source, documentPath);
  const graphArtifact = artifactFor(source, graphPath);
  const documentExcerpt = markdownChunks(documentPath, document)[0]?.content;
  if (!documentExcerpt) throw new Error('Demo content package failed: terminology introduction is missing');
  const evidence = [
    {
      evidenceRef: 'guanyijia_semantica_demo:document:1', evidenceClass: 'DERIVED_DEMO', excerptKind: 'DOCUMENT_SECTION',
      title: sourceTitle(document), excerpt: documentExcerpt, locationLabel: '派生资料章节', locationValue: `${documentPath}:L1-L${lineCount(document)}`,
      excerptSha256: sha256(documentExcerpt), artifactDigest: documentArtifact.sha256, affectedObjectRefs: [`graph:${source.snapshotId}`],
    },
    {
      evidenceRef: 'guanyijia_semantica_demo:graph:1', evidenceClass: 'DERIVED_DEMO', excerptKind: 'DERIVED_GRAPH',
      title: '术语图JSON', excerpt: graphText, locationLabel: '派生术语图', locationValue: `${graphPath}:L1-L${lineCount(graphText)}`,
      excerptSha256: sha256(graphText), artifactDigest: graphArtifact.sha256, affectedObjectRefs: graph.terms.slice(0, 8).map((term) => `term:${term.termId}`),
    },
  ];
  const domains = [...new Set(graph.terms.map((term) => term.domain))].slice(0, 4);
  const traceLinks = [{
    linePrefix: 'trace:semantica:introduction',
    markdownAnchor: 'semantica-introduction',
    section: 1,
    sectionId: 'guanyijia_semantica_demo:section:1',
    sectionPurpose: reviewSectionsBySource.guanyijia_semantica_demo[0],
    claim: { title: '术语图用于统一业务阅读', statement: '术语图把对象、流程、指标和职责关联起来，帮助统一阅读方式。', focusIdentifiers: [] },
    evidenceRefs: [evidence[0].evidenceRef],
  }];
  const termSummaries = domains.map((domain, index) => {
    const terms = graph.terms.filter((term) => term.domain === domain).slice(0, 5);
    const anchor = `semantica-domain-${index + 1}`;
    const evidenceRef = evidence[1].evidenceRef;
    const names = terms.map((term) => term.name);
    traceLinks.push({
      linePrefix: `trace:semantica:domain:${index + 1}`,
      markdownAnchor: anchor,
      section: 1,
      sectionId: 'guanyijia_semantica_demo:section:1',
      sectionPurpose: reviewSectionsBySource.guanyijia_semantica_demo[0],
      claim: {
        title: `${domain}术语关系`,
        statement: `本组整理${names.join('、')}等术语及其关系，用于辅助理解资料。`,
        focusIdentifiers: names,
      },
      evidenceRefs: [evidenceRef],
    });
    return `### ${domain}\n\n${terms.map((term) => `- **${term.name}**：${term.definition}`).join('\n')}`;
  });
  const markdown = [
    '# 企业术语图',
    '',
    '> 派生术语图：用于统一对象、流程和指标的阅读方式，不替代来源材料或业务确认。',
    '',
    `## 1. ${reviewSectionsBySource.guanyijia_semantica_demo[0]}`,
    '',
    documentExcerpt,
    '',
    ...termSummaries,
  ].join('\n\n');
  return sourceReview({
    source,
    formalSnapshotId,
    title: '企业术语图',
    coverageLabel: `${graph.terms.length} 个术语 · ${graph.relations.length} 条关系`,
    markdown,
    evidence,
    traceLinks,
    sourceType: '派生术语图',
    location: '术语图快照',
    scope: `${graph.terms.length}个术语、${graph.relations.length}条关系`,
    credentialLabel: '不需要',
  });
}

async function readRichFrozenReview(root, source) {
  const path = v6ReviewPathBySource[source.sourceId];
  if (!path) throw new Error(`Demo content package failed: no rich review path for ${source.sourceId}`);
  const artifact = artifactFor(source, path);
  const text = await readFile(join(root, path), 'utf8');
  if (sha256(text) !== artifact.sha256) {
    throw new Error(`Demo content package failed: rich review digest mismatch for ${source.sourceId}`);
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`Demo content package failed: rich review JSON is invalid for ${source.sourceId}`);
  }
}

async function richSourceMetadata(root, source) {
  switch (source.sourceId) {
    case 'guanyijia_mysql': {
      const referencePath = 'sources/mysql/reference.json';
      const artifact = artifactFor(source, referencePath);
      const referenceText = await readFile(join(root, referencePath), 'utf8');
      if (sha256(referenceText) !== artifact.sha256) {
        throw new Error('Demo content package failed: rich MySQL reference digest mismatch');
      }
      const reference = JSON.parse(referenceText);
      return {
        sourceFileCount: 0,
        sourceType: 'MySQL 冻结逻辑快照',
        location: 'jsh_erp / 20260813T032528Z',
        scope: `${reference.objectCounts.tables}张表、${reference.objectCounts.views}个视图、${reference.objectCounts.procedures}个过程`,
        credentialLabel: '已配置',
      };
    }
    case 'guanyijia_github': {
      const repositoryPath = 'sources/github/repository.json';
      const artifact = artifactFor(source, repositoryPath);
      const repositoryText = await readFile(join(root, repositoryPath), 'utf8');
      if (sha256(repositoryText) !== artifact.sha256) {
        throw new Error('Demo content package failed: rich GitHub repository digest mismatch');
      }
      const repository = JSON.parse(repositoryText);
      return {
        sourceFileCount: repository.trackedFileCount,
        sourceType: '固定版本源码节选',
        location: 'jishenghua/jshERP',
        scope: '本次选择性审阅的业务相关源码',
        credentialLabel: '不需要',
      };
    }
    case 'guanyijia_official_docs':
      return {
        sourceFileCount: 0,
        sourceType: '业务说明（演示编写）',
        location: 'frozen official/*.md',
        scope: '系统边界、业务流程、财务与状态口径',
        credentialLabel: '不需要',
      };
    case 'guanyijia_demo_policy':
      return {
        sourceFileCount: 0,
        sourceType: 'ERP 管理制度（演示草案）',
        location: 'frozen policy/*.md',
        scope: '权限、采购销售、库存、财务、审核与统计',
        credentialLabel: '不需要',
      };
    case 'guanyijia_semantica_demo':
      return {
        sourceFileCount: 0,
        sourceType: '派生术语图',
        location: '术语图快照',
        scope: '企业术语与关系',
        credentialLabel: '不需要',
      };
    default:
      throw new Error(`Demo content package failed: unknown rich source ${source.sourceId}`);
  }
}

async function buildRichDemoContentPublication(snapshotRoot, manifest, sourceById, publicationLineage) {
  const sources = [];
  for (const sourceId of Object.keys(formalSnapshotIds)) {
    const source = sourceById.get(sourceId);
    const [review, metadata] = await Promise.all([
      readRichFrozenReview(snapshotRoot, source),
      richSourceMetadata(snapshotRoot, source),
    ]);
    sources.push(packageV6SourceReview({
      source,
      formalSnapshotId: formalSnapshotIds[sourceId],
      review,
      ...metadata,
    }));
  }
  const previousSnapshotId = previousPublicationSnapshotId(manifest);
  const legacyPublications = previousSnapshotId
    ? [await buildDemoContentPublicationAt(
      join(dirname(snapshotRoot), previousSnapshotId),
      publicationLineage,
    )]
    : [];
  return {
    schemaVersion: 1,
    storyKey: manifest.storyKey,
    contentSnapshotId: manifest.snapshotId,
    contentSha256: manifest.contentSha256,
    sources,
    mergedDocument: buildV6MergedBaseline(sources, { contentSnapshotId: manifest.snapshotId }),
    // Persisted runs keep resolving to the exact immutable material they began
    // with. Follow the sealed sidecar lineage recursively rather than
    // silently reinterpreting a prior rich publication as the new one.
    ...(legacyPublications.length > 0 ? { legacyPublications } : {}),
  };
}

/**
 * Package-time validation intentionally repeats the V7-specific sealed
 * snapshot validation.  The generic snapshot validator only establishes
 * file-integrity; it cannot prove the reader narratives, V6 lineage, and
 * deterministic transformed reviews still form a valid V7 publication.
 */
async function validateSnapshotForPublication(snapshotRoot) {
  await validateDemoContentSnapshot(snapshotRoot);
  const manifest = JSON.parse(await readFile(join(snapshotRoot, 'manifest.json'), 'utf8'));
  if (manifest.snapshotId === V6_SNAPSHOT_ID) {
    await validateV6Snapshot(snapshotRoot);
  } else if (manifest.snapshotId === V7_SNAPSHOT_ID) {
    await validateV7Snapshot(snapshotRoot, {
      sourceSnapshotRoot: join(dirname(resolve(snapshotRoot)), validatedV6SnapshotId),
    });
  }
  return manifest;
}

/**
 * Explicit maintenance-time projection. It reads an already validated
 * sidecar and returns only browser-safe material; it never invokes Git,
 * a network source, a scanner, or Codex.
 */
async function buildDemoContentPublicationAt(snapshotRoot, publicationLineage) {
  const canonicalSnapshotRoot = resolve(snapshotRoot);
  if (publicationLineage.has(canonicalSnapshotRoot)) {
    throw new Error('Demo content package failed: publication lineage contains a cycle');
  }
  const nextLineage = new Set(publicationLineage);
  nextLineage.add(canonicalSnapshotRoot);
  const manifest = await validateSnapshotForPublication(snapshotRoot);
  const sourceById = new Map(manifest.sources.map((source) => [source.sourceId, source]));
  const required = Object.keys(formalSnapshotIds);
  if (required.some((sourceId) => !sourceById.has(sourceId)) || sourceById.size !== 5) {
    throw new Error('Demo content package failed: five-source manifest membership mismatch');
  }
  if (isRichSnapshot(manifest.snapshotId)) {
    const publication = await buildRichDemoContentPublication(snapshotRoot, manifest, sourceById, nextLineage);
    if (manifest.snapshotId === V6_SNAPSHOT_ID) {
      validateV6PublicationCompleteness(publication, { snapshotRoot });
    }
    return publication;
  }
  const mysql = sourceById.get('guanyijia_mysql');
  const github = sourceById.get('guanyijia_github');
  const official = sourceById.get('guanyijia_official_docs');
  const policy = sourceById.get('guanyijia_demo_policy');
  const semantica = sourceById.get('guanyijia_semantica_demo');
  const mysqlReference = JSON.parse(await readFile(join(snapshotRoot, 'sources/mysql/reference.json'), 'utf8'));
  const sources = [
    sourceReview({
      source: mysql,
      formalSnapshotId: formalSnapshotIds.guanyijia_mysql,
      title: '数据库建模审阅（真实证据节选）',
      coverageLabel: '30 / 95 张表 · 6 个部署过程',
      markdown: '', evidence: [], traceLinks: [],
      sourceType: 'MySQL 冻结逻辑快照',
      location: 'jsh_erp / 20260813T032528Z',
      scope: `${mysqlReference.objectCounts.tables}张表、${mysqlReference.objectCounts.views}个视图、${mysqlReference.objectCounts.procedures}个过程`,
      credentialLabel: '已配置',
    }),
    github.artifacts.some((artifact) => artifact.path === 'sources/github/review-claims.json')
      ? await githubV5Review(snapshotRoot, github, formalSnapshotIds.guanyijia_github)
      : await githubReview(snapshotRoot, github, formalSnapshotIds.guanyijia_github),
    await authoredReview({
      root: snapshotRoot, source: official, formalSnapshotId: formalSnapshotIds.guanyijia_official_docs,
      title: '业务文档审阅（演示编写）', coverageLabel: '3 份业务说明',
      paths: official.artifacts.map((artifact) => artifact.path),
      sourceType: '业务文档（演示）', location: 'frozen official/*.md', scope: '系统边界、业务流程、财务与状态口径',
    }),
    await authoredReview({
      root: snapshotRoot, source: policy, formalSnapshotId: formalSnapshotIds.guanyijia_demo_policy,
      title: 'ERP管理制度审阅（演示草案）', coverageLabel: '5 份制度草案',
      paths: policy.artifacts.map((artifact) => artifact.path),
      sourceType: 'ERP 管理制度（演示）', location: 'frozen policy/*.md', scope: '权限、采购销售、库存、财务、审核与统计',
    }),
    await semanticaReview(snapshotRoot, semantica, formalSnapshotIds.guanyijia_semantica_demo),
  ];
  return {
    schemaVersion: 1,
    storyKey: manifest.storyKey,
    contentSnapshotId: manifest.snapshotId,
    contentSha256: manifest.contentSha256,
    sources,
  };
}

export async function buildDemoContentPublication(snapshotRoot = defaultSnapshotRoot) {
  return buildDemoContentPublicationAt(snapshotRoot, new Set());
}

export function renderDemoContentPublication(publication) {
  return [
    '/** This file is generated from an immutable Demo content sidecar. Do not edit manually. */',
    `export const pinnedDemoContentPublication = ${JSON.stringify(publication, null, 2)} as const;`,
    '',
  ].join('\n');
}

async function main() {
  const [command, snapshotRoot = defaultSnapshotRoot, output = defaultOutput] = process.argv.slice(2);
  if (!['--package', '--check'].includes(command) || process.argv.length > 5) {
    throw new Error('Usage: node scripts/evidence/guanyijia-demo-content-package.mjs --package|--check [snapshot-root] [output]');
  }
  const publication = await buildDemoContentPublication(snapshotRoot);
  const expected = renderDemoContentPublication(publication);
  if (command === '--check') {
    const actual = await readFile(output, 'utf8').catch(() => '');
    if (actual !== expected) throw new Error('Demo content browser package is missing or stale');
    return;
  }
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, expected, 'utf8');
  process.stdout.write(`${JSON.stringify({ contentSnapshotId: publication.contentSnapshotId, sources: publication.sources.length }, null, 2)}\n`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
