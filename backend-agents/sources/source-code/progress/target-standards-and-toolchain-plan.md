# Progress: target standards and toolchain plan

- Status: COMPLETE
- Role/model: Sol/ultra planning authority
- Started: 2026-08-31
- Worktree: `/private/tmp/linguan-github-code-target-implementation`
- Scope: create and maintain the user-approved Chinese core plan for implementation standards, open-source dependencies, Maven quality tooling, agent workflow, elapsed-time estimates, default authorization, caller-driven/environment/platform gates for the approved eight-stage target architecture.
- Ownership: this progress file and the plan under `docs/plans/`; this docs-only maintenance unit does not edit POM, code, tests, `.gitignore`, source capture, runtime state, commit, or push.

## Constraints

- Core dependency/plugin/tool/workflow choices are approved. Exact in-scope Maven Central downloads, POM edits, formatting, directly scoped tests, commits, and non-force pushes are default-authorized for their future work units; platform permissions and scoped AGENTS still apply.
- JGit alternate adapter, Taplo, markdownlint-cli2, and Maven Wrapper are caller-driven deferred items, not user-approval blockers; OWASP feed/credentials/cache and any actually needed new JDK are environment-gated. Force push, destructive Git, customer code execution, real model/Provider use, and customer/external source access remain outside authority.
- Preserve Stage01–08 module/publication/store architecture and the docs-only publication gate.
- Prefer mature standard parser/schema/formatter/linter tooling; keep custom code only for domain identities, artifact roots/receipts, evidence/proof/flow semantics, and orchestration invariants.
- At most two agents may be active during future execution; heavy Maven commands are serialized.
- Use only primary official documentation or Maven Central metadata if a current version claim is needed. When confidence is insufficient, state an exact verification gate instead of guessing.

## Current state

- The cross-stage Round-3 correction resumed at its exact Stage02 boundary recorded in `progress/target-cross-stage-persistence-design.md`.
- The repository planning and verification-before-completion skills were read and applied.
- Read-only inventory covered the current POM, scoped AGENTS guidance, the complete target module registry, all direct selector names, Stage01–08 observable artifact sets/counts, and existing toolchain assumptions.
- Created the Chinese plan at `docs/plans/target-standards-and-toolchain-plan.md`; the user approved its six-item core package and later granted default authority for in-scope downloads/POM/format/direct tests/commit/non-force-push without repeated conversational approval. The approved file was renamed from its former `-draft` path so its filename no longer misstates status.
- The approved plan selects isolated Git CLI instead of homemade Git plumbing, keeps JGit deferred until a concrete caller exists, freezes the standard parser/schema/test/plugin matrices, separates overlapping quality responsibilities, defines offline/direct-selector Maven defaults, and limits custom code to the approved domain algorithms.
- The approved plan includes 51 executable checkbox tasks, all Stage01–08/exterior/adapters selectors and observable outputs, the docs-only publication gate, Luna RED/Terra GREEN/Sol debug ownership, per-agent progress, two-agent/heavy-Maven serialization, a 134–190 continuous-hour estimate, default-action authority, and explicit caller/environment/platform gates.
- Read-only toolchain preflight recorded Maven 3.9.16, shell Java 26.0.1, Homebrew JDK 17.0.19 at `/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home`, its stable `/usr/local/opt/openjdk@17/...` symlink, and no user `~/.m2/toolchains.xml`. The approved plan now requires a project-tracked `.mvn/toolchains.xml` using the stable JDK 17 home and prefixes every Maven command with `-t .mvn/toolchains.xml`; it explicitly forbids mutating/falling back to user-level toolchain configuration.
- Additional read-only preflight recorded Node v25.9.0, npm 11.12.1, and xmllint/libxml2 2.9.13 present, with Taplo and markdownlint-cli2 absent. The plan now treats those two CLIs as caller-driven adoption plus official version/integrity gates, and does not install or execute them in this docs-only unit.
- Scoped discoverability is complete: `README.md` links the approved plan under Technical Reference, and `AGENTS.md` requires implementation agents to read it before POM/config/code/test work without duplicating its matrices.
- No authoritative design, POM, code, tests, `.gitignore`, source capture, runtime state, Maven command, dependency download, provider/model/network action, commit, or push was performed.

## Exact next action

- Send the completed Round-3 authoritative correction through fresh independent Sol/ultra review and the parent task's docs-only commit/non-force-push gate.
- After that gate, begin the foundation work unit under the approved/default-authorized plan without repeating authorization questions for its exact listed downloads, POM edit, formatting, direct tests, commit, or non-force push; deferred tools activate only for a concrete caller, environment-gated tools only when prerequisites exist, and only platform-mandated escalation or genuinely out-of-scope work can pause execution.

## Verification

- The final approved-plan gate passed at `plan_lines=431 checkbox_tasks=51 official_links=23 executable_maven_commands=12 shorthand_selector_mentions=1 bad_maven_prefixes=0 stale_approval_markers=0 navigation_links=2 errors=0`.
- Local navigation and Markdown structure passed: the 12 scoped design/plan/navigation docs contain `41` local links with `0` broken, while the 14 scoped docs plus both owned progress records contain `84` balanced fences with `0` unclosed.
- `git diff --check -- <scoped tracked docs>` plus a trailing-whitespace scan over all 14 scoped docs/progress files: PASS — exit 0/no output and no whitespace errors after final progress maintenance.
- Scoped status/name/numstat inspection confirmed that this plan maintenance owns only the approved plan/navigation and two progress records; unrelated pre-existing implementation/config paths were not modified by this task.
- Manual overlap review: PASS — Spotless/gjf owns formatting; PMD owns narrow source rules; SpotBugs owns bytecode correctness; ArchUnit owns architecture; CPD is non-blocking and Checkstyle/Mockito are not preinstalled. Enforcer/Dependency/CycloneDX/OWASP have separate environment/usage/inventory/vulnerability duties.
- Version-confidence review: PASS — 23 official/Maven Central source links support fixed recommendations; Git CLI compatibility, any future JDK vendor build, Taplo, and markdownlint-cli2 deliberately use caller/environment plus official version/integrity gates instead of guessed versions.

## Blockers

- None.
