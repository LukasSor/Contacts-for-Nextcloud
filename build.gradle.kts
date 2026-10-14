plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    val buildRoot = System.getenv("LOCALAPPDATA")
        ?: System.getenv("XDG_CACHE_HOME")
        ?: "${System.getProperty("user.home")}/.cache"
    layout.buildDirectory.set(
        file("$buildRoot/nextcloud-contacts-build/${project.name}"),
    )
}
