export function selectSingleMarkdown(current: File | null, next: File) {
  if (current) return { file: current, error: '请先删除当前附件，再添加另一份 Markdown。' };
  if (!next.name.toLowerCase().endsWith('.md')) return { file: null, error: '只支持添加 Markdown（.md）资料。' };
  if (next.size > 1024 * 1024) return { file: null, error: 'Markdown 资料不能超过 1 MiB。' };
  return { file: next, error: null };
}

export function classifyClipboardAttachment(files: ArrayLike<File>, text: string) {
  if (files.length > 0) return 'FILE' as const;
  if (/(^|[\\/])[^\n\\/]+\.(md|pdf|wps|png|jpe?g|zip)\s*$/i.test(text.trim())) return 'FILE_NAME_ONLY' as const;
  return 'TEXT' as const;
}
