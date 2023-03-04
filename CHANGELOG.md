# 1.0.5

**Breaking *technical* changes!**

Instead of creating a JsPlugin and registering a plugin through the Bukkit pluginManager, the plugins are now only handled internally, and instead the CraftJS JavaPlugin is used for events and command registrations.

This is because of Paper's new Paper Plugins API uses a new loader, that doesn't support PluginBase based plugins. It also would not have supported hot reloading.

No changes are needed to be made to JS plugins.
