import { sha256HexSync } from '../ai-modeling/sha256.ts';
import type {
  SourceReviewEvidence,
  SourceReviewTrace,
} from '../guanyijia-evidence-factory/source-review-document.ts';
import { pinnedDemoContentPublication } from './pinned-demo-content.generated.ts';

export type DemoContentOrigin =
  | 'SNAPSHOT_REFERENCE'
  | 'SOURCE_NATIVE'
  | 'DEMO_AUTHORED'
  | 'DERIVED_DEMO';

type DemoContentSnapshotId =
  | 'guanyijia-demo-content-v5-20260826'
  | 'guanyijia-demo-content-v6-20260826'
  | 'guanyijia-demo-content-v7-20260827';

export type DemoContentRunBinding = {
  runId: string;
  storyKey: 'guanyijia-five-source-v1';
  contentSnapshotId: DemoContentSnapshotId;
  contentSha256: `sha256:${string}`;
  sourceBindings: Array<{
    sourceId: string;
    formalSnapshotId: string;
    contentSourceSnapshotId: string;
    origin: DemoContentOrigin;
  }>;
};

/** Immutable V6 descriptor, retained verbatim in the generated package. */
export type FrozenStandardSection = {
  sectionId: string;
  heading: string;
  purpose: string;
  markdownAnchor: string;
  claimIds: string[];
};

/** Adapter shape consumed by the existing source-review document runtime. */
export type DemoContentStandardSection = {
  index: number;
  sectionId: string;
  title: string;
  purpose: string;
  markdownAnchor: string;
  claimIds: string[];
};

export type DemoContentReviewClaim = {
  claimId: string;
  section: number;
  sectionId: string;
  sectionPurpose: string;
  title: string;
  statement: string;
  focusIdentifiers: string[];
  markdownAnchor: string;
  evidenceRefs: string[];
  affectedObjectRefs: string[];
  [key: string]: unknown;
};

export type DemoContentSourceReview = {
  schemaVersion: 1;
  documentKind?: 'SOURCE' | 'MERGED';
  sourceId: string;
  snapshotId: string;
  title: string;
  coverageLabel: string;
  markdown: string;
  markdownSha256: `sha256:${string}`;
  evidence: SourceReviewEvidence[];
  traceLinks: SourceReviewTrace[];
  contentOrigin: DemoContentOrigin;
  /** V6 rich documents only. V5 deliberately stays on its legacy shape. */
  standardSections?: DemoContentStandardSection[];
  claims?: DemoContentReviewClaim[];
  sections?: Record<string, unknown>;
  [key: string]: unknown;
};

export type DemoContentSourceConfiguration = {
  sourceId: string;
  formalSnapshotId: string;
  contentSourceSnapshotId: string;
  origin: DemoContentOrigin;
  sourceType: string;
  location: string;
  scope: string;
  credentialLabel: string;
};

type FormalSource = { sourceId: string; snapshotId: string };
/**
 * Generated publications are JSON-shaped and intentionally permit extra
 * frozen fields. Keep the reader-facing fields explicit: an index signature
 * on DemoContentSourceReview would otherwise turn them back into `unknown`
 * after Omit/intersection.
 */
type PackagedReview = {
  schemaVersion: 1;
  documentKind?: 'SOURCE' | 'MERGED';
  sourceId: string;
  snapshotId: string;
  title: string;
  coverageLabel: string;
  markdown: string;
  markdownSha256: `sha256:${string}`;
  evidence: SourceReviewEvidence[];
  traceLinks: SourceReviewTrace[];
  contentOrigin?: DemoContentOrigin;
  standardSections?: FrozenStandardSection[];
  claims?: DemoContentReviewClaim[];
  sections?: Record<string, unknown>;
  [key: string]: unknown;
};
type PublicationSource = {
  sourceId: string;
  formalSnapshotId: string;
  contentSourceSnapshotId: string;
  origin: DemoContentOrigin;
  review: PackagedReview;
};
type PublicationConfigurationSource = PublicationSource & {
  sourceType: string;
  location: string;
  scope: string;
  credentialLabel: string;
};
type ContentPublication = {
  schemaVersion: number;
  storyKey: string;
  contentSnapshotId: string;
  contentSha256: string;
  sources: PublicationConfigurationSource[];
  mergedDocument?: PackagedReview;
  legacyPublications?: ContentPublication[];
};
type ContentPin = {
  snapshotId: DemoContentSnapshotId;
  contentSha256: `sha256:${string}`;
};

