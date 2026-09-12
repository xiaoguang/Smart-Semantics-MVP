# Progress: JDT Core syntax-helper tests

- Status: COMPLETE
- Agent role: root test owner (sub-agent thread limit reached)
- Scope: Task 1.3 standalone JDT Core syntax helper and Java 17 host client only.
- Started: 2026-09-12

## Required result

- JDT Core, not JavaParser, parses one complete frozen Java file.
- The response preserves complete declarations, parameters, calls, controls, exits and exact UTF-16 source ranges.
- Lambda and nested callable ownership is explicit; duplicate call spellings are not collapsed.
- The JSONL host boundary rejects bad identity, malformed responses, timeout and failed shutdown rather than returning empty syntax.

## Current state

- RED is established. `mvn -f tools/jdt-syntax-helper/pom.xml test` resolved the pinned JDT Core dependency, reached test compilation, and failed with 20 missing-type errors for `JdtSyntaxProtocol` and `JdtSyntaxReader`. No production helper source existed.
- The initial helper projection is GREEN for declarations, calls, controls, exits, lambda ownership, CRLF and UTF-16 ranges. A real frozen `UserService.java` extraction returned `validateCaptcha` (291–319), `registerUser` (607–661) and `checkLoginName` (770–805) with complete source.
- Executing the first shaded JAR exposed an invalid dependency-signature digest. The packaging filter and a real `java -jar` integration test now cover that boundary; helper `verify` passes 5 tests.
- Host process behavior is GREEN for the actual helper, response identity, unknown fields, timeout, nonzero exit and shutdown timeout.

## Next action

- Task 1.4 will consume the frozen syntax response and use only JDT LS for declaration and implementation navigation.
