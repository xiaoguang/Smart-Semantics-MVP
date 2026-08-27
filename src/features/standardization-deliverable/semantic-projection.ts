import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type { ConflictResolutionArtifact } from '../guanyijia-standardization-story/types.ts';
import {
  modelingDocumentSemanticPayloadSha256,
  projectModelingDocument,
} from '../modeling-document-projector/index.ts';
import {
  canonicalModelingJson,
  renderStandardModelingMarkdown,
} from '../modeling-document-bridge/standard-markdown.ts';
import type {
  ModelingDocumentArtifact,
  ModelingDocumentSemanticPayload,
} from '../modeling-document-bridge/types.ts';
import type {
  ModelingProjector,
  ProtectedBaseline,
  SemanticPathDifference,
  ZeroDeltaReport,
} from './types.ts';

const clone = <T,>(value: T): T => structuredClone(value);

function canonicalComparable(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(canonicalComparable).sort((left, right) => (
      canonicalModelingJson(left).localeCompare(canonicalModelingJson(right), 'en')
    ));
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right, 'en'))
      .map(([key, child]) => [key, canonicalComparable(child)]));
  }
  return value;
}

export function semanticPathDifferences(before: unknown, after: unknown): SemanticPathDifference[] {
  const differences: SemanticPathDifference[] = [];
  const visit = (left: unknown, right: unknown, path: string) => {
    if (canonicalModelingJson(left) === canonicalModelingJson(right)) return;
    if (Array.isArray(left) && Array.isArray(right)) {
      const normalizedLeft = canonicalComparable(left) as unknown[];
      const normalizedRight = canonicalComparable(right) as unknown[];
      const length = Math.max(normalizedLeft.length, normalizedRight.length);
      for (let index = 0; index < length; index += 1) {
        visit(normalizedLeft[index], normalizedRight[index], `${path}[${index}]`);
      }
      return;
    }
    if (left && right && typeof left === 'object' && typeof right === 'object') {
      const leftRecord = left as Record<string, unknown>;
      const rightRecord = right as Record<string, unknown>;
      const keys = [...new Set([...Object.keys(leftRecord), ...Object.keys(rightRecord)])].sort();
      for (const key of keys) visit(leftRecord[key], rightRecord[key], path ? `${path}.${key}` : key);
      return;
    }
    differences.push({ path, ...(left !== undefined ? { before: left } : {}), ...(right !== undefined ? { after: right } : {}) });
  };
  visit(canonicalComparable(before), canonicalComparable(after), '$');
  return differences;
}

function evidenceFor(resolution: ConflictResolutionArtifact, objectId: string) {
  const sides = [resolution.hunk.current, resolution.hunk.incoming, ...resolution.hunk.corroborating];
  return [...new Set(sides.filter((side) => side.block.affectedObjectRefs.some((ref) => ref.objectId === objectId))
    .flatMap((side) => [...side.block.evidenceRefs, ...side.assertion.evidenceRefs]))].sort();
}

function removeByCode<T extends { code: string }>(items: T[], code: string) {
  const index = items.findIndex((item) => item.code === code);
  if (index >= 0) items.splice(index, 1);
}

function applyResolutionPatches(
  baseline: ModelingDocumentSemanticPayload,
  resolutions: ConflictResolutionArtifact[],
) {
  const result = clone(baseline);
  for (const resolution of resolutions) {
    for (const operation of resolution.structuredPatch.objectDispositionOperations) {
      const { objectRef, disposition } = operation.value;
      const code = objectRef.objectId;
      const evidenceRefs = evidenceFor(resolution, code);
      if (disposition === 'KEEP') {
        removeByCode(result.exclusions, code);
        removeByCode(result.pendingAssets, `deferred:${objectRef.kind}:${code}`);
        removeByCode(result.pendingAssets, `candidate:${objectRef.kind}:${code}`);
        continue;
      }
      if (disposition === 'EXCLUDED') {
        if (!result.exclusions.some((item) => item.code === code)) {
          result.exclusions.push({
            code, name: resolution.hunk.title,
            reason: `标准化决定 ${resolution.resolutionId} 将该对象排除。`,
            decision: resolution.reason, evidenceRefs,
          });
        }
        continue;
      }
      if (disposition === 'DEFERRED') {
        const pendingCode = `deferred:${objectRef.kind}:${code}`;
        if (!result.pendingAssets.some((item) => item.code === pendingCode)) {
          result.pendingAssets.push({
            code: pendingCode, name: resolution.hunk.title,
            reason: `标准化决定 ${resolution.resolutionId} 延后该对象。`, evidenceRefs,
          });
        }
        continue;
      }
      if (disposition === 'CANDIDATE_ONLY') {
        const alreadyCandidate = objectRef.kind === 'RULE'
          && result.ruleCandidates.some((item) => item.code === code);
        const pendingCode = `candidate:${objectRef.kind}:${code}`;
        if (!alreadyCandidate && !result.pendingAssets.some((item) => item.code === pendingCode)) {
          result.pendingAssets.push({
            code: pendingCode, name: resolution.hunk.title,
            reason: `标准化决定 ${resolution.resolutionId} 仅登记为候选。`, evidenceRefs,
          });
        }
      }
      // KEEP_WITHOUT_ENUM_MEMBER intentionally preserves an existing dimension while adding no enum member.
    }
  }
  return result;
}

