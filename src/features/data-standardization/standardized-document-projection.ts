import type { MysqlSchemaEvidence } from './mysql-schema-evidence.ts';
import type { ReviewEvidenceView } from '../guanyijia-evidence-factory/source-review-document.ts';

export type StandardizedDocumentView = 'READING' | 'MARKDOWN_SOURCE';

export type StandardizedDocumentClaim = {
  claimId: string;
  sectionId: string;
  sectionTitle: string;
  sectionPurpose: string;
  markdownAnchor: string;
  title: string;
  statement: string;
  fields?: string[];
  /**
   * Saved MySQL structure for an object entry. The readable document keeps
   * this optional because only database claims have a field-level schema.
   */
  schemaEvidence?: MysqlSchemaEvidence;
  /**
   * A rich source document can attach more than one saved MySQL structure to
   * one business conclusion (for example a table plus its supporting index).
   * Keep the legacy singular field during the transition so older frozen
   * reviews continue to render unchanged.
   */
  schemaEvidences?: readonly MysqlSchemaEvidence[];
  /** Saved, claim-bound detail material for every supported source kind. */
  detailViews?: readonly ReviewEvidenceView[];
  kind: 'OBJECT' | 'RULE' | 'GAP' | 'TERM' | 'RELATION';
  nextStep?: string;
};

export type StandardizedDocumentSection = {
  id: string;
  title: string;
  purpose: string;
  /** Reader-only context saved beside, never duplicated from, its claims. */
  narrative?: string;
};

export type StandardizedDocumentInput = {
  revisionLabel: string;
  markdownSource: string;
  /**
   * Present only for the rich V6 deliverable.  Explicit descriptors preserve
   * the common nine-chapter coordinate even when a source has a documented
   * gap in one of the chapters.  Legacy reviews intentionally keep their
   * existing claim-driven section behaviour.
   */
  standardSections?: readonly StandardizedDocumentSection[];
  claims: StandardizedDocumentClaim[];
};

export type BusinessDocumentEntry = Pick<StandardizedDocumentClaim,
  'claimId' | 'markdownAnchor' | 'title' | 'statement' | 'fields' | 'schemaEvidence' | 'schemaEvidences' | 'detailViews' | 'kind' | 'nextStep'>;

export type BusinessDocumentSection = StandardizedDocumentSection & {
  entries: BusinessDocumentEntry[];
};

export type StandardizedDocumentProjection = {
  revisionLabel: string;
  sections: BusinessDocumentSection[];
  markdownSource: string;
  anchors: Record<string, string>;
  preserveEmptySections: boolean;
};

function cloneSchemaEvidence(evidence: MysqlSchemaEvidence): MysqlSchemaEvidence {
  return {
    ...evidence,
    columns: evidence.columns.map((column) => ({ ...column })),
  };
}

function cloneSchemaEvidences(claim: StandardizedDocumentClaim): MysqlSchemaEvidence[] | undefined {
  const source = claim.schemaEvidences?.length
    ? claim.schemaEvidences
    : claim.schemaEvidence ? [claim.schemaEvidence] : undefined;
  return source?.map(cloneSchemaEvidence);
}

function cloneDetailView(view: ReviewEvidenceView): ReviewEvidenceView {
  if (view.kind === 'MYSQL_SCHEMA') return {
    ...view,
    columns: view.columns.map((column) => ({ ...column })),
  };
  if (view.kind === 'TERM_RELATION') return {
    ...view,
    terms: view.terms.map((term) => ({ ...term })),
    relations: view.relations.map((relation) => ({ ...relation })),
  };
  return { ...view };
}

/**
 * Projects a readable, reviewer-facing document from the same explicit claims
 * that own the immutable Markdown source.  It deliberately has no evidence
 * cards or cross-source findings: those belong to the checklist.
 */
export function projectStandardizedDocument(input: StandardizedDocumentInput): StandardizedDocumentProjection {
  const explicitSections = input.standardSections;
  if (explicitSections) {
    const ids = new Set<string>();
    for (const section of explicitSections) {
      if (!section.id.trim() || !section.title.trim() || !section.purpose.trim()) {
        throw new Error('标准化文档章节定义不完整');
      }
      if (ids.has(section.id)) throw new Error(`标准化文档章节重复：${section.id}`);
      if (section.narrative !== undefined && !section.narrative.trim()) {
        throw new Error(`标准化文档章节阅读说明为空：${section.id}`);
      }
      ids.add(section.id);
    }
  }
  const sections = new Map<string, BusinessDocumentSection>(
    explicitSections?.map((section) => [section.id, {
      id: section.id,
      title: section.title,
      purpose: section.purpose,
      ...(section.narrative?.trim() ? { narrative: section.narrative.trim() } : {}),
      entries: [],
    }]) ?? [],
  );
  const anchors: Record<string, string> = {};
  for (const claim of input.claims) {
    const existing = sections.get(claim.sectionId);
    if (explicitSections && !existing) {
      throw new Error(`结论未映射到标准章节：${claim.sectionId}`);
    }
    const section = existing ?? {
      id: claim.sectionId,
      title: claim.sectionTitle,
      purpose: claim.sectionPurpose,
      entries: [],
    };
    const schemaEvidences = cloneSchemaEvidences(claim);
    const detailViews = claim.detailViews?.map(cloneDetailView);
    section.entries.push({
      claimId: claim.claimId,
      markdownAnchor: claim.markdownAnchor,
      title: claim.title,
      statement: claim.statement,
      ...(claim.fields?.length ? { fields: [...claim.fields] } : {}),
      ...(schemaEvidences?.length ? {
        schemaEvidence: schemaEvidences[0]!,
        schemaEvidences,
      } : {}),
      ...(detailViews?.length ? { detailViews } : {}),
      kind: claim.kind,
      ...(claim.nextStep ? { nextStep: claim.nextStep } : {}),
    });
    sections.set(claim.sectionId, section);
    anchors[claim.claimId] = claim.markdownAnchor;
  }
  return {
    revisionLabel: input.revisionLabel,
    sections: [...sections.values()],
    markdownSource: input.markdownSource,
    anchors,
    preserveEmptySections: Boolean(explicitSections),
  };
}
