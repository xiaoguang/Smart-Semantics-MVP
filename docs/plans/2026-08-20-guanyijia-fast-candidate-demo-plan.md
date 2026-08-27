# 管伊佳快速候选证据 Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 90–120 分钟内交付一个不含 Tenant 153 业务行、能显示真实数据库证据并明确标识其他来源可信等级的本地候选 Demo。

**Architecture:** 保留现有 V1 `PinnedSourceSnapshotBundle` 及其五源工作流作为回滚面。新增一个只读 `CandidateEvidenceOverlay`：它由现有 MySQL 快照的零行派生 manifest 驱动，附加真实、脱敏的数据库片段和少量可复现的关系投影；GitHub 与官方资料只能按 `FROZEN_RECORD` 或 `GAP` 呈现。Overlay 不是正式 `FrozenDemoEvidenceBundle`，不写 trusted registry、不修改正式 V1、也不需要私有 Codeup/LFS 发布。

**Tech Stack:** TypeScript、Node test runner、React/Vite、现有固定五源 Bundle。

**Spec:** `docs/architecture/multi-source-evidence-governance.md`（Task 4 创建）及本计划。它取代 `docs/plans/2026-08-20-guanyijia-evidence-factory-implementation-plan.md` 的 Task 4–6 执行范围；Task 1–3 已有实现不重做。

## Global Constraints

- 不连接 MySQL、GitHub、官方站点，不抓取、不 OCR、不调用 Codex 或 LLM。
- 只读取现有 MySQL snapshot `20260813T032528Z-abb0502c7d79`；历史 sample/profile 文件保持原样，但 candidate 的 manifest、catalog、Bundle、UI 和索引不得引用它们。
- 允许的数据库类别：DDL（含 view/index/constraint/FK）、PROGRAMMABILITY（procedure/function/trigger/event）与参数化 DML digest；拒绝 SAMPLE、PROFILE、原始行、结果行、参数和 example。
- 当前事实必须保留：95 table、2 view、35 procedure、0 function、0 trigger、1 event、17 DML digest。不得虚构 function/trigger 或数据分布结论。
- private catalog 可保留原始文本文字；prototype 只提交 public redacted projection。public projection 需删去 `AUTO_INCREMENT=<number>`、tenant/user/fixed-ID 环境常量，以及 DML 的次数、耗时、first/last seen。
- `OBSERVED` 表示可逐字定位的已保存原始片段；`FROZEN_RECORD` 表示可校验的结构化归档、不得称为原文；`GENERATED_TARGET` 与 `DERIVED` 不增加根证据；没有支持则是 `GAP`。
- 正式管伊佳 V1、零售 V1/V2、V1 Markdown/Semantic SHA、Catalog fingerprint 和对象计数不可修改。差异只能显示为 candidate。
- UI 默认正文优先，SHA、内部 ref、内部枚举只在技术详情；多标签可访问，但不增加并发写一致性测试。
- 已通过的受控 Codex/no-live 入口审计不在本计划扩展。只有变更其入口文件时才复审该固定边界。

---

### Task 1: 零行业务 MySQL 准入

**Files:**
- Create: `scripts/evidence/guanyijia-admit.mjs`
- Create: `scripts/evidence/guanyijia-admit.test.mjs`
- Modify: `package.json`
- Create: `src/features/guanyijia-evidence-factory/guanyijia-candidate-evidence.generated.ts`

**Interfaces:**

```ts
type CandidateEvidenceClass = 'OBSERVED' | 'FROZEN_RECORD' | 'GENERATED_TARGET' | 'DERIVED';

type CandidateArtifact = {
  artifactRef: string;
  evidenceClass: CandidateEvidenceClass;
  category: 'DDL' | 'PROGRAMMABILITY' | 'DML_DIGEST';
  relativePath: string;
  privateSha256: `sha256:${string}`;
  publicExcerpt: string;
  locator: { kind: 'FILE_LINES'; path: string; startLine: number; endLine: number };
};

type CandidateAdmission = {
  schemaVersion: 1;
  kind: 'GUANYIJIA_ZERO_ROW_CANDIDATE';
  basisSnapshotId: '20260813T032528Z-abb0502c7d79';
  includedObjectCounts: {
    tables: 95; views: 2; procedures: 35; functions: 0; triggers: 0; events: 1; dmlDigests: 17;
  };
  exclusions: readonly ['samples/**', 'profiles/**', 'raw-row', 'query-result', 'bound-parameter', 'example'];
  artifacts: readonly CandidateArtifact[];
  admissionSha256: `sha256:${string}`;
};

export function deriveZeroRowMySqlAdmission(input: {
  snapshotRoot: string;
  manifest: unknown;
  readText(relativePath: string): string;
}): CandidateAdmission;
```

