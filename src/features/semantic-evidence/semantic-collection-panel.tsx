import { useMemo, useState } from 'react';
import { Alert, Button, Collapse, Segmented, Space, Tag, Typography } from 'antd';
import { CloudDownloadOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { compileSemanticEvidence } from './compiler.ts';
import { guanyijiaSemanticExample, retailSemanticExample } from './example-fixtures.ts';
import { generateSemanticReviewBook } from './review-book.ts';
import { semanticCollectionCopy, semanticCollectionNotice } from './semantic-copy.ts';
import type { SemanticCandidateKind, SemanticEvidencePackage } from './types.ts';
import './semantic-evidence.css';

type SemanticView = 'EVIDENCE' | 'CLAIMS' | 'FINDINGS' | 'MODEL' | 'REVIEW';

const candidateLabels: Record<SemanticCandidateKind, string> = {
  ENTITY: '实体', EVENT: '事件', FIELD: '字段', RELATION: '关系', DIMENSION: '维度',
  METRIC: '指标', HIERARCHY: '层级', RULE: '规则候选', ALIAS: '同义词', TIME_SEMANTIC: '时间语义',
};

const authorityLabels = {
  PRIMARY: '主要依据', CORROBORATING: '辅助互证', DERIVED: '派生依据', AUXILIARY: '辅助资料',
} as const;

const findingLabels = {
  CONSISTENT: '多来源一致', CONFLICT: '来源冲突', SINGLE_SOURCE: '单一依据', DERIVED: '派生结论',
} as const;

function downloadReviewBook(pkg: SemanticEvidencePackage) {
  const book = generateSemanticReviewBook(pkg);
  const bytes = new Uint8Array(book.zipBytes.byteLength);
  bytes.set(book.zipBytes);
  const url = URL.createObjectURL(new Blob([bytes.buffer], { type: 'application/zip' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `${pkg.exampleId}-review.zip`;
  link.click();
  URL.revokeObjectURL(url);
}

export default function SemanticCollectionPanel({ projectId }: { projectId: string }) {
  const input = projectId === 'guanyijia_erp' ? guanyijiaSemanticExample : retailSemanticExample;
  const [pkg, setPackage] = useState<SemanticEvidencePackage>();
  const [view, setView] = useState<SemanticView>('EVIDENCE');
  const [showReview, setShowReview] = useState(false);
  const review = useMemo(() => pkg ? generateSemanticReviewBook(pkg) : null, [pkg]);
  const sourceNames = useMemo(() => pkg
    ? [...new Map(pkg.records.map((record) => [record.connectionId, record.connectionName])).values()]
    : [], [pkg]);

  const loadExample = () => {
    const compiled = compileSemanticEvidence(input);
    setPackage(compiled);
    setView('EVIDENCE');
    setShowReview(false);
  };

  return <section className="semantic-collection-panel">
    <header>
      <div>
        <Typography.Title level={5}>资料整理与建模建议</Typography.Title>
        <Typography.Text type="secondary">从已保存资料中整理可核对的结论，并据此形成实体、事件、关系、维度、指标和规则建议。</Typography.Text>
      </div>
      <Button icon={<PlayCircleOutlined />} onClick={loadExample}>加载示例资料</Button>
    </header>

    <dl className="semantic-profile-grid">
      <div><dt>{semanticCollectionCopy.profile.materialScope}</dt><dd>{input.profile.namedGraphs.length} 组已保存资料</dd></div>
      <div><dt>{semanticCollectionCopy.profile.startConcepts}</dt><dd>{input.profile.seedConcepts.length} 个业务概念</dd></div>
      <div><dt>{semanticCollectionCopy.profile.mappedFields}</dt><dd>{input.profile.predicateMappings.length} 条</dd></div>
      <div><dt>{semanticCollectionCopy.profile.upstream}</dt><dd>{input.profile.upstreamSourceIds.length ? '已关联上游资料' : '无'}</dd></div>
    </dl>
    <Alert type="info" showIcon {...semanticCollectionNotice} />

    {!pkg ? <div className="semantic-empty-steps">
      {semanticCollectionCopy.emptySteps.map((label, index) => <span key={label}>{index + 1} {label}</span>)}
    </div> : <>
      <div className="semantic-package-summary">
        <div><strong>{pkg.title}</strong><small>本次资料已完成完整性校验</small></div>
        <Space wrap>
          <Tag color="green">校验通过</Tag>
          <Tag>{sourceNames.length} 个来源</Tag>
          <Tag>{pkg.validation.independentRootSourceCount} 个独立根来源</Tag>
        </Space>
      </div>
      <Segmented block value={view} onChange={(value) => setView(value as SemanticView)} options={[
        { label: `${semanticCollectionCopy.tabs.evidence} ${pkg.records.length}`, value: 'EVIDENCE' },
        { label: `${semanticCollectionCopy.tabs.claims} ${pkg.claims.length}`, value: 'CLAIMS' },
        { label: `${semanticCollectionCopy.tabs.findings} ${pkg.findings.length}`, value: 'FINDINGS' },
        { label: `${semanticCollectionCopy.tabs.model} ${pkg.candidates.length}`, value: 'MODEL' },
        { label: `${semanticCollectionCopy.tabs.review} ${pkg.unknownPredicates.length}`, value: 'REVIEW' },
      ]} />

      {view === 'EVIDENCE' && <div className="semantic-result-list">{sourceNames.map((name) => {
        const records = pkg.records.filter((record) => record.connectionName === name);
        return <article key={name}><div><strong>{name}</strong><small>{records.length} 条已保存材料</small></div><div>{records.map((record) => <Tag key={record.evidenceId}>已归档材料</Tag>)}</div></article>;
      })}</div>}

      {view === 'CLAIMS' && <div className="semantic-result-list">{pkg.claims.map((claim) => <article key={claim.claimId}>
        <div><strong>{claim.predicateLabel}：{claim.value}</strong><small>基于已保存资料整理</small></div>
        <div><Tag>{authorityLabels[claim.authority]}</Tag><Tag>{claim.independentSourceCount} 个独立来源</Tag><Tag>{claim.evidenceRefs.length} 条证据</Tag></div>
      </article>)}</div>}

      {view === 'FINDINGS' && <div className="semantic-result-list">{pkg.findings.map((finding) => <article key={finding.findingId}>
        <div><strong>{finding.title}</strong><small>{finding.agreement ?? finding.difference ?? '等待确认'}</small></div>
        <div><Tag color={finding.severity === 'BLOCKER' ? 'red' : finding.status === 'CONSISTENT' ? 'green' : 'gold'}>{findingLabels[finding.status]}</Tag><span>{finding.participants.map((item) => `${item.connectionName}：${item.assertion}`).join('；')}</span></div>
      </article>)}</div>}

      {view === 'MODEL' && <div className="semantic-result-list">{pkg.candidates.map((candidate) => <article key={candidate.objectRef}>
        <div><strong>{candidate.name}</strong><small>{candidate.summary}</small></div>
        <div><Tag>{candidateLabels[candidate.kind]}</Tag><Tag color={candidate.status === 'BLOCKED' ? 'red' : candidate.status === 'VERIFY' ? 'gold' : 'green'}>{candidate.status === 'BLOCKED' ? '阻断' : candidate.status === 'VERIFY' ? '待确认' : '建议加入'}</Tag><span>{candidate.evidenceRefs.length} 条依据</span></div>
      </article>)}</div>}

      {view === 'REVIEW' && <div className="semantic-result-list">{pkg.unknownPredicates.length ? pkg.unknownPredicates.map((item, index) => <article key={item.predicate}>
        <div><strong>{semanticCollectionCopy.profile.unknownField} {index + 1}</strong><small>资料中出现但尚未纳入统一解释</small></div><Tag color="gold">待解释</Tag>
      </article>) : <Alert type="success" showIcon message="没有未解释谓词" />}</div>}

      <div className="semantic-review-actions">
        <Button onClick={() => setShowReview((value) => !value)}>{showReview ? '关闭审阅说明书' : '查看审阅说明书'}</Button>
        <Button icon={<CloudDownloadOutlined />} onClick={() => downloadReviewBook(pkg)}>导出审阅说明书 ZIP</Button>
      </div>
      {showReview && review && <Collapse className="semantic-review-book" items={Object.entries(review.files)
        .filter(([name]) => name.endsWith('.md'))
        .map(([name, content]) => ({ key: name, label: name, children: <pre>{content}</pre> }))} />}
    </>}
  </section>;
}
