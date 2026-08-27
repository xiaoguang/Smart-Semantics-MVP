import assert from 'node:assert/strict';
import test from 'node:test';
import { projectMysqlSchemaEvidence } from './mysql-schema-evidence.ts';

const evidence = {
  objectName: 'jsh_depot_head',
  objectComment: '出入库单据表头',
  columns: [
    {
      name: 'id',
      dataType: 'bigint',
      nullable: false,
      defaultValue: 'AUTO_INCREMENT',
      comment: '主键',
      keyRole: 'PRIMARY' as const,
      highlighted: false,
    },
    {
      name: 'type',
      dataType: 'varchar(50)',
      nullable: true,
      defaultValue: 'NULL',
      comment: '类型（出库/入库）',
      highlighted: true,
    },
    {
      name: 'status',
      dataType: 'varchar(1)',
      nullable: true,
      defaultValue: 'NULL',
      comment: '单据审核状态',
      highlighted: true,
    },
    {
      name: 'remark',
      dataType: 'varchar(255)',
      nullable: true,
      comment: '备注',
      highlighted: false,
    },
  ],
  rawDdl: 'CREATE TABLE `jsh_depot_head` (\n  `id` bigint NOT NULL AUTO_INCREMENT,\n  `type` varchar(50) DEFAULT NULL COMMENT \'类型（出库/入库）\',\n  `status` varchar(1) DEFAULT NULL COMMENT \'单据审核状态\',\n  `remark` varchar(255) COMMENT \'备注\'\n);',
  locationValue: 'ddl/tables/jsh_depot_head.sql:L1-L6',
};

function valuesOf(projection: unknown) {
  return JSON.stringify(projection);
}

test('same schema evidence projects to a readable six-column wide layout', () => {
  const projection = projectMysqlSchemaEvidence(evidence, { containerWidth: 960 });

  assert.equal(projection.layout, 'WIDE');
  assert.deepEqual(projection.structured.columns, [
    { key: 'field', label: '字段' },
    { key: 'type', label: '类型' },
    { key: 'nullable', label: '可空' },
    { key: 'defaultValue', label: '默认值' },
    { key: 'comment', label: '说明' },
    { key: 'keyRole', label: '键' },
  ]);
  assert.equal(projection.structured.rows.length, evidence.columns.length);
  assert.equal(valuesOf(projection.structured.rows).includes('—'), false,
    '宽布局不能用破折号伪造缺失信息');
});

test('same schema evidence projects to a four-column compact layout with merged constraints', () => {
  const projection = projectMysqlSchemaEvidence(evidence, { containerWidth: 720 });

  assert.equal(projection.layout, 'COMPACT');
  assert.deepEqual(projection.structured.columns, [
    { key: 'field', label: '字段' },
    { key: 'type', label: '类型' },
    { key: 'constraints', label: '约束' },
    { key: 'comment', label: '说明' },
  ]);
  assert.match(valuesOf(projection.structured.rows), /可空/u);
  assert.match(valuesOf(projection.structured.rows), /默认 NULL/u);
  assert.match(valuesOf(projection.structured.rows), /主键/u);
  assert.equal(valuesOf(projection.structured.rows).includes('—'), false,
    '紧凑布局不能为缺失的键、默认值或说明渲染破折号');
});

test('same schema evidence projects to narrow definition rows and keeps raw DDL separate', () => {
  const projection = projectMysqlSchemaEvidence(evidence, { containerWidth: 480 });

  assert.equal(projection.layout, 'NARROW');
  assert.deepEqual(projection.structured.columns, undefined);
  assert.deepEqual(projection.structured.rows[0], {
    field: 'id',
    type: 'bigint',
    description: '主键',
    constraints: ['不可为空', '自增', '主键'],
  });
  assert.equal(valuesOf(projection.structured.rows).includes('—'), false,
    '窄布局不能以破折号填充没有值的字段');
  assert.deepEqual(projection.rawDdl, {
    text: evidence.rawDdl,
    location: evidence.locationValue,
  });
  assert.equal(valuesOf(projection.structured).includes(evidence.rawDdl), false,
    '原始 DDL 必须在结构化字段展示之外单独提供');
});
