import rawDatabaseEvidence from '../ai-modeling/fixtures/guanyijia-database-evidence.json' with { type: 'json' };
import rawRepositoryEvidence from '../ai-modeling/fixtures/guanyijia-repository-evidence.json' with { type: 'json' };
import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type { ModelBrowserObjectKind, ModelBrowserObjectRef } from '../model-browser/types.ts';
import {
  canonicalModelingJson,
  renderStandardModelingMarkdown,
  standardSectionOrder,
} from '../modeling-document-bridge/standard-markdown.ts';
import {
  projectSourceDocumentAssertions,
  projectSourceDocumentSections,
} from '../source-documents/structured-projection.ts';
import { guanyijiaOfficialDocumentsFixture } from './official-documents-fixture.ts';
import type {
  SourceDocumentBlock,
  SourceDocumentCompilation,
  DemoPolicyDocumentInput,
  SemanticConflictDefinition,
  SourceCompilationDiff,
  StorySource,
  StorySourceReadSummary,
  StructuredValue,
} from './types.ts';

type EvidenceFile = {
  evidenceId: string;
  category: string;
  objectType: string;
  objectName: string;
  relativePath: string;
  sha256: string;
};

type FrozenEvidenceFixture = {
  source: { summary: string; evidenceIds: string[] };
  manifest: {
    snapshotId: string;
    objectCounts: Record<string, number>;
    evidenceFiles: EvidenceFile[];
    warnings: Array<{ objectName: string; stage: string; status: string; reason: string }>;
    [key: string]: unknown;
  };
};

const databaseEvidence = rawDatabaseEvidence as unknown as FrozenEvidenceFixture;
const repositoryEvidence = rawRepositoryEvidence as unknown as FrozenEvidenceFixture;

export function getGuanyijiaFrozenRealSourceSnapshotIds() {
  return {
    database: databaseEvidence.manifest.snapshotId,
    repository: repositoryEvidence.manifest.snapshotId,
    officialDocs: guanyijiaOfficialDocumentsFixture.source.snapshotId,
  };
}

function resultRef(kind: ModelBrowserObjectKind, objectId: string, ownerId?: string): ModelBrowserObjectRef {
  return {
    scope: 'RESULT', documentVersion: 'guanyijia-five-source-work-standard', kind, objectId,
    ...(ownerId ? { ownerId } : {}),
  };
}

function structuredValue(normalized: string, text: string, detail: Record<string, StructuredValue> = {}): StructuredValue {
  return { normalized, text, ...detail };
}

function block(input: Omit<SourceDocumentBlock, 'blockId'> & { blockId?: string }): SourceDocumentBlock {
  return { ...input, blockId: input.blockId ?? `gyj-block:${input.stableCode}` };
}

function evidenceId(fixture: FrozenEvidenceFixture, objectType: string, objectName: string) {
  const record = fixture.manifest.evidenceFiles.find((candidate) => (
    candidate.objectType === objectType && candidate.objectName === objectName
  ));
  if (!record) throw new Error(`冻结 Evidence 缺少 ${objectType}:${objectName}`);
  return record.evidenceId;
}

function tableEvidence(fixture: FrozenEvidenceFixture, table: string) {
  return evidenceId(fixture, 'TABLE', table);
}

function columnEvidence(table: string, field: string) {
  return evidenceId(databaseEvidence, 'COLUMN', `${table}.${field}`);
}

function locatorsFor(blocks: SourceDocumentBlock[]) {
  const records = [...databaseEvidence.manifest.evidenceFiles, ...repositoryEvidence.manifest.evidenceFiles];
  const byId = new Map(records.map((record) => [record.evidenceId, record]));
  return Object.fromEntries([...new Set(blocks.flatMap((candidate) => candidate.evidenceRefs))].flatMap((ref) => {
    const record = byId.get(ref);
    return record ? [[ref, {
      kind: 'FROZEN_FILE', relativePath: record.relativePath, sha256: record.sha256,
      objectType: record.objectType, objectName: record.objectName,
    }]] : [];
  }));
}

function compileBlocks(input: {
  source: StorySource;
  readSummary: StorySourceReadSummary;
  blocks: SourceDocumentBlock[];
  priorCompilations: SourceDocumentCompilation[];
  introducedConflictIds?: string[];
  corroboratedConflictIds?: string[];
  lineageStatus?: SourceDocumentCompilation['lineageStatus'];
  evidenceLocators?: SourceDocumentCompilation['evidenceLocators'];
}) {
  const blockIds = new Set<string>();
  const semanticKeys = new Set<string>();
  for (const candidate of input.blocks) {
    if (blockIds.has(candidate.blockId)) throw new Error(`来源文档 blockId 重复：${candidate.blockId}`);
    blockIds.add(candidate.blockId);
    const semanticKey = canonicalModelingJson({
      stableCode: candidate.stableCode, value: candidate.value, evidenceRefs: candidate.evidenceRefs,
    });
    if (semanticKeys.has(semanticKey)) throw new Error(`来源文档语义块重复：${candidate.stableCode}`);
    semanticKeys.add(semanticKey);
  }
  const sections = projectSourceDocumentSections(input.blocks);
  const assertions = projectSourceDocumentAssertions(input.blocks);
  const priorBlocks = input.priorCompilations.flatMap((compilation) => compilation.blocks);
  const addedBlockIds: string[] = [];
  const changedBlockIds: string[] = [];
  for (const candidate of input.blocks) {
    const matchingCode = priorBlocks.filter((prior) => prior.stableCode === candidate.stableCode);
    if (!matchingCode.length) addedBlockIds.push(candidate.blockId);
    else if (!matchingCode.some((prior) => canonicalModelingJson(prior.value) === canonicalModelingJson(candidate.value))) {
      changedBlockIds.push(candidate.blockId);
    }
  }
  const markdown = renderStandardModelingMarkdown({
    artifactId: `source-compilation:${input.source.sourceId}:${input.source.snapshotId}`,
    projectId: 'guanyijia_erp',
    documentCode: `guanyijia-five-source--${input.source.sourceId}`,
    title: `${input.source.sourceName}来源文档`,
    sourceBatch: {
      batchId: `source:${input.source.snapshotId}`,
      fingerprint: sha256HexSync(input.source.snapshotId),
      snapshotIds: [input.source.snapshotId],
    },
    sections,
    assertions,
  });
  return {
    sourceId: input.source.sourceId,
    sourceName: input.source.sourceName,
    sourceClass: input.source.sourceClass,
    snapshotId: input.source.snapshotId,
    authority: input.source.authority,
    readSummary: structuredClone(input.readSummary),
    sections,
    blocks: structuredClone(input.blocks),
    assertions,
    markdown,
    markdownSha256: sha256HexSync(markdown),
    evidenceLocators: structuredClone(input.evidenceLocators ?? locatorsFor(input.blocks)),
    delta: {
      addedBlockIds,
      changedBlockIds,
      addedGapIds: input.blocks
        .filter((candidate) => candidate.semanticKind === 'GAP'
          && !priorBlocks.some((prior) => prior.stableCode === candidate.stableCode))
        .map((candidate) => candidate.blockId),
    },
    introducedConflictIds: [...(input.introducedConflictIds ?? [])],
    corroboratedConflictIds: [...(input.corroboratedConflictIds ?? [])],
    lineageStatus: input.lineageStatus ?? input.source.lineageStatus,
    upstreamSourceIds: [...input.source.upstreamSourceIds],
  } satisfies SourceDocumentCompilation;
}

function normalizedValueOf(candidate: SourceDocumentBlock) {
  if (typeof candidate.value !== 'object' || candidate.value === null || Array.isArray(candidate.value)) return null;
  return typeof candidate.value.normalized === 'string' ? candidate.value.normalized : null;
}

function hasTrustedVariant(input: {
  priorCompilations: SourceDocumentCompilation[];
  sourceId: string;
  snapshotId: string;
  stableCode: string;
  normalizedValue: string;
  evidenceRefs: string[];
}) {
  return input.priorCompilations.some((compilation) => {
    if (compilation.sourceId !== input.sourceId || compilation.snapshotId !== input.snapshotId) return false;
    const blocks = compilation.blocks.filter((candidate) => candidate.stableCode === input.stableCode);
    if (blocks.length !== 1 || normalizedValueOf(blocks[0]) !== input.normalizedValue) return false;
    const candidate = blocks[0];
    if (!input.evidenceRefs.every((ref) => candidate.evidenceRefs.includes(ref)
      && compilation.evidenceLocators[ref] !== undefined)) return false;
    const assertions = compilation.assertions.filter((assertion) => assertion.assertionId === candidate.blockId);
    return assertions.length === 1
      && assertions[0].section === candidate.section
      && canonicalModelingJson(assertions[0].evidenceRefs) === canonicalModelingJson(candidate.evidenceRefs);
  });
}

