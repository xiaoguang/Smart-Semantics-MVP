import type { StorageLike } from './runtime.ts';

export type WorkspaceDefinition = {
  systemCode: string;
  displayName: string;
  kind: 'DEFAULT' | 'FIXTURE' | 'USER_CREATED';
  createdAt: string;
};

const storageKey = 'linguan:modeling-workspaces:v1';
const codePattern = /^[a-z][a-z0-9_]{2,63}$/;
const seed: WorkspaceDefinition[] = [
  { systemCode: 'default', displayName: '默认模型空间', kind: 'DEFAULT', createdAt: '2026-08-01T00:00:00.000Z' },
  { systemCode: 'digital_sales_warehouse', displayName: '数码产品销售仓储语义模型', kind: 'FIXTURE', createdAt: '2026-08-01T00:00:00.000Z' },
  { systemCode: 'omnichannel_retail_ops', displayName: '全渠道零售经营语义模型', kind: 'FIXTURE', createdAt: '2026-08-06T00:00:00.000Z' },
  { systemCode: 'guanyijia_erp', displayName: '管伊佳 ERP 语义模型', kind: 'FIXTURE', createdAt: '2026-08-06T00:00:00.000Z' },
  { systemCode: 'group_retail_ops', displayName: '零售经营语义模型', kind: 'FIXTURE', createdAt: '2026-08-08T00:00:00.000Z' },
];

export function validateModelingCode(value: string) {
  return codePattern.test(value) ? null : '编码必须以小写字母开头，仅包含小写字母、数字和下划线，长度为 3–64 位';
}

export function createWorkspaceRegistry(
  storage: Pick<StorageLike, 'getItem' | 'setItem'>,
  now: () => string = () => new Date().toISOString(),
) {
  const load = () => {
    try {
      const raw = storage.getItem(storageKey);
      if (!raw) return structuredClone(seed);
      const parsed = JSON.parse(raw) as { schemaVersion?: number; workspaces?: WorkspaceDefinition[] };
      if (parsed.schemaVersion !== 1 || !Array.isArray(parsed.workspaces)) return structuredClone(seed);
      const saved = parsed.workspaces.filter((item) => item.kind === 'USER_CREATED' && !validateModelingCode(item.systemCode));
      return [...structuredClone(seed), ...saved.filter((item) => !seed.some((fixed) => fixed.systemCode === item.systemCode))];
    } catch {
      return structuredClone(seed);
    }
  };
  let workspaces = load();
  const persist = () => storage.setItem(storageKey, JSON.stringify({ schemaVersion: 1, workspaces }));
  if (!storage.getItem(storageKey)) persist();
  return {
    list: () => structuredClone(workspaces),
    get: (systemCode: string) => structuredClone(workspaces.find((item) => item.systemCode === systemCode)),
    create(input: { systemCode: string; displayName: string }) {
      const error = validateModelingCode(input.systemCode);
      if (error) throw new Error(error);
      if (!input.displayName.trim()) throw new Error('请填写模型空间名称');
      if (workspaces.some((item) => item.systemCode === input.systemCode)) throw new Error('该系统编码已存在');
      const workspace: WorkspaceDefinition = {
        systemCode: input.systemCode,
        displayName: input.displayName.trim(),
        kind: 'USER_CREATED',
        createdAt: now(),
      };
      workspaces = [...workspaces, workspace];
      persist();
      return structuredClone(workspace);
    },
  };
}
