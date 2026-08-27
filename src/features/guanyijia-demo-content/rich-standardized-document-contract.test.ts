import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { standardSectionOrder } from '../modeling-document-bridge/standard-markdown.ts';
import { createMemoryContentStore, createSourceDocumentRuntime } from '../source-documents/runtime.ts';
import { createStandardizationRunRuntime } from '../standardization-run/runtime.ts';
import { createGuanyijiaStandardizationStory } from '../guanyijia-standardization-story/index.ts';
import { createGuanyijiaWorkbenchRuntime } from '../data-standardization/guanyijia-workbench-runtime.ts';
import { buildStandardizedDocumentReadingEntries } from '../data-standardization/standardized-document-reading.ts';
import { projectStandardizedDocument } from '../data-standardization/standardized-document-projection.ts';
import { readSourceStandardDocument } from '../guanyijia-evidence-factory/source-review-document.ts';
import {
  bindDemoContentRun,
  readDemoContentSourceReview,
  type DemoContentSourceReview,
} from './demo-content-review.ts';
import { pinnedDemoContentPublication } from './pinned-demo-content.generated.ts';

const sourceInputs = [
  { sourceId: 'guanyijia_mysql', snapshotId: '20260813T032528Z-abb0502c7d79' },
  { sourceId: 'guanyijia_github', snapshotId: '20260813032126Z-5821d0ece9b1' },
  { sourceId: 'guanyijia_official_docs', snapshotId: 'gyjerp-official-docs-20260813T031656Z' },
  { sourceId: 'guanyijia_demo_policy', snapshotId: 'guanyijia-demo-policy-f6c6d209ffe3fd53' },
  { sourceId: 'guanyijia_semantica_demo', snapshotId: 'guanyijia-semantica-demo-ff948845dc5bd778' },
] as const;

const selectedTables = [
  'jsh_account', 'jsh_account_head', 'jsh_account_item', 'jsh_depot',
  'jsh_depot_head', 'jsh_depot_item', 'jsh_function', 'jsh_in_out_item',
  'jsh_material', 'jsh_material_attribute', 'jsh_material_category',
  'jsh_material_current_stock', 'jsh_material_extend', 'jsh_material_initial_stock',
  'jsh_material_property', 'jsh_msg', 'jsh_orga_user_rel', 'jsh_organization',
  'jsh_person', 'jsh_platform_config', 'jsh_role', 'jsh_serial_number',
  'jsh_supplier', 'jsh_sys_dict_data', 'jsh_sys_dict_type', 'jsh_system_config',
  'jsh_tenant', 'jsh_unit', 'jsh_user', 'jsh_user_business',
] as const;

const selectedProcedures = [
  'sp_rebalance_below_low_stock_requisition',
  'sp_rebalance_over_high_stock_sales',
  'sp_run_inventory_stock_rebalance',
  'sp_run_retail_return_rate_and_fact_sync',
  'sp_run_store_retail_return_coverage_and_fact_sync',
  'sp_sync_retail_out_fact_batch',
] as const;

function readRichSource(input: (typeof sourceInputs)[number]): DemoContentSourceReview {
  const review = readDemoContentSourceReview(input);
  assert.ok(review, `${input.sourceId} must expose its V6 rich reading document`);
  return review;
}

