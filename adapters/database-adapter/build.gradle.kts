plugins {
    kotlin("jvm")
}

val flywayVersion = "10.20.1"
val testcontainersVersion = "1.20.4"
val hikariVersion = "5.1.0"

dependencies {
    implementation(project(":entities"))
    implementation(project(":database-boundary"))
    implementation(project(":common"))

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
}
