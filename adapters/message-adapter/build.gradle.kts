plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":common"))

    api("com.rabbitmq:amqp-client:5.21.0")
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
}