function counts(payload: ModelingDocumentSemanticPayload) {
  return {
    entities: payload.entities.length,
    events: payload.events.length,
    fields: payload.fields.length,
    relations: payload.relations.length,
    dimensions: payload.dimensions.length,
    metrics: payload.metrics.length,
    hierarchies: payload.hierarchies.length,
    rules: payload.ruleCandidates.length,
    aliases: payload.aliases.length,
    timeRules: payload.timeRules.length,
    pendingAssets: payload.pendingAssets.length,
    exclusions: payload.exclusions.length,
  };
}

function createArtifact(input: {
  baseline: ProtectedBaseline;
  run: Parameters<ModelingProjector['project']>[0]['run'];
  resolutions: ConflictResolutionArtifact[];
  mergedDocument: Parameters<ModelingProjector['project']>[0]['mergedDocument'];
  semanticPayload: ModelingDocumentSemanticPayload;
}) {
  const semanticSha256 = modelingDocumentSemanticPayloadSha256(input.semanticPayload);
  const identityCore = canonicalModelingJson({
    schemaVersion: 1,
    runId: input.run.runId,
    scenarioKey: input.run.scenarioKey,
    sources: input.run.sources.map(({ sourceId, documentId, documentRevision }) => ({
      sourceId, documentId, documentRevision,
    })),
    decisions: input.resolutions.map(({ hunk, resolutionId, previewSha256 }) => ({
      conflictId: hunk.conflictId, resolutionId, previewSha256,
    })),
    sections: input.mergedDocument.sections,
    semanticSha256,
  });
  const inputFingerprint = sha256HexSync(identityCore);
  const artifactId = `artifact-standardization-${inputFingerprint.slice(0, 16)}`;
  const artifactBase = {
    artifactId,
    revision: 1,
    derivedFromArtifactId: input.baseline.artifact.artifactId,
    projectId: input.run.projectId,
    documentCode: `standardization_${input.run.scenarioKey}`,
    title: '标准化定版资料',
    origin: 'STANDARDIZATION' as const,
    sourceBatch: {
      batchId: input.run.batchId,
      fingerprint: inputFingerprint,
      snapshotIds: [...input.mergedDocument.sourceSnapshotIds],
    },
    delta: {
      kind: 'DECISION' as const,
      addedSourceIds: input.run.sources.map((source) => source.sourceId),
      addedSnapshotIds: [...input.mergedDocument.sourceSnapshotIds],
      addedEvidenceRefs: [],
      addedClaimIds: [],
      resolvedIssueIds: input.resolutions.map((resolution) => resolution.hunk.conflictId),
    },
    sections: clone(input.mergedDocument.sections),
    assertions: [],
    semanticPayload: { data: clone(input.semanticPayload), sha256: semanticSha256 },
    review: undefined,
    generation: {
      runId: input.run.runId,
      agentVersion: 'standardization-deliverable/1',
      promptVersion: 'deterministic-structured-projection/1',
      inputFingerprint,
    },
    approval: { authorUserId: input.run.createdBy },
    validation: { errors: [], warnings: [], gaps: [] },
    status: 'FROZEN' as const,
    audit: [],
    createdAt: input.run.createdAt,
    updatedAt: input.run.createdAt,
  };
  const markdown = renderStandardModelingMarkdown(artifactBase);
  return {
    ...artifactBase,
    markdown: {
      fileName: `${artifactBase.documentCode}.md`, content: markdown,
      sha256: sha256HexSync(markdown), byteLength: new TextEncoder().encode(markdown).byteLength,
    },
  } satisfies ModelingDocumentArtifact;
}

export function createStandardizationModelingProjector(): ModelingProjector {
  return {
    async project({ run, baseline, resolutions, mergedDocument }) {
      const protectedPayload = baseline.artifact.semanticPayload?.data;
      if (!protectedPayload) throw new Error('受保护基线缺少语义载荷');
      const semanticPayload = applyResolutionPatches(protectedPayload, resolutions);
      const artifact = createArtifact({ baseline, run, resolutions, mergedDocument, semanticPayload });
      const semanticDifferences = semanticPathDifferences(protectedPayload, semanticPayload);
      const projection = projectModelingDocument(artifact, baseline.catalogData, baseline.projectionContext);
      const zeroDeltaReport: ZeroDeltaReport = {
        schemaVersion: 1,
        semanticDifferences,
        catalogChanges: clone(projection.changes),
        counts: counts(semanticPayload),
      };
      return { artifact, semanticPayload, zeroDeltaReport };
    },
  };
}
