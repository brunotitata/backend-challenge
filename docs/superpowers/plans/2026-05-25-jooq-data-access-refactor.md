# jOOQ Data Access Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing manual JDBC DAO implementation for `wallets`, `policies`, and `wallet_policies` with jOOQ generated schema classes and jOOQ queries.

**Architecture:** Keep the existing clean architecture boundaries intact: use cases depend on `WalletDAOSpec` and `PolicyDAOSpec`, while `adapters/database-adapter` owns jOOQ configuration, generated schema access, transactions, and entity mapping. Flyway migrations remain the schema source of truth; jOOQ code generation reads those migrations and produces typed Java sources consumed by Kotlin DAO code.

**Tech Stack:** Kotlin 1.9.22, Ktor 2.3.12, Gradle multi-module, PostgreSQL, HikariCP, Flyway, jOOQ OSS, Testcontainers.

**Commit Policy:** Do not commit during this implementation. The user explicitly requested no commits.

---

## File Structure

- Modify: `build.gradle.kts` to add the jOOQ Gradle plugin version at the root with `apply false`.
- Modify: `adapters/database-adapter/build.gradle.kts` to add jOOQ runtime/codegen dependencies, configure generated source directory, and wire code generation before Kotlin compilation.
- Create: `adapters/database-adapter/src/main/kotlin/com/trace/payment/adapters/database/config/JooqFactory.kt` to create `DSLContext` from the existing `DataSource`.
- Modify: `adapters/database-adapter/src/main/kotlin/com/trace/payment/adapters/database/dao/WalletDAOSpecImpl.kt` to use `DSLContext`, generated jOOQ table references, and jOOQ transactions.
- Modify: `adapters/database-adapter/src/main/kotlin/com/trace/payment/adapters/database/dao/PolicyDAOSpecImpl.kt` to use `DSLContext`, generated jOOQ table references, and jOOQ transactions.
- Modify: `application/src/main/kotlin/com/trace/payment/Application.kt` to instantiate one `DSLContext` and inject it into DAOs.
- Modify: `application/src/test/kotlin/com/trace/payment/application/WalletIntegrationTest.kt` to instantiate `DSLContext` in test wiring and, if helpful, use jOOQ for direct database assertions.
- Modify: `application/src/test/kotlin/com/trace/payment/application/PolicyIntegrationTest.kt` to instantiate `DSLContext` in test wiring.
- Modify: `adapters/database-adapter/src/test/kotlin/com/trace/payment/adapters/database-adapter/DatabaseMigrationIntegrationTest.kt` only if build/codegen requires generated sources during adapter tests.

---

### Task 1: Configure jOOQ Code Generation

**Files:**
- Modify: `build.gradle.kts`
- Modify: `adapters/database-adapter/build.gradle.kts`

- [ ] **Step 1: Add the jOOQ plugin to the root build**

Edit `build.gradle.kts` so the plugins block contains:

```kotlin
plugins {
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
    id("nu.studer.jooq") version "9.0" apply false
}
```

- [ ] **Step 2: Configure jOOQ in the database adapter**

Edit `adapters/database-adapter/build.gradle.kts` to apply the plugin, add dependencies, generated sources, and codegen configuration:

```kotlin
plugins {
    kotlin("jvm")
    id("nu.studer.jooq")
}

val flywayVersion = "10.20.1"
val testcontainersVersion = "1.20.4"
val hikariVersion = "5.1.0"
val jooqVersion = "3.19.15"

dependencies {
    implementation(project(":entities"))
    implementation(project(":database-boundary"))
    implementation(project(":common"))

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.jooq:jooq:$jooqVersion")

    jooqGenerator("org.jooq:jooq-meta-extensions:$jooqVersion")
    jooqGenerator("org.postgresql:postgresql:42.7.4")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated-src/jooq/main"))
    }
}

jooq {
    version.set(jooqVersion)
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)
            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        includes = "wallets|policies|wallet_policies"
                        excludes = "flyway_schema_history"
                        inputSchema = "public"
                        properties.add(org.jooq.meta.jaxb.Property().withKey("scripts").withValue("src/main/resources/db/migration/*.sql"))
                        properties.add(org.jooq.meta.jaxb.Property().withKey("sort").withValue("semantic"))
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isPojos = false
                        isDaos = false
                        isFluentSetters = false
                    }
                    target.apply {
                        packageName = "com.trace.payment.adapters.database.jooq"
                        directory = layout.buildDirectory.dir("generated-src/jooq/main").get().asFile.path
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Run code generation**

Run: `./gradlew :database-adapter:generateJooq`

Expected: task succeeds and generated Java sources appear under `adapters/database-adapter/build/generated-src/jooq/main/com/trace/payment/adapters/database/jooq`.

- [ ] **Step 4: Verify generated table names**

Inspect generated sources and confirm these classes exist:

```text
com.trace.payment.adapters.database.jooq.tables.Wallets
com.trace.payment.adapters.database.jooq.tables.Policies
com.trace.payment.adapters.database.jooq.tables.WalletPolicies
```

If class names differ only by capitalization/pluralization, use the actual generated names in later tasks.

---

### Task 2: Add DSLContext Factory

**Files:**
- Create: `adapters/database-adapter/src/main/kotlin/com/trace/payment/adapters/database/config/JooqFactory.kt`

- [ ] **Step 1: Create the factory**

Create `JooqFactory.kt`:

```kotlin
package com.trace.payment.adapters.database.config

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

