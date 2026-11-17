pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://dl.bintray.com/rikkaw/Shizuku") }
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    }
}

rootProject.name = "Operit"
include(":app")
include(":terminal")
project(":terminal").projectDir = file("modules/terminal")
include(":mnn")
project(":mnn").projectDir = file("modules/llm/mnn")
include(":llama")
project(":llama").projectDir = file("modules/llm/llama")
include(":showerclient")
project(":showerclient").projectDir = file("modules/showerclient")
include(":quickjs")
project(":quickjs").projectDir = file("modules/quickjs")
include(":a2ui-compat")
project(":a2ui-compat").projectDir = file("modules/a2ui-compat")
include(":material-icons-subset")
project(":material-icons-subset").projectDir = file("modules/material-icons-subset")
