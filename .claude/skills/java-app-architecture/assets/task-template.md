# Task File Template

Copy this structure for every task file (`NNN-feature-name.md`) in a phase directory. Keep the sections in this order. Don't omit any — write "N/A" if a section genuinely doesn't apply, since an empty section still tells the implementer you considered it.

```markdown
# Task NNN - Feature Name

## Functional Requirements
What the feature must do. Express as clear, testable statements.

## Acceptance Criteria
Specific conditions that must be true for the task to be considered complete.
- [ ] Criterion 1
- [ ] Criterion 2

## Technical Design
Architecture, data flow, API design, and diagrams (Mermaid syntax).
Reference Java packages, classes, interfaces, and design patterns where applicable.
Specify the target Java version and any version-specific features used.

## Implementation Notes
Guidance for the engineer who will implement this:
- Files to create or modify (full paths following Maven/Gradle conventions)
- Packages, classes, and interfaces to define
- Libraries and frameworks to use (prefer standard library; justify external dependencies)
- Migration strategies, backward compatibility concerns
- Code examples where helpful
- Build configuration changes (pom.xml / build.gradle additions)

## Non-Functional Requirements
Performance targets, security considerations, scalability expectations.

## Dependencies
Other tasks, features, services, or external systems this task depends on or is blocked by.

## Risks & Mitigations
Potential challenges and how to address them.

## Testing Strategy
How this feature will be tested (unit, integration, parameterized scenarios, etc.).
Specify testing frameworks (JUnit 5, AssertJ, Mockito, Testcontainers, etc.).

## Deployment Strategy
How this feature will be rolled out (e.g., feature flags, phased rollout, etc.).
```

## Why each section exists

- **Functional Requirements / Acceptance Criteria**: the *what* and the *done test*. Phrase acceptance criteria so they could be turned into test assertions almost verbatim.
- **Technical Design**: the *how*, with diagrams. This is where Mermaid sequence/class/ER diagrams belong and where you commit to the Java version and the patterns being used.
- **Implementation Notes**: the engineer's launch pad — concrete file paths, class and package names, and dependency additions so they aren't guessing at structure.
- **Non-Functional Requirements**: the qualities that won't show up in a happy-path test but will sink the feature in production if ignored.
- **Dependencies / Risks & Mitigations**: surfaces sequencing constraints and known hazards before they become surprises mid-build.
- **Testing & Deployment Strategy**: makes verification and rollout part of the design rather than an afterthought.
