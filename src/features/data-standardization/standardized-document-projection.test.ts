import assert from 'node:assert/strict';
import test from 'node:test';
import {
  projectStandardizedDocument,
  type BusinessDocumentEntry,
  type StandardizedDocumentInput,
} from './standardized-document-projection.ts';
import { buildStandardizedDocumentReadingEntries } from './standardized-document-reading.ts';
import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import type { MysqlSchemaEvidence } from './mysql-schema-evidence.ts';

const input: StandardizedDocumentInput = {
  revisionLabel: '第 2 版',
  markdownSource: '# 数据库审阅\n\n## 业务对象\n\n账户主数据\n',
  claims: [
    {
      claimId: 'claim:account',
      sectionId: 'business-objects',
      sectionTitle: '业务对象',
      sectionPurpose: '说明本次确认的业务对象。',
      markdownAnchor: 'account',
      title: '账户主数据（jsh_account）',
      statement: '保存账户名称、期初金额、当前余额、启用状态与租户归属。',
      fields: ['name', 'initial_amount', 'current_amount', 'enabled', 'tenant_id'],
      kind: 'OBJECT',
    },
    {
      claimId: 'claim:gap',
      sectionId: 'open-items',
      sectionTitle: '待确认事项',
      sectionPurpose: '记录不会进入建模候选的已知缺口。',
      markdownAnchor: 'status-nine',
      title: '状态 9 的业务含义尚未确认',
      statement: '当前资料不能确认状态 9 的正式业务含义。',
      kind: 'GAP',
      nextStep: '等待业务负责人确认。',
    },
  ],
};

test('由同一组结论投影连续阅读版，并保留逐字 Markdown 源文', () => {
  const document = projectStandardizedDocument(input);

  assert.equal(document.revisionLabel, '第 2 版');
  assert.equal(document.markdownSource, input.markdownSource);
  assert.deepEqual(document.sections.map((section) => section.title), ['业务对象', '待确认事项']);
  assert.equal(document.sections[0]!.entries[0]!.title, '账户主数据（jsh_account）');
  assert.deepEqual(document.sections[0]!.entries[0]!.fields, [
    'name', 'initial_amount', 'current_amount', 'enabled', 'tenant_id',
  ]);
  assert.equal(document.sections[1]!.entries[0]!.nextStep, '等待业务负责人确认。');
  assert.equal(document.sections.some((section) => section.entries.length === 0), false);
});

test('数据库结论保留独立的可展开结构依据，且不会与输入对象共享可变字段数组', () => {
  const schemaEvidence = {
    objectName: 'jsh_account',
    objectComment: '账户主数据',
    columns: [{
      name: 'name', dataType: 'varchar(50)', nullable: true,
      defaultValue: 'NULL', comment: '名称', highlighted: true,
    }],
    rawDdl: 'CREATE TABLE `jsh_account` (`name` varchar(50));',
    locationValue: 'ddl/tables/jsh_account.sql',
  };
  const document = projectStandardizedDocument({
    revisionLabel: '第 2 版',
    markdownSource: '# 数据库审阅\n',
    claims: [{
      ...input.claims[0]!,
      schemaEvidence,
    }],
  });

  const entry = document.sections[0]!.entries[0]!;
  assert.deepEqual(entry.schemaEvidence, schemaEvidence);
  assert.notStrictEqual(entry.schemaEvidence, schemaEvidence);
  assert.notStrictEqual(entry.schemaEvidence?.columns, schemaEvidence.columns);
});

test('V6 显式保留规范九章及其顺序，即使某一章暂时没有结论', () => {
  const standardSections = standardSectionOrder.map(({ key, heading }) => ({
    id: key,
    title: heading.replace(/^\d+\.\s/u, ''),
    purpose: `说明${heading.replace(/^\d+\.\s/u, '')}中的可审阅内容。`,
  }));
  const v6Input = {
    revisionLabel: '第 1 版',
    markdownSource: '# 数据库审阅\n',
    standardSections,
    claims: [{
      ...input.claims[0]!,
      sectionId: 'OBJECT',
      sectionTitle: standardSections[2]!.title,
      sectionPurpose: standardSections[2]!.purpose,
    }],
  } as unknown as StandardizedDocumentInput;

  const document = projectStandardizedDocument(v6Input);

  assert.deepEqual(document.sections.map(({ id, title, purpose }) => ({ id, title, purpose })), standardSections);
  assert.equal(document.sections.length, 9);
  assert.equal(document.sections.find((section) => section.id === 'GOAL')?.entries.length, 0);
});

test('V6 的 30 个 MySQL 表展开器保留每条结论的全部结构依据，并传递到阅读投影', () => {
  const schemaEvidence = (objectName: string, locationValue: string): MysqlSchemaEvidence => ({
    objectName,
    objectComment: `${objectName} 结构`,
    columns: [{
      name: 'id', dataType: 'bigint', nullable: false,
      defaultValue: undefined, comment: '主键', keyRole: 'PRIMARY', highlighted: true,
    }],
    rawDdl: `CREATE TABLE \`${objectName}\` (\`id\` bigint NOT NULL);`,
    locationValue,
  });
  const first = schemaEvidence('jsh_account', 'ddl/tables/jsh_account.sql:L1-L4');
  const second = schemaEvidence('jsh_account', 'ddl/indexes/jsh_account.sql:L1-L2');
  const tableClaims = Array.from({ length: 30 }, (_, index) => {
    const objectName = index === 0 ? 'jsh_account' : `jsh_table_${index + 1}`;
    const evidences = index === 0
      ? [first, second]
      : [schemaEvidence(objectName, `ddl/tables/${objectName}.sql:L1-L4`)];
    return {
      ...input.claims[0]!,
      claimId: `claim:table:${index + 1}`,
      sectionId: 'business-objects',
      title: `${objectName} 业务对象`,
      schemaEvidences: evidences,
    };
  });
  const v6Input = {
    revisionLabel: '第 1 版',
    markdownSource: '# 数据库审阅\n',
    claims: tableClaims,
  } as unknown as StandardizedDocumentInput;

  const document = projectStandardizedDocument(v6Input);
  assert.equal(document.sections.length, 1);
  assert.equal(document.sections[0]!.entries.length, 30, 'every selected MySQL table must remain expandable');
  const entries = document.sections[0]!.entries as Array<BusinessDocumentEntry & {
    schemaEvidences?: readonly MysqlSchemaEvidence[];
  }>;
  assert.deepEqual(entries[0]!.schemaEvidences?.map(({ locationValue }) => locationValue), [
    first.locationValue,
    second.locationValue,
  ]);
  assert.equal(entries.reduce((count, entry) => count + (entry.schemaEvidences?.length ?? 0), 0), 31);

  const reading = buildStandardizedDocumentReadingEntries(document);
  assert.equal(reading[0]!.items.length, 30);
  const readingEntries = reading[0]!.items as Array<BusinessDocumentEntry & {
    schemaEvidences?: readonly MysqlSchemaEvidence[];
  }>;
  assert.equal(readingEntries.reduce((count, entry) => count + (entry.schemaEvidences?.length ?? 0), 0), 31);
  assert.deepEqual(readingEntries[0]!.schemaEvidences?.map(({ rawDdl }) => rawDdl), [first.rawDdl, second.rawDdl]);
});
