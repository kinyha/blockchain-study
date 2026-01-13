plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.study"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Криптография (ECDSA, SHA-256)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // JSON сериализация
    implementation("com.google.code.gson:gson:2.10.1")

    // Тестирование
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.graphics")
}

application {
    mainClass.set("com.study.blockchain.ui.App")
}
