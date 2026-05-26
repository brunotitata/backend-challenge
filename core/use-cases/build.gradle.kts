plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.22"
}

dependencies {
    implementation(project(":entities"))
    implementation(project(":input-boundary"))
    implementation(project(":database-boundary"))
    implementation(project(":exceptions"))
    implementation(project(":common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
