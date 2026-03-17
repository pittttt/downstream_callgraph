plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.0"
}

group = "com.downstreamcallgraph"
version = "1.1"

repositories {
    mavenCentral()
}

intellij {
    version = "2022.1"
    type = "IC"
    plugins.set(listOf("com.intellij.java"))
}

dependencies {
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "8"
        targetCompatibility = "8"
    }

    runIde {
        val ideaVmOptions = project.findProperty("ideaVmOptions") as String?
        if (ideaVmOptions != null) {
            jvmArgs(ideaVmOptions.split(" "))
        }
    }

    patchPluginXml {
        sinceBuild = "211"
        untilBuild = ""
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