function hasValidProjection(compilation: SourceDocumentCompilation) {
  if (compilation.blocks.length !== compilation.assertions.length) return false;
  if (new Set(compilation.blocks.map((candidate) => candidate.blockId)).size !== compilation.blocks.length) return false;
  if (new Set(compilation.assertions.map((candidate) => candidate.assertionId)).size !== compilation.assertions.length) return false;
  const expectedSections = projectSourceDocumentSections(compilation.blocks);
  if (canonicalModelingJson(compilation.sections) !== canonicalModelingJson(expectedSections)) return false;
  const expectedAssertions = projectSourceDocumentAssertions(compilation.blocks);
  if (canonicalModelingJson(compilation.assertions) !== canonicalModelingJson(expectedAssertions)) return false;
  if (!compilation.blocks.every((candidate) => candidate.evidenceRefs.every((ref) => (
    compilation.evidenceLocators[ref] !== undefined
  )))) return false;
  const expectedMarkdown = renderStandardModelingMarkdown({
    artifactId: `source-compilation:${compilation.sourceId}:${compilation.snapshotId}`,
    projectId: 'guanyijia_erp',
    documentCode: `guanyijia-five-source--${compilation.sourceId}`,
    title: `${compilation.sourceName}来源文档`,
    sourceBatch: {
      batchId: `source:${compilation.snapshotId}`,
      fingerprint: sha256HexSync(compilation.snapshotId),
      snapshotIds: [compilation.snapshotId],
    },
    sections: expectedSections,
    assertions: expectedAssertions,
  });
  return compilation.markdown === expectedMarkdown
    && compilation.markdownSha256 === sha256HexSync(expectedMarkdown);
}

function trustedPriorNormalizedValue(
  priorCompilations: SourceDocumentCompilation[],
  sourceId: string,
  stableCode: string,
) {
  const compilation = priorCompilations.find((candidate) => candidate.sourceId === sourceId);
  if (!compilation || !hasValidProjection(compilation)) return null;
  const blocks = compilation.blocks.filter((candidate) => candidate.stableCode === stableCode);
  return blocks.length === 1 ? normalizedValueOf(blocks[0]!) : null;
}

function conflictProjection(input: {
  sourceId: string;
  blocks: SourceDocumentBlock[];
  priorCompilations: SourceDocumentCompilation[];
}) {
  const normalized = (stableCode: string) => {
    const blocks = input.blocks.filter((candidate) => candidate.stableCode === stableCode);
    return blocks.length === 1 ? normalizedValueOf(blocks[0]!) : null;
  };
  if (input.sourceId === 'guanyijia_github') {
    const priorDebt = trustedPriorNormalizedValue(
      input.priorCompilations, 'guanyijia_mysql', 'schema.jsh_depot_head.debt_fields',
    );
    return {
      introducedConflictIds: priorDebt === 'DEPLOYED_SCHEMA_NO_DEBT'
        && normalized('schema.jsh_depot_head.debt_fields') === 'SOURCE_SCHEMA_HAS_DEBT'
        ? ['gyj-conflict-debt-schema'] : [],
      corroboratedConflictIds: [],
    };
  }
  if (input.sourceId === 'guanyijia_demo_policy') {
    const priorNegative = trustedPriorNormalizedValue(
      input.priorCompilations, 'guanyijia_github', 'rule.negative_stock',
    );
    const priorStatus = trustedPriorNormalizedValue(
      input.priorCompilations, 'guanyijia_github', 'dimension.document_status.nine',
    );
    const currentNegative = normalized('rule.negative_stock');
    const currentStatus = normalized('dimension.document_status.nine');
    return {
      introducedConflictIds: [
        ...(priorNegative && currentNegative && priorNegative !== currentNegative
          ? ['gyj-conflict-negative-stock'] : []),
        ...(priorStatus && currentStatus && priorStatus !== currentStatus
          ? ['gyj-conflict-status-nine'] : []),
      ],
      corroboratedConflictIds: [],
    };
  }
  if (input.sourceId === 'guanyijia_semantica_demo') {
    return {
      introducedConflictIds: [],
      corroboratedConflictIds: [
        ...(normalized('derived.rule.negative_stock') === 'DERIVED_NEGATIVE_STOCK_RULE_CANDIDATE'
          ? ['gyj-conflict-negative-stock'] : []),
        ...(normalized('derived.dimension.document_status.nine') === 'DERIVED_STATUS_NINE_TERM'
          ? ['gyj-conflict-status-nine'] : []),
      ],
    };
  }
  return { introducedConflictIds: [], corroboratedConflictIds: [] };
}

export function reviseSourceCompilation(input: {
  compilation: SourceDocumentCompilation;
  priorCompilations: SourceDocumentCompilation[];
  changes: Array<{ blockId: string; label?: string; value?: StructuredValue }>;
  conflictDefinitions: SemanticConflictDefinition[];
}): { compilation: SourceDocumentCompilation; diff: SourceCompilationDiff } {
  if (!hasValidProjection(input.compilation)) throw new Error('待修订来源编译的结构化投影已损坏');
  if (!input.changes.length) throw new Error('来源文档修改不能为空');
  const changeIds = input.changes.map((change) => change.blockId);
  if (changeIds.some((blockId) => !blockId.trim()) || new Set(changeIds).size !== changeIds.length) {
    throw new Error('来源文档修改的blockId不能为空或重复');
  }
  const blocks = structuredClone(input.compilation.blocks);
  const changedBlockIds: string[] = [];
  for (const change of input.changes) {
    const target = blocks.find((candidate) => candidate.blockId === change.blockId);
    const before = input.compilation.blocks.find((candidate) => candidate.blockId === change.blockId);
    if (!target || !before) throw new Error(`来源文档结构化块不存在：${change.blockId}`);
    if (change.label !== undefined) {
      if (!change.label.trim()) throw new Error('结构化块名称不能为空');
      target.label = change.label;
    }
    if (Object.hasOwn(change, 'value')) target.value = structuredClone(change.value!);
    if (canonicalModelingJson(target) !== canonicalModelingJson(before)) changedBlockIds.push(change.blockId);
  }
  if (!changedBlockIds.length) throw new Error('修改没有产生任何变化');

  const conflicts = conflictProjection({
    sourceId: input.compilation.sourceId,
    blocks,
    priorCompilations: input.priorCompilations,
  });
  const compilation = compileBlocks({
    source: {
      sourceId: input.compilation.sourceId,
      sourceName: input.compilation.sourceName,
      sourceClass: input.compilation.sourceClass,
      snapshotId: input.compilation.snapshotId,
      authority: input.compilation.authority,
      lineageStatus: input.compilation.lineageStatus === 'UPSTREAM_MISSING'
        ? 'DERIVED_VALID' : input.compilation.lineageStatus,
      upstreamSourceIds: [...input.compilation.upstreamSourceIds],
    },
    readSummary: input.compilation.readSummary,
    blocks,
    priorCompilations: input.priorCompilations,
    evidenceLocators: input.compilation.evidenceLocators,
    lineageStatus: input.compilation.lineageStatus,
    introducedConflictIds: conflicts.introducedConflictIds,
    corroboratedConflictIds: conflicts.corroboratedConflictIds,
  });
  const changedBlocks = changedBlockIds.map((blockId) => ({
    blockId,
    before: structuredClone(input.compilation.blocks.find((block) => block.blockId === blockId)!),
    after: structuredClone(compilation.blocks.find((block) => block.blockId === blockId)!),
  }));
  const changedAssertions = changedBlockIds.map((assertionId) => ({
    assertionId,
    before: structuredClone(input.compilation.assertions.find((assertion) => assertion.assertionId === assertionId)!),
    after: structuredClone(compilation.assertions.find((assertion) => assertion.assertionId === assertionId)!),
  }));
  const sectionChanges = standardSectionOrder.flatMap(({ key }) => (
    input.compilation.sections[key] === compilation.sections[key]
      ? []
      : [{ section: key, before: input.compilation.sections[key], after: compilation.sections[key] }]
  ));
  const affectedObjectRefs = [...new Map(changedBlocks
    .flatMap((change) => change.after.affectedObjectRefs)
    .map((reference) => [canonicalModelingJson(reference), reference])).values()];
  const affectedConflicts = input.conflictDefinitions.flatMap((definition) => {
    const relevant = changedBlocks.find((change) => definition.sections.includes(change.after.section)
      && change.after.affectedObjectRefs.some((reference) => definition.affectedObjectRefs.some((candidate) => (
        canonicalModelingJson(candidate) === canonicalModelingJson(reference)
      ))));
    if (!relevant) return [];
    return [{
      conflictId: definition.conflictId,
      title: definition.title,
      ...(normalizedValueOf(relevant.before) ? { beforeVariant: normalizedValueOf(relevant.before)! } : {}),
      ...(normalizedValueOf(relevant.after) ? { afterVariant: normalizedValueOf(relevant.after)! } : {}),
      affectedObjectRefs: structuredClone(definition.affectedObjectRefs),
    }];
  });
  return {
    compilation,
    diff: {
      changedBlockIds,
      blockChanges: changedBlocks,
      assertionChanges: changedAssertions,
      sectionChanges,
      markdown: { before: input.compilation.markdown, after: compilation.markdown },
      affectedConflicts,
      affectedObjectRefs: structuredClone(affectedObjectRefs),
    },
  };
}

