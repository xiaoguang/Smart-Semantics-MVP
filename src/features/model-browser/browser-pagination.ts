export const modelBrowserPageSize = 20;

export function paginateBrowserItems<T>(items: readonly T[], page: number) {
  const pageCount = Math.max(1, Math.ceil(items.length / modelBrowserPageSize));
  const currentPage = Math.min(Math.max(1, Math.floor(page)), pageCount);
  const start = (currentPage - 1) * modelBrowserPageSize;
  return {
    items: items.slice(start, start + modelBrowserPageSize),
    page: currentPage,
    pageCount,
    total: items.length,
  };
}
