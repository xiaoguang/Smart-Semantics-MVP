import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  evidencePackageDefinitions,
  modelProjectDefinitions,
} from './domain-registry.ts';

const projectRoot = fileURLToPath(new URL('../../../', import.meta.url));
const collaborationContextPath = new URL('../collaboration/collaboration-context.tsx', import.meta.url);
const appLayoutPath = new URL('../../layout/AppLayout.tsx', import.meta.url);

function currentReadUiResult(storedValue: string | null, fallback = 'erp_data_governance') {
  const source = readFileSync(collaborationContextPath, 'utf8');
  const match = source.match(/function readUi\(userId: string, fallback: string\) \{([\s\S]*?)\n\}/);
  assert.ok(match, 'collaboration-context 必须保留可核查的初始工作空间解析入口');

  const localStorage = {
    getItem() { return storedValue; },
  };
  const evaluate = new Function(
    'localStorage',
    'userId',
    'fallback',
    `const uiKey = (value) => \`linguan:collaboration:ui:v1:\${value}\`;\n${match[1]!}`,
  ) as (storage: typeof localStorage, userId: string, fallbackWorkspaceId: string) => string;

  return evaluate(localStorage, 'user_administer', fallback);
}

test('没有已保存选择时默认进入管伊佳工作空间', () => {
  assert.equal(currentReadUiResult(null), 'erp_data_governance');
});

test('无效的历史工作空间选择回退到管伊佳', () => {
  assert.equal(
    currentReadUiResult(JSON.stringify({ schemaVersion: 1, activeWorkspaceId: 'removed_workspace' })),
    'erp_data_governance',
  );
});

test('隐藏后的零售历史选择不再恢复，回退到管伊佳', () => {
  assert.equal(
    currentReadUiResult(JSON.stringify({ schemaVersion: 1, activeWorkspaceId: 'retail_semantic_modeling' })),
    'erp_data_governance',
  );
});

test('用户可见的模型项目下拉不直接使用全部可访问项目', () => {
  const source = readFileSync(appLayoutPath, 'utf8');
  const projectOptions = source.match(/const projectOptions = useMemo\(([\s\S]*?)\n  useEffect\(/)?.[1] ?? '';
  assert.ok(projectOptions, '必须能定位用户可见的模型项目选项投影');
  assert.doesNotMatch(
    projectOptions,
    /accessibleModelProjects\(collaboration\.workspaces\)\s*\n?\s*\.map/,
    '用户可见下拉不能把全部可访问项目直接渲染出来；零售项目应从此投影隐藏',
  );
});

test('隐藏零售入口不删除零售项目、证据包或运行 Fixture', () => {
  assert.ok(modelProjectDefinitions.some((project) => project.projectId === 'group_retail_ops'));
  assert.equal(
    evidencePackageDefinitions.filter((item) => item.projectId === 'group_retail_ops').length,
    4,
  );
  for (const relativePath of [
    'src/features/ai-modeling/group-retail-fixture.ts',
    'src/features/ai-modeling/group-retail-model-fixture.ts',
    'src/features/ai-modeling/group-retail-runtime.ts',
    'src/features/collaboration/retail-evidence-fixture.ts',
  ]) {
    assert.equal(existsSync(`${projectRoot}${relativePath}`), true, `${relativePath} 必须保留`);
  }
});
