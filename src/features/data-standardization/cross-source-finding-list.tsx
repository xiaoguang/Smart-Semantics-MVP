import { Button } from 'antd';
import { Fragment, useEffect, useState } from 'react';
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

/**
 * Cross-source material is intentionally local to the finding. Opening it
 * never redirects the reviewer to the source rail or changes their selection.
 */
export function CrossSourceFindingList(input: {
  findings: readonly CandidateReviewProjection[];
  expansionKey?: string;
}) {
  const [expandedFindingId, setExpandedFindingId] = useState<string>();
  useEffect(() => setExpandedFindingId(undefined), [input.expansionKey]);

  return <section className="guanyijia-cross-source-findings" aria-label="本次读取发现">
    <header>
      <h3>本次读取发现</h3>
      <p>这里汇总当前已读资料之间的一致之处、差异和需要继续确认的问题。</p>
    </header>
    <div className="guanyijia-cross-source-findings-table">
      <table>
        <thead><tr><th>议题</th><th>判断</th><th>当前来源</th><th>新来源</th><th>下一步</th></tr></thead>
        <tbody>{input.findings.map((finding) => {
          const materials = materialsForFinding(finding);
          const expanded = expandedFindingId === finding.topic;
          const controlsId = `finding-materials:table:${finding.topic}`;
          return <Fragment key={finding.topic}>
            <tr>
              <th>{finding.heading}</th>
              <td><strong>{finding.relationLabel}</strong><small>{finding.relationExplanation}</small></td>
              <td><MaterialSummary material={materials[0]} /></td>
              <td><MaterialSummary material={materials[1]} /></td>
              <td><FindingToggle expanded={expanded} controlsId={controlsId} onToggle={() => setExpandedFindingId((current) => toggleFindingExpansion(current, finding.topic))} /><small>{finding.relation === 'CONFLICTS' ? '完成本次审阅后进入决定' : finding.reviewGuidance}</small></td>
            </tr>
            {expanded && <tr className="guanyijia-cross-source-expanded-row" id={controlsId}>
              <td colSpan={5}><FindingMaterials finding={finding} /></td>
            </tr>}
          </Fragment>;
        })}</tbody>
      </table>
    </div>
    <div className="guanyijia-cross-source-findings-list" role="list" aria-label="跨来源发现">
      {input.findings.map((finding) => {
        const materials = materialsForFinding(finding);
        const expanded = expandedFindingId === finding.topic;
        const controlsId = `finding-materials:list:${finding.topic}`;
        return <article key={finding.topic} role="listitem">
          <header><strong>{finding.heading}</strong><span>{finding.relationLabel}</span></header>
          <p className="guanyijia-cross-source-finding-explanation">{finding.relationExplanation}</p>
          <div className="guanyijia-cross-source-materials">
            <section><span>当前来源</span><MaterialSummary material={materials[0]} /></section>
            <section><span>新来源</span><MaterialSummary material={materials[1]} /></section>
          </div>
          <footer>
            <FindingToggle expanded={expanded} controlsId={controlsId} onToggle={() => setExpandedFindingId((current) => toggleFindingExpansion(current, finding.topic))} />
            <small>{finding.relation === 'CONFLICTS' ? '完成本次审阅后进入决定' : finding.reviewGuidance}</small>
          </footer>
          {expanded && <div id={controlsId}><FindingMaterials finding={finding} /></div>}
        </article>;
      })}
    </div>
  </section>;
}
