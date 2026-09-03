# Progress: M6 public publication standalone-payload identity GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Diagnose and repair only the established M6 public publication / canonical standalone
  JSON payload identity path. Preserve the seven M6 semantic payloads, M6 module receipt-last
  and analysis-step receipt-last installation, and the existing `G ∪ S` accounting: index and
  receipts contain `G ∪ S`; `graph-gaps.jsonl` contains local `G` only.
- Explicit non-scope: M1--M5 algorithms and drafts, test sources, POM, policy/schema/design
  changes, graph topology, scope semantics, root/receipt prepublication, or any new output.

## Approved contract

- `docs/analysis-steps/03-program-graphs.md` M6: five standalone graph JSON payloads plus
  `graph-index.json` and `graph-gaps.jsonl`, exactly once; only the analysis-step store owns the
  analysis-step root and receipt.
- The existing Luna public seams are the RED. The expected failure is
  `MODULE_INSTALL_REQUEST_INVALID` from
  `AtomicCanonicalPublicationEngine.validateStandaloneJsonArtifact` while installing a final M6
  standalone JSON payload.

## Investigation

- Read scoped `AGENTS.md`, both implementation plans, current M5/M6 progress records, and the
  M6/public-wire contract.
- Confirmed the production path derives standalone IDs in
  `ProgramGraphSetPublicationSpecifier.payloadJson`, and the generic publisher rederives them
  after removing only the `artifactId` field.
- Root cause: `prefix(String)` omitted the required `program-graphs-` namespace for
  `CONTROL_FLOW` and `DATA_FLOW`. Their public JSON IDs therefore differed from the exact
  policy `artifactIdPrefix`; the generic publisher correctly rejected the first affected final
  standalone payload as `MODULE_INSTALL_REQUEST_INVALID`.
- Applied the narrow production correction in `ProgramGraphSetPublicationSpecifier` only. The
  public wire, public schema/type, content bytes, graph topology, seven-file set, and all `G`/`S`
  accounting remain unchanged; only the two content-addressed ID prefixes now match their
  registered policy values.
- Maven is intentionally not run until the concurrent Luna test correction is complete and the
  parent agent explicitly releases that gate.

## Verification plan

1. After the parent release, run only
   `ProgramGraphsPublicationSpecifierTest,ProgramGraphPublicWireTest`.
2. If green, run the exact serial 57-test M3 selector recorded in
   `progress/m5-m6-persistence-publication-green.md`.
3. Run `spotless:apply` and `git diff --check`; record exact output here. No commit.

## Verification

| Command | Result | Exact outcome |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsPublicationSpecifierTest,ProgramGraphPublicWireTest test` | PASS | 2 tests run; 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`. |
| exact 57-test selector from `progress/m5-m6-persistence-publication-green.md` (first run) | BLOCKED | 57 tests run; 1 failure and 1 error. The two named M6 public selectors remain green. The failures were stale Luna-owned expectations: M1 file-level Gap ownership and M6 projection-test policy prefixes. No production behavior was changed for either mismatch. |
| exact 57-test selector from `progress/m5-m6-persistence-publication-green.md` (after Luna corrections) | PASS | 57 tests run; 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`. This includes both named M6 public seams and the real M3 local-Gap-to-JSONL projection. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | `BUILD SUCCESS`. Spotless reports 286 Java files clean; it formatted three pre-existing dirty graph builders outside this task-owned source file. |

`git diff --check` is the final pending mechanical check after the formatting run.

## Next action

No commit. Hand the verified, narrow production correction and this progress record to the parent
agent; it owns integration and the user-requested durable backlog update.
