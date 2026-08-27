import type { DocumentVersion, SemanticFixture } from './types.ts';

export type AssistantToolMenuItem = {
  key: 'download' | 'unknown' | 'reset';
  label: string;
  disabled?: boolean;
  danger?: boolean;
};

export function selectDemoDocument(
  fixture: SemanticFixture,
  activeSystemCode: string,
  nextExpectedVersion?: DocumentVersion | null,
) {
  const documentVersion = activeSystemCode === fixture.system.code
    ? nextExpectedVersion
    : 'v1';
  if (!documentVersion) return undefined;
  return fixture.documents.find((document) => document.documentVersion === documentVersion);
}

export function projectAssistantToolMenu(input: {
  canUpload: boolean;
  demoTools: boolean;
  nextExampleAvailable: boolean;
}): AssistantToolMenuItem[] {
  if (!input.canUpload) return [];
  const items: AssistantToolMenuItem[] = [{
    key: 'download',
    label: '下载下一份示例资料',
    disabled: !input.nextExampleAvailable,
  }];
  if (input.demoTools) items.push({ key: 'unknown', label: '下载未知资料' });
  items.push({ key: 'reset', label: '恢复当前空间', danger: true });
  return items;
}
