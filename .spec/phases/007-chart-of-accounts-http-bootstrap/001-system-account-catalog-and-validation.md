# Task 001 - System Account Catalog & Validation

## Functional Requirements
- Define the YAML-bound catalog of SYSTEM accounts the chaos machine must provision in the
  ledger, and validate it at startup. The catalog supplies **account definitions** (role, code,
  name, category, currency, ownership, optional parent, overdraft/min balance) — **not** VA ids.
  Account **codes must be unique**.

## Acceptance Criteria
- [ ] Catalog binds from `chaos.bootstrap.system-accounts` in `chaos-bootstrap.yml` via
      `@ConfigurationProperties`.
- [ ] Startup **fails fast** if any `accountCode` is duplicated or blank, or any required field
      (role, accountName, accountCategory, currency, ownershipType) is missing/invalid.
- [ ] Each entry's `accountCategory` ∈ ledger `AccountCategoryEnum`, `currency` is ISO-4217,
      `ownershipType` = `SYSTEM`, and `accountCode` satisfies the ledger's hierarchical-code shape.
- [ ] Optional `parentRole` references another entry in the catalog (validated to exist; no cycles).
- [ ] Roles are unique; the catalog covers the six MANIFEST roles (with §10 code corrections).

## Technical Design
Catalog entry (record, bound + bean-validated):

```yaml
chaos:
  bootstrap:
    provision-on-startup: true
    system-accounts:
      - role: SETTLEMENT_ACCOUNT
        accountCode: ASSET.BANK.SETTLEMENT.0000000000001.GHS
        accountName: Bank Settlement Account
        accountCategory: ASSET
        currency: GHS
        ownershipType: SYSTEM
      - role: PLATFORM_FLOAT
        accountCode: ASSET.PLATFORM.FLOAT
        accountName: Platform Float
        accountCategory: ASSET
        currency: GHS
        ownershipType: SYSTEM
      - role: PLATFORM_FLOAT_MTN
        accountCode: ASSET.PLATFORM.FLOAT.MTN      # corrected (was dup of TELECEL)
        accountName: Platform Float - MTN
        accountCategory: ASSET
        currency: GHS
        ownershipType: SYSTEM
        parentRole: PLATFORM_FLOAT
      - role: PLATFORM_FLOAT_TELECEL
        accountCode: ASSET.PLATFORM.FLOAT.TELECEL
        accountName: Platform Float - Telecel
        accountCategory: ASSET
        currency: GHS
        ownershipType: SYSTEM
        parentRole: PLATFORM_FLOAT
      - role: PLATFORM_FEE
        accountCode: REVENUE.PLATFORM.FEE
        accountName: Platform Fee Revenue
        accountCategory: REVENUE
        currency: GHS
        ownershipType: SYSTEM
      - role: PROVIDER_FEE
        accountCode: REVENUE.PROVIDER.FEE          # corrected (was blank)
        accountName: Provider Fee Revenue
        accountCategory: REVENUE
        currency: GHS
        ownershipType: SYSTEM
```

```java
@Validated
public record SystemAccountDefinition(
    @NotNull AccountRole role,
    @NotBlank @HierarchicalCode String accountCode,
    @NotBlank String accountName,
    @IsInEnum(AccountCategory.class) String accountCategory,
    @ISO4217 String currency,
    @IsInEnum(AccountOwnershipType.class) String ownershipType,   // SYSTEM
    @Nullable AccountRole parentRole,
    @Nullable @PositiveOrZero BigDecimal overdraftLimit,
    @Nullable @PositiveOrZero BigDecimal minimumBalance) {}
```

Validation component `SystemAccountCatalogValidator` runs on context refresh (or as the first
step of the bootstrap runner): asserts unique `accountCode` and `role`, resolves `parentRole`
references, and computes a topological provisioning order (parents before children), rejecting
cycles.

## Implementation Notes
- Package `account/bootstrap` (`SystemAccountCatalog`, `SystemAccountDefinition`,
  `SystemAccountCatalogValidator`).
- Reuse Phase 001 validation annotations (`@ISO4217`, `@IsInEnum`) and the ledger-compatible
  `@HierarchicalCode` shape.
- Keep account codes the single contract with the ledger; document that they must match the
  ledger's hierarchical-code rules.

## Non-Functional Requirements
- Validation < 50ms. Misconfiguration aborts startup with a precise message naming the bad code/role.

## Dependencies
Phase 001 (config + validation annotations). Consumed by Tasks 002/003 of this phase.

## Risks & Mitigations
- *Code typos vs ledger rules* → `@HierarchicalCode` + a test asserting the six seeded codes validate.
- *Hidden duplicate after env override* → validation runs post-binding, after overrides applied.

## Testing Strategy (intent; implemented in Phase 006)
- Unique-code + blank-code rejection; parent resolution + cycle detection; topological order.

## Deployment Strategy
Catalog shipped in `chaos-bootstrap.yml`; overridable per environment via mounted file/env.
