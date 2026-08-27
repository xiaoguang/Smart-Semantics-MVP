import type { ReviewContentCache } from './index.ts';
import type { DeliverableMetadataStore, StandardizationRunReader } from '../standardization-deliverable/types.ts';
import type { StandardizationMetadataStore } from '../standardization-run/types.ts';

export type ReviewPersistenceFault =
  | 'STANDARDIZATION_CAS_STALE'
  | 'DELIVERABLE_CAS_STALE'
  | 'REVIEW_CACHE_QUOTA'
  | 'DELIVERY_EVENT_APPEND_FAILED';

export type ReviewPersistenceFaultController = {
  arm(fault: ReviewPersistenceFault): void;
  pending(fault: ReviewPersistenceFault): boolean;
};

export function createReviewPersistenceFaultController(): ReviewPersistenceFaultController & {
  consume(fault: ReviewPersistenceFault): boolean;
} {
  const armed = new Set<ReviewPersistenceFault>();
  return {
    arm(fault) { armed.add(fault); },
    pending(fault) { return armed.has(fault); },
    consume(fault) { return armed.delete(fault); },
  };
}

type InternalController = ReturnType<typeof createReviewPersistenceFaultController>;

export function withStandardizationMetadataFaultpoints(
  delegate: StandardizationMetadataStore,
  controller: InternalController,
): StandardizationMetadataStore {
  return {
    read: () => delegate.read(),
    compareAndSet(expectedVersion, nextRaw) {
      if (controller.consume('STANDARDIZATION_CAS_STALE')) return Promise.resolve(false);
      return delegate.compareAndSet(expectedVersion, nextRaw);
    },
  };
}

export function withDeliverableMetadataFaultpoints(
  delegate: DeliverableMetadataStore,
  controller: InternalController,
): DeliverableMetadataStore {
  return {
    read: () => delegate.read(),
    compareAndSet(expectedVersion, nextRaw) {
      if (controller.consume('DELIVERABLE_CAS_STALE')) return Promise.resolve(false);
      return delegate.compareAndSet(expectedVersion, nextRaw);
    },
  };
}

export function withReviewContentCacheFaultpoints(
  delegate: ReviewContentCache,
  controller: InternalController,
): ReviewContentCache {
  return {
    read: (contentRef) => delegate.read(contentRef),
    estimate: () => delegate.estimate(),
    async write(contentRef, content) {
      if (controller.consume('REVIEW_CACHE_QUOTA')) {
        throw new DOMException('review cache quota faultpoint', 'QuotaExceededError');
      }
      await delegate.write(contentRef, content);
    },
  };
}

export function withStandardizationRunReaderFaultpoints(
  delegate: StandardizationRunReader,
  controller: InternalController,
): StandardizationRunReader {
  return {
    read: (runId) => delegate.read(runId),
    readEventPayload: (runId, eventId) => delegate.readEventPayload(runId, eventId),
    async appendEvent(input) {
      if (controller.consume('DELIVERY_EVENT_APPEND_FAILED')) {
        throw new Error('injected delivery event append failure');
      }
      return delegate.appendEvent(input);
    },
  };
}