- [ ] **Step 1: Write the failing test**

Create `scripts/evidence/guanyijia-admit.test.mjs` with a self-contained fixture manifest containing one DDL, one PROGRAMMABILITY file, one parameterized DML digest, one SAMPLE and one PROFILE entry. Assert all of the following from one call to `deriveZeroRowMySqlAdmission`:

```js
assert.deepEqual(result.exclusions, [
  'samples/**', 'profiles/**', 'raw-row', 'query-result', 'bound-parameter', 'example',
]);
assert.deepEqual(result.artifacts.map(({ category }) => category), ['DDL', 'DML_DIGEST', 'PROGRAMMABILITY']);
assert.doesNotMatch(JSON.stringify(result), /sample-row|profile-example|Tenant 153|AUTO_INCREMENT=10402000/);
assert.match(result.artifacts[0].publicExcerpt, /AUTO_INCREMENT=REDACTED/);
assert.equal(result.includedObjectCounts.functions, 0);
assert.equal(result.includedObjectCounts.triggers, 0);
assert.deepEqual(result.artifacts[0].locator, {
  kind: 'FILE_LINES', path: 'ddl/tables/jsh_system_config.sql', startLine: 1, endLine: 8,
});
```

Use an absent dynamic import so the first run proves the new production seam is missing.

- [ ] **Step 2: Run the test and record RED**

Run:

```bash
node --test scripts/evidence/guanyijia-admit.test.mjs
```

Expected: FAIL because `scripts/evidence/guanyijia-admit.mjs` does not exist or does not export `deriveZeroRowMySqlAdmission`.

- [ ] **Step 3: Implement minimal deterministic admission**

Implement only local manifest filtering and text projection:

```js
const included = manifest.evidenceFiles.filter((entry) =>
  entry.category === 'DDL'
  || entry.category === 'CONSTRAINT'
  || entry.category === 'PROGRAMMABILITY'
  || entry.category === 'DML_DIGEST',
);
```

Map `CONSTRAINT` to public `DDL`; reject every other category. Reject a DML file whose text contains non-parameterized literals, result JSON, `EXECUTION_COUNT`, `SUM_TIMER_WAIT`, `FIRST_SEEN`, or `LAST_SEEN` in its public projection. For each file, compute a real SHA-256 from private text, derive a line-bounded excerpt, redact public text, sort artifacts by `artifactRef`, then hash canonical JSON plus one newline for `admissionSha256`.

Write only the safe generated TypeScript overlay under `src/features/guanyijia-evidence-factory/`; keep private exact catalog beside the snapshot root, outside prototype source, when the explicit command is run. Add:

```json
"evidence:guanyijia:admit": "node scripts/evidence/guanyijia-admit.mjs"
```

The CLI accepts exactly `--snapshot-root <absolute-path> --output <prototype-generated-ts-path>` and refuses output outside the prototype source path. It must not open a network, database, Git, LFS or Codex capability.

- [ ] **Step 4: Run GREEN and generate the real candidate overlay**

Run:

```bash
node --test scripts/evidence/guanyijia-admit.test.mjs
npm run evidence:guanyijia:admit -- --snapshot-root /Users/yexiaoguang/Documents/ErpMock/modeling-evidence/guanyijia/database/mysql/jsh_erp/snapshots/20260813T032528Z-abb0502c7d79 --output src/features/guanyijia-evidence-factory/guanyijia-candidate-evidence.generated.ts
```

Verify generated public output contains the expected object counts, no `samples/`, no `profiles/`, no `AUTO_INCREMENT=<number>`, and a real `jsh_system_config.sql` fragment for negative-stock configuration.

