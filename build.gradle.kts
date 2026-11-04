import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.gitmsg"
version = "0.7.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Platform selection (local IDE, downloaded Rider, or downloaded IntelliJ IDEA):
        //   -PplatformMode=local          -> installed IDE from -PplatformLocalPath / gradle.properties
        //   -PplatformMode=download-rider -> Rider platform from JetBrains repositories (~4 GB, opt-in)
        //   -PplatformMode=intellij-idea  -> IntelliJ IDEA platform (recommended for hosted CI)
        //   auto (default)                -> local when the configured directory exists, intellij-idea otherwise
        // PLATFORM_MODE / PLATFORM_VERSION environment variables are accepted as fallbacks.
        val platformMode = providers.gradleProperty("platformMode")
            .orElse(providers.environmentVariable("PLATFORM_MODE"))
            .getOrElse("auto")
        val platformVersion = providers.gradleProperty("platformVersion")
            .orElse(providers.environmentVariable("PLATFORM_VERSION"))
            .orNull
        val localPath = providers.gradleProperty("platformLocalPath")
            .orElse(providers.environmentVariable("PLATFORM_LOCAL_PATH"))
            .orNull
        val localPathExists = !localPath.isNullOrBlank() && File(localPath).isDirectory

        when {
            platformMode == "local" -> {
                check(!localPath.isNullOrBlank()) {
                    "platformMode=local requires -PplatformLocalPath=<installed IDE directory>"
                }
                local(localPath!!)
            }

            platformMode == "download-rider" -> create(
                IntelliJPlatformType.Rider,
                platformVersion ?: "2026.2.3.1"
            )

            platformMode == "intellij-idea" -> create(
                IntelliJPlatformType.IntellijIdea,
                platformVersion ?: "2026.2"
            )

            localPathExists -> local(localPath!!)

            else -> {
                logger.lifecycle(
                    "platformLocalPath '$localPath' is not available; " +
                        "downloading the IntelliJ IDEA platform for this build."
                )
                create(
                    IntelliJPlatformType.IntellijIdea,
                    platformVersion ?: "2026.2"
                )
            }
        }
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
        id = "dev.gitmsg.ai.commithelper"
        name = "AIGenCommitMsg"
        version = project.version.toString()

        description = """
            <p>Generates a Git commit message from staged changes and inserts it into the commit message field.</p>
            <p><b>Features</b></p>
            <ul>
              <li>One-click generation from the commit toolbar, right next to the commit message history button (default shortcut Ctrl+Alt+G).</li>
              <li>DeepSeek Chat Completions API (default base URL https://api.deepseek.com, default model deepseek-chat), with configurable base URL, model, temperature, commit message language and Conventional Commits.</li>
              <li>Custom generation rules appended to the system prompt with the highest priority.</li>
              <li>API token stored in the system keychain when available (Windows Credential Manager, macOS Keychain, Linux Secret Service) with the IDE PasswordSafe as fallback, plus a built-in Test Connection button with readable errors for invalid token (401/403), insufficient balance (402), rate limit (429), timeout and network failures.</li>
              <li>Token hardening: settings never refill the plaintext token, a masked summary and a clear-token action are provided, secrets are redacted from notifications and errors, the base URL is restricted to https (official host by default), and staged diffs are scanned and masked before sending.</li>
              <li>Searchable commit-message language picker with 200+ languages and region variants (CN, TW, US, GB, BR, PT, SA, JP and more), filtered by country code, English name or native name.</li>
              <li>Offline template fallback when no API token is configured, so the action always works.</li>
            </ul>
            <p><b>Configuration</b></p>
            <p>Open <code>Settings &gt; Tools &gt; AIGenCommitMsg (DeepSeek)</code>, paste a DeepSeek API token, optionally adjust the model or generation rules, and click Apply. The token is never written to project files; it goes to the platform keychain or the IDE PasswordSafe.</p>
            <p><b>Supported IDE</b></p>
            <p>2026.2 and later (build 262+). Uses the standard VCS APIs and works with any Git repository.</p>
        """.trimIndent()

        changeNotes = """
            <ul>
              <li>0.7.0: token security hardening and three-platform system keychain support (Windows Credential Manager, macOS Keychain, Linux Secret Service) with PasswordSafe fallback; no plaintext refill in settings, masked/clear actions, centralized redaction, https-only base URL policy, automatic legacy plaintext migration and pre-send diff secret masking.</li>
              <li>0.6.0: multilingual commit-message language picker (200+ entries, searchable, random order), language-aware system prompt, legacy zh/en migration and English fallback notice.</li>
              <li>0.5.0: renamed the display name to AIGenCommitMsg to keep it unique on Marketplace.</li>
              <li>0.4.1: added plugin icons (SVG for IDE, 40px/80px PNG for Marketplace), real vendor link and Marketplace-ready description.</li>
              <li>0.4.0: switched to a neutral plugin id for Marketplace compliance.</li>
              <li>0.3.0: added custom commit-message rules and a restore-default-rules button.</li>
              <li>0.2.0: DeepSeek-only generation, PasswordSafe token storage and token test button.</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "262"
            // No untilBuild: the platform validator recommends forward compatibility for build 243+.
        }

        vendor {
            name = "GitMsg AI"
            url = "https://github.com/windyuuy/RiderGenGitMsg"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
