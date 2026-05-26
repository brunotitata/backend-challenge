plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("com.trace.payment.ApplicationKt")
}

val ktorVersion = "2.3.12"
val testcontainersVersion = "1.20.4"

dependencies {
    implementation(project(":entities"))
    implementation(project(":use-cases"))
    implementation(project(":database-boundary"))
    implementation(project(":exceptions"))
    implementation(project(":input-boundary"))
    implementation(project(":common"))
    implementation(project(":database-adapter"))
    implementation(project(":message-adapter"))
    implementation(project(":web"))

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.github.smiley4:ktor-swagger-ui:2.10.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
}