- [ ] **Step 5: Commit**

```bash
git add scripts/evidence/guanyijia-admit.mjs scripts/evidence/guanyijia-admit.test.mjs package.json src/features/guanyijia-evidence-factory/guanyijia-candidate-evidence.generated.ts
git commit -m "feat: admit zero-row database evidence candidate"
```

### Task 2: 候选治理关系投影

**Files:**
- Create: `src/features/guanyijia-evidence-factory/candidate-governance.ts`
- Create: `src/features/guanyijia-evidence-factory/candidate-governance.test.ts`
- Modify: `src/features/guanyijia-evidence-factory/guanyijia-candidate-evidence.generated.ts`

**Interfaces:**

```ts
export type CandidateClaim = {
  claimId: string;
  subjectRef: string;
  predicate: string;
  normalizedValue: string;
  scope: string;
  effectiveTime?: string;
  evidenceClass: CandidateEvidenceClass;
  evidenceRefs: string[];
};

export type CandidateRelation = {
  leftClaimId: string;
  rightClaimId: string;
  relation: 'CORROBORATES' | 'COMPLEMENTS' | 'CONFLICTS' | 'SCOPE_DIFFERENCE' | 'TEMPORAL_DRIFT' | 'UNSUPPORTED';
  explanation: string;
};

export function compareCandidateClaims(left: CandidateClaim, right: CandidateClaim): CandidateRelation;
```

- [ ] **Step 1: Write failing direct tests**

Cover exactly these cases:

```ts
assert.equal(compareCandidateClaims(sameClaim, sameClaimCopy).relation, 'CORROBORATES');
assert.equal(compareCandidateClaims(databaseNegativeStock, policyNegativeStock).relation, 'CONFLICTS');
assert.equal(compareCandidateClaims(databaseNegativeStock, otherTenantNegativeStock).relation, 'SCOPE_DIFFERENCE');
assert.equal(compareCandidateClaims(asOfCurrentStockGap, policyStockTime).relation, 'UNSUPPORTED');
```

The `UNSUPPORTED` case must require at least one side to have no evidence ref or use `GENERATED_TARGET`; `DERIVED` must never turn one corroboration into two independent supports.

- [ ] **Step 2: Run RED**

Run:

```bash
node --experimental-strip-types --test src/features/guanyijia-evidence-factory/candidate-governance.test.ts
```

Expected: FAIL because module or export is missing.

- [ ] **Step 3: Implement the exact comparison table**

Apply this order:

1. Empty evidence refs or `GENERATED_TARGET` without an observed counterpart returns `UNSUPPORTED`.
2. Different `subjectRef` or `predicate` returns `COMPLEMENTS`.
3. Different scope returns `SCOPE_DIFFERENCE`.
4. Different nonempty `effectiveTime` returns `TEMPORAL_DRIFT`.
5. Equal normalized values returns `CORROBORATES`.
6. Otherwise returns `CONFLICTS`.

Create three candidate claims in the generated overlay: negative stock, status semantics, and `current_stock_as_of` GAP. Do not add a generic graph database, LLM decision path or global source priority.

- [ ] **Step 4: Run GREEN**

Run:

```bash
node --experimental-strip-types --test src/features/guanyijia-evidence-factory/candidate-governance.test.ts
```

- [ ] **Step 5: Commit**

```bash
git add src/features/guanyijia-evidence-factory/candidate-governance.ts src/features/guanyijia-evidence-factory/candidate-governance.test.ts src/features/guanyijia-evidence-factory/guanyijia-candidate-evidence.generated.ts
git commit -m "feat: project candidate evidence relationships"
```

### Task 3: Markdown-first candidate evidence in the existing workbench

**Files:**
- Modify: `src/features/data-standardization/human-readable-evidence.ts`
- Modify: `src/features/data-standardization/human-readable-evidence.test.ts`
- Modify: `src/features/data-standardization/guanyijia-standardization-workbench.tsx`
- Modify: `src/features/data-standardization/data-standardization.test.ts`
- Modify: `tests/e2e/guanyijia-document-reentry.spec.ts`

**Interfaces:**

