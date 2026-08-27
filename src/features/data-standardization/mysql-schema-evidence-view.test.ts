import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import test from 'node:test';

const featureDirectory = dirname(new URL(import.meta.url).pathname);
const sourceDocumentPath = resolve(featureDirectory, 'source-document-readable.tsx');
const inspectorPath = resolve(featureDirectory, 'standardization-facts-inspector.tsx');
const stylesheetPath = resolve(featureDirectory, 'data-standardization.css');
const sourceDocument = readFileSync(sourceDocumentPath, 'utf8');
const inspector = readFileSync(inspectorPath, 'utf8');
const stylesheet = readFileSync(stylesheetPath, 'utf8');

function importedRelativeModules(source: string) {
  return [...source.matchAll(/from\s+['"](\.[^'"]+)['"]/gu)]
    .map((match) => match[1])
    .filter((modulePath) => /mysql.*schema|schema.*evidence/iu.test(modulePath));
}

function resolveModule(modulePath: string) {
  const candidates = [modulePath, `${modulePath}.tsx`, `${modulePath}.ts`]
    .map((candidate) => resolve(featureDirectory, candidate));
  return candidates.find((candidate) => existsSync(candidate));
}

test('主审阅区和来源资料区共享同一个 MySQL 结构依据展示模块', () => {
  const sourceImports = importedRelativeModules(sourceDocument);
  const inspectorImports = importedRelativeModules(inspector);
  const sharedModulePath = sourceImports
    .map((modulePath) => ({ modulePath, resolved: resolveModule(modulePath) }))
    .find(({ modulePath, resolved }) => resolved && inspectorImports.includes(modulePath))?.resolved;

  assert.ok(
    sharedModulePath,
    'source-document-readable.tsx 与 standardization-facts-inspector.tsx 必须导入同一个共享结构依据展示模块',
  );

  const sharedModule = readFileSync(sharedModulePath, 'utf8');
  assert.match(sharedModule, /projectMysqlSchemaEvidence|project[A-Za-z]*Schema[A-Za-z]*Evidence/u);
  assert.match(sharedModule, /<|React\.createElement/u, '共享模块必须是实际的 React 展示模块');
});

test('MySQL 结构依据按自身容器宽度提供宽、紧凑、窄三种展示契约', () => {
  assert.match(stylesheet, /@container[^{}]*\{[\s\S]*?(?:WIDE|wide)/u);
  assert.match(stylesheet, /(?:COMPACT|compact)/u);
  assert.match(stylesheet, /(?:NARROW|narrow)/u);
  assert.match(stylesheet, /(?:data-layout|layout)[^{}]*(?:WIDE|COMPACT|NARROW)/u);
});

test('结构依据不使用破折号伪造缺失字段值', () => {
  assert.doesNotMatch(sourceDocument, /column\.(?:defaultValue|comment)\s*\?\?\s*['"]—['"]/u);
  assert.doesNotMatch(inspector, /column\.(?:defaultValue|comment|keyRole)\s*\?\?\s*['"]—['"]/u);
  assert.doesNotMatch(stylesheet, /['"]—['"]/u);
});

test('原始 DDL 在两种嵌入位置都只在自身区域横向滚动', () => {
  assert.match(stylesheet, /\.guanyijia-mysql-schema-ddl pre\s*\{[^}]*overflow-x:\s*auto/u);
  assert.match(stylesheet, /\.guanyijia-readable-evidence \.guanyijia-mysql-schema-ddl pre,[\s\S]*?\.guanyijia-claim-evidence \.guanyijia-mysql-schema-ddl pre/u);
});
