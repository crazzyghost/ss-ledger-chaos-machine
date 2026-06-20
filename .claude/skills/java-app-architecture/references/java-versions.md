# Java Version Reference

Always document which Java version a design targets and which version-specific features it relies on, so the implementer knows what they can use. If you target a version other than the project's current one, justify it in an ADR.

**Choosing the version:**
- **Existing project** → match what it already runs on (read it from the build config). Don't silently upgrade; if an upgrade genuinely helps the design, propose it in an ADR with the cost/benefit.
- **Greenfield project** → default to the **latest Java LTS**. New work has no legacy constraint, so there's no reason to start behind; the latest LTS gives you records, sealed classes, pattern matching, and — most importantly for service design — virtual threads. Step down to an older version only for a concrete reason (a deployment platform that pins an older runtime, an organization-wide standard, a library that hasn't caught up).

## What each version unlocks

**Java 8** — Baseline for legacy compatibility.
- Lambdas, the Streams API, `Optional`, `CompletableFuture`, and the `java.time` API.
- Target this only when constrained by an existing legacy environment.

**Java 11** — First modern LTS.
- The standard `HttpClient` (no more pulling in Apache HttpClient for basic needs).
- `var` for local type inference.
- The module system (`module-info.java`).

**Java 17** — A widely deployed enterprise LTS; common as the inherited version in existing projects.
- Records (concise immutable data carriers — ideal for DTOs and value objects).
- Sealed classes (closed type hierarchies that pair well with exhaustive switches).
- Pattern matching for `instanceof`.
- Text blocks (multi-line string literals — great for embedded SQL, JSON, templates).
- `switch` expressions.

**Java 21** — Transformative for concurrent service design.
- **Virtual threads** — cheap, massively scalable threads that make thread-per-request models viable again; reconsider reactive/async complexity when targeting this.
- Structured concurrency (preview) — treat related concurrent tasks as a unit.
- Scoped values (preview) — a safer alternative to thread-locals.
- Record patterns — destructure records directly in pattern matches.
- Sequenced collections — uniform first/last access across ordered collections.

**Java 25** — Latest stable; use newly stabilized features where they fit.
- Value classes, primitive patterns, and other recently stabilized APIs.
- Verify the exact stabilized-vs-preview status of a given feature against current release notes before committing a design to it, since feature status moves between releases.

## How to apply this in a design

- State the target version explicitly in each task file's **Technical Design** section.
- Name the specific features you're using and tie them to a concrete benefit (e.g., "use records for the request/response DTOs to get immutability and `equals`/`hashCode` for free").
- If a feature is in preview for the target version, say so and note the flag/risk, rather than assuming it's GA.
