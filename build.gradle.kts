plugins {
    id("java")
}

group = "com.jrmh"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(20) // Specify your JDK version here
    }
}

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

// Define a task to run Publisher
tasks.register<JavaExec>("runPublisher") {
    group = "application"
    description = "Run the Publisher application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.jrmh.Publisher")
    // Pass CLI arguments to Publisher
    args = if (project.hasProperty("publisherArgs")) {
        project.property("publisherArgs").toString().split(",")
    } else {
        listOf<String>()
    }
}

// Define a task to run Analyzer
tasks.register<JavaExec>("runAnalyzer") {
    group = "application"
    description = "Run the Analyzer application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.jrmh.Analyser")
    // Pass CLI arguments to Analyzer
    args = if (project.hasProperty("analyzerArgs")) {
        project.property("analyzerArgs").toString().split(",")
    } else {
        listOf<String>()
    }
}