const contentError = '冻结内容快照校验失败';
const v5ContentPin = {
  snapshotId: 'guanyijia-demo-content-v5-20260826',
  contentSha256: 'sha256:2663cc64b19b5aa149ae0dfcea2a1917e9295ea490e0a59b422558cbcd009a2c',
} as const satisfies ContentPin;

/**
 * V6 is a separately frozen rich-reading publication. Keeping this exact pin
 * alongside V5 lets persisted runs resolve their original material rather
 * than being silently upgraded when the active package changes.
 */
const v6ContentPin = {
  snapshotId: 'guanyijia-demo-content-v6-20260826',
  contentSha256: 'sha256:6da765901357f4b0856e3b1c5aaab159aec75c895b60b1bb2ea11539997f8180',
} as const satisfies ContentPin;

const expectedSources = [
  ['guanyijia_mysql', '20260813T032528Z-abb0502c7d79'],
  ['guanyijia_github', '20260813032126Z-5821d0ece9b1'],
  ['guanyijia_official_docs', 'gyjerp-official-docs-20260813T031656Z'],
  ['guanyijia_demo_policy', 'guanyijia-demo-policy-f6c6d209ffe3fd53'],
  ['guanyijia_semantica_demo', 'guanyijia-semantica-demo-ff948845dc5bd778'],
] as const;
const richSectionIds = [
  'OVERVIEW', 'GOAL', 'OBJECT', 'ACTIVITY', 'FIELD', 'RELATION', 'METRIC', 'QUESTION', 'UNRESOLVED',
] as const;

function fail(): never {
  throw new Error(contentError);
}

function clone<T>(value: T): T {
  return structuredClone(value);
}

function sha(value: string): `sha256:${string}` {
  return `sha256:${sha256HexSync(value)}`;
}

function readableHeading(heading: string): string {
  return heading.replace(/^\d+\.\s*/u, '').trim();
}

/**
 * Adapts V6's frozen rich shape to SourceReviewSection. The raw package stays
 * untouched; this read-only projection adds only the legacy numeric index and
 * display title required by the existing reader.
 */
export function normalizeDemoContentSourceReview(input: PackagedReview & {
  contentOrigin: DemoContentOrigin;
}): DemoContentSourceReview {
  const review = clone(input);
  const standardSections = review.standardSections?.map((section, index) => ({
    index: index + 1,
    sectionId: section.sectionId,
    title: readableHeading(section.heading),
    purpose: section.purpose,
    markdownAnchor: section.markdownAnchor,
    claimIds: clone(section.claimIds),
  }));
  return {
    ...review,
    standardSections,
    claims: review.claims ? clone(review.claims) : undefined,
    contentOrigin: input.contentOrigin,
  };
}

/**
 * The generated publication is a committed, immutable browser asset. Capture
 * every append-only snapshot pin when this module loads, before a caller can
 * bind a run. This lets a newly generated rich publication (V7 and later)
 * participate without a second, version-specific reader path while still
 * rejecting any snapshot that is not part of the published chain.
 */
function frozenPublicationPins(publication: ContentPublication): Map<string, ContentPin> {
  const pins = new Map<string, ContentPin>();
  const publications = new Set<ContentPublication>();
  const snapshots = new Set<string>();

  const visit = (candidate: ContentPublication): void => {
    if (publications.has(candidate) || snapshots.has(candidate.contentSnapshotId)) fail();
    publications.add(candidate);
    snapshots.add(candidate.contentSnapshotId);
    if (!candidate.contentSnapshotId || typeof candidate.contentSha256 !== 'string') fail();
    pins.set(candidate.contentSnapshotId, {
      snapshotId: candidate.contentSnapshotId as DemoContentSnapshotId,
      contentSha256: candidate.contentSha256 as `sha256:${string}`,
    });
    for (const legacy of candidate.legacyPublications ?? []) visit(legacy);
  };

  visit(publication);
  return pins;
}

