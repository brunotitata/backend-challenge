plugins {
    kotlin("jvm")
    `java-library`
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
    api("org.jooq:jooq:$jooqVersion")

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
                        includes = "wallets|policies|wallet_policies|payments|limit_consumptions|payment_idempotency_keys|payment_audit_events"
                        excludes = "flyway_schema_history"
                        properties.add(
                            org.jooq.meta.jaxb.Property()
                                .withKey("scripts")
                                .withValue("src/main/resources/db/migration/*.sql"),
                        )
                        properties.add(
                            org.jooq.meta.jaxb.Property()
                                .withKey("sort")
                                .withValue("semantic"),
                        )
                        properties.add(
                            org.jooq.meta.jaxb.Property()
                                .withKey("defaultNameCase")
                                .withValue("lower"),
                        )
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
