# Progress: target-stage02-mapper-catalog

- Status: IN_PROGRESS
- Agent role: Stage 02 M3 implementation
- Model: Terra / xhigh (implementation), guided by the approved Stage 02 design
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Implement only deterministic MyBatis Mapper candidate cataloging and its direct public-seam tests.
- Approved inputs: docs/DESIGN.md, docs/stages/02-discover-application-and-entries.md, persisted Stage01/M1 artifacts, and the approved GitHub Code Agent implementation plan.
- Current branch/worktree: codex/github-code-target-implementation at /private/tmp/linguan-github-code-target-implementation

## Completed

- Read the M3 contract: it catalogs Java/XML/config candidates and complete site dispositions only. It must not create a Java-to-XML call binding, SQL data-flow, Fact, or Flow.
- Added the frozen Mapper Java/XML positive fixture and observed the expected public-seam RED: M3 cataloger, result, and profile were absent.
- Implemented the M3 persisted positive vertical slice. Standard MyBatis DOCTYPE is accepted under a secure parser; the candidate output remains CANDIDATE_NOT_YET_BOUND.

## Current state

- The positive M3 selector is GREEN: one frozen Java interface and matching XML root/statement become a catalog candidate and a source-evidenced mapper-resource site. Security and mismatch Gap cases remain.

## Changed files

- progress/target-stage02-mapper-catalog.md
- src/main/java/com/linguan/codemd/target/stage02/mappercatalog/
- src/test/java/com/linguan/codemd/target/stage02/applicationprofile/Stage02MapperCapabilityCatalogerTest.java
- src/test/resources/target/stage02/application-profile/DepotHeadMapper.{java,xml}
- Shared Stage02 fixture policy and source inventory inputs

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| mvn -t .mvn/toolchains.xml -o -Dtest=Stage02MapperCapabilityCatalogerTest test | RED | Only M3 public seam types were absent. |
| mvn -t .mvn/toolchains.xml -o -Dtest=Stage02MapperCapabilityCatalogerTest test | PASS | 1 test, 0 failures/errors/skips; candidate catalog remains unbound. |

## Decisions

- Standard MyBatis DOCTYPE syntax will be accepted only with external DTD, entity, schema, and network resolution disabled; a secure parser cannot silently fall back to permissive XML parsing.
- M3 will persist candidate relations and typed Gap/site accounting. Stage03 alone may establish a unique Mapper binding.

## Blockers

- None.

## Exact next action

- Add one public-seam external-entity rejection test; it must fail closed before any XML resource is admitted.

## Resume checks

- Re-read this file, git status --short, Stage02 M3 design, and run only Stage02MapperCapabilityCatalogerTest after changes.