function mysqlBlocks() {
  const depotHead = tableEvidence(databaseEvidence, 'jsh_depot_head');
  const depotItem = tableEvidence(databaseEvidence, 'jsh_depot_item');
  const material = tableEvidence(databaseEvidence, 'jsh_material');
  const materialExtend = tableEvidence(databaseEvidence, 'jsh_material_extend');
  const supplier = tableEvidence(databaseEvidence, 'jsh_supplier');
  const currentStock = tableEvidence(databaseEvidence, 'jsh_material_current_stock');
  const coreTables = [
    ['jsh_supplier', '往来单位物理表', supplier, 'jsh_supplier'],
    ['jsh_depot_head', '库存单据主表', depotHead, 'jsh_depot_head'],
    ['jsh_depot_item', '库存单据明细表', depotItem, 'jsh_depot_item'],
    ['jsh_material', '商品物理表', material, 'jsh_material'],
    ['jsh_material_extend', '商品规格物理表', materialExtend, 'jsh_material_extend'],
  ] as const;
  const blocks: SourceDocumentBlock[] = [
    block({
      stableCode: 'source.mysql.manifest', section: 'OVERVIEW', semanticKind: 'ENTITY', label: '部署快照读取范围',
      value: structuredValue('MYSQL_MANIFEST_CURRENT', '当前部署快照覆盖物理结构、程序对象、DML 摘要、统计与脱敏样例。', {
        snapshotId: databaseEvidence.manifest.snapshotId,
      }), evidenceStatus: 'FACT', evidenceRefs: [depotHead], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'boundary.mysql.ddl', section: 'GOAL', semanticKind: 'GAP', label: '数据库证据边界',
      value: structuredValue('DDL_ONLY_PROVES_STRUCTURE', 'DDL 能确认结构；完整业务名称、公式和制度仍需其他来源。'),
      evidenceStatus: 'GAP', evidenceRefs: [depotHead], affectedObjectRefs: [],
    }),
    ...coreTables.map(([table, label, evidence, objectId]) => block({
      stableCode: `physical.table.${table}`, section: 'OBJECT' as const, semanticKind: 'ENTITY' as const, label,
      value: structuredValue('DEPLOYED_TABLE_PRESENT', '该业务载体存在于当前部署结构。', { physicalTable: table }),
      evidenceStatus: 'FACT' as const, evidenceRefs: [evidence], affectedObjectRefs: [resultRef('ENTITY', objectId)],
    })),
    block({
      stableCode: 'physical.document.header_line', section: 'ACTIVITY', semanticKind: 'EVENT', label: '库存单据主从结构',
      value: structuredValue('DEPOT_HEAD_ITEM_PHYSICAL_CARRIER', '主表与明细表提供单据及行项目的物理载体，但 DDL 不命名具体业务动作。', {
        headerTable: 'jsh_depot_head', itemTable: 'jsh_depot_item',
      }), evidenceStatus: 'FACT', evidenceRefs: [depotHead, depotItem], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'field.jsh_depot_head.oper_time', section: 'FIELD', semanticKind: 'FIELD', label: '库存单据操作时间字段',
      value: structuredValue('OPER_TIME_PRESENT', '部署结构存在操作时间字段；业务含义仍待文档解释。', {
        physicalTable: 'jsh_depot_head', physicalField: 'oper_time',
      }), evidenceStatus: 'FACT', evidenceRefs: [columnEvidence('jsh_depot_head', 'oper_time')],
      affectedObjectRefs: [resultRef('FIELD', 'business_time', 'purchase_receipt')],
    }),
    block({
      stableCode: 'field.jsh_account_head.bill_time', section: 'FIELD', semanticKind: 'FIELD', label: '财务单据时间字段',
      value: structuredValue('BILL_TIME_PRESENT', '部署结构存在财务单据时间字段；业务含义仍待文档解释。', {
        physicalTable: 'jsh_account_head', physicalField: 'bill_time',
      }), evidenceStatus: 'FACT', evidenceRefs: [columnEvidence('jsh_account_head', 'bill_time')],
      affectedObjectRefs: [resultRef('FIELD', 'jsh_account_head_bill_time', 'financial_transaction')],
    }),
    block({
      stableCode: 'field.record.create_time', section: 'FIELD', semanticKind: 'FIELD', label: '记录创建时间字段',
      value: structuredValue('CREATE_TIME_PRESENT', '部署结构存在记录创建时间字段，不能据此直接定义业务发生时间。', {
        physicalTable: 'jsh_material_extend', physicalField: 'create_time',
      }), evidenceStatus: 'FACT', evidenceRefs: [columnEvidence('jsh_material_extend', 'create_time')],
      affectedObjectRefs: [resultRef('FIELD', 'jsh_material_extend_create_time', 'jsh_material_extend')],
    }),
    block({
      stableCode: 'schema.jsh_depot_head.debt_fields', section: 'FIELD', semanticKind: 'EXCLUSION', label: '部署欠款字段基准',
      value: structuredValue('DEPLOYED_SCHEMA_NO_DEBT', '当前部署单据主表不存在欠款、期末欠款和期末订金三个源码字段。', {
        physicalTable: 'jsh_depot_head', absentFields: ['debt', 'last_debt', 'last_deposit'],
      }), evidenceStatus: 'FACT', evidenceRefs: [depotHead],
      affectedObjectRefs: [resultRef('METRIC', 'receivable_debt'), resultRef('RULE', 'deposit')],
    }),
    block({
      stableCode: 'relation.depot_item.header', section: 'RELATION', semanticKind: 'RELATION', label: '单据明细归属主单',
      value: structuredValue('HEADER_ID_COLUMN_PRESENT', '明细记录包含主单标识字段；业务基数仍需代码佐证。', {
        sourceTable: 'jsh_depot_item', sourceField: 'header_id', targetTable: 'jsh_depot_head',
      }), evidenceStatus: 'INFERENCE', evidenceRefs: [columnEvidence('jsh_depot_item', 'header_id'), depotHead],
      affectedObjectRefs: [resultRef('RELATION', 'depot_item_header')],
    }),
    block({
      stableCode: 'relation.depot_item.material', section: 'RELATION', semanticKind: 'RELATION', label: '单据明细关联商品',
      value: structuredValue('MATERIAL_ID_COLUMN_PRESENT', '明细记录包含商品标识字段；业务基数仍需代码佐证。', {
        sourceTable: 'jsh_depot_item', sourceField: 'material_id', targetTable: 'jsh_material',
      }), evidenceStatus: 'INFERENCE', evidenceRefs: [columnEvidence('jsh_depot_item', 'material_id'), material],
      affectedObjectRefs: [resultRef('RELATION', 'depot_item_material')],
    }),
    block({
      stableCode: 'relation.material.extend', section: 'RELATION', semanticKind: 'RELATION', label: '商品关联规格',
      value: structuredValue('MATERIAL_EXTEND_ID_PRESENT', '商品规格记录包含商品标识字段；业务基数仍需代码佐证。', {
        sourceTable: 'jsh_material_extend', sourceField: 'material_id', targetTable: 'jsh_material',
      }), evidenceStatus: 'INFERENCE', evidenceRefs: [columnEvidence('jsh_material_extend', 'material_id'), material],
      affectedObjectRefs: [resultRef('RELATION', 'material_extend_material')],
    }),
    block({
      stableCode: 'gap.current_stock_as_of', section: 'METRIC', semanticKind: 'GAP', label: '当前库存生效时点缺口',
      value: structuredValue('CURRENT_STOCK_AS_OF_UNPROVEN', '当前库存表没有可信的业务生效时间，不能生成库存时点规则。', {
        physicalTable: 'jsh_material_current_stock',
      }), evidenceStatus: 'GAP', evidenceRefs: [currentStock],
      affectedObjectRefs: [resultRef('ENTITY', 'inventory_position')],
    }),
    block({
      stableCode: 'gap.mysql.example_questions', section: 'QUESTION', semanticKind: 'GAP', label: '示例问题证据缺口',
      value: structuredValue('NO_SUPPORTED_EXAMPLE_QUESTION', '数据库结构不足以单独给出有业务口径的示例问题。'),
      evidenceStatus: 'GAP', evidenceRefs: [depotHead], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'gap.mysql.business_semantics', section: 'UNRESOLVED', semanticKind: 'GAP', label: '业务名称与公式待补充',
      value: structuredValue('BUSINESS_SEMANTICS_MISSING', '完整业务名称、公式、状态含义与制度约束不能由 DDL 单独确认。'),
      evidenceStatus: 'GAP', evidenceRefs: [depotHead], affectedObjectRefs: [],
    }),
  ];
  const repositoryTables = new Set(repositoryEvidence.manifest.evidenceFiles
    .filter((record) => record.objectType === 'TABLE').map((record) => record.objectName));
  const pendingTables = databaseEvidence.manifest.evidenceFiles
    .filter((record) => record.objectType === 'TABLE' && !repositoryTables.has(record.objectName))
    .sort((left, right) => left.objectName.localeCompare(right.objectName));
  if (pendingTables.length !== 63) throw new Error(`部署扩展表差集应为63，实际为${pendingTables.length}`);
  blocks.push(...pendingTables.map((record, index) => block({
    stableCode: `pending.table.${record.objectName}`,
    blockId: `gyj-block:pending.table:${String(index + 1).padStart(2, '0')}`,
    section: 'UNRESOLVED', semanticKind: 'PENDING_ASSET', label: `部署扩展资产（第${index + 1}项）`,
    value: structuredValue('PENDING_BUSINESS_CLASSIFICATION', '该部署扩展表缺少固定源码业务定义，保持待归类。', {
      physicalTable: record.objectName,
    }), evidenceStatus: 'GAP', evidenceRefs: [record.evidenceId], affectedObjectRefs: [],
  })));
  return blocks;
}

