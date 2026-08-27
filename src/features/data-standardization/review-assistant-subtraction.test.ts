import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const panelSource = readFileSync(new URL('./review-assistant-panel.tsx', import.meta.url), 'utf8');
const workbenchSource = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
const stylesSource = readFileSync(new URL('./data-standardization.css', import.meta.url), 'utf8');

test('审阅助手只保留底部对话输入，不再渲染重复标题或当前讨论卡', () => {
  assert.doesNotMatch(panelSource, /<span><RobotOutlined\s*\/>\s*审阅助手<\/span>/u);
  assert.doesNotMatch(panelSource, /当前讨论/u);
  assert.doesNotMatch(panelSource, /guanyijia-assistant-current-context/u);
  assert.match(panelSource, /guanyijia-review-assistant-composer/u);
  assert.match(panelSource, /data-review-anchor="assistant-composer"/u);
});

test('对话输入作为工作台第二行保留，不占用正文滚动区，也不使用固定定位', () => {
  assert.match(workbenchSource, /guanyijia-workbench-thread/u);
  assert.match(workbenchSource, /ReviewAssistantPanel/u);
  assert.doesNotMatch(stylesSource, /\.guanyijia-review-assistant-panel\s*\{[^}]*position\s*:\s*(?:fixed|sticky)/su);
  assert.match(stylesSource, /\.guanyijia-workbench-thread\s*\{[^}]*grid-template-rows\s*:\s*minmax\(0,\s*1fr\)\s+auto/su);
  assert.match(stylesSource, /\.guanyijia-workbench-scroll\s*\{[^}]*overflow-y\s*:\s*auto/su);
});

test('来源资料行只定位右侧流程，不会清除主阅读区的证据状态', () => {
  const handlerStart = workbenchSource.indexOf('const selectSourceFromInspector =');
  const handlerEnd = workbenchSource.indexOf('const assistantSelection =', handlerStart);
  const handlerSource = workbenchSource.slice(handlerStart, handlerEnd);

  assert.match(handlerSource, /setSelectedInspectorSourceId\(sourceId\)/u);
  assert.match(handlerSource, /setWorkflowLocateRequest\(/u);
  assert.doesNotMatch(handlerSource, /setSelectedBlockId\(undefined\)/u);
  assert.doesNotMatch(handlerSource, /setSelectedCandidateEvidenceRef\(undefined\)/u);
  assert.doesNotMatch(handlerSource, /setSelectedCuratedEvidenceRef\(undefined\)/u);
  assert.doesNotMatch(handlerSource, /setSelectedCuratedTraceAnchor\(undefined\)/u);
});
