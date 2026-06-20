# Phase DESIGN.md Template

Each phase directory (`.spec/phases/NNN-phase-name/`) gets exactly one `DESIGN.md`. It is the phase's single source of truth: a reader should understand the whole phase from this file and follow links down into individual task files for detail. Update it whenever the phase changes.

Use this structure:

```markdown
# Phase N - Phase Name

## Summary
A few sentences on what this phase delivers.

## Motivation
Why this phase exists — the problem or opportunity it addresses.

## User-Facing Changes
What changes for end users, API consumers, or operators as a result of this phase.

## Architecture Impact
How this phase affects the overall system architecture. Reference ADRs.
Include a system-level diagram (Mermaid) when it aids understanding.

## Edge Cases
Boundary conditions, failure modes, and unusual inputs the phase must handle.

## Testing Strategy
The phase-level approach to verification across its tasks.

## Deployment Strategy
How the phase as a whole is rolled out.

## Tasks
Ordered list linking to each task file in this phase, with a one-line description.
- [001 - Feature Name](./001-feature-name.md) — brief description
- [002 - Feature Name](./002-feature-name.md) — brief description

## Parallel Tasks
Which tasks can be worked on concurrently vs. which must be sequential.
Call out the dependency chain explicitly.
```

## Notes

- The **Tasks** section is the index into the phase — every task file should be linked here, in implementation order.
- The **Parallel Tasks** section is what lets an implementer (or a team) safely fan out work. Be explicit about what blocks what; "these three are independent, but all depend on 001" is exactly the kind of guidance that prevents wasted effort.
- Link out to any ADRs in `.spec/decisions/` that this phase relies on, so the reasoning is one click away.
- Keep `.spec/ARCHITECTURE.md` updated to link to this `DESIGN.md` whenever you add or substantially revise a phase.