export function compileMysqlSource(input: {
  source: StorySource;
  priorCompilations: SourceDocumentCompilation[];
}) {
  const counts = databaseEvidence.manifest.objectCounts;
  return compileBlocks({
    source: input.source,
    priorCompilations: input.priorCompilations,
    readSummary: {
      summary: databaseEvidence.source.summary,
      objectCount: counts.tables ?? 0,
      evidenceCount: databaseEvidence.manifest.evidenceFiles.length,
      objectCounts: structuredClone(counts),
      versionRef: String(databaseEvidence.manifest.snapshotId),
      warnings: databaseEvidence.manifest.warnings.map((warning) => (
        `${warning.objectName} ${warning.stage} ${warning.status}：${warning.reason}`
      )),
    },
    blocks: mysqlBlocks(),
  });
}

function githubBlocks() {
  const sourceTable = tableEvidence(repositoryEvidence, 'jsh_depot_head');
  const activities = [
    ['purchase_receipt', '采购入库', 'PURCHASE_RECEIPT', 'jshERP-web/src/views/bill/PurchaseInList.vue'],
    ['purchase_return', '采购退货', 'PURCHASE_RETURN', 'jshERP-web/src/views/bill/PurchaseBackList.vue'],
    ['sales_delivery', '销售出库', 'SALES_DELIVERY', 'jshERP-web/src/views/bill/SaleOutList.vue'],
    ['sales_return', '销售退货', 'SALES_RETURN', 'jshERP-web/src/views/bill/SaleBackList.vue'],
    ['stock_transfer', '库存调拨', 'STOCK_TRANSFER', 'jshERP-web/src/views/bill/AllocationOutList.vue'],
    ['stocktake_review', '库存盘点', 'STOCKTAKE_REVIEW', 'jshERP-web/src/views/report/MaterialStock.vue'],
    ['assembly', '商品组装', 'ASSEMBLY', 'jshERP-web/src/views/bill/AssembleList.vue'],
    ['disassembly', '商品拆卸', 'DISASSEMBLY', 'jshERP-web/src/views/bill/DisassembleList.vue'],
  ] as const;
  const vocabularyEvidence = (path: string) => {
    const record = repositoryEvidence.manifest.evidenceFiles.find((candidate) => (
      candidate.objectType === 'UI_VOCABULARY'
      && candidate.evidenceId.includes(path)
    ));
    if (!record) throw new Error(`冻结 GitHub Evidence 缺少 UI 术语：${path}`);
    return record.evidenceId;
  };
  const exact = (id: string) => {
    if (!repositoryEvidence.manifest.evidenceFiles.some((record) => record.evidenceId === id)) {
      throw new Error(`冻结 GitHub Evidence 缺少：${id}`);
    }
    return id;
  };
  const accountActivity = exact('github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java:METHOD:select:91');
  const joinEvidence = exact('github_jshERP@v1:DATA_ACCESS:jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml:STATEMENT:findDetailByDepotIdsAndMaterialIdList');
  const numberMetric = exact('github_jshERP@v1:DATA_ACCESS:jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml:STATEMENT:buyOrSaleNumber');
  const amountMetric = exact('github_jshERP@v1:DATA_ACCESS:jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml:STATEMENT:buyOrSalePriceTotal');
  const statusWorkflow = exact('github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:METHOD:batchSetStatus:742');
  const negativeStock = exact('github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java:METHOD:getMinusStockFlag:511');
  const batchNumber = exact('github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemService.java:METHOD:getDepotItemByBatchNumber:829');
  const serialNumber = exact('github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/SerialNumberService.java:METHOD:checkAndUpdateSerialNumber:131');
  const transfer = exact('github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemService.java:METHOD:saveDetials:381');
  const deposit = exact('github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:METHOD:getFinishDepositMapByNumberList:324');
  const statusConstant = exact('github_jshERP@v1:DOMAIN_ENUM:jshERP-boot/src/main/java/com/jsh/erp/constants/BusinessConstants.java');
  return [
    block({
      stableCode: 'source.github.manifest', section: 'OVERVIEW', semanticKind: 'ENTITY', label: '源码快照读取范围',
      value: structuredValue('GITHUB_MANIFEST_CURRENT', '固定提交覆盖核心表、数据访问、服务、控制器、迁移、租户规则与界面术语。', {
        commit: String(repositoryEvidence.manifest.commit),
      }), evidenceStatus: 'FACT', evidenceRefs: [sourceTable], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'boundary.github.candidates', section: 'GOAL', semanticKind: 'GAP', label: '源码解释边界',
      value: structuredValue('CODE_SUPPORTS_CANDIDATES', '源码用于解释关系、工作流和规则候选；未经审阅的候选不能作为正式可执行规则。'),
      evidenceStatus: 'GAP', evidenceRefs: [statusWorkflow], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'object.github.core_schema', section: 'OBJECT', semanticKind: 'ENTITY', label: '源码核心业务结构',
      value: structuredValue('SOURCE_CORE_TABLES_PRESENT', '固定源码 schema 包含 32 张核心业务表，并与部署中的同名表对照。', {
        tableCount: 32,
      }), evidenceStatus: 'FACT', evidenceRefs: [sourceTable], affectedObjectRefs: [resultRef('ENTITY', 'jsh_supplier')],
    }),
    ...activities.map(([code, label, normalized, path]) => block({
      stableCode: `activity.${code}`, section: 'ACTIVITY' as const, semanticKind: 'EVENT' as const, label,
      value: structuredValue(normalized, `源码界面与工作流提供“${label}”活动佐证。`),
      evidenceStatus: 'FACT' as const, evidenceRefs: [vocabularyEvidence(path)],
      affectedObjectRefs: [resultRef('EVENT', code)],
    })),
    block({
      stableCode: 'activity.financial_transaction', section: 'ACTIVITY', semanticKind: 'EVENT', label: '财务收支',
      value: structuredValue('FINANCIAL_TRANSACTION', '财务服务读取逻辑提供收支活动佐证。'),
      evidenceStatus: 'FACT', evidenceRefs: [accountActivity], affectedObjectRefs: [resultRef('EVENT', 'financial_transaction')],
    }),
    block({
      stableCode: 'schema.jsh_depot_head.debt_fields', section: 'FIELD', semanticKind: 'FIELD', label: '源码欠款字段定义',
      value: structuredValue('SOURCE_SCHEMA_HAS_DEBT', '源码 schema 定义欠款、期末欠款和期末订金字段。', {
        physicalTable: 'jsh_depot_head', fields: ['debt', 'last_debt', 'last_deposit'],
      }), evidenceStatus: 'CONFLICT', evidenceRefs: [sourceTable],
      affectedObjectRefs: [resultRef('METRIC', 'receivable_debt'), resultRef('RULE', 'deposit')],
    }),
    block({
      stableCode: 'dimension.document_status.nine', section: 'FIELD', semanticKind: 'DIMENSION', label: '状态 9 源码工作流',
      value: structuredValue('REVIEWING_UNCONFIRMED_ENUM', '工作流把状态 9 用于审核中处理，但常量枚举依据仍不完整。'),
      evidenceStatus: 'INFERENCE', evidenceRefs: [statusWorkflow, statusConstant],
      affectedObjectRefs: [resultRef('DIMENSION', 'document_status'), resultRef('RULE', 'audit_status')],
    }),
    block({
      stableCode: 'relation.depot_item.header', section: 'RELATION', semanticKind: 'RELATION', label: '单据明细与主单连接',
      value: structuredValue('HEADER_DETAIL_JOIN_IN_MAPPER', 'Mapper 语句按主单与明细字段组合读取单据详情。'),
      evidenceStatus: 'FACT', evidenceRefs: [joinEvidence], affectedObjectRefs: [resultRef('RELATION', 'depot_item_header')],
    }),
    block({
      stableCode: 'metric.purchase_sales.quantity', section: 'METRIC', semanticKind: 'METRIC', label: '采购销售数量候选',
      value: structuredValue('MAPPER_BUY_OR_SALE_NUMBER', 'Mapper 语句提供采购与销售数量计算候选，仍需业务审阅。'),
      evidenceStatus: 'INFERENCE', evidenceRefs: [numberMetric],
      affectedObjectRefs: [resultRef('METRIC', 'purchase_quantity'), resultRef('METRIC', 'sales_quantity')],
    }),
    block({
      stableCode: 'metric.purchase_sales.amount', section: 'METRIC', semanticKind: 'METRIC', label: '采购销售金额候选',
      value: structuredValue('MAPPER_BUY_OR_SALE_AMOUNT', 'Mapper 语句提供采购与销售金额计算候选，仍需业务审阅。'),
      evidenceStatus: 'INFERENCE', evidenceRefs: [amountMetric],
      affectedObjectRefs: [resultRef('METRIC', 'purchase_amount'), resultRef('METRIC', 'sales_amount')],
    }),
    block({
      stableCode: 'rule.negative_stock', section: 'METRIC', semanticKind: 'RULE', label: '负库存控制候选',
      value: structuredValue('TENANT_CONFIG_CONTROLS', '实现按租户配置决定是否允许负库存。'),
      evidenceStatus: 'FACT', evidenceRefs: [negativeStock], affectedObjectRefs: [resultRef('RULE', 'negative_stock')],
    }),
    block({
      stableCode: 'question.github.purchase_sales', section: 'QUESTION', semanticKind: 'METRIC', label: '采购销售分析问题',
      value: structuredValue('SUPPORTED_PURCHASE_SALES_QUESTION', '可继续审阅各商品的采购、销售数量与金额如何计算。'),
      evidenceStatus: 'INFERENCE', evidenceRefs: [numberMetric, amountMetric], affectedObjectRefs: [],
    }),
    ...[
      ['rule.batch_number', '批次管理候选', 'BATCH_WORKFLOW_PRESENT', batchNumber, 'batch_number'],
      ['rule.serial_number', '序列号管理候选', 'SERIAL_WORKFLOW_PRESENT', serialNumber, 'serial_number'],
      ['rule.transfer_warehouse', '调拨仓库校验候选', 'TRANSFER_VALIDATION_PRESENT', transfer, 'transfer_warehouse'],
      ['rule.deposit', '订金处理候选', 'DEPOSIT_WORKFLOW_PRESENT', deposit, 'deposit'],
    ].map(([stableCode, label, normalized, evidence, objectId]) => block({
      stableCode, section: 'UNRESOLVED' as const, semanticKind: 'RULE' as const, label,
      value: structuredValue(normalized, '源码提供实现依据，但结构化条件与正式发布状态仍待补充。'),
      evidenceStatus: 'INFERENCE' as const, evidenceRefs: [evidence],
      affectedObjectRefs: [resultRef('RULE', objectId)],
    })),
    block({
      stableCode: 'gap.github.rule_publication', section: 'UNRESOLVED', semanticKind: 'GAP', label: '规则发布边界',
      value: structuredValue('RULE_CANDIDATES_NOT_EXECUTABLE', '上述规则均为可审阅候选，不能作为正式可执行规则。'),
      evidenceStatus: 'GAP', evidenceRefs: [statusWorkflow], affectedObjectRefs: [],
    }),
  ];
}

