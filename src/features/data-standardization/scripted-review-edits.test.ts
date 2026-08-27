import assert from 'node:assert/strict';
import test from 'node:test';
import type { SourceDocumentBlock } from '../source-documents/types.ts';
import { readSourceReviewDocument } from '../guanyijia-evidence-factory/source-review-document.ts';
import {
  buildScriptedReviewBlockChange,
  listScriptedReviewEdits,
  validateScriptedReviewEditBinding,
} from './scripted-review-edits.ts';

const mysql = {
  sourceId: 'guanyijia_mysql',
  snapshotId: '20260813T032528Z-abb0502c7d79',
} as const;
const github = {
  sourceId: 'guanyijia_github',
  snapshotId: '20260813032126Z-5821d0ece9b1',
} as const;

const depotHeadBlock: SourceDocumentBlock = {
  blockId: 'gyj-block:physical.table.jsh_depot_head',
  section: 'OBJECT', semanticKind: 'ENTITY', stableCode: 'physical.table.jsh_depot_head',
  label: '库存单据主表',
  value: { normalized: 'DEPLOYED_TABLE_PRESENT', text: '该业务载体存在于当前部署结构。', physicalTable: 'jsh_depot_head' },
  evidenceStatus: 'FACT', evidenceRefs: ['mysql_jsh_erp@v1:TABLE:jsh_depot_head'],
  affectedObjectRefs: [{ scope: 'RESULT', documentVersion: 'guanyijia-five-source-work-standard', kind: 'ENTITY', objectId: 'jsh_depot_head' }],
};

const negativeStockBlock: SourceDocumentBlock = {
  blockId: 'gyj-block:rule.negative_stock',
  section: 'METRIC', semanticKind: 'RULE', stableCode: 'rule.negative_stock',
  label: '负库存控制候选',
  value: { normalized: 'TENANT_CONFIG_CONTROLS', text: '实现按租户配置决定是否允许负库存。' },
  evidenceStatus: 'FACT',
  evidenceRefs: ['github_jshERP@v1:WORKFLOW:jshERP-boot/src/main/java/com/jsh/erp/service/SystemConfigService.java:METHOD:getMinusStockFlag:511'],
  affectedObjectRefs: [{ scope: 'RESULT', documentVersion: 'guanyijia-five-source-work-standard', kind: 'RULE', objectId: 'negative_stock' }],
};

test('the Demo exposes exactly two source-bound scripted edits', () => {
  const definitions = listScriptedReviewEdits();
  assert.deepEqual(definitions.map((definition) => definition.editId), [
    'scripted:mysql:rename-depot-head',
    'scripted:github:clarify-negative-stock',
  ]);
  assert.deepEqual(definitions[0]?.editableFields, ['label']);
  assert.deepEqual(definitions[1]?.editableFields, ['label', 'text']);
  assert.ok(definitions.every((definition) => definition.lockedFields.includes('evidenceRefs')));
  assert.ok(definitions.every((definition) => definition.lockedFields.includes('normalized')));
});

test('script bindings reject guessed blocks and only build allowed changes', () => {
  const mysqlReview = readSourceReviewDocument(mysql)!;
  const githubReview = readSourceReviewDocument(github)!;
  const mysqlDefinition = listScriptedReviewEdits({ sourceId: mysql.sourceId })[0]!;
  const githubDefinition = listScriptedReviewEdits({ sourceId: github.sourceId })[0]!;

  validateScriptedReviewEditBinding({ definition: mysqlDefinition, review: mysqlReview, block: depotHeadBlock });
  validateScriptedReviewEditBinding({ definition: githubDefinition, review: githubReview, block: negativeStockBlock });
  assert.throws(() => validateScriptedReviewEditBinding({
    definition: mysqlDefinition,
    review: mysqlReview,
    block: { ...depotHeadBlock, blockId: 'gyj-block:physical.table.jsh_depot_item' },
  }), /剧本编辑映射校验失败/u);

  assert.deepEqual(buildScriptedReviewBlockChange(mysqlDefinition, depotHeadBlock, {
    label: '库存单据表头（jsh_depot_head）',
  }), {
    blockId: depotHeadBlock.blockId,
    label: '库存单据表头（jsh_depot_head）',
  });
  assert.throws(() => buildScriptedReviewBlockChange(mysqlDefinition, depotHeadBlock, {
    text: '不允许修改正文',
  }), /不允许修改正文/u);

  const githubChange = buildScriptedReviewBlockChange(githubDefinition, negativeStockBlock, {
    label: '租户级负库存控制',
    text: '固定版本的系统配置表定义了按租户保存的负库存启用标记；是否允许负库存仍需结合运行配置和业务制度确认。',
  });
  assert.deepEqual(githubChange, {
    blockId: negativeStockBlock.blockId,
    label: '租户级负库存控制',
    value: {
      normalized: 'TENANT_CONFIG_CONTROLS',
      text: '固定版本的系统配置表定义了按租户保存的负库存启用标记；是否允许负库存仍需结合运行配置和业务制度确认。',
    },
  });
});
