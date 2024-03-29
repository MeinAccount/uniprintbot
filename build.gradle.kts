import com.google.cloud.tools.gradle.appengine.standard.AppEngineStandardExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.google.cloud.tools:appengine-gradle-plugin:2.4.1")
    }
}

// setup java and kotlin
plugins {
    java
    war

    kotlin("jvm") version "1.5.10"
}

group = "de.mrk"
version = "1.3"


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}


// setup appengine
apply(plugin = "com.google.cloud.tools.appengine")
configure<AppEngineStandardExtension> {
    stage.enableJarClasses = true
    deploy.projectId = "uniprintbot"
    deploy.version = "v${project.version.toString().replace(".", "-")}"
}


// load dependencies
repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")

    implementation("org.telegram:telegrambots:5.3.0")
    implementation("com.hierynomus:sshj:0.31.0")
    implementation("org.slf4j:slf4j-simple:1.7.30")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")


    implementation("com.google.cloud:google-cloud-datastore:1.106.5")
    implementation("com.google.appengine:appengine-api-1.0-sdk:1.9.89")
    add("providedCompile", "javax.servlet:javax.servlet-api:3.1.0")

    testImplementation("com.google.appengine:appengine-testing:+")
    testImplementation("com.google.appengine:appengine-api-stubs:+")
    testImplementation("com.google.appengine:appengine-tools-sdk:+")
}