export function compileGithubSource(input: {
  source: StorySource;
  priorCompilations: SourceDocumentCompilation[];
}) {
  const counts = repositoryEvidence.manifest.objectCounts;
  return compileBlocks({
    source: input.source,
    priorCompilations: input.priorCompilations,
    readSummary: {
      summary: repositoryEvidence.source.summary,
      objectCount: counts.selectedFiles ?? 0,
      evidenceCount: repositoryEvidence.manifest.evidenceFiles.length,
      objectCounts: structuredClone(counts),
      versionRef: String(repositoryEvidence.manifest.commit),
      warnings: repositoryEvidence.manifest.warnings.map((warning) => (
        `${warning.objectName} ${warning.stage} ${warning.status}：${warning.reason}`
      )),
    },
    blocks: githubBlocks(),
    introducedConflictIds: hasTrustedVariant({
      priorCompilations: input.priorCompilations,
      sourceId: 'guanyijia_mysql',
      snapshotId: databaseEvidence.manifest.snapshotId,
      stableCode: 'schema.jsh_depot_head.debt_fields',
      normalizedValue: 'DEPLOYED_SCHEMA_NO_DEBT',
      evidenceRefs: [tableEvidence(databaseEvidence, 'jsh_depot_head')],
    }) ? ['gyj-conflict-debt-schema'] : [],
  });
}

function officialFacts() {
  const { source, evidence } = guanyijiaOfficialDocumentsFixture;
  if (evidence.length !== 14 || source.evidenceIds.length !== 14) {
    throw new Error(`官方核心文档冻结 Evidence 应为14，实际为${evidence.length}`);
  }
  const byId = new Map(evidence.map((record) => [record.evidenceId, record]));
  for (const evidenceId of source.evidenceIds) {
    if (!byId.has(evidenceId)) throw new Error(`官方核心文档缺少冻结 Evidence：${evidenceId}`);
  }
  return { source, evidence };
}

function officialBlocks() {
  const { source } = officialFacts();
  const ref = (suffix: string) => {
    const evidenceId = `official:${suffix}`;
    if (!source.evidenceIds.includes(evidenceId)) throw new Error(`官方核心文档缺少 Evidence：${evidenceId}`);
    return evidenceId;
  };
  const counterparty = ref('function:counterparty-roles');
  const category = ref('function:product-category-hierarchy');
  const purchaseFlow = ref('function:purchase-order-flow');
  const purchaseReturn = ref('function:purchase-return');
  const salesFlow = ref('function:sales-order-flow');
  const salesReturn = ref('function:sales-return');
  const transfer = ref('function:transfer-inventory-effect');
  const stocktake = ref('function:stocktake-review');
  const assembly = ref('function:assembly-inventory-effect');
  const disassembly = ref('function:disassembly-inventory-effect');
  const purchaseMetrics = ref('function:purchase-metrics');
  const salesMetrics = ref('function:sales-metrics');
  const depotTime = ref('database:depot-head-event-time');
  const accountTime = ref('database:account-head-event-time');
  return [
    block({
      stableCode: 'source.official_docs.manifest', section: 'OVERVIEW', semanticKind: 'ENTITY', label: '官方核心文档读取范围',
      value: structuredValue('OFFICIAL_CORE_DOCS_FROZEN', '冻结的数据库设计与功能清单为业务定义提供辅助依据。'),
      evidenceStatus: 'FACT', evidenceRefs: [counterparty], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'goal.official.business_flows', section: 'GOAL', semanticKind: 'EVENT', label: '采购销售流程定义',
      value: structuredValue('PURCHASE_AND_SALES_FLOWS_DEFINED', '官方文档补充采购、退货、销售与退货流程名称，但不覆盖部署数据库事实。'),
      evidenceStatus: 'FACT', evidenceRefs: [purchaseFlow, purchaseReturn, salesFlow, salesReturn], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'dimension.partner_role', section: 'OBJECT', semanticKind: 'DIMENSION', label: '往来单位角色',
      value: structuredValue('COUNTERPARTY_ROLES_DEFINED', '往来单位包含客户、供应商与会员等业务角色。'),
      evidenceStatus: 'FACT', evidenceRefs: [counterparty], affectedObjectRefs: [resultRef('DIMENSION', 'partner_role')],
    }),
    ...[
      ['purchase_receipt', '采购入库', 'PURCHASE_RECEIPT', purchaseFlow],
      ['purchase_return', '采购退货', 'PURCHASE_RETURN', purchaseReturn],
      ['sales_delivery', '销售出库', 'SALES_DELIVERY', salesFlow],
      ['sales_return', '销售退货', 'SALES_RETURN', salesReturn],
      ['stock_transfer', '库存调拨', 'STOCK_TRANSFER', transfer],
      ['stocktake_review', '库存盘点', 'STOCKTAKE_REVIEW', stocktake],
      ['assembly', '商品组装', 'ASSEMBLY', assembly],
      ['disassembly', '商品拆卸', 'DISASSEMBLY', disassembly],
    ].map(([code, label, normalized, evidence]) => block({
      stableCode: `activity.${code}`, section: 'ACTIVITY' as const, semanticKind: 'EVENT' as const, label,
      value: structuredValue(normalized, `官方功能清单定义“${label}”业务活动。`),
      evidenceStatus: 'FACT' as const, evidenceRefs: [evidence], affectedObjectRefs: [resultRef('EVENT', code)],
    })),
    block({
      stableCode: 'time.inventory_document.business_time', section: 'FIELD', semanticKind: 'TIME_RULE', label: '库存活动业务时间',
      value: structuredValue('OPER_TIME_IS_BUSINESS_TIME', '库存类活动以操作时间作为业务时间；创建时间仅表示记录创建。', {
        physicalField: 'oper_time', recordField: 'create_time',
      }), evidenceStatus: 'FACT', evidenceRefs: [depotTime],
      affectedObjectRefs: [resultRef('TIME_RULE', 'purchase_receipt_business_time')],
    }),
    block({
      stableCode: 'time.financial_document.business_time', section: 'FIELD', semanticKind: 'TIME_RULE', label: '财务活动业务时间',
      value: structuredValue('BILL_TIME_IS_BUSINESS_TIME', '财务收支以单据时间作为业务时间；创建时间仅表示记录创建。', {
        physicalField: 'bill_time', recordField: 'create_time',
      }), evidenceStatus: 'FACT', evidenceRefs: [accountTime],
      affectedObjectRefs: [resultRef('TIME_RULE', 'financial_bill_time')],
    }),
    block({
      stableCode: 'hierarchy.material_category_tree', section: 'RELATION', semanticKind: 'HIERARCHY', label: '商品分类层级',
      value: structuredValue('PRODUCT_CATEGORY_HIERARCHY_DEFINED', '官方商品管理文档定义商品分类及其组织层级。'),
      evidenceStatus: 'FACT', evidenceRefs: [category], affectedObjectRefs: [resultRef('HIERARCHY', 'material_category_tree')],
    }),
    block({
      stableCode: 'metric.purchase_sales.names', section: 'METRIC', semanticKind: 'METRIC', label: '采购销售指标名称',
      value: structuredValue('PURCHASE_AND_SALES_METRIC_NAMES_DEFINED', '官方报表清单确认采购数量、采购金额、销售数量和销售金额的业务名称，不单独证明公式。'),
      evidenceStatus: 'FACT', evidenceRefs: [purchaseMetrics, salesMetrics],
      affectedObjectRefs: [resultRef('METRIC', 'purchase_quantity'), resultRef('METRIC', 'purchase_amount'),
        resultRef('METRIC', 'sales_quantity'), resultRef('METRIC', 'sales_amount')],
    }),
    block({
      stableCode: 'question.official.flow_metrics', section: 'QUESTION', semanticKind: 'METRIC', label: '流程与指标审阅问题',
      value: structuredValue('SUPPORTED_FLOW_METRIC_QUESTION', '可审阅采购、销售与退货活动分别如何影响数量和金额。'),
      evidenceStatus: 'INFERENCE', evidenceRefs: [purchaseFlow, purchaseReturn, salesFlow, salesReturn, purchaseMetrics, salesMetrics],
      affectedObjectRefs: [],
    }),
    block({
      stableCode: 'gap.official.authority_boundary', section: 'UNRESOLVED', semanticKind: 'GAP', label: '辅助证据权威边界',
      value: structuredValue('AUXILIARY_DOES_NOT_OVERRIDE_DEPLOYED_SCHEMA', '官方文档只补充业务定义；部署字段存在性与实现规则仍以对应真实来源核验。'),
      evidenceStatus: 'GAP', evidenceRefs: [depotTime, accountTime], affectedObjectRefs: [],
    }),
  ];
}

