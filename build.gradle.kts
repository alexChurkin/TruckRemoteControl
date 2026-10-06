plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

// ktlint (code style) and detekt (code smells, complexity, potential bugs) are run directly
// (as recommended by ktlint), they don't depend on Kotlin/AGP plugin internals
val ktlint: Configuration = configurations.create("ktlint")
val detekt: Configuration = configurations.create("detekt")

dependencies {
    ktlint(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
    ktlint(libs.compose.rules.ktlint)
    detekt(libs.detekt.cli)
}

val ktlintSources = listOf("**/src/**/*.kt", "**/*.kts", "!**/build/**")

tasks.register<JavaExec>("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(ktlintSources)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fixes Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(listOf("-F") + ktlintSources)
}

tasks.register<JavaExec>("detekt") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Finds code smells and potential bugs in Kotlin code"
    classpath = detekt
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--input",
        "app/src",
        "--build-upon-default-config",
        "--config",
        "config/detekt/detekt.yml",
        "--report",
        "html:build/reports/detekt.html",
    )
}
