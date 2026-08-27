import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type RefObject } from 'react';
import {
  applyReviewSurfaceEffects,
  createReviewSurfaceNavigator,
  initialReviewSurfaceState,
  restoreReviewSurfaceState,
  type ReviewAssistantDraft,
  type ReviewSurfaceAnchor,
  type ReviewSurfaceEvent,
  type ReviewSurfaceLayer,
  type ReviewSurfaceState,
  type ReviewSurfaceViewport,
} from './index.ts';

function focusableElements(root: HTMLElement) {
  return [...root.querySelectorAll<HTMLElement>(
    'button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
  )].filter((element) => !element.hidden && element.getAttribute('aria-hidden') !== 'true');
}

function elementForStableId(shell: HTMLElement, stableId: string) {
  return [...shell.querySelectorAll<HTMLElement>('[data-review-layer-id]')]
    .find((element) => element.dataset.reviewLayerId === stableId) ?? null;
}

function modalRoots(shell: HTMLElement, stableId: string) {
  const activeLayer = elementForStableId(shell, stableId);
  if (!activeLayer) return [];
  return [activeLayer, ...shell.querySelectorAll<HTMLElement>('[data-review-modal-companion-for]')]
    .filter((element) => element === activeLayer
      || element.dataset.reviewModalCompanionFor === stableId);
}

