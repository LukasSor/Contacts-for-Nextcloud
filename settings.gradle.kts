pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Nextcloud Contacts"
include(":app")

// Room KSP needs sqlite-jdbc's native library. Extract it and temp files under ~/.cache
// so the build still works when /tmp is full or extraction is blocked.
run {
    val buildRoot = System.getenv("LOCALAPPDATA")
        ?: System.getenv("XDG_CACHE_HOME")
        ?: "${System.getProperty("user.home")}/.cache"
    val tmpDir = java.io.File(buildRoot, "nextcloud-contacts-tmp")
    tmpDir.mkdirs()
    System.setProperty("java.io.tmpdir", tmpDir.absolutePath)

    val sqliteNativeDir = java.io.File(buildRoot, "nextcloud-contacts-build/sqlite-native/Linux/x86_64")
    val sqliteNative = java.io.File(sqliteNativeDir, "libsqlitejdbc.so")
    if (!sqliteNative.exists()) {
        sqliteNativeDir.mkdirs()
        val sqliteJdbc = java.nio.file.Files.walk(java.io.File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/org.xerial/sqlite-jdbc").toPath())
            .filter { it.fileName.toString().startsWith("sqlite-jdbc-") && it.fileName.toString().endsWith(".jar") }
            .max(java.util.Comparator.comparing { it.toString() })
            .orElse(null)
        if (sqliteJdbc != null) {
            java.util.zip.ZipFile(sqliteJdbc.toFile()).use { zip ->
                zip.getEntry("org/sqlite/native/Linux/x86_64/libsqlitejdbc.so")?.let { entry ->
                    zip.getInputStream(entry).use { input ->
                        sqliteNative.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
    if (sqliteNative.exists()) {
        System.setProperty("org.sqlite.lib.path", sqliteNativeDir.absolutePath)
    }
}