export function compileOfficialDocsSource(input: {
  source: StorySource;
  priorCompilations: SourceDocumentCompilation[];
}) {
  const { source, evidence } = officialFacts();
  return compileBlocks({
    source: input.source,
    priorCompilations: input.priorCompilations,
    readSummary: {
      summary: source.summary,
      objectCount: source.objectCounts.pages ?? 0,
      evidenceCount: evidence.length,
      objectCounts: structuredClone(source.objectCounts),
      versionRef: source.versionRef,
      warnings: [],
    },
    blocks: officialBlocks(),
    evidenceLocators: Object.fromEntries(evidence.map((record) => [record.evidenceId, structuredClone(record.locator)])),
  });
}

function policyEvidenceId(path: DemoPolicyDocumentInput['path'], anchor: string) {
  return `demo-policy://${path}#${anchor}`;
}

function sectionContent(document: DemoPolicyDocumentInput, heading: string) {
  const marker = `## ${heading}`;
  const start = document.content.indexOf(marker);
  if (start < 0) throw new Error(`演示制度 ${document.path} 缺少章节：${heading}`);
  const bodyStart = start + marker.length;
  const next = document.content.indexOf('\n## ', bodyStart);
  const content = document.content.slice(bodyStart, next < 0 ? document.content.length : next).trim();
  if (!content) throw new Error(`演示制度 ${document.path} 章节为空：${heading}`);
  return content;
}

function policyFacts(policyDocuments: DemoPolicyDocumentInput[]) {
  const byPath = new Map(policyDocuments.map((document) => [document.path, document]));
  const counterpartyDocument = byPath.get('业务术语/往来单位.md');
  const statusDocument = byPath.get('单据管理/审核状态.md');
  const inventoryDocument = byPath.get('库存管理/负库存与库存时点.md');
  if (!counterpartyDocument || !statusDocument || !inventoryDocument) throw new Error('演示制度三份文件不完整');
  const counterpartyClause = sectionContent(counterpartyDocument, '角色口径');
  const statusClause = sectionContent(statusDocument, '状态 9');
  const negativeStockClause = sectionContent(inventoryDocument, '负库存控制');
  const stockTimeClause = sectionContent(inventoryDocument, '库存生效时点');
  const statusValue = /状态\s*9\s*表示待审核/.test(statusClause)
    ? 'PENDING_REVIEW'
    : `POLICY_STATUS_NINE_${sha256HexSync(statusClause).slice(0, 12).toUpperCase()}`;
  const negativeStockValue = /一律禁止负库存/.test(negativeStockClause)
    ? 'ALWAYS_FORBIDDEN'
    : /允许负库存/.test(negativeStockClause)
      ? 'POLICY_ALLOWS_NEGATIVE_STOCK'
      : `POLICY_NEGATIVE_STOCK_${sha256HexSync(negativeStockClause).slice(0, 12).toUpperCase()}`;
  const evidence = {
    counterparty: policyEvidenceId(counterpartyDocument.path, '角色口径'),
    status: policyEvidenceId(statusDocument.path, '状态-9'),
    negativeStock: policyEvidenceId(inventoryDocument.path, '负库存控制'),
    stockTime: policyEvidenceId(inventoryDocument.path, '库存生效时点'),
  };
  const locators = Object.fromEntries([
    [evidence.counterparty, counterpartyDocument, '角色口径'],
    [evidence.status, statusDocument, '状态 9'],
    [evidence.negativeStock, inventoryDocument, '负库存控制'],
    [evidence.stockTime, inventoryDocument, '库存生效时点'],
  ].map(([evidenceId, document, section]) => [evidenceId as string, {
    kind: 'DEMO_POLICY', uri: evidenceId as string,
    path: (document as DemoPolicyDocumentInput).path,
    documentVersion: (document as DemoPolicyDocumentInput).version,
    contentSha256: sha256HexSync((document as DemoPolicyDocumentInput).content),
    section: section as string,
  }]));
  return {
    counterpartyClause, statusClause, negativeStockClause, stockTimeClause,
    statusValue, negativeStockValue, evidence, locators,
  };
}

function policyBlocks(policyDocuments: DemoPolicyDocumentInput[]) {
  const facts = policyFacts(policyDocuments);
  return [
    block({
      stableCode: 'source.demo_policy.notice', section: 'OVERVIEW', semanticKind: 'GAP', label: '制度性质声明',
      value: structuredValue('DEMO_POLICY_NOT_PRODUCTION', '演示制度，不是真实生产制度；仅用于演示冲突审阅。'),
      evidenceStatus: 'GAP', evidenceRefs: [facts.evidence.counterparty], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'goal.demo_policy.review', section: 'GOAL', semanticKind: 'RULE', label: '制度审阅目标',
      value: structuredValue('DEMO_POLICY_REVIEW_ONLY', '用短小制度条款演示业务定义、实现事实与待落地目标的差异。'),
      evidenceStatus: 'INFERENCE', evidenceRefs: [facts.evidence.negativeStock, facts.evidence.status], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'dimension.partner_role', section: 'OBJECT', semanticKind: 'DIMENSION', label: '往来单位多角色口径',
      value: structuredValue('COUNTERPARTY_MULTI_ROLE_ALLOWED', facts.counterpartyClause),
      evidenceStatus: 'FACT', evidenceRefs: [facts.evidence.counterparty], affectedObjectRefs: [resultRef('DIMENSION', 'partner_role')],
    }),
    block({
      stableCode: 'activity.review_gate', section: 'ACTIVITY', semanticKind: 'EVENT', label: '单据审核门槛',
      value: structuredValue('APPROVAL_REQUIRED_FOR_OFFICIAL_STATISTICS', '演示条款要求单据审核通过后才进入正式统计。'),
      evidenceStatus: 'FACT', evidenceRefs: [facts.evidence.status], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'dimension.document_status.nine', section: 'FIELD', semanticKind: 'DIMENSION', label: '状态 9 制度含义',
      value: structuredValue(facts.statusValue, facts.statusClause), evidenceStatus: 'CONFLICT',
      evidenceRefs: [facts.evidence.status],
      affectedObjectRefs: [resultRef('DIMENSION', 'document_status'), resultRef('RULE', 'audit_status')],
    }),
    block({
      stableCode: 'relation.counterparty.roles', section: 'RELATION', semanticKind: 'RELATION', label: '往来单位与角色关系',
      value: structuredValue('COUNTERPARTY_CAN_HAVE_MULTIPLE_ROLES', '同一往来单位可以同时承担客户、供应商和会员等角色。'),
      evidenceStatus: 'FACT', evidenceRefs: [facts.evidence.counterparty], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'rule.negative_stock', section: 'METRIC', semanticKind: 'RULE', label: '负库存制度条款',
      value: structuredValue(facts.negativeStockValue, facts.negativeStockClause), evidenceStatus: 'CONFLICT',
      evidenceRefs: [facts.evidence.negativeStock], affectedObjectRefs: [resultRef('RULE', 'negative_stock')],
    }),
    block({
      stableCode: 'rule.current_stock_effective_time_proposal', section: 'METRIC', semanticKind: 'RULE', label: '库存生效时点建议',
      value: structuredValue('RECOMMENDATION_ONLY_DOES_NOT_RESOLVE_GAP', facts.stockTimeClause), evidenceStatus: 'GAP',
      evidenceRefs: [facts.evidence.stockTime], affectedObjectRefs: [resultRef('ENTITY', 'inventory_position')],
    }),
    block({
      stableCode: 'question.demo_policy.review', section: 'QUESTION', semanticKind: 'RULE', label: '制度差异审阅问题',
      value: structuredValue('SUPPORTED_POLICY_REVIEW_QUESTION', '可审阅负库存是否应一律禁止，以及状态 9 应采用哪一种业务含义。'),
      evidenceStatus: 'INFERENCE', evidenceRefs: [facts.evidence.negativeStock, facts.evidence.status], affectedObjectRefs: [],
    }),
    block({
      stableCode: 'gap.demo_policy.stock_time', section: 'UNRESOLVED', semanticKind: 'GAP', label: '库存时点仍待实现证据',
      value: structuredValue('STOCK_TIME_IMPLEMENTATION_EVIDENCE_MISSING', '库存生效时点只是演示建议，不能解除真实来源留下的证据缺口。'),
      evidenceStatus: 'GAP', evidenceRefs: [facts.evidence.stockTime], affectedObjectRefs: [resultRef('ENTITY', 'inventory_position')],
    }),
  ];
}

