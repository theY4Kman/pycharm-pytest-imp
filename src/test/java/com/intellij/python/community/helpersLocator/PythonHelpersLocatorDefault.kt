package com.intellij.python.community.helpersLocator

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Path

/**
 * Previous versions placed all sub-modules of plugins inside JARs within the plugin's lib/
 * directory. 2025.3 appears to place unpacked sub-module JARs within lib/modules/. And,
 * unfortunately, PluginManagerCore.getPluginDistDirByClass is only prepared for JARs
 * within lib/, otherwise it raises an IllegalStateException.
 *
 * This extension replaces the Python CE HelpersLocator extension with a stub that calculates
 * helpers location without using of getPluginDistDirByClass.
 */
class PythonHelpersLocatorDefault : PythonHelpersLocator {
    override fun getRoot(): Path? {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("PythonCore"))
        return plugin?.pluginPath?.resolve("helpers")
    }
}
