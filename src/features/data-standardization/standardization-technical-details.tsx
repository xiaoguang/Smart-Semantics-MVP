import type { StandardizationDeliverable } from '../standardization-deliverable/types.ts';
import type { SourceModelingDocument } from '../source-documents/types.ts';
import type { GuanyijiaTechnicalResolutionSummary } from './guanyijia-workbench-runtime.ts';

type TechnicalSource = Pick<SourceModelingDocument,
  'sourceName' | 'revision'> & { sourceId: string };

export default function StandardizationTechnicalDetails({
  sources,
  resolutions,
  deliverable,
  resolutionLoading,
  resolutionError,
  onResolutionToggle,
}: {
  sources: TechnicalSource[];
  resolutions: GuanyijiaTechnicalResolutionSummary[];
  deliverable?: StandardizationDeliverable | null;
  resolutionLoading?: boolean;
  resolutionError?: string;
  onResolutionToggle?: (open: boolean) => void;
}) {
  return <details
    className="guanyijia-technical-details"
    aria-label="内容校验"
    onToggle={(event) => onResolutionToggle?.(event.currentTarget.open)}
  >
    <summary>内容校验</summary>
    <section aria-labelledby="guanyijia-technical-sources">
      <h3 id="guanyijia-technical-sources">来源文档</h3>
      {!sources.length ? <p>尚未读取来源文档。</p> : <div className="guanyijia-technical-source-list">
        {sources.map((source) => <article key={source.sourceId}>
          <h4>{source.sourceName}</h4>
          <dl>
            <div><dt>当前文档</dt><dd>第 {source.revision} 版</dd></div>
            <div><dt>内容状态</dt><dd>已通过读取校验</dd></div>
          </dl>
        </article>)}
      </div>}
    </section>
    <section aria-labelledby="guanyijia-technical-resolutions">
      <h3 id="guanyijia-technical-resolutions">来源差异记录</h3>
      {resolutionLoading && <p role="status">正在读取已保存的来源差异记录…</p>}
      {resolutionError && <p role="alert">来源差异记录暂时无法读取。请刷新当前步骤后重试。</p>}
      {!resolutionLoading && !resolutionError && !resolutions.length
        ? <p>当前没有已保存的来源差异记录。</p>
        : <div className="guanyijia-technical-resolution-list">
        {resolutions.map((resolution) => <article key={resolution.resolutionId}>
            <h4>{resolution.title}</h4>
            <dl>
              <div><dt>保存时间</dt><dd>{new Date(resolution.createdAt).toLocaleString('zh-CN')}</dd></div>
              <div><dt>记录状态</dt><dd>已校验</dd></div>
            </dl>
          </article>)}
      </div>}
    </section>
    <section aria-labelledby="guanyijia-technical-deliverable">
      <h3 id="guanyijia-technical-deliverable">标准化结果</h3>
      <p>{deliverable
        ? deliverable.status === 'FROZEN' || deliverable.status === 'HANDED_OFF'
          ? '当前结果已完成内容校验。'
          : '当前结果仍在审阅中，定版前会再次校验来源内容。'
        : '生成标准化结果后，会在这里显示内容校验状态。'}</p>
    </section>
  </details>;
}
