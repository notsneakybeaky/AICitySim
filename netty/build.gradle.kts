plugins {
    application
}

// Set the Java language version to 25
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // High-performance networking for your 500-bot swarm
    implementation("io.netty:netty-all:4.1.119.Final")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.google.code.gson:gson:2.11.0")

    // --- THE FIX: NEW GOOGLE GEN AI SDK ---
    // This is the new stable GA library that replaces the older 'generativelanguage' artifact.
    implementation("com.google.genai:google-genai:1.42.0")

    // Required gRPC dependencies for Google's transport layer
    implementation("io.grpc:grpc-netty-shaded:1.62.2")
    implementation("io.grpc:grpc-protobuf:1.62.2")
    implementation("io.grpc:grpc-stub:1.62.2")
}

tasks.named<JavaExec>("run") {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(23))
    })
}