export function useReviewSurface(input: {
  shellRef: RefObject<HTMLElement | null>;
  scrollRef: RefObject<HTMLElement | null>;
  runId: string;
  runRevision: number;
  cursorEpoch: string;
  viewport: ReviewSurfaceViewport;
}) {
  const navigator = useMemo(() => createReviewSurfaceNavigator(), []);
  const [state, setState] = useState<ReviewSurfaceState>(() => initialReviewSurfaceState({
    runId: input.runId,
    runRevision: input.runRevision,
    cursorEpoch: input.cursorEpoch,
  }));
  const stateRef = useRef(state);
  const inertedRef = useRef(new Map<HTMLElement, { inert: boolean; ariaHidden: string | null }>());
  const interactionEpochRef = useRef(0);
  const deferredFocusFrameRef = useRef<number | undefined>(undefined);
  const [announcement, setAnnouncement] = useState('');
  const [trapFocus, setTrapFocus] = useState(false);
  const viewportRef = useRef(input.viewport);
  const persistenceKey = (runId: string) => `linguan-review-surface-v1:${runId}`;

  const apply = useCallback((effects: ReturnType<typeof navigator.transition>['effects']) => {
    window.requestAnimationFrame(() => {
      const shell = input.shellRef.current;
      const scroll = input.scrollRef.current;
      if (!shell || !scroll) return;
      applyReviewSurfaceEffects(effects, {
        itemTop(itemId) {
          const element = [...shell.querySelectorAll<HTMLElement>('[data-review-anchor]')]
            .find((candidate) => candidate.dataset.reviewAnchor === itemId);
          return element?.getBoundingClientRect().top ?? null;
        },
        viewportTop: () => scroll.getBoundingClientRect().top,
        readScrollTop: () => scroll.scrollTop,
        writeScrollTop: (value) => { scroll.scrollTop = value; },
        focus(focusId) {
          const element = document.getElementById(focusId)
            ?? [...shell.querySelectorAll<HTMLElement>('[data-review-focus]')]
              .find((candidate) => candidate.dataset.reviewFocus === focusId);
          element?.focus({ preventScroll: true });
        },
        restoreFocus(focusId) {
          // Closing an overlay switches off the focus trap in the same React
          // commit. Focus only after that cleanup so the old trap cannot pull
          // it back into a disappearing inspector.
          const epoch = interactionEpochRef.current;
          if (deferredFocusFrameRef.current !== undefined) {
            window.cancelAnimationFrame(deferredFocusFrameRef.current);
          }
          deferredFocusFrameRef.current = window.requestAnimationFrame(() => {
            deferredFocusFrameRef.current = window.requestAnimationFrame(() => {
              deferredFocusFrameRef.current = undefined;
              if (interactionEpochRef.current !== epoch) return;
              const element = document.getElementById(focusId)
                ?? [...shell.querySelectorAll<HTMLElement>('[data-review-focus]')]
                  .find((candidate) => candidate.dataset.reviewFocus === focusId);
              element?.focus({ preventScroll: true });
            });
          });
        },
        restoreTextSelection(focusId, start, end) {
          const element = document.getElementById(focusId);
          if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
            element.setSelectionRange(start, end);
          }
        },
        announce(message) { setAnnouncement(message); },
        setBodyLocked(value) { document.body.classList.toggle('standardization-overlay-active', value); },
        setBackgroundInert(value) {
          const top = stateRef.current.layers.at(-1);
          for (const [element, original] of inertedRef.current) {
            element.inert = original.inert;
            if (original.ariaHidden === null) element.removeAttribute('aria-hidden');
            else element.setAttribute('aria-hidden', original.ariaHidden);
            delete element.dataset.reviewSurfaceInerted;
          }
          inertedRef.current.clear();
          if (!value || !top) return;
          const roots = modalRoots(shell, top.stableId);
          const appRoot = shell.closest<HTMLElement>('.app-shell') ?? shell;
          if (!roots.length) return;
          const keep = (element: HTMLElement) => roots.some((root) => (
            element === root || element.contains(root) || root.contains(element)
          ));
          const markInert = (element: HTMLElement) => {
            if (keep(element) || inertedRef.current.has(element)) return;
            inertedRef.current.set(element, {
              inert: element.inert,
              ariaHidden: element.getAttribute('aria-hidden'),
            });
            element.inert = true;
            element.setAttribute('aria-hidden', 'true');
            element.dataset.reviewSurfaceInerted = 'true';
          };
          const ancestors = new Set<HTMLElement>();
          for (const root of roots) {
            let current: HTMLElement | null = root;
            while (current) {
              ancestors.add(current);
              if (current === appRoot) break;
              current = current.parentElement;
            }
          }
          for (const ancestor of ancestors) {
            if (!ancestor.parentElement) continue;
            for (const sibling of ancestor.parentElement.children) {
              if (sibling instanceof HTMLElement && !ancestors.has(sibling)) markInert(sibling);
            }
          }
          // A layer may live inside a broad sibling such as the inspector
          // rail.  Mark its explicitly declared interactive descendants too:
          // this keeps semantic controls (for example the workflow timeline)
          // inert even when their parent is later repositioned by responsive
          // CSS, while modal companions remain exempt through `keep()`.
          for (const candidate of shell.querySelectorAll<HTMLElement>('[data-review-inertable]')) {
            markInert(candidate);
          }
        },
        setFocusTrap: setTrapFocus,
      });
    });
  }, [input.scrollRef, input.shellRef]);

  const reprojectModal = useCallback(() => {
    const effects = navigator.transition({
      state: stateRef.current,
      event: { type: 'VIEWPORT_CHANGED' },
      viewport: input.viewport,
    }).effects.filter((effect) => effect.type === 'SET_MODAL_STATE');
    apply(effects);
  }, [apply, input.viewport, navigator]);

  const dispatch = useCallback((event: ReviewSurfaceEvent, viewport = viewportRef.current) => {
    interactionEpochRef.current += 1;
    const result = navigator.transition({ state: stateRef.current, event, viewport });
    stateRef.current = result.state;
    setState(result.state);
    if (!result.state.runId.endsWith(':pending')) {
      try { sessionStorage.setItem(persistenceKey(result.state.runId), JSON.stringify(result.state)); }
      catch { setAnnouncement('当前浏览器无法保存审阅位置，刷新后需要重新定位'); }
    }
    apply(result.effects);
    return result.state;
  }, [apply, navigator]);

  const captureAnchor = useCallback((trigger?: HTMLElement | null): ReviewSurfaceAnchor => {
    const scroll = input.scrollRef.current;
    const candidate = trigger?.closest<HTMLElement>('[data-review-anchor]')
      ?? (document.activeElement as HTMLElement | null)?.closest<HTMLElement>('[data-review-anchor]');
    if (!scroll) return stateRef.current.anchor;
    const anchor = candidate?.dataset.reviewAnchor && scroll.contains(candidate)
      ? {
          itemId: candidate.dataset.reviewAnchor,
          relativeTop: candidate.getBoundingClientRect().top - scroll.getBoundingClientRect().top,
          ...(candidate.dataset.reviewCursor ? { cursor: candidate.dataset.reviewCursor } : {}),
        }
      : candidate?.dataset.reviewAnchor ? {
          itemId: stateRef.current.anchor.itemId,
          relativeTop: stateRef.current.anchor.relativeTop,
          ...(stateRef.current.anchor.cursor ? { cursor: stateRef.current.anchor.cursor } : {}),
        } : stateRef.current.anchor;
    const textControl = trigger instanceof HTMLInputElement || trigger instanceof HTMLTextAreaElement
      ? trigger : null;
    return {
      ...anchor,
      scrollTop: scroll.scrollTop,
      ...(trigger?.id ? { focusId: trigger.id } : trigger?.dataset.reviewFocus
        ? { focusId: trigger.dataset.reviewFocus } : candidate?.dataset.reviewFocus
          ? { focusId: candidate.dataset.reviewFocus } : {}),
      ...(textControl ? { textSelection: {
        start: textControl.selectionStart ?? 0,
        end: textControl.selectionEnd ?? textControl.selectionStart ?? 0,
      } } : {}),
    };
  }, [input.scrollRef]);

  const open = useCallback((
    layer: Omit<ReviewSurfaceLayer, 'returnAnchor'>,
    trigger?: HTMLElement | null,
    returnAnchor?: ReviewSurfaceAnchor,
  ) => {
    const current = stateRef.current.layers.at(-1);
    if (current?.kind === layer.kind && current.stableId === layer.stableId) {
      dispatch({ type: 'UPDATE_TOP_LAYER', layer: {
        ...layer,
        returnAnchor: returnAnchor ?? (trigger ? captureAnchor(trigger) : current.returnAnchor),
      } });
      return;
    }
    dispatch({ type: 'OPEN', layer: { ...layer, returnAnchor: returnAnchor ?? captureAnchor(trigger) } });
  }, [captureAnchor, dispatch]);

  const close = useCallback(() => dispatch({ type: 'CLOSE' }), [dispatch]);
  const updateDraft = useCallback((draft: ReviewAssistantDraft) => {
    dispatch({ type: 'DRAFT_CHANGED', draft });
  }, [dispatch]);

  useEffect(() => {
    viewportRef.current = input.viewport;
    dispatch({ type: 'VIEWPORT_CHANGED' }, input.viewport);
  }, [dispatch, input.viewport]);

  useLayoutEffect(() => {
    // A newly opened fullscreen layer is rendered by the same commit as the
    // state transition. Re-project only modal state after that commit so the
    // background inert walk can see the layer and its companion DOM roots;
    // re-running viewport focus would steal focus from an active composer.
    reprojectModal();
  }, [reprojectModal, state.layers]);

  useEffect(() => {
    const shell = input.shellRef.current;
    if (!shell || typeof MutationObserver === 'undefined') return;
    const reprojectModal = () => {
      if (!stateRef.current.layers.length) return;
      const effects = navigator.transition({
        state: stateRef.current,
        event: { type: 'VIEWPORT_CHANGED' },
        viewport: input.viewport,
      }).effects.filter((effect) => effect.type === 'SET_MODAL_STATE');
      apply(effects);
    };
    const observer = new MutationObserver(reprojectModal);
    observer.observe(shell, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [apply, input.shellRef, input.viewport, navigator]);

  useEffect(() => {
    if (stateRef.current.runId === input.runId) return;
    let raw: string | null = null;
    try { raw = sessionStorage.getItem(persistenceKey(input.runId)); }
    catch { setAnnouncement('当前浏览器无法读取审阅位置，已从运行时间线安全开始'); }
    const next = restoreReviewSurfaceState(raw, {
      runId: input.runId, runRevision: input.runRevision, cursorEpoch: input.cursorEpoch,
    });
    if (!next.assistantDraft.value && stateRef.current.assistantDraft.value) {
      next.assistantDraft = stateRef.current.assistantDraft;
    }
    stateRef.current = next;
    setState(next);
    apply(navigator.transition({ state: next, event: { type: 'VIEWPORT_CHANGED' }, viewport: input.viewport }).effects);
  }, [apply, input.cursorEpoch, input.runId, input.runRevision, input.viewport, navigator]);

  useEffect(() => {
    if (!trapFocus) return;
    const shell = input.shellRef.current;
    if (!shell) return;
    const currentRoots = () => {
      const top = stateRef.current.layers.at(-1);
      return top ? modalRoots(shell, top.stableId) : [];
    };
    const allFocusable = () => currentRoots().flatMap(focusableElements);
    const contains = (candidate: EventTarget | null) => candidate instanceof Node
      && currentRoots().some((root) => root.contains(candidate));
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = allFocusable();
      if (!focusable.length) { event.preventDefault(); currentRoots()[0]?.focus(); return; }
      const first = focusable[0];
      const last = focusable.at(-1)!;
      if (event.shiftKey && (document.activeElement === first || !contains(document.activeElement))) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !contains(document.activeElement))) {
        event.preventDefault(); first.focus();
      }
    };
    const onFocusIn = (event: FocusEvent) => {
      if (!contains(event.target)) allFocusable()[0]?.focus({ preventScroll: true });
    };
    let restoreFrame = 0;
    const onFocusOut = () => {
      cancelAnimationFrame(restoreFrame);
      restoreFrame = requestAnimationFrame(() => {
        restoreFrame = 0;
        if (!contains(document.activeElement)) allFocusable()[0]?.focus({ preventScroll: true });
      });
    };
    document.addEventListener('keydown', onKeyDown, true);
    document.addEventListener('focusin', onFocusIn, true);
    document.addEventListener('focusout', onFocusOut, true);
    let guardFrame = 0;
    const enforceFocus = () => {
      if (!contains(document.activeElement)) allFocusable()[0]?.focus({ preventScroll: true });
      guardFrame = requestAnimationFrame(enforceFocus);
    };
    guardFrame = requestAnimationFrame(enforceFocus);
    return () => {
      cancelAnimationFrame(restoreFrame);
      cancelAnimationFrame(guardFrame);
      document.removeEventListener('keydown', onKeyDown, true);
      document.removeEventListener('focusin', onFocusIn, true);
      document.removeEventListener('focusout', onFocusOut, true);
    };
  }, [close, input.shellRef, state.layers, trapFocus]);

  useEffect(() => () => {
    if (deferredFocusFrameRef.current !== undefined) {
      cancelAnimationFrame(deferredFocusFrameRef.current);
    }
    document.body.classList.remove('standardization-overlay-active');
    for (const [element, original] of inertedRef.current) {
      element.inert = original.inert;
      if (original.ariaHidden === null) element.removeAttribute('aria-hidden');
      else element.setAttribute('aria-hidden', original.ariaHidden);
      delete element.dataset.reviewSurfaceInerted;
    }
    inertedRef.current.clear();
  }, []);

  return { state, announcement, trapFocus, dispatch, open, close, captureAnchor, updateDraft };
}
