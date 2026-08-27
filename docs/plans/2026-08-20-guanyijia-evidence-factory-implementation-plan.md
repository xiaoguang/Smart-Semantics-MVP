# Guanyijia Evidence Factory Implementation Plan

> **Execution status (2026-08-20):** Task 1–3 implementation and their
> scoped reviews remain the reusable foundation. The original Task 4–6 formal
> private-LFS/V2-release path is intentionally superseded for the current
> delivery by `docs/plans/2026-08-20-guanyijia-fast-candidate-demo-plan.md`.
> That candidate plan does not claim a formal V2 release and must not alter
> this document's completed Task 1–3 history.

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` task-by-task. Each task starts
> from a failing test and has a task-scoped review gate.

**Goal:** Replace metadata-derived demo evidence with immutable, traceable
five-source evidence packages and make Markdown plus real evidence the primary
Guanyijia review experience.

**Architecture:** Private raw source artifacts live in the separately managed
Codeup LFS repository; the prototype consumes only a sanitized,
content-addressed frozen evidence Bundle. `EvidenceFactory` is the deep module
whose small interface loads verified public Bundle content, validates trace
links, and rejects missing evidence. Explicit maintenance adapters capture
and generate new Bundle versions; demo, tests, recovery and builds have no
external-source or LLM capability.

**Tech Stack:** TypeScript, Vite/React, Node test runner, IndexedDB/local
browser stores, Codex ChatGPT session, Git LFS in a private Codeup repository.

**Spec:** User-approved “Linguan 真实五源证据工厂与内容优先审阅计划” in
this task conversation, 2026-08-20.

## Global Constraints

- Preserve Guanyijia V1, all protected hashes/fingerprint/counts, and retail
  V1/V2. Semantic differences become candidates, never automatic changes.
- The raw MySQL Tenant 153 archive and raw source checkout are private LFS
  content only; no raw rows go to the prototype, browser Bundle or deployment.
- `capture` and `generate` are explicit maintenance commands. Routine
  development, demo, test, recovery, build and hot reload never rescan or use
  LLMs.
- LLM generation uses `gpt-5.6-luna` at `xhigh` through authenticated Codex
  ChatGPT session only; output is a proposal and must pass citation validation.
- All evidence excerpts displayed to users must have real artifact locators;
  generated targets are visibly non-observed.
- Multi-tab access is allowed; concurrent write correctness is a documented
  future TODO, not an acceptance condition.

---

### Task 1: Evidence contract and verified public-bundle seam

**Files:**
- Create: `src/features/guanyijia-evidence-factory/types.ts`
- Create: `src/features/guanyijia-evidence-factory/runtime.ts`
- Create: `src/features/guanyijia-evidence-factory/evidence-factory.test.ts`
- Modify: `src/features/guanyijia-standardization-story/types.ts`
- Modify: `package.json`
- Modify: `docs/architecture/guanyijia-fixed-source-snapshot.md`

**Interfaces:**
- Produce `RawSourceSnapshotManifest`, `EvidenceFragment`,
  `GenerationRunManifest`, and `FrozenDemoEvidenceBundle`.
- Expose one caller-facing interface:
  `EvidenceFactory.readBundle({ storyKey })` and
  `EvidenceFactory.readFragment({ evidenceRef })`.
- `readBundle` validates the Bundle digest, all source identities, all trace
  links and every non-target fragment before returning any compilation.

- [ ] Write a failing test that rejects a Bundle whose block trace refers to a
  missing `EvidenceFragment`.
- [ ] Run `node --experimental-strip-types --test src/features/guanyijia-evidence-factory/evidence-factory.test.ts` and confirm the expected missing-module failure.
- [ ] Implement the smallest content-addressed types/runtime that verifies the
  source identities, fragment references and digest.
- [ ] Re-run the test and add the package script `test:evidence-factory`.
- [ ] Update the fixed-snapshot architecture document with the public/private
  boundary and no-rescan invariant.

### Task 2: Explicit private capture preflight and immutable raw manifests

**Files:**
- Create: `scripts/evidence/guanyijia-capture.mjs`
- Create: `scripts/evidence/guanyijia-capture.test.mjs`
- Create: `docs/guanyijia-evidence-capture.md`
- Modify: `package.json`

**Interfaces:**
- `npm run evidence:guanyijia:capture -- --manifest <path>` verifies private
  Codeup remote, Git LFS, explicit authorization flag, fixed Git commit and
  capture adapters before any external read.
- It writes a manifest only after its artifacts are content-digested. It never
  puts raw row payloads in `linguan-prototype-v2`.

- [ ] Write a failing test that a capture request without LFS/private remote/
  authorization fails before opening a reader.
- [ ] Verify that test fails against the absent command.
- [ ] Implement the preflight-only command and deterministic manifest writer.
- [ ] Re-run the test; document the capture input/output paths and the
  `onblocked`/partial-capture failure behavior.

### Task 3: Codex proposal generation and independent citation validation

**Files:**
- Create: `scripts/evidence/guanyijia-generate.mjs`
- Create: `scripts/evidence/guanyijia-generate.test.mjs`
- Create: `scripts/evidence/schemas/guanyijia-proposal.schema.json`
- Create: `docs/guanyijia-evidence-generation.md`
- Modify: `package.json`

**Interfaces:**
- The generator checks `codex login status`, removes `OPENAI_API_KEY`, invokes
  Luna xhigh read-only only, and emits `GenerationRunManifest` plus Proposal
  JSON. It cannot alter a frozen Bundle.
- Citation validation rejects proposal statements whose artifact locator and
  excerpt do not match the raw manifest. `GENERATED_TARGET` proposals retain
  their label.

- [ ] Write failing tests for API-key/auth fallback rejection and a proposal
  with a non-matching citation.
- [ ] Confirm each failure occurs before implementing the command.
- [ ] Implement the schema, invocation envelope, manifest recording and
  independent locator/excerpt validator.
- [ ] Re-run tests without calling a live model; write exact operator steps
  for the explicit, authorized live command.

### Task 4: Build the v2 frozen public evidence Bundle from accepted proposals

**Files:**
- Create: `scripts/evidence/guanyijia-freeze.mjs`
- Create: `scripts/evidence/guanyijia-freeze.test.mjs`
- Create: `src/features/guanyijia-evidence-factory/guanyijia-evidence-v2.generated.ts`
- Modify: `src/features/guanyijia-standardization-story/pinned-source-snapshot.ts`
- Modify: `src/features/guanyijia-standardization-story/compiler.ts`
- Modify: `src/features/data-standardization/human-readable-evidence.ts`

**Interfaces:**
- Freezing atomically produces compilations, blocks, assertions, Markdown,
  trace links, real public evidence excerpts and conflict projections.
- Every observed block has an assertion/anchor/fragment. No template “frozen
  SQL/DDL” excerpt is accepted. Formal V1 differences are candidates.

- [ ] Write failing tests that reject template evidence, an observed block
  without a real trace, and a Bundle whose formal semantic payload differs
  without a candidate record.
- [ ] Confirm the failures against v1 fixture behavior.
- [ ] Implement the v2 compiler input and public artifact projection; preserve
  v1 as a selectable rollback bundle until v2 has accepted real artifacts.
- [ ] Re-run tests and `npm run test:source-snapshots`.

### Task 5: Markdown-first review, evidence triad and action contract

**Files:**
- Modify: `src/features/data-standardization/guanyijia-standardization-workbench.tsx`
- Modify: `src/features/data-standardization/standardization-facts-inspector.tsx`
- Modify: `src/features/data-standardization/human-readable-evidence.ts`
- Modify: `src/features/data-standardization/data-standardization.css`
- Modify: `src/features/data-standardization/data-standardization.test.ts`
- Modify: `tests/e2e/guanyijia-five-source-story.spec.ts`

**Interfaces:**
- Default source document tab is Markdown; selecting paragraph/object/evidence
  updates the three-way evidence projection. Technical fields stay within a
  disclosure.
- Visible actions only execute a command, navigate a review target, or expand
  a named panel. The five-source inspector controls document re-entry and
  collapse at the 1280/900 responsive transitions.

- [ ] Write failing direct and browser tests for Markdown-first opening,
  source-object-Markdown navigation, no visible template evidence/SHA, and a
  visible state change for every current-task action.
- [ ] Confirm failures.
- [ ] Implement the smallest UI projection changes and Chinese business labels.
- [ ] Re-run direct test plus targeted story/re-entry/responsive browser tests
  serially.

### Task 6: One-time capture/generate/freeze release and verification

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture/m3-m4-standardization.md`
- Modify: `docs/demo-step-by-step.md`
- Modify: `docs/plans/future-multi-tab-standardization-todo.md`
- Create: `docs/plans/guanyijia-evidence-factory-release-report.md`

**Interfaces:**
- A release report records the source manifests, accepted Luna proposal runs,
  redaction status, Bundle digest, immutable previous Bundle, and verified V1
  status/candidates.

- [ ] Run the capture preflight. If Codeup private-repository or LFS authority
  is unavailable, record the exact blocking prerequisite and do not place raw
  artifacts in ordinary Git.
- [ ] After authorized capture/generation/freeze, verify all manifests and
  public Bundle fragments without network/LLM access.
- [ ] Run only the test commands directly covering the changed areas, followed
  by types, lint, build and the five-source/responsive/protected E2E flows
  serially.
- [ ] Request independent Spec and Quality review before any remote deployment.
