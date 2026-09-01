# Backend Source Agent Instructions

## Scope and inheritance

- These rules apply to everything under `backend-agents/` and
  supplement the repository and `linguan-prototype-v2` instructions.
- This directory owns offline source-to-nine-section candidate processes. It
  is not a browser runtime, a live source connector, or a publication surface.
- Each source directory is owned by one explicitly assigned Source Agent. An
  agent may modify only its assigned source directory unless the user also
  assigns a shared-contract change.

## Source ownership

- `sources/mysql/`: frozen database material to nine-section candidate.
- `sources/source-code/`: frozen source code to one repository-level
  nine-section candidate. Its target name is source-oriented rather than
  hosting-provider-oriented.
- `sources/business-docs/`: frozen business-document material to candidate.
- `sources/erp-policy/`: frozen ERP policy material to candidate.
- `sources/terminology-graph/`: frozen terminology graph to candidate.
- Implementation work is source-scoped: work only in the source directory
  named by the user. Repository-structure or shared-contract edits require
  explicit scope in addition to a source assignment.
- The pre-reset implementation currently remains under `sources/github-code/`.
  Only an explicitly approved docs-only naming work unit may describe the
  transition there. The implementation Wire Reset moves the complete source
  Agent to `sources/source-code/`; it does not leave an alias, symlink,
  compatibility reader, or second implementation at the old path.
- The target Maven coordinate is
  `org.sourceanalysis:source-code-analysis-agent`, the display name is
  `Source Code Analysis Agent`, and the Java root is `org.sourceanalysis.app`.
  Its eight closed semantic package/key pairs are
  `org.sourceanalysis.app.analysis.inventory` / `verified-source-inventory`,
  `org.sourceanalysis.app.analysis.discovery` / `application-discovery`,
  `org.sourceanalysis.app.analysis.graph` / `program-graphs`,
  `org.sourceanalysis.app.analysis.fact` / `proven-code-facts`,
  `org.sourceanalysis.app.analysis.flow` / `business-flows`,
  `org.sourceanalysis.app.analysis.interpretation` / `flow-interpretation`,
  `org.sourceanalysis.app.analysis.knowledge` / `repository-knowledge`, and
  `org.sourceanalysis.app.analysis.document` / `nine-section-document`.
  Numerical prefixes order only their documentation and `steps/` runtime
  directories; see the source-scoped instructions for the exact paths.

## Shared contract seam

- `../shared/source-agent-contracts/` is the only shared seam between Source Agents. Keep its
  interface small and language-neutral: frozen input identity, candidate
  identity, the canonical `NineSectionProfile`, validation receipt,
  reader-candidate lineage, and explicit selection. Do not add a shared JSON
  Schema until at least two source implementations require the same concrete
  wire contract and that change is separately approved.
- Source-specific file selection, parsing, prompting, evidence rules, and
  rendering helpers belong inside the owning source directory.
- Do not import another source directory's implementation. If two real source
  adapters require the same rule, propose it as a shared-contract change first.

## Fixed-input and generation gates

- Consume only explicitly named, immutable source snapshots. Never fetch,
  rescan, refresh, or silently substitute a source.
- Missing, corrupt, or drifting input fails closed. Do not synthesize excerpts,
  placeholders, claims, or chapters.
- Creating or editing this workspace does not authorize a new generation run.
  Historical approval of the phase-one page or of V7 does not authorize another
  LLM/source call, capture, candidate generation, freeze, package, deployment,
  or runtime activation; each such maintenance action requires its own current,
  explicit authorization.
- A Source Agent produces an immutable, unpublished candidate. Selection,
  freeze, package, and activation are separate explicit maintenance actions.
- Never modify V6, a Pinned Bundle, a formal model, formal evidence, or a prior
  candidate. Improvements create new candidate identities linked to a parent.

## Generative-task contract

- Every model-backed task declares its exact frozen input and output artifact,
  ideal acceptance, task-specific fatal errors, deterministic validation,
  human review, `ReaderCandidateRound` 1 and 2, the post-round-2 rule, and
  estimated execution time before invocation.
- `ReaderCandidateRound` counts product-content candidates for one frozen
  source artifact. It permits at most two: Round 1 is the initial candidate;
  Round 2 is the only replacement and may run only when explicitly authorized
  against named findings. Never retry automatically, create a third product
  candidate, or rerun another source because one source failed.
- `FlowInterpretationRound` is a source-Agent-internal review protocol within
  one reader candidate. Its R1 interpretation and R2 precision review operate
  on the same frozen Flow and contribute to the same candidate; R2 is not a
  replacement reader candidate and does not consume another
  `ReaderCandidateRound`.
- Reader Candidate Round 1 prioritizes factual correctness, source identity,
  required coverage, schema, structure, and traceability. Reader Candidate
  Round 2 fixes only named accuracy, clarity, density, or presentation findings
  without expanding frozen input or scope.
- After Reader Candidate Round 2, proceed only when no task-specific fatal
  error remains. Preserve and report non-fatal warnings. If a fatal error
  remains, stop and preserve the candidate and validation receipt.

## Verification

- The final Markdown must be rendered deterministically from a validated
  structured candidate; a model must not author source identities, paths,
  line ranges, hashes, evidence refs, review decisions, or Markdown styling.
- Tests must cover the shared interface or the assigned source implementation,
  use frozen fixtures, and make no live or model calls.
- Run only tests added by or directly covering the current change.
