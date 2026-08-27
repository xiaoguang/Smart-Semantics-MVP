import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import {
  compileDemoPolicySource,
  compileGithubSource,
  compileMysqlSource,
  compileOfficialDocsSource,
  compileSemanticaSource,
  createConflictDefinitions,
  getGuanyijiaFrozenRealSourceSnapshotIds,
  reviseSourceCompilation,
} from './compiler.ts';
import { defaultGuanyijiaPolicyDocuments } from './policy-documents.ts';
import { validateAndClonePolicyDocuments } from './policy-documents.ts';
import { loadOrBuildSourceSnapshot } from './source-snapshot-store.ts';
import { loadPinnedGuanyijiaFiveSourceSnapshot } from './pinned-source-snapshot.ts';
import {
  buildConflictHunk as projectConflictHunk,
  previewConflictResolution as projectConflictResolution,
  projectConflictResolution as reprojectConflictResolution,
} from './conflict-resolution.ts';
import type {
  ConflictSourceRevision,
  DemoPolicyDocumentInput,
  GuanyijiaStandardizationStory,
  SourceDocumentCompilation,
  StorySource,
} from './types.ts';

function clone<T>(value: T): T {
  return structuredClone(value);
}

function policySnapshotId(policyDocuments: DemoPolicyDocumentInput[]) {
  return `guanyijia-demo-policy-${sha256HexSync(canonicalModelingJson(policyDocuments)).slice(0, 16)}`;
}

function immutableBlockProjection(compilation: SourceDocumentCompilation) {
  return compilation.blocks.map(({ label: _label, value: _value, ...immutable }) => immutable);
}

function assertCompilationBoundary(input: {
  actual: SourceDocumentCompilation;
  canonical: SourceDocumentCompilation;
  source: StorySource;
  label: '来源' | '先前来源' | '冲突来源';
}) {
  const actualIdentity = {
    sourceId: input.actual.sourceId,
    sourceName: input.actual.sourceName,
    sourceClass: input.actual.sourceClass,
    snapshotId: input.actual.snapshotId,
    authority: input.actual.authority,
    lineageStatus: input.actual.lineageStatus,
    upstreamSourceIds: input.actual.upstreamSourceIds,
    readSummary: input.actual.readSummary,
    evidenceLocators: input.actual.evidenceLocators,
    immutableBlocks: immutableBlockProjection(input.actual),
  };
  const canonicalIdentity = {
    sourceId: input.source.sourceId,
    sourceName: input.source.sourceName,
    sourceClass: input.source.sourceClass,
    snapshotId: input.source.snapshotId,
    authority: input.source.authority,
    lineageStatus: input.canonical.lineageStatus,
    upstreamSourceIds: input.source.upstreamSourceIds,
    readSummary: input.canonical.readSummary,
    evidenceLocators: input.canonical.evidenceLocators,
    immutableBlocks: immutableBlockProjection(input.canonical),
  };
  if (canonicalModelingJson(actualIdentity) !== canonicalModelingJson(canonicalIdentity)) {
    throw new Error(`${input.label}身份或不可修改字段与当前故事不一致`);
  }
}

