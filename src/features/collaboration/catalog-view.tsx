import { Collapse, Tag } from 'antd';
import type { ChangeRequest, CollaborationCatalog } from './types.ts';
import { demoUsers } from './runtime.ts';
import { ModelBrowser } from '../model-browser/model-browser.tsx';
import { projectCatalogBrowser } from './catalog-browser-adapter.ts';

function nameOf(userId: string) {
  if (userId === 'LEGACY') return '历史记录';
  if (userId === 'seed') return '系统预置';
  return demoUsers.find((user) => user.userId === userId)?.displayName ?? userId;
}

export function CollaborationCatalogRecord({ catalog, request }: { catalog: CollaborationCatalog; request?: ChangeRequest | null }) {
  return <article className="collaboration-catalog-record">
    <header><Tag color="green">已发布</Tag><strong>{catalog.catalogVersion} 正式模型</strong><time>{new Date(catalog.publishedAt).toLocaleString('zh-CN')}</time></header>
    <Collapse ghost size="small" items={[{
      key: 'audit', label: '查看发布记录', children: <div className="collaboration-catalog-audit">
        {request && <><span>变更：{request.title}</span><span>内容：{request.changes.map((item) => item.summary).join('；')}</span></>}
        <span>作者：{nameOf(catalog.authorUserId)}</span>
        <span>审核者：{catalog.reviewerUserIds.map(nameOf).join('、')}</span>
        {request?.approvals.at(-1)?.reason && <span>审核理由：{request.approvals.at(-1)?.reason}</span>}
        <span>发布者：{nameOf(catalog.publisherUserId)}</span>
        {catalog.publicationReason && <span>发布理由：{catalog.publicationReason}</span>}
        {!request && catalog.authorUserId === 'seed' && <span>系统预置版本，无人工变更申请。</span>}
      </div>,
    }]} />
  </article>;
}

export function CollaborationCatalogInspector({ catalog, open, onOpen, onClose }: {
  catalog: CollaborationCatalog;
  open: boolean;
  onOpen(): void;
  onClose(): void;
}) {
  return <ModelBrowser view={projectCatalogBrowser(catalog)} open={open} onOpen={onOpen} onClose={onClose} />;
}
