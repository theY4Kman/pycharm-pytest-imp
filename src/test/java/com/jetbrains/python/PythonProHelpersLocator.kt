package com.jetbrains.python

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import java.nio.file.Path

/**
 * Previous versions placed all sub-modules of plugins inside JARs within the plugin's lib/
 * directory. 2025.3 appears to place unpacked sub-module JARs within lib/modules/. And,
 * unfortunately, PluginManagerCore.getPluginDistDirByClass is only prepared for JARs
 * within lib/, otherwise it raises an IllegalStateException.
 *
 * This extension replaces the Python Pro HelpersLocator extension with a stub that calculates
 * helpers location without using of getPluginDistDirByClass.
 */
class PythonProHelpersLocator : PythonHelpersLocator {
    override fun getRoot(): Path? {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("Pythonid"))
        return plugin?.pluginPath?.resolve("helpers-pro")
    }
}
