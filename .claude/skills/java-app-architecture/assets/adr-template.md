# ADR (Architecture Decision Record) Template

Write an ADR for any consequential architectural choice: database selection, framework choice, sync-vs-async communication, build tool (Maven vs. Gradle), target Java version, serialization format (JSON/Avro/Protobuf), and similar. Store them in `.spec/decisions/` named `NNN-short-decision-title.md` (e.g., `001-chose-postgres-over-mongo.md`).

Use this structure:

```markdown
# ADR NNN - Decision Title

## Status
Proposed | Accepted | Superseded by ADR NNN

## Context
What problem or question prompted this decision. Include the forces at play:
constraints, requirements, and the options considered.

## Decision
What was decided and why. State the choice plainly, then the reasoning that
made it win over the alternatives.

## Consequences
Both the positive and the negative impacts of this decision. Be honest about
the trade-offs and what this choice closes off or makes harder.
```

## Notes

- The **Context** and **Consequences** sections are the point of an ADR. A decision recorded without its reasoning and trade-offs is just an assertion; the value is letting a future reader understand *why* without having to re-litigate it.
- When a later decision overturns this one, don't delete or rewrite the original — set its **Status** to `Superseded by ADR NNN` and write the new ADR. The trail of superseded decisions is itself useful history.
- Link the ADR from the `DESIGN.md` and task files it affects so the reasoning travels with the work that depends on it.
- Number ADRs sequentially across the whole project (not per phase) — they form one global decision log.
