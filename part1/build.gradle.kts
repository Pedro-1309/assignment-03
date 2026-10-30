plugins {
    id("scala")
    application
}

val akkaVersion = "2.8.5"

dependencies {
    implementation("org.scala-lang:scala3-library_3:3.3.3")
    implementation("com.typesafe.akka:akka-actor-typed_2.13:$akkaVersion")

    // Logger SLF4J for Akka
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Test
    testImplementation("com.typesafe.akka:akka-actor-testkit-typed_2.13:$akkaVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("ass03.Main")
}

tasks.test {
    useJUnitPlatform()
}