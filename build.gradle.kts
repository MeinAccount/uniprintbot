import com.google.cloud.tools.gradle.appengine.standard.AppEngineStandardExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.cloud.tools:appengine-gradle-plugin:2.0.0-rc6")
    }
}

// setup java and kotlin
plugins {
    java
    war

    kotlin("jvm") version "1.3.30"
}

group = "de.mrk"
version = "1.2"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}


// setup appengine
apply(plugin = "com.google.cloud.tools.appengine")
configure<AppEngineStandardExtension> {
    deploy.projectId = "uniprintbot"
    deploy.version = "v${project.version.toString().replace(".", "-")}"
}


// load dependencies
repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.2.0")
    compile("org.jetbrains.kotlinx:kotlinx-html-jvm:0.6.10")

    compile("org.telegram:telegrambots:4.2")
    compile("com.hierynomus:sshj:0.27.0")
    compile("org.slf4j:slf4j-simple:1.7.26")

    compile("com.squareup.retrofit2:retrofit:2.5.0")
    compile("com.squareup.retrofit2:converter-scalars:2.5.0")


    compile("com.google.cloud:google-cloud-datastore:1.70.0")
    compile("com.google.cloud:google-cloud-language:1.70.0")
    compile("com.google.appengine:appengine-api-1.0-sdk:1.9.73")
    add("providedCompile", "javax.servlet:javax.servlet-api:4.0.1")

    testCompile("com.google.appengine:appengine-testing:+")
    testCompile("com.google.appengine:appengine-api-stubs:+")
    testCompile("com.google.appengine:appengine-tools-sdk:+")
}
