# Progress: JDT Core syntax-helper implementation

- Status: COMPLETE
- Agent role: root production owner (sub-agent thread limit reached)
- Scope: Task 1.3 standalone JDT Core helper and Java 17 host boundary only.
- Started: 2026-09-12
- Completed: 2026-09-12

## Implemented

- Added an independent Java 21 helper project pinned to JDT Core 3.47.0 and Jackson 2.21.4.
- JDT Core parses a complete supplied source file with binding resolution disabled and projects declarations, methods, constructors, initializers, lambdas, ordered parameters, supported call sites, controls, exits and diagnostics.
- Every retained source value is sliced from the original UTF-16 Java string using JDT start/length positions; the helper never scans repository paths and has no JavaParser dependency.
- Added explicit ELSE and FINALLY scope records while retaining the containing IF/TRY relation.
- Added a strict one-request-at-a-time JSONL host client with identity/fingerprint checks, bounded stderr, query timeout, process-failure and shutdown-timeout behavior.
- The helper shaded JAR removes invalid dependency signature metadata and is exercised by a real `java -jar` integration test.

## Real-source checkpoint

The helper read the already frozen jshERP commit `8c30ce7861570458920175e200bb2a6442713580` file `com/jsh/erp/service/UserService.java` with SHA-256 `95552b925cec2902502f2b1dcd6631164c5497eeb9f48c3104b25e7f553c343a` and returned no parse errors.

- `validateCaptcha`: complete declaration at lines 291–319.
- `registerUser`: complete declaration at lines 607–661, including default status setters, user insertion, role relation, tenant construction and tenant insertion.
- `checkLoginName`: complete declaration at lines 770–805.
- `registerUser` call records include exact actual arguments for `userMapper.insertSelective(ue)`, `userService.updateUserTenant(user)`, `userBusinessService.insertUserBusiness(ubObj, null)` and `tenantMapper.insertSelective(tenant)`.

This proves exact syntax extraction only. JDT LS target resolution and recursive implementation collection remain Task 1.4.

## Verification

- `mvn -o -f tools/jdt-syntax-helper/pom.xml verify`: PASS, 6 unit tests and 1 executable-JAR integration test.
- `mvn -o -t .mvn/toolchains.xml -Dtest=JdtSyntaxHelperClientTest test`: PASS, 4 tests.
- Shaded-JAR inventory: `JDT_CORE=1 JACKSON=1 JAVAPARSER=0` for the representative class checks.
- Formatting was applied with the repository Spotless configuration and the helper's own identical Spotless configuration.

## Next action

Implement Task 1.4: use JDT LS call hierarchy, definition and implementation queries to resolve these syntax call positions and collect a coherent entry context.