object JooqFactory {
    fun create(dataSource: DataSource): DSLContext {
        return DSL.using(dataSource, SQLDialect.POSTGRES)
    }
}
```

- [ ] **Step 2: Compile the adapter**

Run: `./gradlew :database-adapter:compileKotlin`

Expected: compilation succeeds.

---

### Task 3: Refactor Wallet DAO To jOOQ

**Files:**
- Modify: `adapters/database-adapter/src/main/kotlin/com/trace/payment/adapters/database/dao/WalletDAOSpecImpl.kt`
- Modify: `application/src/main/kotlin/com/trace/payment/Application.kt`
- Modify: `application/src/test/kotlin/com/trace/payment/application/WalletIntegrationTest.kt`
- Modify: `application/src/test/kotlin/com/trace/payment/application/PolicyIntegrationTest.kt`

- [ ] **Step 1: Change constructor dependency**

Replace `WalletDAOSpecImpl` constructor from `DataSource` to `DSLContext`:

```kotlin
class WalletDAOSpecImpl(
    private val dsl: DSLContext,
) : WalletDAOSpec {
```

Add imports:

```kotlin
import org.jooq.DSLContext
import org.jooq.impl.DSL
import com.trace.payment.adapters.database.jooq.tables.WalletPolicies.WALLET_POLICIES
import com.trace.payment.adapters.database.jooq.tables.Wallets.WALLETS
import com.trace.payment.adapters.database.jooq.tables.Policies.POLICIES
```

Remove imports for `java.sql.Connection`, `java.sql.Timestamp`, and `javax.sql.DataSource`.

- [ ] **Step 2: Replace `save` with a jOOQ transaction**

Use this implementation:

```kotlin
override fun save(wallet: WalletEntity): WalletEntity {
    return dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        ensureDefaultPolicy(tx)
        val savedWallet = insertWallet(tx, wallet)
        insertDefaultActivePolicy(tx, savedWallet.id)
        savedWallet
    }
}
```

- [ ] **Step 3: Replace read methods**

Use these implementations:

```kotlin
override fun findActivePolicyName(walletId: UUID): String? {
    return dsl
        .select(POLICIES.NAME)
        .from(WALLET_POLICIES)
        .join(POLICIES).on(POLICIES.ID.eq(WALLET_POLICIES.POLICY_ID))
        .where(WALLET_POLICIES.WALLET_ID.eq(walletId))
        .and(WALLET_POLICIES.ACTIVE.eq(true))
        .fetchOne(POLICIES.NAME)
}

override fun existsById(walletId: UUID): Boolean {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(WALLETS)
            .where(WALLETS.ID.eq(walletId)),
    )
}
```

- [ ] **Step 4: Replace private write helpers**

Use these helper methods:

```kotlin
private fun insertWallet(tx: DSLContext, wallet: WalletEntity): WalletEntity {
    val record = tx
        .insertInto(WALLETS)
        .set(WALLETS.ID, wallet.id)
        .set(WALLETS.OWNER_NAME, wallet.ownerName)
        .set(WALLETS.CREATED_AT, wallet.createdAt.atOffset(java.time.ZoneOffset.UTC))
        .returning(WALLETS.ID, WALLETS.CREATED_AT)
        .fetchOne() ?: error("Wallet insert did not return a row")

    return WalletEntity(
        id = record.get(WALLETS.ID),
        ownerName = wallet.ownerName,
        createdAt = record.get(WALLETS.CREATED_AT).toInstant(),
    )
}

private fun insertDefaultActivePolicy(tx: DSLContext, walletId: UUID) {
    val affectedRows = tx
        .insertInto(WALLET_POLICIES, WALLET_POLICIES.WALLET_ID, WALLET_POLICIES.POLICY_ID, WALLET_POLICIES.ACTIVE)
        .select(
            tx.select(
                DSL.`val`(walletId),
                POLICIES.ID,
                DSL.`val`(true),
            )
                .from(POLICIES)
                .where(POLICIES.NAME.eq("DEFAULT_VALUE_LIMIT")),
        )
        .execute()

    check(affectedRows == 1) {
        "Default policy DEFAULT_VALUE_LIMIT not found"
    }
}

