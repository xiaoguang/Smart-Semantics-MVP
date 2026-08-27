export type PublishedNavigationHint = {
  systemCode: string;
  catalogVersion: string;
} | null;

export type PublishedNavigationEvent =
  | { type: 'PUBLISHED'; systemCode: string; catalogVersion: string }
  | { type: 'ENTER_MODELING'; systemCode: string; catalogVersion: string }
  | { type: 'VIEW_HISTORY' };

export function transitionPublishedNavigationHint(
  current: PublishedNavigationHint,
  event: PublishedNavigationEvent,
): PublishedNavigationHint {
  if (event.type === 'PUBLISHED') {
    return { systemCode: event.systemCode, catalogVersion: event.catalogVersion };
  }
  if (event.type === 'ENTER_MODELING'
    && current?.systemCode === event.systemCode
    && current.catalogVersion === event.catalogVersion) return null;
  return current;
}
