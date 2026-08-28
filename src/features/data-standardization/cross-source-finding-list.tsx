import { Button } from 'antd';
import { useEffect, useState } from 'react';
import type { CandidateReviewEvidence, CandidateReviewProjection } from '../guanyijia-evidence-factory/candidate-review-projection.ts';
import type { SourceReviewMatter } from '../guanyijia-evidence-factory/source-review-visibility.ts';
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

function stateLabel(matter: SourceReviewMatter): string {
  if (matter.state === 'ACTIONABLE') return '待决定';
  if (matter.state === 'BLOCKED_BY_LOCAL_SUGGESTION') return '先核对本来源建议';
  if (matter.state === 'BLOCKED_BY_PREVIOUS_CONFLICT') return '请先处理上一项';
  if (matter.state === 'RESOLVED') return '已保存';
  return '等待更多来源';
}

/**
 * Cross-source material is intentionally local to the finding. Opening it
 * never redirects the reviewer to the source rail or changes their selection.
 * Its cards share the review-items surface with scripted source suggestions;
 * this avoids treating an unresolved comparison as a conclusion.
 */
export function CrossSourceFindingList(input: {
  findings: readonly SourceReviewMatter[];
  expansionKey?: string;
  className?: string;
  actionForFinding?(matter: SourceReviewMatter): CrossSourceFindingAction;
}) {
  const [expandedFindingId, setExpandedFindingId] = useState<string>();
  useEffect(() => setExpandedFindingId(undefined), [input.expansionKey]);

  return <section className={`guanyijia-cross-source-findings ${input.className ?? ''}`.trim()} aria-label="来源差异与比较">
    <header>
      <h3>来源差异与比较</h3>
      <p>依据当前已准入的资料显示；未具备决定条件的事项会明确说明下一步。</p>
    </header>
    {input.findings.map((matter) => {
      const finding = matter.finding;
      const materials = materialsForFinding(finding);
      const expanded = expandedFindingId === matter.stableId;
      const controlsId = `finding-materials:${matter.stableId}`;
      const action = input.actionForFinding?.(matter);
      return <article key={matter.stableId} data-review-matter={finding.topic} data-conflict-id={matter.conflictId} tabIndex={-1}>
        <header><strong>{finding.heading}</strong><span>{stateLabel(matter)}</span></header>
        <div><span>当前资料</span><MaterialSummary material={materials[0]} /></div>
        <div><span>新来源资料</span><MaterialSummary material={materials[1]} /></div>
        <div><span>为什么需要确认</span><p>{finding.relationExplanation}</p></div>
        <footer>
          <FindingToggle expanded={expanded} controlsId={controlsId} onToggle={() => setExpandedFindingId((current) => toggleFindingExpansion(current, matter.stableId))} />
          {action?.onAction && <Button type="link" size="small" onClick={action.onAction}>{action.label ?? '处理该项'}</Button>}
          <small>{action?.description ?? finding.reviewGuidance}</small>
        </footer>
        {expanded && <div id={controlsId}><FindingMaterials finding={finding} /></div>}
      </article>;
    })}
  </section>;
}