export function compileDemoPolicySource(input: {
  source: StorySource;
  priorCompilations: SourceDocumentCompilation[];
  policyDocuments: DemoPolicyDocumentInput[];
}) {
  const facts = policyFacts(input.policyDocuments);
  const versions = [...new Set(input.policyDocuments.map((document) => document.version))];
  return compileBlocks({
    source: input.source,
    priorCompilations: input.priorCompilations,
    readSummary: {
      summary: '三份内置演示制度 Markdown；醒目标记为非真实生产制度。',
      objectCount: input.policyDocuments.length,
      evidenceCount: Object.keys(facts.locators).length,
      objectCounts: { documents: input.policyDocuments.length, clauses: Object.keys(facts.locators).length },
      versionRef: versions.join('+'),
      warnings: ['演示制度，不是真实生产制度。'],
    },
    blocks: policyBlocks(input.policyDocuments),
    evidenceLocators: facts.locators,
    introducedConflictIds: [
      ...(hasTrustedVariant({
        priorCompilations: input.priorCompilations,
        sourceId: 'guanyijia_github',
        snapshotId: repositoryEvidence.manifest.snapshotId,
        stableCode: 'rule.negative_stock',
        normalizedValue: 'TENANT_CONFIG_CONTROLS',
        evidenceRefs: ['github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java:METHOD:getMinusStockFlag:511'],
      }) ? ['gyj-conflict-negative-stock'] : []),
      ...(hasTrustedVariant({
        priorCompilations: input.priorCompilations,
        sourceId: 'guanyijia_github',
        snapshotId: repositoryEvidence.manifest.snapshotId,
        stableCode: 'dimension.document_status.nine',
        normalizedValue: 'REVIEWING_UNCONFIRMED_ENUM',
        evidenceRefs: [
          'github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:METHOD:batchSetStatus:742',
          'github_jshERP@v1:DOMAIN_ENUM:jshERP-boot/src/main/java/com/jsh/erp/constants/BusinessConstants.java',
        ],
      }) ? ['gyj-conflict-status-nine'] : []),
    ],
  });
}

export function createConflictDefinitions(policyDocuments: DemoPolicyDocumentInput[]): SemanticConflictDefinition[] {
  const facts = policyFacts(policyDocuments);
  const mysqlDebt = tableEvidence(databaseEvidence, 'jsh_depot_head');
  const githubDebt = tableEvidence(repositoryEvidence, 'jsh_depot_head');
  const negativeStock = 'github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java:METHOD:getMinusStockFlag:511';
  const statusWorkflow = 'github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java:METHOD:batchSetStatus:742';
  const statusConstant = 'github_jshERP@v1:DOMAIN_ENUM:jshERP-boot/src/main/java/com/jsh/erp/constants/BusinessConstants.java';
  const debtCurrent = {
    sourceId: 'guanyijia_mysql', stableCode: 'schema.jsh_depot_head.debt_fields',
    normalizedValue: 'DEPLOYED_SCHEMA_NO_DEBT', evidenceRefs: [mysqlDebt],
  };
  const debtIncoming = {
    sourceId: 'guanyijia_github', stableCode: 'schema.jsh_depot_head.debt_fields',
    normalizedValue: 'SOURCE_SCHEMA_HAS_DEBT', evidenceRefs: [githubDebt],
  };
  const negativeCurrent = {
    sourceId: 'guanyijia_github', stableCode: 'rule.negative_stock',
    normalizedValue: 'TENANT_CONFIG_CONTROLS', evidenceRefs: [negativeStock],
  };
  const negativeIncoming = {
    sourceId: 'guanyijia_demo_policy', stableCode: 'rule.negative_stock',
    normalizedValue: facts.negativeStockValue, evidenceRefs: [facts.evidence.negativeStock],
  };
  const statusCurrent = {
    sourceId: 'guanyijia_github', stableCode: 'dimension.document_status.nine',
    normalizedValue: 'REVIEWING_UNCONFIRMED_ENUM', evidenceRefs: [statusWorkflow, statusConstant],
  };
  const statusIncoming = {
    sourceId: 'guanyijia_demo_policy', stableCode: 'dimension.document_status.nine',
    normalizedValue: facts.statusValue, evidenceRefs: [facts.evidence.status],
  };
  return [
    {
      conflictId: 'gyj-conflict-debt-schema', title: '欠款字段结构冲突',
      introducedBySourceId: 'guanyijia_github', current: debtCurrent, incoming: debtIncoming,
      corroborating: [], objectKinds: ['FIELD'], sections: ['FIELD'], variants: [debtCurrent, debtIncoming],
      affectedObjectRefs: [resultRef('METRIC', 'receivable_debt'), resultRef('RULE', 'deposit')],
      allowedStrategies: ['KEEP_CURRENT', 'ACCEPT_INCOMING', 'MERGE', 'DEFER_AS_GAP'],
      defaultStrategy: 'KEEP_CURRENT',
      defaultResolutionDraft: '采用部署结构并继续排除应收欠款指标；订金只保留候选。',
    },
    {
      conflictId: 'gyj-conflict-negative-stock', title: '负库存制度与实现冲突',
      introducedBySourceId: 'guanyijia_demo_policy', current: negativeCurrent, incoming: negativeIncoming,
      corroborating: [{
        sourceId: 'guanyijia_semantica_demo', stableCode: 'derived.rule.negative_stock',
        normalizedValue: 'DERIVED_NEGATIVE_STOCK_RULE_CANDIDATE',
        evidenceRefs: ['semantica://guanyijia-demo/statement/derived.rule.negative_stock', facts.evidence.negativeStock],
        upstreamSourceId: 'guanyijia_demo_policy',
      }], objectKinds: ['RULE'], sections: ['METRIC'], variants: [negativeCurrent, negativeIncoming],
      affectedObjectRefs: [resultRef('RULE', 'negative_stock')],
      allowedStrategies: ['KEEP_CURRENT', 'ACCEPT_INCOMING', 'MERGE', 'DEFER_AS_GAP'],
      defaultStrategy: 'MERGE',
      defaultResolutionDraft: '保留按租户配置的实现事实；演示制度记为未落地目标。',
    },
    {
      conflictId: 'gyj-conflict-status-nine', title: '状态 9 业务含义冲突', objectKinds: ['DIMENSION', 'RULE'],
      introducedBySourceId: 'guanyijia_demo_policy', current: statusCurrent, incoming: statusIncoming,
      corroborating: [{
        sourceId: 'guanyijia_semantica_demo', stableCode: 'derived.dimension.document_status.nine',
        normalizedValue: 'DERIVED_STATUS_NINE_TERM',
        evidenceRefs: ['semantica://guanyijia-demo/statement/derived.dimension.document_status.nine', facts.evidence.status],
        upstreamSourceId: 'guanyijia_demo_policy',
      }], sections: ['FIELD', 'METRIC'], variants: [statusCurrent, statusIncoming],
      affectedObjectRefs: [resultRef('DIMENSION', 'document_status'), resultRef('RULE', 'audit_status')],
      allowedStrategies: ['KEEP_CURRENT', 'ACCEPT_INCOMING', 'MERGE', 'DEFER_AS_GAP'],
      defaultStrategy: 'DEFER_AS_GAP',
      defaultResolutionDraft: '保留字段和规则候选，但不发布状态 9 的正式枚举成员。',
    },
  ];
}