export function createGuanyijiaStandardizationStory(input: {
  policyDocuments?: readonly DemoPolicyDocumentInput[];
  /** Internal seam for the repository-owned default snapshot and its tests. */
  pinnedSourceSnapshot?: readonly SourceDocumentCompilation[];
} = {}): GuanyijiaStandardizationStory {
  const policyDocuments = validateAndClonePolicyDocuments(input.policyDocuments ?? defaultGuanyijiaPolicyDocuments);
  const policySnapshot = policySnapshotId(policyDocuments);
  const semanticaSnapshot = `guanyijia-semantica-demo-${sha256HexSync(policySnapshot).slice(0, 16)}`;
  const realSourceSnapshotIds = getGuanyijiaFrozenRealSourceSnapshotIds();
  const sources: StorySource[] = [
    {
      sourceId: 'guanyijia_mysql', sourceName: '管伊佳部署数据库', sourceClass: 'REAL',
      snapshotId: realSourceSnapshotIds.database, authority: 'PRIMARY', lineageStatus: 'ROOT', upstreamSourceIds: [],
    },
    {
      sourceId: 'guanyijia_github', sourceName: 'jshERP 源码', sourceClass: 'REAL',
      snapshotId: realSourceSnapshotIds.repository, authority: 'CORROBORATING', lineageStatus: 'ROOT', upstreamSourceIds: [],
    },
    {
      sourceId: 'guanyijia_official_docs', sourceName: '管伊佳官方核心文档', sourceClass: 'REAL',
      snapshotId: realSourceSnapshotIds.officialDocs, authority: 'AUXILIARY', lineageStatus: 'ROOT', upstreamSourceIds: [],
    },
    {
      sourceId: 'guanyijia_demo_policy', sourceName: '管伊佳演示制度 Markdown', sourceClass: 'DEMO_POLICY',
      snapshotId: policySnapshot, authority: 'AUXILIARY', lineageStatus: 'ROOT', upstreamSourceIds: [],
    },
    {
      sourceId: 'guanyijia_semantica_demo', sourceName: '管伊佳演示制度术语图', sourceClass: 'DERIVED',
      snapshotId: semanticaSnapshot, authority: 'DERIVED', lineageStatus: 'DERIVED_VALID',
      upstreamSourceIds: ['guanyijia_demo_policy'],
    },
  ];

  const compileFresh = (compileInput: {
    sourceId: string;
    priorCompilations: SourceDocumentCompilation[];
  }) => {
    const source = sources.find((candidate) => candidate.sourceId === compileInput.sourceId);
    if (!source) throw new Error(`未知管伊佳来源：${compileInput.sourceId}`);
    if (source.sourceId === 'guanyijia_mysql') {
      return compileMysqlSource({ source, priorCompilations: compileInput.priorCompilations });
    }
    if (source.sourceId === 'guanyijia_github') {
      return compileGithubSource({ source, priorCompilations: compileInput.priorCompilations });
    }
    if (source.sourceId === 'guanyijia_official_docs') {
      return compileOfficialDocsSource({ source, priorCompilations: compileInput.priorCompilations });
    }
    if (source.sourceId === 'guanyijia_demo_policy') {
      return compileDemoPolicySource({
        source, priorCompilations: compileInput.priorCompilations, policyDocuments,
      });
    }
    if (source.sourceId === 'guanyijia_semantica_demo') {
      const expectedPolicySnapshotId = sources.find((candidate) => candidate.sourceId === 'guanyijia_demo_policy')?.snapshotId;
      if (!expectedPolicySnapshotId) throw new Error('演示制度来源身份缺失');
      return compileSemanticaSource({
        source, priorCompilations: compileInput.priorCompilations, policyDocuments, expectedPolicySnapshotId,
      });
    }
    throw new Error(`来源编译尚未实现：${source.sourceId}`);
  };

  // The default five-source demo always starts from the repository-owned
  // Bundle. Browser storage only mirrors it; normal runs never compile or
  // re-read DDL, code, documents, or triples. Custom policy input is the sole
  // explicit maintenance/test path that can derive a new local compilation.
  let sourceSnapshot: SourceDocumentCompilation[] | undefined;
  const getSourceSnapshot = () => {
    if (sourceSnapshot) return sourceSnapshot;
    const defaultPinned = input.policyDocuments === undefined
      ? loadPinnedGuanyijiaFiveSourceSnapshot().compilations
      : undefined;
    const pinnedInput = input.pinnedSourceSnapshot ?? defaultPinned;
    if (pinnedInput) {
      const pinned = clone([...pinnedInput]);
      if (pinned.length !== sources.length || pinned.some((compilation, index) => (
        compilation.sourceId !== sources[index]?.sourceId
          || compilation.snapshotId !== sources[index]?.snapshotId
      ))) {
        throw new Error('固定来源快照与当前标准化流程身份不一致');
      }
      sourceSnapshot = loadOrBuildSourceSnapshot({
        key: `linguan:guanyijia:source-snapshot:${policySnapshot}`,
        storyKey: 'guanyijia-five-source-v1',
        sourceIds: sources.map((source) => source.sourceId),
        sourceSnapshots: sources.map((source) => source.snapshotId),
        pinned,
        build: () => { throw new Error('固定来源快照不允许运行时重新编译'); },
      }).value;
      return sourceSnapshot;
    }
    const cached = loadOrBuildSourceSnapshot({
      key: `linguan:guanyijia:source-snapshot:${policySnapshot}`,
      storyKey: 'guanyijia-five-source-v1',
      sourceIds: sources.map((source) => source.sourceId),
      sourceSnapshots: sources.map((source) => source.snapshotId),
      build: () => {
        const built: SourceDocumentCompilation[] = [];
        for (const source of sources) {
          built.push(compileFresh({ sourceId: source.sourceId, priorCompilations: built }));
        }
        return built;
      },
    });
    sourceSnapshot = cached.value;
    return sourceSnapshot;
  };

  const compile = (compileInput: {
    sourceId: string;
    priorCompilations: SourceDocumentCompilation[];
  }) => {
    const sourceIndex = sources.findIndex((source) => source.sourceId === compileInput.sourceId);
    const snapshot = getSourceSnapshot();
    const cachedPriors = snapshot.slice(0, sourceIndex);
    const sameCachedPrefix = sourceIndex >= 0
      && compileInput.priorCompilations.length === cachedPriors.length
      && compileInput.priorCompilations.every((prior, index) => (
        canonicalModelingJson(prior) === canonicalModelingJson(cachedPriors[index])
      ));
    if (sameCachedPrefix) return clone(snapshot[sourceIndex]!);
    return compileFresh(compileInput);
  };

  const validateSourceRevisions = (sourceRevisions: ConflictSourceRevision[]) => {
    const validatedCompilations: SourceDocumentCompilation[] = [];
    const seenSourceIds = new Set<string>();
    for (let index = 0; index < sourceRevisions.length; index += 1) {
      const revision = sourceRevisions[index]!;
      const expectedSource = sources[index];
      if (!expectedSource || revision.compilation.sourceId !== expectedSource.sourceId
        || seenSourceIds.has(revision.compilation.sourceId)) {
        throw new Error('冲突来源revision必须按当前故事顺序组成唯一前缀');
      }
      assertCompilationBoundary({
        actual: revision.compilation,
        canonical: compile({
          sourceId: revision.compilation.sourceId,
          priorCompilations: validatedCompilations,
        }),
        source: expectedSource,
        label: '冲突来源',
      });
      seenSourceIds.add(revision.compilation.sourceId);
      validatedCompilations.push(revision.compilation);
    }
    return sourceRevisions;
  };

  return {
    listSources() { return clone(sources); },
    compileSource: compile,
    reviseCompilation(reviseInput) {
      const source = sources.find((candidate) => candidate.sourceId === reviseInput.compilation.sourceId);
      if (!source || source.snapshotId !== reviseInput.compilation.snapshotId) {
        throw new Error('待修订来源编译不属于当前管伊佳故事版本');
      }
      const validatedPriors: SourceDocumentCompilation[] = [];
      for (const prior of reviseInput.priorCompilations) {
        const priorSource = sources.find((candidate) => candidate.sourceId === prior.sourceId);
        if (!priorSource) throw new Error('先前来源身份或不可修改字段与当前故事不一致');
        assertCompilationBoundary({
          actual: prior,
          canonical: compile({ sourceId: prior.sourceId, priorCompilations: validatedPriors }),
          source: priorSource,
          label: '先前来源',
        });
        validatedPriors.push(prior);
      }
      assertCompilationBoundary({
        actual: reviseInput.compilation,
        canonical: compile({ sourceId: source.sourceId, priorCompilations: validatedPriors }),
        source,
        label: '来源',
      });
      return reviseSourceCompilation({
        ...reviseInput, priorCompilations: validatedPriors,
        conflictDefinitions: createConflictDefinitions(policyDocuments),
      });
    },
    buildConflictHunk(hunkInput) {
      return projectConflictHunk({
        ...hunkInput,
        sourceRevisions: validateSourceRevisions(hunkInput.sourceRevisions),
        sources,
        definitions: createConflictDefinitions(policyDocuments),
      });
    },
    previewConflictResolution(previewInput) {
      return projectConflictResolution({
        ...previewInput,
        sourceRevisions: validateSourceRevisions(previewInput.sourceRevisions),
        sources,
        definitions: createConflictDefinitions(policyDocuments),
      });
    },
    validateConflictResolutionArtifactProjection(artifact) {
      const definition = createConflictDefinitions(policyDocuments).find((candidate) => (
        candidate.conflictId === artifact.hunk.conflictId
      ));
      if (!definition || artifact.sourceId !== definition.introducedBySourceId
        || !definition.allowedStrategies.includes(artifact.strategy)
        || canonicalModelingJson(artifact.hunk.affectedObjectRefs)
          !== canonicalModelingJson(definition.affectedObjectRefs)) {
        throw new Error('冲突决定Artifact与Story定义身份不一致');
      }
      const assertSide = (
        side: typeof artifact.hunk.current,
        expected: typeof definition.current,
        role: typeof side.role,
      ) => {
        const value = typeof side.block.value === 'object' && side.block.value !== null
          && !Array.isArray(side.block.value) && typeof side.block.value.normalized === 'string'
          ? side.block.value.normalized
          : null;
        if (side.role !== role || side.sourceId !== expected.sourceId
          || side.block.stableCode !== expected.stableCode || value !== expected.normalizedValue
          || canonicalModelingJson(side.block.evidenceRefs) !== canonicalModelingJson(expected.evidenceRefs)
          || side.assertion.assertionId !== side.block.blockId
          || side.assertion.section !== side.block.section
          || canonicalModelingJson(side.assertion.evidenceRefs)
            !== canonicalModelingJson(side.block.evidenceRefs)) {
          throw new Error('冲突决定Artifact的Hunk side无法通过Story定义复算');
        }
      };
      assertSide(artifact.hunk.current, definition.current, 'CURRENT');
      assertSide(artifact.hunk.incoming, definition.incoming, 'INCOMING');
      const seenCorroborating = new Set<string>();
      for (const side of artifact.hunk.corroborating) {
        const expected = definition.corroborating.find((candidate) => candidate.sourceId === side.sourceId);
        if (!expected || seenCorroborating.has(side.sourceId)) {
          throw new Error('冲突决定Artifact包含未知或重复Corroborating side');
        }
        seenCorroborating.add(side.sourceId);
        assertSide(side, expected, 'CORROBORATING');
      }
      const expected = reprojectConflictResolution({ hunk: artifact.hunk, strategy: artifact.strategy });
      const actual = {
        hunk: artifact.hunk,
        strategy: artifact.strategy,
        result: artifact.result,
        structuredPatch: artifact.structuredPatch,
        markdownDiff: artifact.markdownDiff,
        provenanceSources: artifact.provenanceSources,
        previewSha256: artifact.previewSha256,
      };
      if (canonicalModelingJson(actual) !== canonicalModelingJson(expected)) {
        throw new Error('冲突决定Artifact无法通过Story纯投影复算');
      }
    },
    listConflictDefinitions() { return clone(createConflictDefinitions(policyDocuments)); },
  };
}

export { defaultGuanyijiaPolicyDocuments } from './policy-documents.ts';
export type * from './types.ts';
