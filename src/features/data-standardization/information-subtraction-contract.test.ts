import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const workbenchSource = readFileSync(new URL('./guanyijia-standardization-workbench.tsx', import.meta.url), 'utf8');
const inspectorSource = readFileSync(new URL('./standardization-facts-inspector.tsx', import.meta.url), 'utf8');
const stylesSource = readFileSync(new URL('./data-standardization.css', import.meta.url), 'utf8');

test('来源资料面板只有顶部一个展开开关，面板内部不再复制标题盒', () => {
  const toolbarToggleCount = [...workbenchSource.matchAll(/id="guanyijia-inspector-trigger"/gu)].length;
  assert.equal(toolbarToggleCount, 1);
  assert.match(workbenchSource, /id="guanyijia-inspector-trigger"[\s\S]*?data-source-panel-toggle="true"/u);
  assert.doesNotMatch(inspectorSource, /guanyijia-facts-inspector-header/u);
  assert.doesNotMatch(inspectorSource, /收起资料区/u);
  assert.doesNotMatch(inspectorSource, /<span>来源资料<\/span>/u);
});

test('审阅主区使用有界布局，正文滚动与底部对话输入分属工作台两行', () => {
  assert.match(stylesSource, /\.guanyijia-workbench-layout\s*\{[^}]*height:\s*100%/su);
  assert.match(stylesSource, /\.guanyijia-workbench-thread\s*\{[^}]*grid-template-rows:\s*minmax\(0,\s*1fr\)\s+auto/su);
  assert.match(stylesSource, /\.guanyijia-workbench-scroll\s*\{[^}]*overflow-y:\s*auto/su);
  assert.match(workbenchSource, /<div ref=\{threadRef\} className="guanyijia-workbench-scroll">[\s\S]*?<\/div>\s*\{reviewAssistant\}/u);
});

test('来源行只定位右侧对应检查点，不打开文档或清空主区当前选择', () => {
  const handlerStart = workbenchSource.indexOf('const selectSourceFromInspector =');
  const handlerEnd = workbenchSource.indexOf('const assistantSelection =', handlerStart);
  assert.notEqual(handlerStart, -1);
  assert.notEqual(handlerEnd, -1);
  const handlerSource = workbenchSource.slice(handlerStart, handlerEnd);

  assert.match(handlerSource, /setSelectedTimelineItemId\(`source:\$\{sourceId\}`\)/u);
  assert.match(handlerSource, /setSelectedInspectorSourceId\(sourceId\)/u);
  assert.match(handlerSource, /setWorkflowLocateRequest\(/u);
  assert.doesNotMatch(handlerSource, /openPersistedSourceDocument|setDocumentOpen|setSelectedBlockId\(undefined\)/u);
  assert.doesNotMatch(handlerSource, /setSelectedCandidateEvidenceRef\(undefined\)|setSelectedCuratedEvidenceRef\(undefined\)/u);
  assert.doesNotMatch(handlerSource, /setSelectedCuratedTraceAnchor\(undefined\)/u);
});

test('内联资料区不渲染关闭按钮，覆盖层才拥有唯一图标关闭操作', () => {
  assert.match(inspectorSource, /modal && onClose && <div className="guanyijia-facts-inspector-close">/u);
  assert.match(inspectorSource, /modal && onClose && <div[\s\S]*?aria-label=\{closeLabel\}/u);
  assert.doesNotMatch(workbenchSource, /onClose=\{\(\) => closeLayer\('inspector'\)\}[\s\S]*?inspectorCollapsed/u);
});

test('审阅事项不再把来源材料提升为独立统计或独立阅读区域', () => {
  assert.doesNotMatch(workbenchSource, /\['MATERIALS',\s*`来源材料 \$\{reviewSummary\.materials\.count\}`\]/u);
  assert.doesNotMatch(workbenchSource, /data-review-summary-group="MATERIALS"/u);
  assert.doesNotMatch(workbenchSource, /<h4>来源材料<\/h4>/u);
});

test('对象明细只有在有对象时渲染，并默认展开为可追踪目录', () => {
  assert.match(workbenchSource, /objectDetails\.length(?:\s*>\s*0)?\s*(?:\?|&&)\s*<details\s+open[^>]*aria-label="对象明细"[\s\S]*?<summary>对象目录 · \{curatedWorkspace\.checklist\.objectDetails\.length\} 个对象<\/summary>[\s\S]*?<table>/u);
});

test('结果与当前任务只保留业务标题，不再增加重复眉题', () => {
  assert.doesNotMatch(workbenchSource, /<span>标准化结果<\/span>\s*<h2>/u);
  assert.doesNotMatch(workbenchSource, /<span>当前任务<\/span>\s*<h2/u);
});
