plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":entities"))
    implementation(project(":input-boundary"))
    implementation(project(":database-boundary"))
    implementation(project(":exceptions"))
}