private fun ensureDefaultPolicy(tx: DSLContext) {
    tx.insertInto(POLICIES)
        .columns(
            POLICIES.NAME,
            POLICIES.CATEGORY,
            POLICIES.MAX_PER_PAYMENT,
            POLICIES.DAYTIME_DAILY_LIMIT,
            POLICIES.NIGHTTIME_DAILY_LIMIT,
            POLICIES.WEEKEND_DAILY_LIMIT,
        )
        .select(
            tx.select(
                DSL.`val`("DEFAULT_VALUE_LIMIT"),
                DSL.`val`("VALUE_LIMIT"),
                DSL.`val`(java.math.BigDecimal("1000.00")),
                DSL.`val`(java.math.BigDecimal("4000.00")),
                DSL.`val`(java.math.BigDecimal("1000.00")),
                DSL.`val`(java.math.BigDecimal("1000.00")),
            ).whereNotExists(
                tx.selectOne()
                    .from(POLICIES)
                    .where(POLICIES.NAME.eq("DEFAULT_VALUE_LIMIT")),
            ),
        )
        .execute()
}
```

- [ ] **Step 5: Update production wiring**

In `application/src/main/kotlin/com/trace/payment/Application.kt`, create a DSL context after the data source:

```kotlin
val dataSource = DatabaseFactory.createFromEnv()
val dsl = JooqFactory.create(dataSource)

