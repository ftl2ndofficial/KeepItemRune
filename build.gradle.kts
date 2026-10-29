plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.ftl2nd"
version = "1.0.1"

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
        doLast {
            // Set class file major version to 65 (Java 21) so Paper 1.21.x's bundled ASM 9.7.1
            // and both Java 21 & Java 25 runtimes can read all plugin classes without error.
            destinationDirectory.asFileTree.matching { include("**/*.class") }.forEach { classFile ->
                val bytes = classFile.readBytes()
                if (bytes.size >= 8 &&
                    bytes[0] == 0xCA.toByte() && bytes[1] == 0xFE.toByte() &&
                    bytes[2] == 0xBA.toByte() && bytes[3] == 0xBE.toByte() &&
                    bytes[7] > 65.toByte()
                ) {
                    bytes[6] = 0
                    bytes[7] = 65
                    classFile.writeBytes(bytes)
                }
            }
        }
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
        manifest {
            attributes("Paperweight-Mappings-Namespace" to "mojang")
        }
    }

    shadowJar {
        archiveClassifier.set("")
        from("LICENSE")
        manifest {
            attributes("Paperweight-Mappings-Namespace" to "mojang")
        }
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
