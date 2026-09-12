# JDT application discovery integration

Status: COMPLETE

## Scope

- Project JDT Core declarations and annotations into the engine-neutral catalog.
- Make Spring HTTP and MyBatis Java discovery consume that catalog on the JDT path.
- Persist overload-safe `methodKey` and complete `methodRange` on every HTTP entry.
- Upgrade the HTTP-entry draft and public wire together; reject the retired wire.

## Progress

- Task 1.4 is committed as `7ad2d20` with real registration and financial contexts.
- JDT Core declarations, fields, methods and structured annotations now populate the neutral catalog.
- The selected-session catalog drives Spring HTTP and Mapper discovery; the JDT execution path does not invoke a JavaParser discoverer.
- HTTP entries persist overload-safe `methodKey` and complete `methodRange`; the HTTP draft, public line, publication and direct readers now use v3 and reject the retired v2 wire.
- The syntax helper protocol is v2. It receives only verified projected source roots and approved local classpath entries. JDT Core binding resolves external wildcard-import annotation identities; call navigation remains owned by JDT LS.
- The real frozen jshERP test resolves `PostMapping`, retains the registration Service implementations, and reaches the financial Mapper boundary in one shared index.

## Verification

- `tools/jdt-syntax-helper`: `mvn -o -Dtest=JdtSyntaxReaderTest test` — PASS, 8 tests.
- Host helper protocol: `mvn -o -t .mvn/toolchains.xml -Dtest=JdtSyntaxHelperClientTest test` — PASS, 4 tests.
- Task 1.5 aggregate including real frozen-source JDT: PASS, 44 tests, 0 failures/errors/skips.
