# jOOQ Data Access Refactor Design

## Context

The project is a Kotlin/Ktor multi-module application following a clean architecture layout. The current persistence implementation lives in `adapters/database-adapter` and uses manual JDBC with `DataSource`, `Connection`, `PreparedStatement`, and `ResultSet`.

No ORM dependency such as Hibernate, JPA, Exposed, or Spring Data was found in the implementation. For this refactor, "current ORM" means the existing hand-written JDBC persistence layer that will be replaced by jOOQ-based data access.

## Scope

This refactor is limited to the persistence operations already implemented for these tables:

- `wallets`
- `policies`
- `wallet_policies`

Out of scope:

- New payment tables or payment endpoints from the README.
- New policy categories beyond the behavior already implemented.
- Changes to domain entities, use case contracts, HTTP routes, or response formats unless required to preserve compilation.

## Current Persistence Operations

`WalletDAOSpecImpl` currently performs:

- `save(wallet)`: starts a transaction, ensures `DEFAULT_VALUE_LIMIT`, inserts a wallet, and links the default active policy.
- `findActivePolicyName(walletId)`: reads the active policy name for a wallet.
- `existsById(walletId)`: checks wallet existence.

`PolicyDAOSpecImpl` currently performs:

- `save(policy)`: inserts a policy and returns the persisted row.
- `findAll()`: lists policies ordered by `created_at DESC`.
- `findByWalletId(walletId)`: lists wallet policies ordered by `wallet_policies.created_at DESC`, including the active flag.
- `findById(policyId)`: finds a policy by id.
- `findActiveByWalletId(walletId)`: finds the active policy for a wallet.
- `assignPolicy(walletId, policyId)`: starts a transaction, deactivates the current active policy, and inserts the new active policy.

## Target Architecture

The `database-adapter` module will use generated jOOQ classes as the typed schema access layer. Flyway migrations remain the source of truth for database structure.

The module will add:

- jOOQ runtime dependency.
- Gradle jOOQ code generation configuration.
- Generated jOOQ sources for the existing PostgreSQL schema.
- A small factory or configuration function that creates `DSLContext` from the existing `DataSource`.

The existing DAO interfaces in `boundary-context/database-boundary` remain unchanged. The core and web layers continue to depend only on those interfaces and domain entities.

## Code Generation

jOOQ code generation will target the `public` schema after applying the existing Flyway migrations. The generated package should live under the database adapter namespace, for example:

`com.trace.payment.adapters.database.jooq`

Only generated objects needed for `wallets`, `policies`, and `wallet_policies` should be used by production code. The generation setup may see Flyway metadata tables, but DAO code must not depend on them.

Generated sources should be wired into the `database-adapter` Kotlin compilation so the DAO implementations can use strongly typed table and field references.

## Transaction Design

Transactions remain at the DAO method boundaries where the current implementation already requires atomicity:

- `WalletDAOSpecImpl.save`
- `PolicyDAOSpecImpl.assignPolicy`

These methods will use `DSLContext.transactionResult` or `DSLContext.transaction` so all statements run on the same jOOQ transaction context.

Read-only methods and single insert methods may use the injected `DSLContext` directly.

## Mapping Design

Mapping stays inside the database adapter. jOOQ records are converted to the existing domain entities:

- `WalletEntity`
- `PolicyEntity`

The mapping must preserve current behavior:

- UUID values are returned as `UUID`.
- timestamp columns are returned as `Instant`.
- numeric policy limits are returned as `BigDecimal`.
- nullable policy fields remain nullable.
- `PolicyEntity.active` is only set when reading through `wallet_policies` joins; otherwise it remains `null`.

## Error And Constraint Behavior

Database constraints remain responsible for enforcing uniqueness and referential integrity. The existing partial unique index on active wallet policies remains unchanged:

`idx_wallet_policies_unique_active`

The refactor should not introduce broad exception translation unless existing tests require it. Existing application error handling and use case validation should remain the source of HTTP behavior.

## Testing Strategy

The existing integration tests remain the main acceptance criteria:

- `WalletIntegrationTest`
- `PolicyIntegrationTest`
- `DatabaseMigrationIntegrationTest`

Additional database-adapter tests may be added only if they cover behavior not already verified after replacing JDBC with jOOQ.

Verification should run at least:

- `./gradlew test`

If jOOQ code generation is a separate task, the build must make tests depend on generation or document the required generated sources path through Gradle configuration.

## Acceptance Criteria

- All existing persistence operations for `wallets`, `policies`, and `wallet_policies` use jOOQ instead of manual JDBC in production DAO code.
- `WalletDAOSpec` and `PolicyDAOSpec` remain compatible with current use cases.
- Existing tests pass without changing business expectations.
- Flyway migrations remain unchanged unless a build-time code generation requirement explicitly needs configuration outside production migrations.
- DAO code uses generated jOOQ table and field references instead of raw SQL strings for implemented operations.
- Transactional behavior of wallet creation and policy assignment is preserved.
