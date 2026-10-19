plugins {
    `kotlin-dsl`
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }

dependencies {
    implementation(plugin(libs.plugins.spotbugs))
    implementation(plugin(libs.plugins.spotless))
    implementation(plugin(libs.plugins.ospackage))
}