val walletDAO = WalletDAOSpecImpl(dsl)
val policyDAO = PolicyDAOSpecImpl(dsl)
```

Add import:

```kotlin
import com.trace.payment.adapters.database.config.JooqFactory
```

- [ ] **Step 6: Update test wiring for wallet DAO**

In tests that instantiate `WalletDAOSpecImpl(dataSource)`, create `dsl = JooqFactory.create(dataSource)` and instantiate `WalletDAOSpecImpl(dsl)`.

Use the same pattern in helpers:

```kotlin
val dsl = JooqFactory.create(dataSource)
val walletDAO = WalletDAOSpecImpl(dsl)
```

- [ ] **Step 7: Run focused wallet tests**

Run: `./gradlew :application:test --tests com.trace.payment.application.WalletIntegrationTest`

Expected: wallet integration tests pass.

---

### Task 4: Refactor Policy DAO To jOOQ

**Files:**
- Modify: `adapters/database-adapter/src/main/kotlin/com/trace/payment/adapters/database/dao/PolicyDAOSpecImpl.kt`
- Modify: `application/src/test/kotlin/com/trace/payment/application/PolicyIntegrationTest.kt`

- [ ] **Step 1: Change constructor dependency**

Replace the constructor from `DataSource` to `DSLContext`:

```kotlin
class PolicyDAOSpecImpl(
    private val dsl: DSLContext,
) : PolicyDAOSpec {
```

Add imports:

```kotlin
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import com.trace.payment.adapters.database.jooq.tables.Policies.POLICIES
import com.trace.payment.adapters.database.jooq.tables.WalletPolicies.WALLET_POLICIES
```

Remove imports for `java.sql.ResultSet`, `java.sql.Timestamp`, `java.time.Instant`, and `javax.sql.DataSource` if unused.

- [ ] **Step 2: Replace create and read methods**

Use these implementations:

```kotlin
override fun save(policy: PolicyEntity): PolicyEntity {
    val record = dsl
        .insertInto(POLICIES)
        .set(POLICIES.ID, policy.id)
        .set(POLICIES.NAME, policy.name)
        .set(POLICIES.CATEGORY, policy.category)
        .set(POLICIES.MAX_PER_PAYMENT, policy.maxPerPayment)
        .set(POLICIES.DAYTIME_DAILY_LIMIT, policy.daytimeDailyLimit)
        .set(POLICIES.NIGHTTIME_DAILY_LIMIT, policy.nighttimeDailyLimit)
        .set(POLICIES.WEEKEND_DAILY_LIMIT, policy.weekendDailyLimit)
        .set(POLICIES.CREATED_AT, policy.createdAt.atOffset(java.time.ZoneOffset.UTC))
        .set(POLICIES.UPDATED_AT, policy.updatedAt.atOffset(java.time.ZoneOffset.UTC))
        .returning()
        .fetchOne() ?: error("Policy insert did not return a row")

    return mapToPolicyEntity(record)
}

override fun findAll(): List<PolicyEntity> {
    return dsl
        .selectFrom(POLICIES)
        .orderBy(POLICIES.CREATED_AT.desc())
        .fetch { mapToPolicyEntity(it) }
}

override fun findByWalletId(walletId: UUID): List<PolicyEntity> {
    return dsl
        .select(POLICIES.fields().toList() + WALLET_POLICIES.ACTIVE)
        .from(POLICIES)
        .join(WALLET_POLICIES).on(WALLET_POLICIES.POLICY_ID.eq(POLICIES.ID))
        .where(WALLET_POLICIES.WALLET_ID.eq(walletId))
        .orderBy(WALLET_POLICIES.CREATED_AT.desc())
        .fetch { mapToPolicyEntity(it, it.get(WALLET_POLICIES.ACTIVE)) }
}

override fun findById(policyId: UUID): PolicyEntity? {
    return dsl
        .selectFrom(POLICIES)
        .where(POLICIES.ID.eq(policyId))
        .fetchOne { mapToPolicyEntity(it) }
}

override fun findActiveByWalletId(walletId: UUID): PolicyEntity? {
    return dsl
        .select(POLICIES.fields().toList())
        .from(WALLET_POLICIES)
        .join(POLICIES).on(POLICIES.ID.eq(WALLET_POLICIES.POLICY_ID))
        .where(WALLET_POLICIES.WALLET_ID.eq(walletId))
        .and(WALLET_POLICIES.ACTIVE.eq(true))
        .fetchOne { mapToPolicyEntity(it) }
}
```

- [ ] **Step 3: Replace policy assignment transaction**

Use this implementation:

```kotlin
override fun assignPolicy(walletId: UUID, policyId: UUID) {
    dsl.transaction { configuration ->
        val tx = DSL.using(configuration)

        tx.update(WALLET_POLICIES)
            .set(WALLET_POLICIES.ACTIVE, false)
            .where(WALLET_POLICIES.WALLET_ID.eq(walletId))
            .and(WALLET_POLICIES.ACTIVE.eq(true))
            .execute()

        tx.insertInto(WALLET_POLICIES)
            .set(WALLET_POLICIES.WALLET_ID, walletId)
            .set(WALLET_POLICIES.POLICY_ID, policyId)
            .set(WALLET_POLICIES.ACTIVE, true)
            .execute()
    }
}
```

- [ ] **Step 4: Replace mapper**

Use this mapper:

```kotlin
private fun mapToPolicyEntity(record: Record, active: Boolean? = null): PolicyEntity {
    return PolicyEntity(
        id = record.get(POLICIES.ID),
        name = record.get(POLICIES.NAME),
        category = record.get(POLICIES.CATEGORY),
        maxPerPayment = record.get(POLICIES.MAX_PER_PAYMENT),
        daytimeDailyLimit = record.get(POLICIES.DAYTIME_DAILY_LIMIT),
        nighttimeDailyLimit = record.get(POLICIES.NIGHTTIME_DAILY_LIMIT),
        weekendDailyLimit = record.get(POLICIES.WEEKEND_DAILY_LIMIT),
        dailyTransactionLimit = record.get(POLICIES.DAILY_TRANSACTION_LIMIT),
        active = active,
        createdAt = record.get(POLICIES.CREATED_AT).toInstant(),
        updatedAt = record.get(POLICIES.UPDATED_AT).toInstant(),
    )
}
```

- [ ] **Step 5: Update test wiring for policy DAO**

In tests that instantiate `PolicyDAOSpecImpl(dataSource)`, create `dsl = JooqFactory.create(dataSource)` and instantiate `PolicyDAOSpecImpl(dsl)`.

- [ ] **Step 6: Run focused policy tests**

Run: `./gradlew :application:test --tests com.trace.payment.application.PolicyIntegrationTest`

Expected: policy integration tests pass.

---

### Task 5: Remove Manual JDBC From Production DAO Code And Verify

**Files:**
- Modify only files needed by compiler/test failures from previous tasks.

- [ ] **Step 1: Search for remaining production JDBC persistence usage**

Run: search production Kotlin files for `prepareStatement`, `createStatement`, `executeQuery`, `executeUpdate`, `ResultSet`, and `Connection`.

Expected: no matches in `adapters/database-adapter/src/main/kotlin/com/trace/payment/adapters/database/dao`.

- [ ] **Step 2: Run adapter tests**

Run: `./gradlew :database-adapter:test`

Expected: adapter tests pass.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test`

Expected: all tests pass.

- [ ] **Step 4: Inspect diff**

Run: `git diff -- build.gradle.kts adapters/database-adapter application docs/superpowers/specs docs/superpowers/plans`

Expected: diff contains only the jOOQ refactor, design doc, and plan. No commits are made.
