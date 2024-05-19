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
        languageVersion.set(JavaLanguageVersion.of(22)) // Specify your JDK version here
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
    val publisherArgs = mutableListOf<String>()
    project.findProperty("time")?.toString()?.let { publisherArgs.addAll(listOf("-t", it)) }
    project.findProperty("broker")?.toString()?.let { publisherArgs.addAll(listOf("-b", it)) }
    args = publisherArgs
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(22))
    })
}

// Define a task to run Analyzer
tasks.register<JavaExec>("runAnalyser") {
    group = "application"
    description = "Run the Analyser application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.jrmh.Analyser")
    // Pass CLI arguments to Analyzer
    val analyzerArgs = mutableListOf<String>()
    project.findProperty("time")?.toString()?.let { analyzerArgs.addAll(listOf("-t", it)) }
    project.findProperty("broker")?.toString()?.let { analyzerArgs.addAll(listOf("-b", it)) }
    args = analyzerArgs
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(22))
    })
}