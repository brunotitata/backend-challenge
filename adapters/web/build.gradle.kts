plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val ktorVersion = "2.3.12"

 dependencies {
    implementation(project(":entities"))
    implementation(project(":exceptions"))
    implementation(project(":input-boundary"))

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.github.smiley4:ktor-swagger-ui:2.10.0")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("io.micrometer:micrometer-core:1.12.5")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.5")
 }
