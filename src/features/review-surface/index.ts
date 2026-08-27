export type ReviewSurfaceViewport = 'DESKTOP_INLINE' | 'DESKTOP_OVERLAY' | 'MOBILE_FULLSCREEN';

export type ReviewSurfaceLayerKind =
  | 'TIMELINE'
  | 'DOCUMENT'
  | 'CONFLICT'
  | 'ASSISTANT_PATCH'
  | 'DELIVERABLE'
  | 'INSPECTOR';

export type ReviewSurfaceAnchor = {
  itemId: string;
  cursor?: string;
  relativeTop: number;
  scrollTop?: number;
  focusId?: string;
  textSelection?: { start: number; end: number };
};

export type ReviewAssistantDraft = {
  value: string;
  selectionStart: number;
  selectionEnd: number;
};

export type ReviewSurfaceLayer = {
  kind: ReviewSurfaceLayerKind;
  stableId: string;
  revision?: number;
  returnAnchor: ReviewSurfaceAnchor;
  focusId?: string;
};

export type ReviewSurfaceState = {
  runId: string;
  runRevision: number;
  cursorEpoch: string;
  anchor: ReviewSurfaceAnchor;
  layers: ReviewSurfaceLayer[];
  assistantDraft: ReviewAssistantDraft;
};

export type ReviewSurfaceEvent =
  | { type: 'OPEN'; layer: ReviewSurfaceLayer }
  | { type: 'UPDATE_TOP_LAYER'; layer: ReviewSurfaceLayer }
  | { type: 'CLOSE' }
  | { type: 'ESCAPE' }
  | { type: 'VIEWPORT_CHANGED' }
  | { type: 'CAPTURE_ANCHOR'; anchor: ReviewSurfaceAnchor }
  | { type: 'DRAFT_CHANGED'; draft: ReviewAssistantDraft }
  | {
    type: 'RUN_REVISION_CHANGED';
    runRevision: number;
    validStableIds: string[];
    layerMigrations?: Array<{
      fromStableId: string;
      toStableId: string;
      focusId?: string;
    }>;
    fallbackAnchor: ReviewSurfaceAnchor;
  }
  | { type: 'CURSOR_EXPIRED'; cursorEpoch: string; fallbackAnchor: ReviewSurfaceAnchor };

export type ReviewSurfaceEffect =
  | { type: 'FOCUS'; focusId: string }
  | { type: 'RESTORE_FOCUS'; focusId: string }
  | { type: 'RESTORE_TEXT_SELECTION'; focusId: string; start: number; end: number }
  | { type: 'RESTORE_SCROLL'; anchor: ReviewSurfaceAnchor }
  | { type: 'ANNOUNCE'; message: string }
  | { type: 'SET_MODAL_STATE'; bodyLocked: boolean; backgroundInert: boolean; trapFocus: boolean };

export type SurfaceTransition = { state: ReviewSurfaceState; effects: ReviewSurfaceEffect[] };

export type ReviewSurfaceNavigator = {
  transition(input: {
    state: ReviewSurfaceState;
    event: ReviewSurfaceEvent;
    viewport: ReviewSurfaceViewport;
  }): SurfaceTransition;
};

const layerLabel: Record<ReviewSurfaceLayerKind, string> = {
  TIMELINE: '运行时间线',
  DOCUMENT: '来源文档',
  CONFLICT: '来源差异',
  ASSISTANT_PATCH: '助手修改预览',
  DELIVERABLE: '标准化交付物',
  INSPECTOR: '来源资料',
};

function validateAnchor(anchor: ReviewSurfaceAnchor) {
  if (!anchor.itemId.trim() || !Number.isFinite(anchor.relativeTop)) throw new Error('审阅锚点无效');
  if (anchor.scrollTop !== undefined && (!Number.isFinite(anchor.scrollTop) || anchor.scrollTop < 0)) {
    throw new Error('审阅锚点scrollTop无效');
  }
  if (anchor.cursor !== undefined && !anchor.cursor.trim()) throw new Error('审阅锚点cursor无效');
  if (anchor.textSelection && (!anchor.focusId
    || !Number.isInteger(anchor.textSelection.start) || !Number.isInteger(anchor.textSelection.end)
    || anchor.textSelection.start < 0 || anchor.textSelection.end < anchor.textSelection.start)) {
    throw new Error('审阅锚点文本选区无效');
  }
}

function validateDraft(draft: ReviewAssistantDraft) {
  if (!Number.isInteger(draft.selectionStart) || !Number.isInteger(draft.selectionEnd)
    || draft.selectionStart < 0 || draft.selectionEnd < draft.selectionStart
    || draft.selectionEnd > draft.value.length) throw new Error('助手草稿选择区无效');
}

