package fi.valtakausi.craftjs.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginBase;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;

import fi.valtakausi.craftjs.CraftJsMain;
import fi.valtakausi.craftjs.api.CraftJsContext;
import io.papermc.paper.plugin.configuration.PluginMeta;
import net.kyori.adventure.text.TextComponent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsPlugin {

	public static JsPlugin createCraftJsCore(CraftJsMain craftjs) {
		Path jarPath = craftjs.getFile().toPath();
		Path dataDir = craftjs.getDataFolder().toPath();

		// Root dir is inside CraftJS jar, or override directory (for development)
		Path rootDir = craftjs.getInternalPlugin("craftjs-core");
		return new JsPlugin(craftjs, jarPath, rootDir, "index.js",
				dataDir, "craftjs-core", craftjs.getDescription().getVersion(), false);
	}

	/**
	 * Loader of this plugin.
	 */
	private final JsPluginLoader loader;

	/**
	 * Path to plugin file or directory.
	 */
	private final Path pluginPath;

	/**
	 * Root of plugin distribution. When a plugin is packaged, this is the root
	 * of zip file system.
	 */
	private final Path rootDir;

	/**
	 * Entrypoint, as specified in package.json.
	 */
	private final String entrypoint;

	/**
	 * Plugin data directory.
	 */
	private final Path dataDir;

	/**
	 * Plugin name.
	 */
	private final String name;

	/**
	 * Version string.
	 */
	private final String version;

	/**
	 * Logger of this plugin
	 */
	private final Logger logger;

	/**
	 * Context where this plugin is executed.
	 */
	private final CraftJsContext context;

	/**
	 * Enable internal APIs for this plugin.
	 */
	private final boolean internalApis;

	/**
	 * If this plugin is enabled.
	 */
	private boolean enabled;

	/**
	 * Plugin.yml information
	 */
	private PluginDescriptionFile description;

	/**
	 * The CraftJS JavaPlugin
	 */
	private final CraftJsMain craftjs;

	private final Listener EVENT_LISTENER = new Listener() {
	};

	JsPlugin(CraftJsMain craftjs, Path pluginFile, Path rootDir, String entrypoint, Path dataDir, String name,
			String version,
			boolean internalApis) {
		this.description = new PluginDescriptionFile(name, version, "");
		// If plugin.yml exists, use it, else defaults.
		if (Files.exists(rootDir.resolve("plugin.yml"))) {
			try {
				this.description = new PluginDescriptionFile(Files.newInputStream(rootDir.resolve("plugin.yml")));
			} catch (Exception exception) {
				this.description = new PluginDescriptionFile(name, version, "");
			}
		}
		this.craftjs = craftjs;

		this.loader = craftjs.getJsLoader();
		this.pluginPath = pluginFile;
		this.rootDir = rootDir;
		this.entrypoint = entrypoint;
		this.dataDir = dataDir;
		this.name = name;
		this.version = version;
		this.logger = new PluginLogger(craftjs);
		this.context = new CraftJsContext(craftjs, this);
		this.internalApis = internalApis;
		this.enabled = false;
	}

	public Listener getListener() {
		return this.EVENT_LISTENER;
	}

	public JavaPlugin getPlugin() {
		return this.craftjs;
	}

	public Path getRootDir() {
		return rootDir;
	}

	public Path getPluginPath() {
		return pluginPath;
	}

	public Path getNodeModules() {
		return rootDir.resolve("node_modules");
	}

	public CraftJsContext getContext() {
		return context;
	}

	public boolean internalApisEnabled() {
		return internalApis;
	}

	public File getDataFolder() {
		return dataDir.toFile();
	}

	public PluginDescriptionFile getDescription() {
		return description;
	}

	public FileConfiguration getConfig() {
		return new YamlConfiguration(); // Empty config for now
	}

	public InputStream getResource(String filename) {
		try {
			return Files.newInputStream(rootDir.resolve(filename));
		} catch (IOException e) {
			return null; // TODO handle IO errors better
		}
	}

	public PluginMeta getPluginMeta() {
		return this.description;
	}

	public void saveConfig() {
		// TODO
	}

	public void saveDefaultConfig() {
		// TODO
	}

	public void saveResource(String resourcePath, boolean replace) {
		try {
			Files.copy(rootDir.resolve(resourcePath), dataDir.resolve(resourcePath),
					replace ? new CopyOption[] { StandardCopyOption.REPLACE_EXISTING } : new CopyOption[0]);
		} catch (IOException e) {
			// TODO handle this error
			e.printStackTrace();
		}
	}

	public void reloadConfig() {
		// TODO
	}

	public Server getServer() {
		return Bukkit.getServer();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void onDisable() {
		this.context.unregisterEvents();
		this.context.cancelTasks();
		context.destroyGraalContext(); // Unload all code
	}

	public void setEnabled(boolean state) {
		if (state == enabled) {
			return; // Nothing to do
		}
		enabled = state;
		if (state) {
			onLoad(); // Called for correctness, even if it is empty
			onEnable();
		} else {
			onDisable();
		}
	}

	public void onEnable() {
		context.initGraalContext(); // Load CraftJS core
		// Load, probably specified in package.json
		boolean error = context.eval("__pluginEntrypoint('./" + entrypoint + "');", "entrypoint").asBoolean();
		if (error) {
			logger.severe("Failed to load the plugin, disabling...");
			// Bukkit.getPluginManager().disablePlugin(this);
			this.setEnabled(false);
		}
	}

	public void onLoad() {
		// Everything happens in onEnable/onDisable
	}

	public boolean isNaggable() {
		return false; // TODO figure out what this does? found no documentation (Simple boolean if we
									// can still nag to the logs about things)
	}

	public void setNaggable(boolean canNag) {

	}

	public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
		return null;
	}

	public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull String s, @Nullable String s1) {
		return null;
	}

	public Logger getLogger() {
		return logger;
	}

	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		return null; // Handled per-command basis
	}

	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		return false; // Handled per-command basis
	}

	public final void init(@NotNull PluginLoader loader, @NotNull Server server,
			@NotNull PluginDescriptionFile description, @NotNull File dataFolder, @NotNull File file,
			@NotNull ClassLoader classLoader) {
		return;
	}

	public final void init(@NotNull Server server, @NotNull PluginDescriptionFile description, @NotNull File dataFolder,
			@NotNull File file, @NotNull ClassLoader classLoader, PluginMeta configuration) {
		return;
	}
}