function semanticaGraph(policyDocuments: DemoPolicyDocumentInput[], upstreamSnapshotId: string) {
  const facts = policyFacts(policyDocuments);
  const locators: SourceDocumentCompilation['evidenceLocators'] = structuredClone(facts.locators);
  const derived = (input: Omit<SourceDocumentBlock, 'blockId' | 'evidenceRefs'> & {
    policyRefs: string[];
  }) => {
    const { policyRefs, ...blockInput } = input;
    const statementRef = `semantica://guanyijia-demo/statement/${input.stableCode}`;
    locators[statementRef] = {
      kind: 'SEMANTICA_DERIVED_STATEMENT',
      statementId: input.stableCode,
      upstreamSourceId: 'guanyijia_demo_policy',
      upstreamSnapshotId,
      upstreamEvidenceRefs: policyRefs,
    };
    return block({ ...blockInput, evidenceRefs: [statementRef, ...policyRefs] });
  };
  const allPolicyRefs = Object.values(facts.evidence);
  const blocks = [
    derived({
      stableCode: 'derived.semantica.graph', section: 'OVERVIEW', semanticKind: 'ENTITY', label: '演示制度术语图',
      value: structuredValue('DERIVED_POLICY_GRAPH', '该术语图完全由三份演示制度确定性派生，不构成新的独立根来源。'),
      evidenceStatus: 'INFERENCE', policyRefs: allPolicyRefs, affectedObjectRefs: [],
    }),
    derived({
      stableCode: 'derived.semantica.goal', section: 'GOAL', semanticKind: 'ENTITY', label: '派生目标',
      value: structuredValue('DERIVED_TERMS_AND_RULE_CANDIDATES', '把制度条款投影为可审阅的术语、活动概念和规则候选，并保留上游章节。'),
      evidenceStatus: 'INFERENCE', policyRefs: allPolicyRefs, affectedObjectRefs: [],
    }),
    derived({
      stableCode: 'derived.dimension.partner_role', section: 'OBJECT', semanticKind: 'DIMENSION', label: '往来单位角色术语',
      value: structuredValue('DERIVED_COUNTERPARTY_ROLES', '从往来单位条款派生客户、供应商和会员角色概念。'),
      evidenceStatus: 'INFERENCE', policyRefs: [facts.evidence.counterparty],
      affectedObjectRefs: [resultRef('DIMENSION', 'partner_role')],
    }),
    derived({
      stableCode: 'derived.activity.purchase_receipt', section: 'ACTIVITY', semanticKind: 'EVENT', label: '采购入库概念',
      value: structuredValue('DERIVED_PURCHASE_RECEIPT', '从往来单位角色和审核门槛派生采购入库概念。'),
      evidenceStatus: 'INFERENCE', policyRefs: [facts.evidence.counterparty, facts.evidence.status],
      affectedObjectRefs: [resultRef('EVENT', 'purchase_receipt')],
    }),
    derived({
      stableCode: 'derived.activity.sales_delivery', section: 'ACTIVITY', semanticKind: 'EVENT', label: '销售出库概念',
      value: structuredValue('DERIVED_SALES_DELIVERY', '从往来单位角色和审核门槛派生销售出库概念。'),
      evidenceStatus: 'INFERENCE', policyRefs: [facts.evidence.counterparty, facts.evidence.status],
      affectedObjectRefs: [resultRef('EVENT', 'sales_delivery')],
    }),
    derived({
      stableCode: 'derived.activity.returns', section: 'ACTIVITY', semanticKind: 'EVENT', label: '退货退库概念',
      value: structuredValue('DERIVED_RETURN_CONCEPTS', '从采购、销售角色与审核条款派生退货退库概念，具体实现仍由真实来源核验。'),
      evidenceStatus: 'INFERENCE', policyRefs: [facts.evidence.counterparty, facts.evidence.status],
      affectedObjectRefs: [resultRef('EVENT', 'purchase_return'), resultRef('EVENT', 'sales_return')],
    }),
    derived({
      stableCode: 'derived.dimension.document_status.nine', section: 'FIELD', semanticKind: 'DIMENSION', label: '状态 9 派生术语',
      value: structuredValue('DERIVED_STATUS_NINE_TERM', '从演示条款派生状态 9 术语，并保留其与源码工作流的冲突。', {
        upstreamValue: facts.statusValue,
      }), evidenceStatus: 'CONFLICT', policyRefs: [facts.evidence.status],
      affectedObjectRefs: [resultRef('DIMENSION', 'document_status'), resultRef('RULE', 'audit_status')],
    }),
    derived({
      stableCode: 'derived.relation.counterparty_activity', section: 'RELATION', semanticKind: 'RELATION', label: '往来单位参与活动',
      value: structuredValue('DERIVED_COUNTERPARTY_ACTIVITY_ROLES', '往来单位角色与采购、销售及退货活动形成派生参与关系。'),
      evidenceStatus: 'INFERENCE', policyRefs: [facts.evidence.counterparty], affectedObjectRefs: [],
    }),
    derived({
      stableCode: 'derived.rule.negative_stock', section: 'METRIC', semanticKind: 'RULE', label: '负库存规则候选',
      value: structuredValue('DERIVED_NEGATIVE_STOCK_RULE_CANDIDATE', '从演示制度派生负库存规则候选，并保留其与租户配置实现的冲突。', {
        upstreamValue: facts.negativeStockValue,
      }), evidenceStatus: 'CONFLICT', policyRefs: [facts.evidence.negativeStock],
      affectedObjectRefs: [resultRef('RULE', 'negative_stock')],
    }),
    derived({
      stableCode: 'derived.question.policy_impact', section: 'QUESTION', semanticKind: 'RULE', label: '制度影响分析问题',
      value: structuredValue('DERIVED_POLICY_IMPACT_QUESTION', '可审阅审核状态与负库存制度分别影响哪些业务活动。'),
      evidenceStatus: 'INFERENCE', policyRefs: [facts.evidence.status, facts.evidence.negativeStock], affectedObjectRefs: [],
    }),
    derived({
      stableCode: 'derived.gap.stock_time', section: 'UNRESOLVED', semanticKind: 'GAP', label: '库存生效时点仍未证实',
      value: structuredValue('DERIVED_RECOMMENDATION_NOT_IMPLEMENTATION_EVIDENCE', '派生图只携带制度建议，不能解除真实库存生效时点缺口。'),
      evidenceStatus: 'GAP', policyRefs: [facts.evidence.stockTime], affectedObjectRefs: [resultRef('ENTITY', 'inventory_position')],
    }),
  ];
  return { blocks, locators };
}

export function compileSemanticaSource(input: {
  source: StorySource;
  priorCompilations: SourceDocumentCompilation[];
  policyDocuments: DemoPolicyDocumentInput[];
  expectedPolicySnapshotId: string;
}) {
  const facts = policyFacts(input.policyDocuments);
  const upstream = input.priorCompilations.find((candidate) => (
    candidate.sourceId === 'guanyijia_demo_policy'
    && candidate.snapshotId === input.expectedPolicySnapshotId
    && candidate.sourceClass === 'DEMO_POLICY'
    && candidate.lineageStatus === 'ROOT'
    && hasValidProjection(candidate)
    && [
      ['dimension.partner_role', 'COUNTERPARTY_MULTI_ROLE_ALLOWED', facts.evidence.counterparty],
      ['dimension.document_status.nine', facts.statusValue, facts.evidence.status],
      ['rule.negative_stock', facts.negativeStockValue, facts.evidence.negativeStock],
      ['rule.current_stock_effective_time_proposal', 'RECOMMENDATION_ONLY_DOES_NOT_RESOLVE_GAP', facts.evidence.stockTime],
    ].every(([stableCode, normalizedValue, evidenceRef]) => hasTrustedVariant({
      priorCompilations: [candidate],
      sourceId: 'guanyijia_demo_policy',
      snapshotId: input.expectedPolicySnapshotId,
      stableCode,
      normalizedValue,
      evidenceRefs: [evidenceRef],
    }))
    && Object.entries(facts.locators).every(([ref, locator]) => (
      canonicalModelingJson(candidate.evidenceLocators[ref]) === canonicalModelingJson(locator)
    ))
  ));
  if (!upstream) {
    const missingEvidence = 'semantica://guanyijia-demo/gap/upstream-missing';
    return compileBlocks({
      source: input.source,
      priorCompilations: input.priorCompilations,
      readSummary: {
        summary: '派生术语图缺少匹配的演示制度上游编译，停止生成派生事实。',
        objectCount: 0,
        evidenceCount: 1,
        objectCounts: { derivedStatements: 0, lineageGaps: 1 },
        versionRef: input.source.snapshotId,
        warnings: ['缺少 guanyijia_demo_policy 匹配快照，派生结果无效。'],
      },
      blocks: [block({
        stableCode: 'gap.semantica.upstream', section: 'UNRESOLVED', semanticKind: 'GAP', label: '派生上游缺失',
        value: structuredValue('UPSTREAM_POLICY_COMPILATION_MISSING', '缺少匹配快照的演示制度编译，不能把术语图伪装为有效派生来源。'),
        evidenceStatus: 'GAP', evidenceRefs: [missingEvidence, facts.evidence.counterparty], affectedObjectRefs: [],
      })],
      lineageStatus: 'UPSTREAM_MISSING',
      evidenceLocators: {
        ...facts.locators,
        [missingEvidence]: {
          kind: 'SEMANTICA_LINEAGE_GAP', upstreamSourceId: 'guanyijia_demo_policy',
          expectedUpstreamSnapshotId: input.expectedPolicySnapshotId,
          upstreamEvidenceRefs: [facts.evidence.counterparty],
        },
      },
    });
  }
  const graph = semanticaGraph(input.policyDocuments, upstream.snapshotId);
  const derivedEvidenceCount = Object.keys(graph.locators).filter((ref) => ref.startsWith('semantica://')).length;
  return compileBlocks({
    source: input.source,
    priorCompilations: input.priorCompilations,
    readSummary: {
      summary: '由三份演示制度确定性派生的术语图；每条 statement 回链制度文件与章节。',
      objectCount: graph.blocks.length,
      evidenceCount: derivedEvidenceCount,
      objectCounts: { derivedStatements: graph.blocks.length, upstreamDocuments: 3 },
      versionRef: input.source.snapshotId,
      warnings: ['派生来源不增加独立根来源数。'],
    },
    blocks: graph.blocks,
    evidenceLocators: graph.locators,
    lineageStatus: 'DERIVED_VALID',
    corroboratedConflictIds: ['gyj-conflict-negative-stock', 'gyj-conflict-status-nine'],
  });
}

export { compileBlocks, databaseEvidence, repositoryEvidence, resultRef, structuredValue, block };
export type { EvidenceFile, FrozenEvidenceFixture };
