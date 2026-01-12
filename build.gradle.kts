plugins {
    java
    application
}

group = "com.study"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // Криптография (ECDSA, SHA-256)
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")

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

application {
    mainClass.set("com.study.blockchain.Main")
}
