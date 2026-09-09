# Progress: exact-call denominator contract review

- Status: COMPLETE
- Agent role: Sol design authority; bounded contract interpretation and accepted clarification
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Interpret the published Step 04 M1/M2 `JAVA_EXACT_CALL` denominator contract for malformed `METHOD` canonical values and absent source/rule closure, then publish only the root-approved minimal normative clarification and bounded implementation/test guidance.
- Approved inputs: repository, backend, and source-code `AGENTS.md`; both `docs/plans/` implementation plans; `docs/analysis-steps/04-proven-code-facts.md` section 8.0 M1 and section 8.0.2; relevant existing implementation and tests as read-only evidence; root acceptance authorizing the minimal Step 04 clarification.
- Current branch/worktree: `codex/source-analysis-process-materials/main12005e8` in `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created the owned progress file before any durable edit.
- Confirmed the worktree already contains concurrent design, implementation, test, and progress changes owned by other agents; they remain untouched.
- Read the applicable repository, backend, and source-code instructions.
- Read both implementation plans completely.
- Traced the published Step 04 M1 denominator, exact-call successor, M2 all-or-nothing, failure-code, and frozen-ruling clauses, then checked only the directly relevant reader/enumerator records to confirm the reported reachable states.
- Sent the root coordinator the bounded interim ruling; no architecture round or unrelated source audit was opened.
- Applied the root-accepted three-sentence clarification only in Step 04 §8.0.2, preserving all unrelated concurrent implementation-audit edits.

## Current state

- **Resolved ruling:** the prior contract was genuinely underspecified, and the accepted §8.0.2 clarification now closes only those gaps. After upstream endpoint/reference validation, every owner-entry × qualifying exact edge remains represented by the exact `entryId|callTargetEdgeId|JAVA_EXACT_CALL` denominator key as either one candidate or one `NOT_APPLICABLE` disposition; the existing missing-endpoint fatal remains unchanged.
- M1 validates canonical grammar before generic evidence closure. A present malformed `METHOD.canonicalValue` is exactly `NOT_APPLICABLE/REQUIRED_ATOM_MISSING` with singleton `missingRoles=[TARGET_METHOD_CANONICAL]`; it does not additionally report evidence roles.
- For canonical-valid tuples, wholly absent generic closures produce `NOT_APPLICABLE/PROOF_NOT_CLOSED`. `missingRoles` is the nonempty ordered subsequence of `[CALL_SITE_EVIDENCE, CALL_TARGET_EDGE_EVIDENCE, TARGET_METHOD_EVIDENCE]` whose corresponding generic closures are absent, retaining that fixed order.
- M2 remains distinct: after M1 emits a valid candidate, any missing permitted exact rule pair retains that candidate and rejects the entire Fact, with direct failing atoms rooted at `PROOF_NOT_CLOSED` and siblings at `COMPOSITE_FACT_REJECTED`.
- M1/M2 publishers and readers, M3 accounting, and Luna/Terra tests consume the ruling without changing statuses, schema, API, model boundary, public files/counts, or denominator/all-or-nothing invariants. The existing v3 replacement and historical-v2 rejection remain exact; no compatibility reader, dual registration, or migration path was introduced.
- No Java, test, fixture, plan, Maven, Provider, customer, network, or Git mutation was authorized or performed.

## Changed files

- `docs/analysis-steps/04-proven-code-facts.md` — one root-approved normative paragraph in §8.0.2.
- `progress/exact-call-denominator-review.md` — this owned ruling and handoff note.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Concurrent changes identified; owned note is the only path this task will edit. |
| Read applicable `AGENTS.md` files | PASS | Scope, publication gate, fail-closed evidence rules, and progress format confirmed. |
| Read both `docs/plans/*.md` files | PASS | Step 04 M1→M2 sequencing, RED/GREEN ownership, and no-compatibility/no-live constraints confirmed. |
| `nl -ba docs/analysis-steps/04-proven-code-facts.md` scoped reads | PASS | Exact clauses at lines 164-179, 185-207, 257-289, and 386-416 traced. |
| Directly relevant reader/enumerator record reads | PASS | Confirmed malformed nonblank METHOD canonical values and missing per-subject generic closure can reach enumeration, and the current exact-call branch silently returns. No code was edited or executed. |
| Exact §8.0.2 hunk read (`nl -ba ... \| sed -n '279,292p'`) | PASS | The accepted clarification is exactly line 285; the preceding missing-endpoint fatal at line 283 and existing M2/version/handoff text remain intact. |
| Clarification token check (`rg -n ...`) | PASS | Endpoint/reference qualifier, both M1 codes, all four exact missing-role names, M2 root/sibling codes, consumers, invariants, and no-compatibility language are present at line 285. |
| Docs/progress trailing-whitespace check (`awk ...`) | PASS | No trailing whitespace found in either changed Markdown file. |
| Maven/tests/Provider/customer/network/Git mutation | NOT RUN | Explicitly outside this bounded review. |

## Decisions

- Terra and Luna must use the published §8.0.2 mapping, not infer a code from the current implementation.
- Do not reuse `JAVA_EXACT_CALL_TARGET_NOT_METHOD` for a node whose kind is actually `METHOD`, and do not reuse `DATA_FLOW_BINDING_UNPROVEN` for this non-data-flow Fact family.
- The clarification reuses existing statuses and stable codes. It changes no schema, subsystem, public file/count, compatibility policy, Step 03 contract, or model boundary; it only makes two M1 negative branches and the already-required M2 negative deterministic.
- `exactTargets.size()>1` is not needed to decide the reported two cases. The current silent return would raise the same no-shrink concern, but its exact reference-versus-accounting classification was not separately specified or expanded in this review.

## Blockers

- None in this bounded clarification. Root will isolate and merge the normative hunk plus this progress note before affected negative implementation.

## Exact next action

- After the docs-only clarification merges, Luna adds the two M1 negative REDs (including fixed multi-role order) and the distinct M2 permitted-pair negative; Terra implements only those frozen outcomes. Independent positive M2 work may continue.

## Resume checks

- Re-read this note and `git status --short`.
- Verify that this task changed only the one §8.0.2 normative paragraph and this owned progress note.
- Verify any later implementation cites the published §8.0.2 mapping rather than inferring from current code.
