# M4 unknown boundary return review

## Scope

Read-only review of the first-green unknown-return vertical slice against the M4 return contract. Reviewed inputs are the contract, the public-seam unknown-return RED, and `DataFlowGraphBuilder` only.

## Constraints

No production, test, POM, or documentation changes; no Maven execution; no commit or push.

## Status

Complete. No P0 findings.

## P1 findings

1. `DataFlowGraphBuilder.java:1548-1567` treats every non-local-initializer,
   non-void external return as absent. This includes a consumed direct condition
   such as `if (auditClient.recordStatus(status))`, which cannot have a
   `VariableDeclarator`. Returning `null` is correct for a void or unconsumed
   return, but it is a silent omission for a consumed, unsupported return shape.
   The M4 contract requires `DATA_FLOW_BINDING_UNPROVEN` for that latter case.

2. `DataFlowGraphBuilder.java:1594-1601` also returns `null` when a recognized
   local-initializer return has no supported post-declaration use or no resolved
   local type. It does not distinguish a genuinely unused/discarded result from
   an unsupported consumed-use shape, so the latter is omitted instead of
   receiving an entry-owned local Gap.

The implemented direct-local first slice otherwise remains technology-neutral:
`DataFlowGraphBuilder.java:521-629` creates no literal/effect payload and uses
generic return rules with Java call/use provenance only. It does not inspect
XML/SQL.

## Bounded follow-up RED

Add one public-seam fixture where the exact `AuditClient` boolean result is
consumed directly as a Java `if` condition, rather than first assigned to a
local. Require an entry-owned `DATA_FLOW_BINDING_UNPROVEN` Gap at the call
locator, with no `UNKNOWN_BOUNDARY_RETURN` node. This isolates the required
unsupported-shape accounting without broadening the direct-local first-green
behavior.
