plugins {
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
}

group = "com.trace"
version = "0.0.1"

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        dependencies {
            "testImplementation"("org.jetbrains.kotlin:kotlin-test:1.9.22")
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "21"
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}

tasks.register("run") {
    group = "application"
    description = "Runs the application module."
    dependsOn(":application:run")
}
