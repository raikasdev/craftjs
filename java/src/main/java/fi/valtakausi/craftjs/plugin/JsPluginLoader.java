package fi.valtakausi.craftjs.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.UnknownDependencyException;

import com.google.gson.JsonSyntaxException;

import fi.valtakausi.craftjs.CraftJsMain;

public class JsPluginLoader {

	private final CraftJsMain craftjs;

	public JsPluginLoader(CraftJsMain craftjs) {
		this.craftjs = craftjs;
	}

	public boolean isJsPlugin(Path pluginPath) {
		if (Files.isDirectory(pluginPath)) {
			// If package.json is present, this is probably JS plugin
			// If this is not enough, consider adding a custom property to it
			return Files.exists(pluginPath.resolve("package.json"));
		}
		return false; // TODO plugin bundles
	}

	private Path getRootDir(Path pluginPath) {
		if (Files.isDirectory(pluginPath)) {
			return pluginPath;
		} else {
			throw new UnsupportedOperationException("js plugin bundle");
		}
	}

	private PackageJson loadManifest(Path path) throws JsonSyntaxException, IOException {
		return PackageJson.load(Files.readString(path));
	}

	public JsPlugin loadPlugin(File file) throws InvalidPluginException, UnknownDependencyException {
		return loadPlugin(file.toPath());
	}

	public JsPlugin loadPlugin(Path path) throws InvalidPluginException, UnknownDependencyException {
		if (!Files.exists(path)) {
			throw new InvalidPluginException("missing plugin: " + path);
		}
		Path rootDir = getRootDir(path);
		Path dataDir = rootDir.resolve("data"); // TODO plugin bundles
		PackageJson manifest;
		try {
			manifest = loadManifest(rootDir.resolve("package.json"));
		} catch (JsonSyntaxException | IOException e) {
			throw new InvalidPluginException("loading package.json failed", e);
		}
		boolean internalApis = manifest._internalApis != null && manifest._internalApis;
		JsPlugin plugin = new JsPlugin(craftjs, path, rootDir, manifest.main, dataDir, manifest.name, manifest.version,
				internalApis);
		return plugin;
	}

	public PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
		PackageJson manifest;
		try {
			manifest = loadManifest(file.toPath());
		} catch (JsonSyntaxException | IOException e) {
			throw new InvalidDescriptionException(e);
		}
		return new PluginDescriptionFile(manifest.name, manifest.version, "");
	}

	public Pattern[] getPluginFileFilters() {
		// This plugin loader is registered AFTER all plugins have been loaded
		// We'll need to find and load JS plugins ourself
		return new Pattern[0];
	}

	public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener,
			Plugin plugin) {
		// TODO Auto-generated method stub
		return null;
	}

	public void enablePlugin(Plugin plugin) {
		if (!(plugin instanceof JsPlugin)) {
			throw new IllegalArgumentException("not a JS plugin");
		}
		((JsPlugin) plugin).setEnabled(true);
	}

	public void disablePlugin(Plugin plugin) {
		if (!(plugin instanceof JsPlugin)) {
			throw new IllegalArgumentException("not a JS plugin");
		}
		((JsPlugin) plugin).setEnabled(false);
	}

}