```ts
type CandidateEvidenceLookup = {
  find(sourceId: string, blockStableCode: string): {
    evidenceClass: CandidateEvidenceClass;
    excerpt?: string;
    locationLabel: string;
    locationValue: string;
    relation?: CandidateRelation;
    gapReason?: string;
  } | undefined;
};
```

- [ ] **Step 1: Write failing direct/UI tests**

Add direct assertions that:

```ts
assert.equal(buildHumanReadableEvidence(mysqlCompilation, mysqlNegativeBlockId)?.excerpt.includes('冻结扫描已识别'), false);
assert.match(buildHumanReadableEvidence(mysqlCompilation, mysqlNegativeBlockId)?.excerpt ?? '', /minus_stock_flag|负库存/);
assert.match(buildHumanReadableEvidence(githubCompilation, githubNegativeBlockId)?.excerpt ?? '', /冻结结构化记录/);
```

Add a workbench test asserting first opened source document uses `MARKDOWN`, and an E2E assertion that the initial database review shows Markdown plus a real source location, not a SHA.

- [ ] **Step 2: Run RED**

Run:

```bash
npm run test:data-standardization
node scripts/run-cp8-playwright.mjs tests/e2e/guanyijia-document-reentry.spec.ts
```

Expected: direct test fails on current template excerpt/default `BLOCKS`; browser assertion fails before the UI change.

- [ ] **Step 3: Implement minimal overlay lookup**

Import only the safe candidate overlay. `buildHumanReadableEvidence` must first look up `(sourceId, stableCode)`:

- `OBSERVED`: display frozen public excerpt and file/line location.
- `FROZEN_RECORD`: display “冻结结构化记录，未保存完整原文”，plus available structured location; never synthesize SQL/Java.
- `GENERATED_TARGET`: display “目标制度提案，待人工确认”。
- `DERIVED`: display “由已确认资料派生，不是独立证据”。
- missing lookup: display “资料缺口”，not a fake excerpt.

Change the initial state from `BLOCKS` to `MARKDOWN`. Keep identification and source-map tabs available. Do not change source runtime state, Markdown bytes, V1 hashes, responsive breakpoints, or any control unrelated to evidence display.

- [ ] **Step 4: Run GREEN**

Run direct tests first, then the single affected browser spec:

```bash
npm run test:data-standardization
node scripts/run-cp8-playwright.mjs tests/e2e/guanyijia-document-reentry.spec.ts
```

Verify port `5202` is closed after the browser runner exits.

- [ ] **Step 5: Commit**

```bash
git add src/features/data-standardization/human-readable-evidence.ts src/features/data-standardization/human-readable-evidence.test.ts src/features/data-standardization/guanyijia-standardization-workbench.tsx src/features/data-standardization/data-standardization.test.ts tests/e2e/guanyijia-document-reentry.spec.ts
git commit -m "feat: show candidate evidence in markdown review"
```

### Task 4: 治理设计、执行规则与候选验收

**Files:**
- Create: `docs/architecture/multi-source-evidence-governance.md`
- Modify: `docs/architecture/guanyijia-fixed-source-snapshot.md`
- Modify: `docs/guanyijia-source-snapshots.md`
- Modify: `AGENTS.md`
- Modify: `docs/plans/2026-08-20-guanyijia-evidence-factory-implementation-plan.md`
- Create: `docs/plans/guanyijia-fast-candidate-demo-report.md`

- [ ] **Step 1: Write the governing design document**

The document must include these exact sections:

```text
1. Goal and non-goals
2. Five displayed sources and three root-evidence roles
3. Evidence class and GAP contract
4. Admission input → artifact → fragment → claim → Markdown flow
5. Comparison decision table
6. Human KEEP/MERGE/DEFER audit and replay
7. Negative stock, status, and time-gap worked examples
8. Future Agent/Frontend seam
9. Known limitations and formal V2 migration
10. Three implementation tiers and incremental GAP closure
```

State explicitly that the candidate does not have business-row distribution claims, complete GitHub source text, official raw HTML/PDF, private publication receipt or formal V2 pin.

The final section must use one comparison table with exactly these three rows:

