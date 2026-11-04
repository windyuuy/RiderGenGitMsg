import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.ridergen"
version = "0.2.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Rider 2026.2.3.1 (build RD-262.10968.170), installed locally.
        // The directory is named "JetBrains Rider 2025.2.3" but its product-info.json
        // reports 2026.2.3.1 (see README).
        local(providers.gradleProperty("platformLocalPath").get())
    }

    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        id = "dev.ridergen.gitmsg"
        name = "RiderGenGitMsg"
        version = project.version.toString()

        description = """
            Generates a Git commit message from staged changes (<code>git diff --cached</code>)
            and inserts it into the Rider commit message field.
            Uses the DeepSeek OpenAI-compatible Chat Completions API
            (default base URL https://api.deepseek.com, model deepseek-chat),
            stores the API token in the IDE PasswordSafe, and can fall back to a local template
            when no token is configured.
        """.trimIndent()

        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }

        vendor {
            name = "RiderGenGitMsg"
            url = "https://github.com/ridergen"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
