import assert from 'node:assert/strict';
import test from 'node:test';
import { semanticCollectionCopy, semanticCollectionNotice } from './semantic-copy.ts';

test('语义采集说明只解释用户可见影响，不泄漏内部版本或发布术语', () => {
  assert.deepEqual(semanticCollectionNotice, {
    message: '示例在独立空间中运行',
    description: '结果仅用于查看资料如何整理，不会改动已保存的来源、已发布模型或个人草稿。',
  });
  assert.doesNotMatch(`${semanticCollectionNotice.message}${semanticCollectionNotice.description}`, /(?:Revision|Catalog|R2)/u);
});

test('语义采集把内部图数据术语转换为可理解的资料审阅文案', () => {
  assert.deepEqual(semanticCollectionCopy, {
    emptySteps: ['读取资料', '整理结论', '核对来源', '形成建模建议'],
    tabs: {
      evidence: '来源材料',
      claims: '审阅结论',
      findings: '来源比较',
      model: '建模建议',
      review: '待解释内容',
    },
    profile: {
      materialScope: '资料范围',
      startConcepts: '起始概念',
      mappedFields: '已确定字段对照',
      upstream: '上游资料',
      unknownField: '待补充的字段含义',
    },
  });
  assert.doesNotMatch(
    [...Object.values(semanticCollectionCopy.tabs), ...semanticCollectionCopy.emptySteps, ...Object.values(semanticCollectionCopy.profile)].join(' '),
    /(?:RDF|Quad|statement|主张|谓词)/iu,
  );
});