| Tier | Delivery shape | What is real and auditable | Deliberate GAP | Upgrade required |
| --- | --- | --- | --- | --- |
| Full evidence factory (several focused days) | Private Codeup repository, LFS where needed, full raw capture, multiple Luna proposals, independent validation, formal V2 receipt/pin and remote release | Original MySQL logic plus approved source archive/official assets, generation manifests, verified citations, review decisions and public release receipt | None except unavailable external documents explicitly recorded as GAP | This is the target state; it is not completed in the candidate phase |
| First reduced V2 factory (10–16 focused hours) | Existing snapshots admitted across all roots, controlled Luna composition, complete candidate-to-formal V2 freeze and UI integration | Immutable manifests, proposal runs, public V2 fragments, relationship graph, trusted receipt/pin | GitHub/official coverage may still be FROZEN_RECORD/GAP; no business-row analytics | Add complete source archive/official HTML-PDF and convert eligible records to OBSERVED without changing V1 |
| Fast candidate Demo (90–120 minutes) | Zero-row MySQL admission plus V1 sidecar overlay and three representative relationships | Real local MySQL DDL/SP/DML safe excerpts, exact file/line locators, visible evidence class, deterministic relation explanation | No private publication, no live Luna run, no full V2 Bundle, GitHub/official not treated as original text, no tenant distribution/quality claims | Admit all three roots, run controlled Luna proposals, freeze a formal V2 receipt/pin, then deploy |

Under the table, document the monotonic upgrade rules: every later tier preserves prior manifests and V1; `FROZEN_RECORD` becomes `OBSERVED` only after exact raw bytes and locator validation exist; `GAP` is never silently filled; candidate relations are retained as auditable inputs but formal V2 is rebuilt and revalidated rather than relabeled in place.

- [ ] **Step 2: Update durable constraints**

Update `AGENTS.md` to replace raw-row archival wording with the zero-row whitelist and to freeze the no-live audit scope. In fixed-snapshot docs, state that normal demo/build/test work never rescan sources; candidate overlay is safe public data, while formal V2 remains a later maintenance release. Mark the old evidence-factory plan Task 4–6 as superseded by this candidate plan; retain its completed Task 1–3 history.

- [ ] **Step 3: Run targeted verification**

Run serially:

```bash
npm run test:evidence-factory
node --test scripts/evidence/guanyijia-admit.test.mjs
node --experimental-strip-types --test src/features/guanyijia-evidence-factory/candidate-governance.test.ts
npm run test:data-standardization
npm run test:source-snapshots
npm run test:demo-session
npx tsc -b --pretty false
npm run build
git diff --check
```

For this untracked project surface, also run scoped no-index whitespace checks for every changed source, test and documentation file; parent-repository `git diff --check` alone is insufficient.

- [ ] **Step 4: Produce the candidate report**

Record the exact basis snapshot ID, include/exclude rules, public redaction rules, generated artifact counts, representative real locator, relation outcomes, commands and test counts. Record any skipped private Codeup/LFS, Codex generation, full V2 freeze and remote deployment as deliberate scope boundaries, not as completed work.

- [ ] **Step 5: Commit**

```bash
git add AGENTS.md docs/architecture/multi-source-evidence-governance.md docs/architecture/guanyijia-fixed-source-snapshot.md docs/guanyijia-source-snapshots.md docs/plans/2026-08-20-guanyijia-evidence-factory-implementation-plan.md docs/plans/guanyijia-fast-candidate-demo-report.md
git commit -m "docs: record candidate evidence governance contract"
```

## Self-review checklist

- [ ] Candidate artefacts never contain business rows, profile examples, query results, bound values or workload statistics.
- [ ] No `FROZEN_RECORD` or GAP is rendered as DDL, SQL, Java or official quotation.
- [ ] Existing formal V1 hash/count regression tests remain unchanged and green.
- [ ] Candidate is called candidate in UI/report; no registry pin, publication receipt or formal V2 release is claimed.
- [ ] The design document explains why the comparison process can discover consistency and conflicts without granting LLM output factual authority.
- [ ] Only affected direct tests and one browser re-entry spec are run before final review; no full suite or multi-tab acceptance is added.