const frozenContentPins = frozenPublicationPins(
  pinnedDemoContentPublication as unknown as ContentPublication,
);

function pinForSnapshot(snapshotId: string): ContentPin | undefined {
  if (snapshotId === v5ContentPin.snapshotId) return v5ContentPin;
  if (snapshotId === v6ContentPin.snapshotId) return v6ContentPin;
  return frozenContentPins.get(snapshotId);
}

function isRichPublication(publication: ContentPublication): boolean {
  return publication.contentSnapshotId !== v5ContentPin.snapshotId;
}

function validateReviewPayload(review: PackagedReview): Map<string, SourceReviewEvidence> {
  if (review.schemaVersion !== 1 || typeof review.markdown !== 'string'
    || sha(review.markdown) !== review.markdownSha256
    || !Array.isArray(review.evidence) || !Array.isArray(review.traceLinks)
    || !review.markdown || review.evidence.length === 0 || review.traceLinks.length === 0) fail();
  const evidence = new Map(review.evidence.map((entry) => [entry.evidenceRef, entry]));
  if (evidence.size !== review.evidence.length
    || review.evidence.some((entry) => sha(entry.excerpt) !== entry.excerptSha256)) fail();
  if (review.traceLinks.some((trace) => {
    const claim = trace.claim;
    const stableClaimTrace = Boolean(trace.claimId?.trim());
    const explicitReadableClaim = Boolean(trace.section !== undefined && trace.sectionId && trace.sectionPurpose
      && claim?.title && claim.statement);
    return (!stableClaimTrace && !explicitReadableClaim
      && (!review.markdown.includes(trace.linePrefix)
        || !review.markdown.includes(`<!-- ${trace.markdownAnchor} -->`)))
      || trace.evidenceRefs.some((evidenceRef) => !evidence.has(evidenceRef));
  })) fail();

  return evidence;
}

function hasStableClaimTraces(review: PackagedReview): boolean {
  return review.traceLinks.some((trace) => trace.claimId !== undefined);
}

