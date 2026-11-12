plugins {
    application
}

dependencies {
    // Client ufficiale AMQP RabbitMQ
    implementation("com.rabbitmq:amqp-client:5.20.0")
    // Serializzatore JSON per i messaggi
    implementation("com.google.code.gson:gson:2.10.1")
    // SLF4J / Logback
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

application {
    mainClass = "ass03.HelloWorld"
}

tasks.test {
    useJUnitPlatform()
}