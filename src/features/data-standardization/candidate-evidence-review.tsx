import type { CandidateReviewProjection } from '../guanyijia-evidence-factory/candidate-review-projection.ts';

/**
 * Read-only presentation of an already admitted candidate projection. The
 * parent owns selection because it is navigation state shared with the source
 * map; this module never issues a workbench command.
 */
export default function CandidateEvidenceReview({
  projection,
  selectedEvidenceRef,
  onEvidenceSelect,
}: {
  projection: CandidateReviewProjection;
  selectedEvidenceRef?: string;
  onEvidenceSelect(evidenceRef: string, trigger: HTMLElement): void;
}) {
  return <section className="candidate-evidence-review" aria-label={`${projection.heading}候选证据`}>
    <header className="candidate-evidence-review-header">
      <span className={`candidate-evidence-relation ${projection.relation.toLowerCase()}`}>{projection.relationLabel}</span>
      <h4>{projection.heading}</h4>
    </header>
    <p className="candidate-evidence-relation-explanation">{projection.relationExplanation}</p>

    <div className="candidate-evidence-fragments">
      {projection.evidence.map((evidence) => <article
        className={selectedEvidenceRef === evidence.evidenceRef ? 'selected' : ''}
        key={evidence.evidenceRef}
      >
        <button
          type="button"
          aria-pressed={selectedEvidenceRef === evidence.evidenceRef}
          onClick={(event) => onEvidenceSelect(evidence.evidenceRef, event.currentTarget)}
        >
          <span>{evidence.evidenceClassLabel}</span>
          <strong>{evidence.title}</strong>
          <small>{evidence.sourceName} · {evidence.locationLabel}：{evidence.locationValue}</small>
        </button>
        <pre>{evidence.excerpt}</pre>
        <p><b>支持结论：</b>{evidence.supportedClaim}</p>
      </article>)}
    </div>

    {projection.target && <section className="candidate-evidence-target">
      <div><span>候选目标制度</span><strong>{projection.target.statusLabel}</strong></div>
      <h5>{projection.target.title}</h5>
      <p>{projection.target.statement}</p>
      <p>这是待确认提案，不是观察事实，也不构成独立互证。</p>
    </section>}

    <p className="candidate-evidence-guidance">审阅提示：{projection.reviewGuidance}</p>
  </section>;
}
