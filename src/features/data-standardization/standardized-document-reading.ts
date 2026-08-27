import type {
  BusinessDocumentEntry,
  StandardizedDocumentProjection,
} from './standardized-document-projection.ts';

export type StandardizedDocumentReadingEntry = {
  sectionId: string;
  sectionTitle: string;
  sectionPurpose: string;
  /** Saved chapter context is shown once before its individual conclusions. */
  sectionNarrative?: string;
  items: BusinessDocumentEntry[];
};

function normalizedSectionHeading(value: string): string {
  return value
    .replace(/^\s*\d+\.\s*/u, '')
    .replace(/[：:]\s*$/u, '')
    .trim();
}

/**
 * Rich source documents may include one short overview conclusion per
 * standard chapter.  The reading view already renders the chapter heading,
 * so repeating an identical title adds visual noise without adding meaning.
 */
export function isRedundantSectionEntryTitle(sectionTitle: string, entryTitle: string): boolean {
  const normalizedSection = normalizedSectionHeading(sectionTitle);
  const normalizedEntry = normalizedSectionHeading(entryTitle);
  return normalizedSection.length > 0 && normalizedSection === normalizedEntry;
}

/**
 * The reading view intentionally consumes the same projection as the source
 * view. It never imports checklist tasks, evidence or finding state.
 */
export function buildStandardizedDocumentReadingEntries(
  document: StandardizedDocumentProjection,
): StandardizedDocumentReadingEntry[] {
  return document.sections
    .filter((section) => document.preserveEmptySections || section.entries.length > 0)
    .map((section) => ({
      sectionId: section.id,
      sectionTitle: section.title,
      sectionPurpose: section.purpose,
      ...(section.narrative ? { sectionNarrative: section.narrative } : {}),
      items: section.entries.map((entry) => ({
        ...entry,
        ...(entry.fields ? { fields: [...entry.fields] } : {}),
        ...(entry.schemaEvidence ? {
          schemaEvidence: {
            ...entry.schemaEvidence,
            columns: entry.schemaEvidence.columns.map((column) => ({ ...column })),
          },
        } : {}),
        ...(entry.schemaEvidences?.length ? {
          schemaEvidences: entry.schemaEvidences.map((evidence) => ({
            ...evidence,
            columns: evidence.columns.map((column) => ({ ...column })),
          })),
        } : {}),
      })),
    }));
}