function isModal(layers: ReviewSurfaceLayer[], viewport: ReviewSurfaceViewport) {
  const top = layers.at(-1);
  if (!top) return false;
  if (viewport === 'MOBILE_FULLSCREEN') return true;
  return viewport === 'DESKTOP_OVERLAY' && top.kind === 'INSPECTOR';
}

function modalEffect(layers: ReviewSurfaceLayer[], viewport: ReviewSurfaceViewport): ReviewSurfaceEffect {
  const modal = isModal(layers, viewport);
  return { type: 'SET_MODAL_STATE', bodyLocked: modal, backgroundInert: modal, trapFocus: modal };
}

function restoreEffects(
  state: ReviewSurfaceState,
  viewport: ReviewSurfaceViewport,
  message: string,
): ReviewSurfaceEffect[] {
  // The destination may still be inert from the layer being closed. Switch the
  // modal projection first so browsers do not silently reject focus().
  const effects: ReviewSurfaceEffect[] = [modalEffect(state.layers, viewport), { type: 'RESTORE_SCROLL', anchor: state.anchor }];
  if (state.anchor.focusId) effects.push({ type: 'RESTORE_FOCUS', focusId: state.anchor.focusId });
  if (state.anchor.focusId && state.anchor.textSelection) effects.push({
    type: 'RESTORE_TEXT_SELECTION',
    focusId: state.anchor.focusId,
    start: state.anchor.textSelection.start,
    end: state.anchor.textSelection.end,
  });
  effects.push({ type: 'ANNOUNCE', message });
  return effects;
}

export function initialReviewSurfaceState(input: {
  runId: string;
  runRevision: number;
  cursorEpoch: string;
  anchor?: ReviewSurfaceAnchor;
  assistantDraft?: ReviewAssistantDraft;
}): ReviewSurfaceState {
  if (!input.runId.trim() || !Number.isInteger(input.runRevision) || input.runRevision < 0
    || !input.cursorEpoch.trim()) throw new Error('审阅surface identity无效');
  const anchor = input.anchor ?? { itemId: 'review-surface:start', relativeTop: 0 };
  const assistantDraft = input.assistantDraft ?? { value: '', selectionStart: 0, selectionEnd: 0 };
  validateAnchor(anchor);
  validateDraft(assistantDraft);
  return { ...input, anchor, assistantDraft, layers: [] };
}

function exactKeys(value: Record<string, unknown>, allowed: string[]) {
  return Object.keys(value).every((key) => allowed.includes(key))
    && allowed.filter((key) => !['cursor', 'scrollTop', 'focusId', 'revision', 'textSelection'].includes(key))
      .every((key) => key in value);
}

function parseAnchor(value: unknown): ReviewSurfaceAnchor | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const candidate = value as Record<string, unknown>;
  const textSelection = candidate.textSelection;
  if (!exactKeys(candidate, ['itemId', 'cursor', 'relativeTop', 'scrollTop', 'focusId', 'textSelection'])
    || typeof candidate.itemId !== 'string' || typeof candidate.relativeTop !== 'number'
    || (candidate.cursor !== undefined && typeof candidate.cursor !== 'string')
    || (candidate.scrollTop !== undefined && typeof candidate.scrollTop !== 'number')
    || (candidate.focusId !== undefined && typeof candidate.focusId !== 'string')
    || (textSelection !== undefined && (
      !textSelection || typeof textSelection !== 'object' || Array.isArray(textSelection)
      || !exactKeys(textSelection as Record<string, unknown>, ['start', 'end'])
      || !Number.isInteger((textSelection as Record<string, unknown>).start)
      || !Number.isInteger((textSelection as Record<string, unknown>).end)
    ))) return null;
  try {
    validateAnchor(candidate as ReviewSurfaceAnchor);
    return candidate as ReviewSurfaceAnchor;
  } catch {
    return null;
  }
}