function sectionHeadings(markdown: string) {
  return [...markdown.matchAll(/^##\s+(.+)$/gmu)].map((match) => match[1]!.trim());
}

function citedSourceLines(review: DemoContentSourceReview) {
  const lines = new Set<string>();
  for (const evidence of review.evidence) {
    const match = /^(.*):L(\d+)(?:-L(\d+))?$/u.exec(evidence.locationValue);
    if (!match) continue;
    const first = Number(match[2]);
    const last = Number(match[3] ?? match[2]);
    for (let line = first; line <= last; line += 1) lines.add(`${match[1]}:${line}`);
  }
  return lines;
}

test('every V6 human source document has exactly the shared nine sections, anchors, and coverage', () => {
  const expectedHeadings = standardSectionOrder.map(({ heading }) => heading);
  for (const input of sourceInputs) {
    const review = readRichSource(input);
    assert.deepEqual(sectionHeadings(review.markdown), expectedHeadings, `${input.sourceId} section order`);
    assert.ok(review.markdown.trim(), `${input.sourceId} rich body must not be empty`);
    assert.equal(new Set(review.traceLinks.map((trace) => trace.markdownAnchor)).size, review.traceLinks.length,
      `${input.sourceId} anchors must be unique`);
    assert.equal(review.traceLinks.every((trace) => (
      Number.isInteger(trace.section)
      && Boolean(trace.sectionId)
      && Boolean(trace.sectionPurpose)
      && trace.markdownAnchor.length > 0
      && trace.evidenceRefs.length > 0
    )), true, `${input.sourceId} traces must carry explicit section, anchor, and evidence coverage`);
    assert.deepEqual(
      [...new Set(review.traceLinks.map((trace) => trace.section))].sort((a, b) => a! - b!),
      standardSectionOrder.map((_, index) => index + 1),
      `${input.sourceId} must explicitly cover all nine sections`,
    );
  }
});

test('the V6 publication contains one exact nine-section merged document beside its human source documents', () => {
  const publication = pinnedDemoContentPublication as unknown as {
    mergedDocument?: DemoContentSourceReview & { documentKind?: string };
  };
  assert.ok(publication.mergedDocument, 'V6 publication must expose mergedDocument');
  const merged = publication.mergedDocument!;
  assert.equal(merged.documentKind, 'MERGED', 'merged document must be identified as MERGED');
  assert.deepEqual(sectionHeadings(merged.markdown), standardSectionOrder.map(({ heading }) => heading));
  assert.ok(merged.traceLinks.length > 0, 'merged document must retain claim coverage');
});

test('each MySQL V6 rich review preserves all selected tables and procedures', () => {
  const mysql = readRichSource(sourceInputs[0]);
  const actualTables = mysql.evidence
    .filter((entry) => entry.evidenceClass === 'OBSERVED' && entry.locationValue.startsWith('ddl/tables/'))
    .map((entry) => entry.locationValue.slice('ddl/tables/'.length).replace(/\.sql(?:#.*)?$/u, ''))
    .sort();
  const actualProcedures = mysql.evidence
    .filter((entry) => entry.evidenceClass === 'OBSERVED' && entry.locationValue.startsWith('programmability/procedures/'))
    .map((entry) => entry.locationValue.slice('programmability/procedures/'.length).replace(/\.sql(?:#.*)?$/u, ''))
    .sort();
  assert.deepEqual(actualTables, [...selectedTables].sort());
  assert.deepEqual(actualProcedures, [...selectedProcedures].sort());
});

test('GitHub V6 rich review preserves 43 admitted claims, at least 69 real evidence items, and 500 cited source lines', () => {
  const github = readRichSource(sourceInputs[1]);
  assert.equal(github.traceLinks.length, 43, 'admitted claim count');
  assert.ok(github.evidence.length >= 69, 'real evidence count');
  assert.equal(github.evidence.every((entry) => entry.evidenceClass === 'SOURCE_NATIVE'), true);
  assert.ok(citedSourceLines(github).size >= 500, 'cited source line scope');
});

test('the V6 reader projection keeps nine chapters for every source and expandable MySQL objects', () => {
  const binding = bindDemoContentRun({
    runId: 'rich-contract:reader-projection',
    formalSources: sourceInputs,
  });
  const expectedSectionIds = standardSectionOrder.map(({ key }) => key);

  const projectReaderDocument = (source: (typeof sourceInputs)[number]) => {
    const standard = readSourceStandardDocument({ ...source, contentBinding: binding });
    assert.ok(standard, `${source.sourceId} must have a V6 reader document`);
    const standardSections = standard.standardSections.map((section) => ({
      id: section.sectionId,
      title: section.title,
      purpose: section.purpose,
    }));
    const claims = standard.claims.map((claim) => {
      const section = standardSections.find((candidate) => candidate.id === claim.sectionId);
      assert.ok(section, `${source.sourceId} claim ${claim.claimId} must map to a declared chapter`);
      return {
        claimId: claim.claimId,
        sectionId: claim.sectionId,
        sectionTitle: section.title,
        sectionPurpose: claim.sectionPurpose,
        markdownAnchor: claim.markdownAnchor,
        title: claim.title,
        statement: claim.statement,
        ...(claim.focusIdentifiers.length ? { fields: [...claim.focusIdentifiers] } : {}),
        ...(claim.schemaEvidences.length ? { schemaEvidences: claim.schemaEvidences } : {}),
        kind: claim.kind === 'OBJECT' ? 'OBJECT' as const : claim.kind === 'GAP' ? 'GAP' as const : 'RULE' as const,
        ...(claim.kind === 'GAP' ? { nextStep: '补充资料或由业务负责人确认。' } : {}),
      };
    });
    return {
      source,
      sourceReview: standard,
      document: projectStandardizedDocument({
        revisionLabel: '第 1 版',
        markdownSource: standard.markdown,
        standardSections,
        claims,
      }),
    };
  };

  const projected = sourceInputs.map(projectReaderDocument);
  for (const { source, sourceReview, document } of projected) {
    assert.deepEqual(document.sections.map((section) => section.id), expectedSectionIds, `${source.sourceId} reader chapters`);
    assert.equal(document.sections.length, 9);
    assert.ok(document.markdownSource.trim().length > 0, `${source.sourceId} Markdown body must be non-empty`);
    assert.equal(buildStandardizedDocumentReadingEntries(document).length, 9);
    assert.equal(sourceReview.standardSections.length, 9);
  }

  const mysql = projected.find(({ source }) => source.sourceId === 'guanyijia_mysql')!;
  const mysqlExpandableObjects = mysql.document.sections
    .flatMap((section) => section.entries)
    .filter((entry) => entry.kind === 'OBJECT' && entry.schemaEvidence !== undefined);
  assert.equal(mysqlExpandableObjects.length, 30, 'MySQL must expose all 30 selected tables as expandable objects');

  const github = projected.find(({ source }) => source.sourceId === 'guanyijia_github')!;
  assert.equal(github.sourceReview.claims.length, 43, 'GitHub reader must retain the rich claim floor');
  assert.ok(github.sourceReview.evidence.length >= 69, 'GitHub reader must retain its admitted evidence floor');
});

const readerSource = readFileSync(new URL('../data-standardization/standardized-document-reader.tsx', import.meta.url), 'utf8');
const readerStyles = readFileSync(new URL('../data-standardization/data-standardization.css', import.meta.url), 'utf8');

test('standards reader uses a top 3x3 section nav, full-width body, and reusable object expanders', () => {
  assert.match(readerStyles, /\.guanyijia-standardized-document-reading\s*>\s*nav\s*\{[^}]*grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)/su);
  assert.doesNotMatch(readerStyles, /\.guanyijia-standardized-document-reading\s*\{[^}]*grid-template-columns:\s*minmax\(150px,\s*190px\)/su);
  assert.match(readerStyles, /\.guanyijia-standardized-document-reading\s*>\s*article\s*\{[^}]*max-width:\s*none/su);
  assert.doesNotMatch(readerStyles, /\.guanyijia-standardized-document-reading\s*>\s*article\s*\{[^}]*max-width:\s*72ch/su);
  assert.match(readerSource, /<details[^>]+className="guanyijia-object-details"/u);
});

class MemoryStorage implements Pick<Storage, 'getItem' | 'setItem'> {
  private readonly values = new Map<string, string>();

  getItem(key: string) { return this.values.get(key) ?? null; }

  setItem(key: string, value: string) { this.values.set(key, value); }
}

test('normal V6 workbench reads remain fixture-only and never call an LLM or network', async () => {
  const metadataStorage = new MemoryStorage();
  const pointerStorage = new MemoryStorage();
  const contentStore = createMemoryContentStore();
  const sourceDocuments = createSourceDocumentRuntime({ metadataStorage, contentStore });
  const story = createGuanyijiaStandardizationStory();
  const standardizationRuns = createStandardizationRunRuntime({ metadataStorage, contentStore });
  const runtime = createGuanyijiaWorkbenchRuntime({
    pointerStorage,
    standardizationRuns,
    sourceDocuments,
    story,
    contentStore,
  });
  const previousFetch = globalThis.fetch;
  let llmOrNetworkCalls = 0;
  Object.defineProperty(globalThis, 'fetch', {
    configurable: true,
    writable: true,
    value: async () => {
      llmOrNetworkCalls += 1;
      throw new Error('normal V6 path must not call fetch/LLM');
    },
  });
  try {
    const started = await runtime.execute({
      type: 'START_RUN', commandId: 'rich-contract:start', expectedRevision: 0, actorUserId: 'user-author',
    });
    await runtime.execute({
      type: 'READ_NEXT_SOURCE', commandId: 'rich-contract:read:mysql',
      expectedRevision: started.run!.revision, actorUserId: 'user-author',
    });
    assert.equal(llmOrNetworkCalls, 0);
    assert.equal(started.contentBinding?.contentSnapshotId, 'guanyijia-demo-content-v6-20260826');
  } finally {
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      writable: true,
      value: previousFetch,
    });
  }
});
