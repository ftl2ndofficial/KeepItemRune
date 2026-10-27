plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.ftl2nd"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.2.1")

    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly(files("D:/Làm plugin/BetterSurvival/26.2/versions/26.2/paper-26.2.jar"))
    compileOnly(fileTree("D:/Làm plugin/BetterSurvival/26.2/libraries") { include("**/*.jar") })

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(files("D:/Làm plugin/BetterSurvival/26.2/versions/26.2/paper-26.2.jar"))
    testImplementation(fileTree("D:/Làm plugin/BetterSurvival/26.2/libraries") { include("**/*.jar") })
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    compileTestJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            filter { line -> line.replace("\${project.version}", project.version.toString()) }
        }
    }

    jar {
        archiveClassifier.set("thin")
        from("LICENSE")
    }

    shadowJar {
        archiveClassifier.set("")
        from("LICENSE")
        configurations = project.configurations.runtimeClasspath.map { setOf(it) }

        dependencies {
            // Only merge bStats into the final jar, no other dependencies
            exclude { it.moduleGroup != "org.bstats" }
        }

        // Relocate bStats into the plugin's package to avoid conflicts with other
        // plugins using bStats
        relocate("org.bstats", project.group.toString())
    }

    test {
        enabled = false
    }

    val unitTest by registering(JavaExec::class) {
        group = "verification"
        onlyIf { file("src/test/java").exists() }
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("com.ftl2nd.keepitemrune.TestRunner")
        jvmArgs("-Dfile.encoding=UTF-8", "-Dnet.bytebuddy.experimental=true")
    }

    check {
        dependsOn(unitTest)
    }

    build {
        dependsOn(shadowJar)
        dependsOn(unitTest)
    }
}
