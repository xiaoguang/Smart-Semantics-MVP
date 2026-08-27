import { sha256HexSync } from '../ai-modeling/sha256.ts';
import { canonicalModelingJson } from '../modeling-document-bridge/standard-markdown.ts';
import { buildHumanReadableEvidence, buildSourceDocumentTraceLinks, type HumanReadableEvidence, type SourceDocumentTraceLink } from '../data-standardization/human-readable-evidence.ts';
import { pinnedGuanyijiaFiveSourceSnapshotCore } from './pinned-source-snapshot.generated.ts';
import type { SourceDocumentCompilation, StorySource } from './types.ts';

export type SourceSnapshotIdentity = Pick<StorySource,
  'sourceId' | 'sourceName' | 'sourceClass' | 'snapshotId' | 'authority' | 'lineageStatus' | 'upstreamSourceIds'>;

export type PinnedSourceSnapshotCore = {
  schemaVersion: 1;
  storyKey: 'guanyijia-five-source-v1';
  compilerVersion: 'content-first-v1';
  sourceIdentities: SourceSnapshotIdentity[];
  compilations: SourceDocumentCompilation[];
  bundleSha256: string;
};

export type PinnedSourceSnapshotBundle = PinnedSourceSnapshotCore & {
  traceLinks: Record<string, SourceDocumentTraceLink[]>;
  evidenceExcerpts: Record<string, HumanReadableEvidence[]>;
};

function clone<T>(value: T): T {
  return structuredClone(value);
}

function coreForHash(bundle: PinnedSourceSnapshotCore) {
  return {
    schemaVersion: bundle.schemaVersion,
    storyKey: bundle.storyKey,
    compilerVersion: bundle.compilerVersion,
    sourceIdentities: bundle.sourceIdentities,
    compilations: bundle.compilations,
  };
}

function validateCore(bundle: PinnedSourceSnapshotCore): PinnedSourceSnapshotCore {
  if (bundle.schemaVersion !== 1 || bundle.storyKey !== 'guanyijia-five-source-v1'
    || bundle.compilerVersion !== 'content-first-v1' || bundle.compilations.length !== 5
    || bundle.sourceIdentities.length !== 5) {
    throw new Error('演示快照不可用：Bundle 结构不完整');
  }
  const expected = `sha256:${sha256HexSync(canonicalModelingJson(coreForHash(bundle)))}`;
  if (bundle.bundleSha256 !== expected) throw new Error('演示快照不可用：Bundle 校验失败');
  for (const [index, compilation] of bundle.compilations.entries()) {
    const identity = bundle.sourceIdentities[index];
    if (!identity || compilation.sourceId !== identity.sourceId || compilation.snapshotId !== identity.snapshotId) {
      throw new Error('演示快照不可用：来源身份不一致');
    }
  }
  return clone(bundle);
}

function enrich(core: PinnedSourceSnapshotCore): PinnedSourceSnapshotBundle {
  const traceLinks: Record<string, SourceDocumentTraceLink[]> = {};
  const evidenceExcerpts: Record<string, HumanReadableEvidence[]> = {};
  for (const compilation of core.compilations) {
    traceLinks[compilation.sourceId] = buildSourceDocumentTraceLinks(compilation);
    evidenceExcerpts[compilation.sourceId] = compilation.blocks.flatMap((block) => {
      const evidence = buildHumanReadableEvidence(compilation, block.blockId);
      return evidence ? [evidence] : [];
    });
  }
  return { ...core, traceLinks, evidenceExcerpts };
}

export const pinnedGuanyijiaFiveSourceSnapshot = enrich(validateCore(pinnedGuanyijiaFiveSourceSnapshotCore));

export function loadPinnedGuanyijiaFiveSourceSnapshot(): PinnedSourceSnapshotBundle {
  return clone(pinnedGuanyijiaFiveSourceSnapshot);
}
