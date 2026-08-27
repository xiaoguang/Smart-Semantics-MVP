import type { DemoPolicyDocumentInput } from './types.ts';

const policyDocuments: DemoPolicyDocumentInput[] = [
  {
    path: '业务术语/往来单位.md',
    version: 'demo-policy-v1',
    content: `# 往来单位

> 演示制度，不是真实生产制度。

## 角色口径

往来单位统一承载客户、供应商和会员角色；同一主体可以同时拥有多个角色。
`,
  },
  {
    path: '单据管理/审核状态.md',
    version: 'demo-policy-v1',
    content: `# 审核状态

> 演示制度，不是真实生产制度。

## 状态 9

状态 9 表示待审核；只有审核通过的单据才能进入正式统计。
`,
  },
  {
    path: '库存管理/负库存与库存时点.md',
    version: 'demo-policy-v1',
    content: `# 负库存与库存时点

> 演示制度，不是真实生产制度。

## 负库存控制

所有租户一律禁止负库存。

## 库存生效时点

建议以审核通过时间作为库存生效时点；实现证据不足时必须保留库存生效时点缺口。
`,
  },
];

export const defaultGuanyijiaPolicyDocuments = Object.freeze(
  policyDocuments.map((document) => Object.freeze({ ...document })),
);

const requiredPaths = policyDocuments.map((document) => document.path);

export function validateAndClonePolicyDocuments(input: readonly DemoPolicyDocumentInput[]) {
  if (input.length !== requiredPaths.length) throw new Error('演示制度必须恰好包含三份冻结 Markdown');
  const byPath = new Map(input.map((document) => [document.path, document]));
  if (byPath.size !== input.length) throw new Error('演示制度文件路径不能重复');
  return requiredPaths.map((path) => {
    const document = byPath.get(path);
    if (!document) throw new Error(`演示制度缺少文件：${path}`);
    if (!document.version.trim() || !document.content.trim()) throw new Error(`演示制度文件内容或版本为空：${path}`);
    if (!document.content.includes('演示制度，不是真实生产制度')) {
      throw new Error(`演示制度文件缺少非生产警示：${path}`);
    }
    return { ...document };
  });
}
