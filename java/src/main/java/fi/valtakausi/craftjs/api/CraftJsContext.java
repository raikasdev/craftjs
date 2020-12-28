package fi.valtakausi.craftjs.api;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.h2.mvstore.MVStore;

import fi.valtakausi.craftjs.CraftJsMain;
import fi.valtakausi.craftjs.plugin.JsPlugin;
import fi.valtakausi.craftjs.plugin.JsPluginCommand;
import fi.valtakausi.craftjs.plugin.JsPluginCommand.CommandHandler;
import fi.valtakausi.craftjs.plugin.JsPluginCommand.JsTabCompleter;

/**
 * CraftJS context exposed as __craftjs.
 * One context per JS plugin.
 *
 */
public class CraftJsContext {
	
	private static final Listener EVENT_LISTENER = new Listener() {};
	
	/**
	 * Cleaner that is used for Graal context leak detection.
	 */
	private static final Cleaner CLEANER = Cleaner.create();
	
	/**
	 * The JS plugin instance.
	 */
	public final JsPlugin plugin;
	
	/**
	 * CraftJS version.
	 */
	public final String version;
	
	/**
	 * Plugin root path.
	 */
	public final Path pluginRoot;
	
	/**
	 * CraftJS plugin instance.
	 */
	private final CraftJsMain craftjs;
	
	/**
	 * Current GraalJS context.
	 */
	private Context context;
	
	/**
	 * Registered commands. Unlike event handlers, these need to be
	 * unregistered by us when context is destroyed.
	 */
	private final List<JsPluginCommand> commands;
	
	/**
	 * Opened databases.
	 */
	private final Map<String, Database> databases;
	
	public CraftJsContext(CraftJsMain craftjs, JsPlugin plugin) {
		this.plugin = plugin;
		this.version = craftjs.getDescription().getVersion();
		this.pluginRoot = plugin.getRootDir();
		this.craftjs = craftjs;
		this.commands = new ArrayList<>();
		this.databases = new HashMap<>();
	}
	
	public void initGraalContext() {
		if (context != null) {
			throw new IllegalStateException("context already exists");
		}
		context = craftjs.newGraalContext(this, plugin.internalApisEnabled());
		craftjs.installCore(this); // Install our core JS APIs
		// Prepare for evaluating the JS plugin
		context.getBindings("js").putMember("__craftjs", this);
	}
	
	public void destroyGraalContext() {
		if (context == null) {
			throw new IllegalStateException("context does not exist");
		}
		// Unload all commands before context is gone
		for (JsPluginCommand command : commands) {
			command.unload(Bukkit.getCommandMap());
		}
		commands.clear();
		
		// Listen for context GC
		AtomicBoolean destroyed = new AtomicBoolean(false);
		CLEANER.register(context, () -> destroyed.set(true));
		context = null; // ... should be GC'd soon
		
		// If the context is still around after a while, emit a warning
		Bukkit.getScheduler().scheduleSyncDelayedTask(craftjs, () -> {
			if (!destroyed.get()) {
				craftjs.getLogger().warning("GraalJS context was not destroyed, this could be a memory leak.");
			}
		}, 5_000);
	}
	
	/**
	 * Evaluates code in context of this plugin.
	 * @param code JS code.
	 * @param name Source (file) name.
	 * @return Return value of the code.
	 */
	public Value eval(String code, String name) {
		return eval(code, plugin.getName(), name);
	}
		
	protected Value eval(String code, String plugin, String name) {
		if (context == null) {
			throw new IllegalStateException("context not initialized");
		}
		Source src = Source.newBuilder("js", code, plugin + ":" + name).buildLiteral();
		return context.eval(src);
	}
	
	/**
	 * Schedules a task once.
	 * @param task Task to run.
	 * @param delay Delay in ticks.
	 * @return Id of new task.
	 */
	public int scheduleOnce(Runnable task, long delay) {
		if (delay == 0) {
			return Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, task);
		} else {
			return Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, task, delay);
		}
	}
	
	/**
	 * Schedules a repeating task.
	 * @param task Task to run.
	 * @param delay Initial delay.
	 * @param period Time between executions.
	 * @return Id of new task.
	 */
	public int scheduleRepeating(Runnable task, long delay, long period) {
		return Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delay, period);
	}
	
	/**
	 * Registers an event handler.
	 * @param event Event type.
	 * @param priority Event priority.
	 * @param handler
	 * @param ignoreCancelled
	 */
	public void registerEvent(Class<? extends Event> event, EventPriority priority, EventExecutor handler, boolean ignoreCancelled) {
		Bukkit.getPluginManager().registerEvent(event, EVENT_LISTENER, priority, handler, plugin, ignoreCancelled);
	}
	
	/**
	 * Registers a command handler.
	 * @param handler Command handler function.
	 * @param completer Tab completer function.
	 * @param name Primary command name.
	 * @param aliases Command aliases (secondary names).
	 * @param description Command description.
	 */
	public void registerCommand(CommandHandler handler, JsTabCompleter completer, String name, List<String> aliases, String description) {
		JsPluginCommand command = new JsPluginCommand(plugin, handler, completer, name, aliases, description);
		Bukkit.getCommandMap().register(plugin.getName().toLowerCase(), command); // Register to global command map
		commands.add(command); // Queue to be unloaded when this is destroyed
	}
	
	/**
	 * Opens a database for this plugin with given name, or returns a
	 * previously opened one.
	 * @param name Database name.
	 * @return A database.
	 */
	public Database openDatabase(String name) throws IOException {
		if (!databases.containsKey(name)) {
			Path path = craftjs.getDatabaseDir().resolve(plugin.getName()).resolve(name + ".db");
			Files.createDirectories(path.getParent());
			databases.put(name, new Database(path));
		}
		return databases.get(name);
	}
}
