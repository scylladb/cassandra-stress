// ktlint formats the Gradle build scripts and the build logic.
plugins {
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion
val ktlintStyle = mapOf("ktlint_code_style" to "intellij_idea")

spotless {
    kotlinGradle {
        target("*.gradle.kts", "src/*/build.gradle.kts", "build-logic/*.gradle.kts", "build-logic/src/**/*.gradle.kts")
        ktlint(ktlintVersion).editorConfigOverride(ktlintStyle)
    }
    kotlin {
        target("build-logic/src/**/*.kt")
        ktlint(ktlintVersion).editorConfigOverride(ktlintStyle)
    }
}

tasks.register("lint") {
    description = "Checks the format of the Gradle build scripts and the build logic."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.spotlessCheck)
}

tasks.register("format") {
    description = "Formats the Gradle build scripts and the build logic with ktlint."
    group = "formatting"
    dependsOn(tasks.spotlessApply)
}
