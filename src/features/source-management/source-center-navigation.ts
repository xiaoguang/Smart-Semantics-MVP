export type SourceCenterNavigationEvent =
  | { type: 'SELECT'; connectionId: string }
  | { type: 'BACK' };

export function sourceCenterMobileNavigation(
  event: SourceCenterNavigationEvent,
) {
  const nextId = event.type === 'SELECT' ? event.connectionId : undefined;
  return {
    selectedConnectionId: nextId,
    className: nextId ? 'mobile-detail' as const : 'mobile-list' as const,
  };
}

export function sourceCenterMobileClass(selectedConnectionId: string | undefined) {
  return selectedConnectionId ? 'mobile-detail' : 'mobile-list';
}
