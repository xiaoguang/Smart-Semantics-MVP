import { Button } from 'antd';
import { useEffect, useState } from 'react';
import type { CandidateReviewEvidence, CandidateReviewProjection } from '../guanyijia-evidence-factory/candidate-review-projection.ts';
import { toggleFindingExpansion } from './finding-expansion.ts';

type FindingMaterial = {
  name: string;
  statement: string;
  excerpt: string;
  location: string;
};

function materialFromEvidence(evidence: CandidateReviewEvidence): FindingMaterial {
  return {
    name: evidence.sourceName,
    statement: evidence.supportedClaim,
    excerpt: evidence.excerpt,
    location: evidence.locationValue,
  };
}

function materialsForFinding(finding: CandidateReviewProjection): FindingMaterial[] {
  const evidence = finding.evidence.map(materialFromEvidence);
  if (finding.target) evidence.push({
    name: '制度目标（待确认）',
    statement: finding.target.statement,
    excerpt: finding.target.statement,
    location: '本次制度资料',
  });
  return evidence;
}

function MaterialSummary({ material }: { material?: FindingMaterial }) {
  return material ? <>
    <strong>{material.name}</strong>
    <p>{material.statement}</p>
    <small>{material.location}</small>
  </> : <span className="guanyijia-cross-source-empty">尚无已读取材料</span>;
}

function FindingMaterials({ finding }: { finding: CandidateReviewProjection }) {
  const materials = materialsForFinding(finding);
  return <section className="guanyijia-cross-source-expanded-materials" aria-label={`${finding.heading}的双方资料`}>
    <h4>参与判断的来源材料</h4>
    <div>
      {materials.map((material, index) => <article key={`${material.name}:${material.location}:${index}`}>
        <strong>{material.name}</strong>
        <p>{material.excerpt}</p>
        <small>位置：{material.location}</small>
        <em>支持判断：{material.statement}</em>
      </article>)}
    </div>
  </section>;
}

function FindingToggle({
  expanded,
  controlsId,
  onToggle,
}: {
  expanded: boolean;
  controlsId: string;
  onToggle(): void;
}) {
  return <Button
    type="link"
    size="small"
    aria-expanded={expanded}
    aria-controls={controlsId}
    onClick={onToggle}
  >{expanded ? '收起双方资料' : '查看双方资料'}</Button>;
}

export type CrossSourceFindingAction = {
  description: string;
  label?: string;
  onAction?(): void;
};

/**
 * Cross-source material is intentionally local to the finding. Opening it
 * never redirects the reviewer to the source rail or changes their selection.
 * Its cards share the review-items surface with scripted source suggestions;
 * this avoids treating an unresolved comparison as a conclusion.
 */
export function CrossSourceFindingList(input: {
  findings: readonly CandidateReviewProjection[];
  expansionKey?: string;
  actionForFinding?(finding: CandidateReviewProjection): CrossSourceFindingAction;
}) {
  const [expandedFindingId, setExpandedFindingId] = useState<string>();
  useEffect(() => setExpandedFindingId(undefined), [input.expansionKey]);

  return <section className="guanyijia-cross-source-findings" aria-label="跨来源事项">
    <header>
      <h3>来源差异与比较</h3>
      <p>依据当前已准入的资料显示；未具备决定条件的事项会明确说明下一步。</p>
    </header>
    {input.findings.map((finding) => {
      const materials = materialsForFinding(finding);
      const expanded = expandedFindingId === finding.topic;
      const controlsId = `finding-materials:${finding.topic}`;
      const action = input.actionForFinding?.(finding);
      return <article key={finding.topic} data-review-matter={finding.topic} tabIndex={-1}>
        <header><strong>{finding.heading}</strong><span>{finding.relationLabel}</span></header>
        <div><span>当前资料</span><MaterialSummary material={materials[0]} /></div>
        <div><span>新来源资料</span><MaterialSummary material={materials[1]} /></div>
        <div><span>为什么需要确认</span><p>{finding.relationExplanation}</p></div>
        <footer>
          <FindingToggle expanded={expanded} controlsId={controlsId} onToggle={() => setExpandedFindingId((current) => toggleFindingExpansion(current, finding.topic))} />
          {action?.onAction && <Button type="link" size="small" onClick={action.onAction}>{action.label ?? '处理该项'}</Button>}
          <small>{action?.description ?? finding.reviewGuidance}</small>
        </footer>
        {expanded && <div id={controlsId}><FindingMaterials finding={finding} /></div>}
      </article>;
    })}
  </section>;
}
