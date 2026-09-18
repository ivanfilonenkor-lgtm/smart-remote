plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
}

// AGP still has Windows code paths that mis-encode non-ASCII project locations.
// Keep generated artifacts in an ASCII-only local cache while sources remain in the shared workspace.
val externalBuildRoot = file("${System.getenv("LOCALAPPDATA")}/SmartRemoteBuild")
layout.buildDirectory.set(externalBuildRoot.resolve("root"))
subprojects {
    layout.buildDirectory.set(externalBuildRoot.resolve(name))
}
