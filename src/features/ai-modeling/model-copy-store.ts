import type { LinguanMetric, LinguanModelSnapshot, ModelVersion } from './types.ts';
import type { StorageLike } from './runtime.ts';

type StoredCopies = { schemaVersion: 1; copies: Record<string, LinguanModelSnapshot> };

const key = 'linguan:model-copies:v1';
const clone = <T>(value: T): T => structuredClone(value);

export type ModelCopyStore = {
  publish(snapshot: LinguanModelSnapshot): void;
  read(systemCode: string, modelVersion: ModelVersion | 'WORKSPACE'): LinguanModelSnapshot | null;
  updateMetric(systemCode: string, modelVersion: ModelVersion, metricCode: string, patch: Partial<Pick<LinguanMetric, 'name' | 'description' | 'unit'>>): void;
  addManualRule(systemCode: string, modelVersion: ModelVersion, rule: { name: string; description: string }): void;
  addAlias(systemCode: string, modelVersion: ModelVersion, text: string): void;
  reset(): void;
};

export function createModelCopyStore(storage: StorageLike): ModelCopyStore {
  const load = (): StoredCopies => {
    const raw = storage.getItem(key);
    if (!raw) return { schemaVersion: 1, copies: {} };
    try {
      const parsed = JSON.parse(raw) as StoredCopies;
      return parsed.schemaVersion === 1 ? parsed : { schemaVersion: 1, copies: {} };
    } catch {
      return { schemaVersion: 1, copies: {} };
    }
  };
  const save = (state: StoredCopies) => storage.setItem(key, JSON.stringify(state));
  const copyKey = (systemCode: string, version: string) => `${systemCode}:${version}`;
  const mutableCopy = (state: StoredCopies, systemCode: string, modelVersion: ModelVersion) => {
    const copy = state.copies[copyKey(systemCode, modelVersion)];
    if (!copy) throw new Error('模型工作副本不存在');
    if (copy.readOnly) throw new Error('历史模型为只读版本');
    return copy;
  };
  return {
    publish(snapshot) {
      const state = load();
      const id = copyKey(snapshot.systemCode, snapshot.modelVersion);
      const existing = state.copies[id];
      Object.entries(state.copies).forEach(([id, copy]) => {
        if (copy.systemCode === snapshot.systemCode) state.copies[id] = { ...copy, readOnly: true };
      });
      const next = clone(snapshot);
      if (existing && (existing.sourceSha256 !== snapshot.sourceSha256 || existing.projectionRevision !== snapshot.projectionRevision)) {
        const oldMetrics = new Map(existing.metrics.map((metric) => [metric.code, metric]));
        next.metrics = next.metrics.map((metric) => {
          const old = oldMetrics.get(metric.code);
          return old ? { ...metric, name: old.name, description: old.description, unit: old.unit } : metric;
        });
        next.executableRules.push(...existing.executableRules.filter((rule) => rule.source === 'MANUAL'));
        const firstEntity = next.entities[0];
        const manualAliases = existing.aliases.filter((alias) => alias.id.startsWith('manual-alias-')).map((alias) => ({
          ...alias,
          targetType: alias.targetType ?? 'ENTITY' as const,
          targetId: alias.targetId ?? firstEntity?.code ?? '',
          ownerId: alias.ownerId ?? null,
          confidence: alias.confidence ?? 1,
          evidenceIds: alias.evidenceIds ?? [],
        }));
        next.aliases.push(...manualAliases);
        const unlinkedMetricCount = existing.metrics.filter((metric) => !next.metrics.some((item) => item.code === metric.code)).length;
        if (unlinkedMetricCount) next.compatibilityNotes.push(`旧工作副本有 ${unlinkedMetricCount} 个指标修改无法重新关联，原存储仍被保留。`);
      }
      state.copies[id] = { ...next, readOnly: false };
      save(state);
    },
    read(systemCode, modelVersion) {
      if (modelVersion === 'WORKSPACE') return null;
      const copy = load().copies[copyKey(systemCode, modelVersion)];
      return copy ? clone(copy) : null;
    },
    updateMetric(systemCode, modelVersion, metricCode, patch) {
      const state = load();
      const copy = mutableCopy(state, systemCode, modelVersion);
      copy.metrics = copy.metrics.map((metric) => metric.code === metricCode ? { ...metric, ...patch } : metric);
      save(state);
    },
    addManualRule(systemCode, modelVersion, rule) {
      const state = load();
      const copy = mutableCopy(state, systemCode, modelVersion);
      copy.executableRules.push({ id: `manual-rule-${Date.now()}`, ...rule, source: 'MANUAL' });
      save(state);
    },
    addAlias(systemCode, modelVersion, text) {
      const state = load();
      const copy = mutableCopy(state, systemCode, modelVersion);
      const target = copy.entities[0];
      if (!target) throw new Error('当前模型没有可关联的实体');
      copy.aliases.push({ id: `manual-alias-${Date.now()}`, text, targetType: 'ENTITY', targetId: target.code, ownerId: null, confidence: 1, evidenceIds: [] });
      save(state);
    },
    reset() {
      storage.removeItem(key);
    },
  };
}