export function restoreReviewSurfaceState(
  raw: string | null,
  identity: { runId: string; runRevision: number; cursorEpoch: string },
): ReviewSurfaceState {
  const fallback = initialReviewSurfaceState(identity);
  if (!raw) return fallback;
  try {
    const value = JSON.parse(raw) as unknown;
    if (!value || typeof value !== 'object' || Array.isArray(value)) return fallback;
    const candidate = value as Record<string, unknown>;
    if (!exactKeys(candidate, ['runId', 'runRevision', 'cursorEpoch', 'anchor', 'layers', 'assistantDraft'])
      || candidate.runId !== identity.runId || candidate.runRevision !== identity.runRevision
      || candidate.cursorEpoch !== identity.cursorEpoch || !Array.isArray(candidate.layers)
      || candidate.layers.length > 6) return fallback;
    const anchor = parseAnchor(candidate.anchor);
    const draft = candidate.assistantDraft;
    if (!anchor || !draft || typeof draft !== 'object' || Array.isArray(draft)
      || !exactKeys(draft as Record<string, unknown>, ['value', 'selectionStart', 'selectionEnd'])) return fallback;
    validateDraft(draft as ReviewAssistantDraft);
    const kinds = new Set<ReviewSurfaceLayerKind>([
      'TIMELINE', 'DOCUMENT', 'CONFLICT', 'ASSISTANT_PATCH', 'DELIVERABLE', 'INSPECTOR',
    ]);
    const layers = candidate.layers.map((layer): ReviewSurfaceLayer | null => {
      if (!layer || typeof layer !== 'object' || Array.isArray(layer)) return null;
      const item = layer as Record<string, unknown>;
      const returnAnchor = parseAnchor(item.returnAnchor);
      if (!exactKeys(item, ['kind', 'stableId', 'revision', 'returnAnchor', 'focusId'])
        || typeof item.kind !== 'string' || !kinds.has(item.kind as ReviewSurfaceLayerKind)
        || typeof item.stableId !== 'string' || !item.stableId.trim() || !returnAnchor
        || (item.revision !== undefined && (!Number.isInteger(item.revision) || item.revision !== identity.runRevision))
        || (item.focusId !== undefined && typeof item.focusId !== 'string')) return null;
      return {
        kind: item.kind as ReviewSurfaceLayerKind,
        stableId: item.stableId,
        ...(item.revision !== undefined ? { revision: item.revision as number } : {}),
        returnAnchor,
        ...(item.focusId !== undefined ? { focusId: item.focusId as string } : {}),
      };
    });
    if (layers.some((layer) => layer === null)) return fallback;
    return {
      runId: identity.runId,
      runRevision: identity.runRevision,
      cursorEpoch: identity.cursorEpoch,
      anchor,
      layers: layers as ReviewSurfaceLayer[],
      assistantDraft: draft as ReviewAssistantDraft,
    };
  } catch {
    return fallback;
  }
}

export function createReviewSurfaceNavigator(): ReviewSurfaceNavigator {
  return {
    transition({ state, event, viewport }) {
      if (event.type === 'OPEN') {
        validateAnchor(event.layer.returnAnchor);
        if (!event.layer.stableId.trim()) throw new Error('审阅layer identity无效');
        const next = { ...state, layers: [...state.layers, event.layer] };
        const effects: ReviewSurfaceEffect[] = [modalEffect(next.layers, viewport)];
        if (event.layer.focusId) effects.push({ type: 'FOCUS', focusId: event.layer.focusId });
        return { state: next, effects };
      }
      if (event.type === 'UPDATE_TOP_LAYER') {
        const current = state.layers.at(-1);
        if (!current || current.kind !== event.layer.kind || current.stableId !== event.layer.stableId) {
          throw new Error('审阅layer更新目标不是当前顶部layer');
        }
        validateAnchor(event.layer.returnAnchor);
        if (!event.layer.stableId.trim()) throw new Error('审阅layer identity无效');
        const next = { ...state, layers: [...state.layers.slice(0, -1), event.layer] };
        const effects: ReviewSurfaceEffect[] = [modalEffect(next.layers, viewport)];
        if (event.layer.focusId) effects.push({ type: 'FOCUS', focusId: event.layer.focusId });
        return { state: next, effects };
      }
      if (event.type === 'CLOSE' || event.type === 'ESCAPE') {
        const closing = state.layers.at(-1);
        if (!closing) return { state, effects: [modalEffect([], viewport)] };
        const next = { ...state, anchor: closing.returnAnchor, layers: state.layers.slice(0, -1) };
        const destination = next.layers.at(-1);
        return {
          state: next,
          effects: restoreEffects(next, viewport, destination
            ? `已返回${layerLabel[destination.kind]}`
            : '已返回运行时间线'),
        };
      }
      if (event.type === 'VIEWPORT_CHANGED') {
        const effects: ReviewSurfaceEffect[] = [modalEffect(state.layers, viewport)];
        const top = state.layers.at(-1);
        if (isModal(state.layers, viewport) && top?.focusId) effects.push({ type: 'FOCUS', focusId: top.focusId });
        return { state, effects };
      }
      if (event.type === 'CAPTURE_ANCHOR') {
        validateAnchor(event.anchor);
        return { state: { ...state, anchor: event.anchor }, effects: [] };
      }
      if (event.type === 'DRAFT_CHANGED') {
        validateDraft(event.draft);
        return { state: { ...state, assistantDraft: event.draft }, effects: [] };
      }
      if (event.type === 'RUN_REVISION_CHANGED') {
        if (!Number.isInteger(event.runRevision) || event.runRevision < state.runRevision) {
          throw new Error('Run revision不能回退');
        }
        validateAnchor(event.fallbackAnchor);
        const allowed = new Set(event.validStableIds);
        const layerMigrations = new Map<string, { toStableId: string; focusId?: string }>();
        for (const migration of event.layerMigrations ?? []) {
          if (!migration.fromStableId.trim() || !migration.toStableId.trim()
            || !allowed.has(migration.toStableId) || layerMigrations.has(migration.fromStableId)) {
            throw new Error('审阅layer revision迁移无效');
          }
          layerMigrations.set(migration.fromStableId, {
            toStableId: migration.toStableId,
            ...(migration.focusId ? { focusId: migration.focusId } : {}),
          });
        }
        const layers = state.layers
          .map((layer) => {
            const migration = layerMigrations.get(layer.stableId);
            return migration ? {
              ...layer,
              stableId: migration.toStableId,
              ...(migration.focusId ? { focusId: migration.focusId } : {}),
            } : layer;
          })
          .filter((layer) => layer.revision === undefined || allowed.has(layer.stableId))
          .map((layer) => layer.revision === undefined ? layer : { ...layer, revision: event.runRevision });
        const next = {
          ...state,
          runRevision: event.runRevision,
          layers,
          anchor: event.fallbackAnchor,
        };
        const effects = restoreEffects(next, viewport, '审阅内容已更新');
        const migratedTop = next.layers.at(-1);
        if (migratedTop?.focusId) {
          effects.splice(effects.length - 1, 0, { type: 'FOCUS', focusId: migratedTop.focusId });
        }
        return { state: next, effects };
      }
      validateAnchor(event.fallbackAnchor);
      const next = {
        ...state,
        cursorEpoch: event.cursorEpoch,
        anchor: { ...event.fallbackAnchor, cursor: undefined },
        layers: [],
      };
      return { state: next, effects: restoreEffects(next, viewport, '列表已更新') };
    },
  };
}

