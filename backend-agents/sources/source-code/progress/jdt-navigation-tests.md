# JDT navigation tests

Status: COMPLETE

## Scope

- Lock the private JDT Core declaration navigation range needed by JDT LS.
- Lock JDT LS declaration, definition, implementation and outgoing-call candidate handling.
- Lock deterministic bounded collection of complete repository method bodies.

## Progress

- Task 1.3 is committed as `a6a41a8`.
- The first Task 1.4 RED requires named declarations to expose their exact identifier range while anonymous callable declarations expose no navigation range.
- JDT LS navigation now preserves call-hierarchy, definition and implementation candidates while JDT Core owns exact method, call, control and exit source ranges.
- Production collection de-duplicates method bodies and physical call occurrences independently, merges multiple JDT navigation reasons for the same callable, retains multiple concrete candidates, and records unresolved boundaries.
- The readiness handshake consumes JDT's `language/status = ServiceReady`; the declaration probe remains as a deterministic fallback for protocol tests and servers that do not publish the extension.
- Raw `definition` / `implementation` arrays are decoded by their protocol fields so both `Location` and `LocationLink`, including empty or null implementation results, are accepted without LSP4J `Either` ambiguity.
- macOS `/var` and `/private/var` aliases are canonicalized before enforcing the frozen-project boundary. This corrected rejection of JDT locations that were physically inside the projected snapshot.
- The real fixed jshERP projection (273 Java files) now produces two inspectable contexts from one project session:
  - registration: 89 methods, 244 call sites; includes `UserService.validateCaptcha`, `checkLoginName`, `registerUser`, and the user/tenant inserts;
  - financial query: 8 methods, 11 call sites; includes `AccountHeadService`, the `AccountHeadMapperEx` declaration, and `billId` actual/formal correspondence.
- Actual local outputs are retained under ignored `.workspace/jdt-production-navigation/registration-context.json` and `financial-context.json`.

## Verification

- `mvn -o -f tools/jdt-syntax-helper/pom.xml verify`: PASS, 6 unit tests and 1 executable integration test.
- `mvn -o -t .mvn/toolchains.xml -Dtest=JdtSyntaxHelperClientTest,JdtProjectSessionTest,JdtNavigationResolverTest,EntryCodeCollectorTest,JdtRealSourceCollectionTest,JavaCodeEngineContractTest,JdtEngineFinalContractTest,EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest test`: PASS, 58 tests, 0 failures/errors/skips.
- Real registration plus financial collection completed in 53.27 seconds inside the aggregate selector and reused one JDT project session/catalog.
