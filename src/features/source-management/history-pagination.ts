export const sourceHistoryPageSize = 20;

export function paginateSourceHistory<T>(items: readonly T[], page: number) {
  const pageCount = Math.max(1, Math.ceil(items.length / sourceHistoryPageSize));
  const currentPage = Math.min(Math.max(1, Math.floor(page)), pageCount);
  const start = (currentPage - 1) * sourceHistoryPageSize;
  return {
    items: items.slice(start, start + sourceHistoryPageSize),
    page: currentPage,
    pageCount,
    total: items.length,
  };
}