export function projectWorkflowPrimary(visibleActionIds: string[]) {
  const unique = [...new Set(visibleActionIds.filter((value) => value.trim()))];
  if (unique.length > 1) throw new Error('主区只能有一个工作流主操作');
  return unique[0];
}

export type ReviewSurfaceEffectPorts = {
  itemTop(itemId: string): number | null;
  viewportTop(): number;
  readScrollTop(): number;
  writeScrollTop(value: number): void;
  focus(focusId: string): void;
  /**
   * Restoring focus happens while a modal is being removed. Consumers that
   * manage an active focus trap can defer this one operation until the trap
   * cleanup has committed; ordinary projections may use the same behavior as
   * `focus`.
   */
  restoreFocus?(focusId: string): void;
  restoreTextSelection(focusId: string, start: number, end: number): void;
  announce(message: string): void;
  setBodyLocked(value: boolean): void;
  setBackgroundInert(value: boolean): void;
  setFocusTrap(value: boolean): void;
};

export function applyReviewSurfaceEffects(
  effects: ReviewSurfaceEffect[],
  ports: ReviewSurfaceEffectPorts,
) {
  for (const effect of effects) {
    if (effect.type === 'RESTORE_SCROLL') {
      if (effect.anchor.scrollTop !== undefined) {
        ports.writeScrollTop(effect.anchor.scrollTop);
        continue;
      }
      const itemTop = ports.itemTop(effect.anchor.itemId);
      if (itemTop !== null) {
        const currentOffset = itemTop - ports.viewportTop();
        ports.writeScrollTop(ports.readScrollTop() + currentOffset - effect.anchor.relativeTop);
      }
      continue;
    }
    if (effect.type === 'FOCUS') {
      ports.focus(effect.focusId);
      continue;
    }
    if (effect.type === 'RESTORE_FOCUS') {
      (ports.restoreFocus ?? ports.focus)(effect.focusId);
      continue;
    }
    if (effect.type === 'RESTORE_TEXT_SELECTION') {
      ports.restoreTextSelection(effect.focusId, effect.start, effect.end);
      continue;
    }
    if (effect.type === 'ANNOUNCE') {
      ports.announce(effect.message);
      continue;
    }
    ports.setBodyLocked(effect.bodyLocked);
    ports.setBackgroundInert(effect.backgroundInert);
    ports.setFocusTrap(effect.trapFocus);
  }
}