function arraysMatch(left: readonly string[], right: readonly string[]): boolean {
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function markdownPreambleForStructuredReview(review: PackagedReview): string {
  const firstSection = review.markdown.search(/^##\s+\d+\.\s+/mu);
  if (firstSection < 0) fail();
  const preamble = review.markdown.slice(0, firstSection).trimEnd();
  if (!preamble || !preamble.startsWith(`# ${review.title}`)) fail();
  return preamble;
}

/**
 * V7 is the first generated package that carries stable trace identities.
 * Its Markdown is not independent input: the saved chapter contexts and
 * claims serialize it deterministically. Validating this at the package seam
 * keeps a tampered browser asset from re-associating a claim with another
 * trace, source excerpt, or chapter.
 */
function validateStableRichReview(
  review: PackagedReview,
  expectedKind: 'SOURCE' | 'MERGED',
  evidence: ReadonlyMap<string, SourceReviewEvidence>,
): void {
  if (review.documentKind !== expectedKind || !Array.isArray(review.standardSections)
    || !Array.isArray(review.claims) || !review.sections || typeof review.sections !== 'object'
    || Array.isArray(review.sections) || review.standardSections.length !== richSectionIds.length
    || review.claims.length === 0) fail();

  const sectionClaims = new Set<string>();
  const sectionAnchors = new Set<string>();
  for (const [index, section] of review.standardSections.entries()) {
    if (section.sectionId !== richSectionIds[index] || !section.heading?.trim()
      || !section.purpose?.trim() || !section.markdownAnchor?.trim()
      || sectionAnchors.has(section.markdownAnchor) || !Array.isArray(section.claimIds)
      || typeof review.sections[section.sectionId] !== 'string'
      || !String(review.sections[section.sectionId]).trim()) fail();
    sectionAnchors.add(section.markdownAnchor);
    for (const claimId of section.claimIds) {
      if (!claimId?.trim() || sectionClaims.has(claimId)) fail();
      sectionClaims.add(claimId);
    }
  }

  const claimsById = new Map<string, DemoContentReviewClaim>();
  const claimAnchors = new Set<string>();
  for (const claim of review.claims) {
    const sectionIndex = richSectionIds.indexOf(
      claim.sectionId as (typeof richSectionIds)[number],
    );
    const section = sectionIndex < 0 ? undefined : review.standardSections[sectionIndex];
    if (!claim.claimId?.trim() || claimsById.has(claim.claimId)
      || !claim.markdownAnchor?.trim() || claimAnchors.has(claim.markdownAnchor)
      || !claim.title?.trim() || !claim.statement?.trim()
      || !Array.isArray(claim.evidenceRefs) || claim.evidenceRefs.length === 0
      || new Set(claim.evidenceRefs).size !== claim.evidenceRefs.length
      || !section || claim.section !== sectionIndex + 1
      || claim.sectionPurpose !== section.purpose
      || !sectionClaims.has(claim.claimId)
      || claim.evidenceRefs.some((evidenceRef) => !evidence.has(evidenceRef))) fail();
    claimsById.set(claim.claimId, claim);
    claimAnchors.add(claim.markdownAnchor);
  }
  if (claimsById.size !== sectionClaims.size) fail();

  const tracesByClaimId = new Map<string, SourceReviewTrace>();
  for (const trace of review.traceLinks) {
    if (!trace.claimId?.trim() || tracesByClaimId.has(trace.claimId)) fail();
    const claim = claimsById.get(trace.claimId);
    if (!claim || trace.markdownAnchor !== claim.markdownAnchor
      || trace.section !== claim.section || trace.sectionId !== claim.sectionId
      || trace.sectionPurpose !== claim.sectionPurpose
      || !arraysMatch(trace.evidenceRefs, claim.evidenceRefs)
      || trace.evidenceRefs.some((evidenceRef) => !evidence.has(evidenceRef))) fail();
    tracesByClaimId.set(trace.claimId, trace);
  }
  if (tracesByClaimId.size !== claimsById.size) fail();

  const lines = [markdownPreambleForStructuredReview(review)];
  for (const section of review.standardSections) {
    lines.push('', `## ${richSectionIds.indexOf(section.sectionId as (typeof richSectionIds)[number]) + 1}. ${readableHeading(section.heading)}`, '', String(review.sections[section.sectionId]).trim());
    for (const claimId of section.claimIds) {
      const claim = claimsById.get(claimId);
      if (!claim || tracesByClaimId.get(claimId)?.markdownAnchor !== claim.markdownAnchor) fail();
      lines.push('', `### ${claim.title}`, '', claim.statement);
    }
  }
  const canonicalMarkdown = lines.join('\n').trimEnd().concat('\n');
  if (review.markdown !== canonicalMarkdown || sha(canonicalMarkdown) !== review.markdownSha256) fail();
}

function validateReview(review: PackagedReview, source: PublicationSource, rich: boolean): void {
  if (review.sourceId !== source.sourceId || review.snapshotId !== source.formalSnapshotId) fail();

  if (!rich && source.sourceId === 'guanyijia_mysql'
    && review.markdown.length === 0 && review.evidence.length === 0 && review.traceLinks.length === 0) return;

  const evidence = validateReviewPayload(review);

  if (!rich) return;
  if (review.documentKind !== 'SOURCE' || !Array.isArray(review.standardSections)
    || !Array.isArray(review.claims) || review.standardSections.length !== richSectionIds.length) fail();
  const claimsById = new Set<string>();
  const sectionClaimIds = new Set<string>();
  for (const [index, section] of review.standardSections.entries()) {
    if (section.sectionId !== richSectionIds[index] || !section.heading || !section.purpose
      || !section.markdownAnchor || !Array.isArray(section.claimIds)) fail();
    for (const claimId of section.claimIds) {
      if (sectionClaimIds.has(claimId)) fail();
      sectionClaimIds.add(claimId);
    }
  }
  for (const claim of review.claims) {
    const expectedSection = richSectionIds.indexOf(
      claim.sectionId as (typeof richSectionIds)[number],
    );
    if (!claim.claimId || claimsById.has(claim.claimId) || expectedSection < 0
      || claim.section !== expectedSection + 1 || !claim.title || !claim.statement
      || !claim.markdownAnchor || !Array.isArray(claim.evidenceRefs) || claim.evidenceRefs.length === 0
      || !sectionClaimIds.has(claim.claimId)) fail();
    claimsById.add(claim.claimId);
    if (claim.evidenceRefs.some((evidenceRef) => !evidence.has(evidenceRef))) fail();
  }
  if (claimsById.size !== sectionClaimIds.size) fail();
  if (hasStableClaimTraces(review)) validateStableRichReview(review, 'SOURCE', evidence);
}

function validateMergedReview(review: PackagedReview, rich: boolean): void {
  if (review.documentKind !== 'MERGED') fail();
  const evidence = validateReviewPayload(review);
  if (!rich) return;
  if (!Array.isArray(review.standardSections) || review.standardSections.length !== richSectionIds.length) fail();
  if (hasStableClaimTraces(review)) validateStableRichReview(review, 'MERGED', evidence);
}

function validatePublication(publication: ContentPublication): ContentPublication {
  const pin = pinForSnapshot(publication.contentSnapshotId);
  if (!pin || publication.schemaVersion !== 1
    || publication.storyKey !== 'guanyijia-five-source-v1'
    || publication.contentSha256 !== pin.contentSha256
    || publication.sources.length !== expectedSources.length) fail();
  for (const [index, [sourceId, snapshotId]] of expectedSources.entries()) {
    const source = publication.sources[index];
    if (!source || source.sourceId !== sourceId || source.formalSnapshotId !== snapshotId
      || source.review.sourceId !== sourceId || source.review.snapshotId !== snapshotId) fail();
    validateReview(source.review, source, isRichPublication(publication));
  }
  if (isRichPublication(publication) && publication.mergedDocument) validateMergedReview(publication.mergedDocument, true);
  return publication;
}

function activePublication(): ContentPublication {
  return validatePublication(pinnedDemoContentPublication as unknown as ContentPublication);
}

function allPublications(): ContentPublication[] {
  const active = activePublication();
  const publications: ContentPublication[] = [];
  const publicationObjects = new Set<ContentPublication>();
  const snapshots = new Set<string>();

  const visit = (publication: ContentPublication): void => {
    if (publicationObjects.has(publication) || snapshots.has(publication.contentSnapshotId)) fail();
    publicationObjects.add(publication);
    snapshots.add(publication.contentSnapshotId);
    validatePublication(publication);
    publications.push(publication);
    for (const legacy of publication.legacyPublications ?? []) visit(legacy);
  };

  visit(active);
  return publications;
}

function publicationForBinding(binding: DemoContentRunBinding): ContentPublication {
  const publication = allPublications().find((candidate) => (
    candidate.contentSnapshotId === binding.contentSnapshotId
    && candidate.contentSha256 === binding.contentSha256
  ));
  if (!publication) fail();
  return publication;
}

function buildBinding(publication: ContentPublication, runId: string): DemoContentRunBinding {
  return {
    runId,
    storyKey: 'guanyijia-five-source-v1',
    contentSnapshotId: publication.contentSnapshotId as DemoContentSnapshotId,
    contentSha256: publication.contentSha256 as `sha256:${string}`,
    sourceBindings: publication.sources.map((source) => ({
      sourceId: source.sourceId,
      formalSnapshotId: source.formalSnapshotId,
      contentSourceSnapshotId: source.contentSourceSnapshotId,
      origin: source.origin,
    })),
  };
}

/** Pins a new Demo run to the currently published immutable content snapshot. */
export function bindDemoContentRun(input: {
  runId: string;
  formalSources: readonly FormalSource[];
}): DemoContentRunBinding {
  const publication = activePublication();
  if (!input.runId || input.formalSources.length !== expectedSources.length) fail();
  for (const [index, [sourceId, snapshotId]] of expectedSources.entries()) {
    const formal = input.formalSources[index];
    if (!formal || formal.sourceId !== sourceId || formal.snapshotId !== snapshotId) fail();
  }
  return clone(buildBinding(publication, input.runId));
}

/**
 * Revalidates a persisted run against the exact publication it originally
   * bound. Activating a later rich publication must never upgrade an
   * in-progress V5 or V6 run.
 */
export function validateDemoContentRunBinding(binding: DemoContentRunBinding): DemoContentRunBinding {
  if (!binding.runId || binding.storyKey !== 'guanyijia-five-source-v1') fail();
  const publication = publicationForBinding(binding);
  const expected = buildBinding(publication, binding.runId);
  if (JSON.stringify(binding) !== JSON.stringify(expected)) fail();
  return clone(expected);
}

function sourceReviewFromPublication(publication: ContentPublication, input: FormalSource): DemoContentSourceReview | undefined {
  const source = publication.sources.find((candidate) => (
    candidate.sourceId === input.sourceId && candidate.formalSnapshotId === input.snapshotId
  ));
  if (!source) return undefined;
  // V5 has no MySQL body in its sidecar; curated DDL is the deliberate legacy
  // fallback. Every later rich publication contains the frozen MySQL review.
  if (source.sourceId === 'guanyijia_mysql' && !isRichPublication(publication)) return undefined;
  return normalizeDemoContentSourceReview({ ...source.review, contentOrigin: source.origin });
}

/** Provides the browser-safe, source-pure review document for the active snapshot. */
export function readDemoContentSourceReview(input: FormalSource): DemoContentSourceReview | undefined {
  return sourceReviewFromPublication(activePublication(), input);
}

/**
 * Keeps the existing checklist/workbench projection on its frozen V5 content
 * while the active V6 publication is consumed by the standard-document reader.
 */
export function readLegacyDemoContentSourceReview(input: FormalSource): DemoContentSourceReview | undefined {
  const publication = allPublications().find((candidate) => candidate.contentSnapshotId === v5ContentPin.snapshotId);
  if (!publication) fail();
  return sourceReviewFromPublication(publication, input);
}

export function readBoundDemoContentSourceReview(input: FormalSource & {
  contentBinding: DemoContentRunBinding;
}): DemoContentSourceReview | undefined {
  const binding = validateDemoContentRunBinding(input.contentBinding);
  const sourceBinding = binding.sourceBindings.find((source) => source.sourceId === input.sourceId);
  if (!sourceBinding || sourceBinding.formalSnapshotId !== input.snapshotId) fail();
  return sourceReviewFromPublication(publicationForBinding(binding), input);
}

/**
 * A deterministic content baseline only. It is not a formal standardization
 * result and never replaces a run's final document.
 */
export function readDemoContentMergedBaseline(): DemoContentSourceReview | undefined {
  const merged = activePublication().mergedDocument;
  if (!merged) return undefined;
  if (merged.documentKind !== 'MERGED') fail();
  return normalizeDemoContentSourceReview({ ...merged, contentOrigin: 'DERIVED_DEMO' });
}

export function readDemoContentSourceConfiguration(): DemoContentSourceConfiguration[] {
  return activePublication().sources.map((source) => clone({
    sourceId: source.sourceId,
    formalSnapshotId: source.formalSnapshotId,
    contentSourceSnapshotId: source.contentSourceSnapshotId,
    origin: source.origin,
    sourceType: source.sourceType,
    location: source.location,
    scope: source.scope,
    credentialLabel: source.credentialLabel,
  }));
}
