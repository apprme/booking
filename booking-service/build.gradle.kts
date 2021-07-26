val akka_platform_version: String by project
val scala_binary_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jmailen.kotlinter")
}

application {
    mainClass.set("me.appr.booking.ApplicationKt")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("booking-service")
    archiveVersion.set("")
    archiveClassifier.set("")
    val newTransformer = com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer()
    newTransformer.resource = "reference.conf"
    transformers.add(newTransformer)

    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(kotlin("reflect"))

    implementation(platform("com.lightbend.akka:akka-platform-dependencies_${scala_binary_version}:${akka_platform_version}"))

    implementation("com.typesafe.akka:akka-http_${scala_binary_version}")
    implementation("com.typesafe.akka:akka-http-jackson_${scala_binary_version}")
    implementation("com.typesafe.akka:akka-serialization-jackson_${scala_binary_version}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.4")

    implementation("com.typesafe.akka:akka-persistence-typed_${scala_binary_version}")
    implementation("com.lightbend.akka:akka-persistence-jdbc_${scala_binary_version}")
    implementation("org.postgresql:postgresql:42.2.23")

    implementation("com.typesafe.akka:akka-cluster-sharding-typed_${scala_binary_version}")
    implementation("com.lightbend.akka.management:akka-management-cluster-bootstrap_${scala_binary_version}")
    implementation("com.lightbend.akka.management:akka-management-cluster-http_${scala_binary_version}")

    implementation("ch.qos.logback:logback-classic:$logback_version")

    testImplementation("com.typesafe.akka:akka-persistence-testkit_${scala_binary_version}")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
}
