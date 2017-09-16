package com.hm.petmaster;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.hm.mcshared.file.CommentedYamlConfiguration;
import com.hm.mcshared.update.UpdateChecker;
import com.hm.petmaster.command.EnableDisableCommand;
import com.hm.petmaster.command.FreeCommand;
import com.hm.petmaster.command.HelpCommand;
import com.hm.petmaster.command.InfoCommand;
import com.hm.petmaster.command.ReloadCommand;
import com.hm.petmaster.command.SetOwnerCommand;
import com.hm.petmaster.listener.PlayerInteractListener;
import com.hm.petmaster.listener.PlayerQuitListener;

import net.milkbowl.vault.economy.Economy;

/**
 * Whose pet is this? A plugin to change or display the owner of a pet via a hologram or a chat message.
 * 
 * PetMaster is under GNU General Public License version 3. Please visit the plugin's GitHub for more information :
 * https://github.com/PyvesB/PetMaster
 * 
 * Official plugin's server: hellominecraft.fr
 * 
 * Bukkit project page: dev.bukkit.org/bukkit-plugins/pet-master
 * 
 * Spigot project page: spigotmc.org/resources/pet-master.15904
 * 
 * @since December 2015.
 * @version 1.6.2
 * @author DarkPyves
 */
public class PetMaster extends JavaPlugin implements Listener {

	private static final String[] NO_COMMENTS = new String[] {};

	// Used for Vault plugin integration.
	private Economy economy;

	// Plugin options and various parameters.
	private String chatHeader;
	private boolean chatMessage;
	private boolean hologramMessage;
	private boolean actionBarMessage;
	private boolean successfulLoad;
	private boolean updatePerformed;

	// Fields related to file handling.
	private CommentedYamlConfiguration config;
	private CommentedYamlConfiguration lang;

	// Plugin listeners.
	private PlayerInteractListener playerInteractListener;
	private PlayerQuitListener playerQuitListener;

	// Used to check for plugin updates.
	private UpdateChecker updateChecker;

	// Additional classes related to plugin commands.
	private HelpCommand helpCommand;
	private InfoCommand infoCommand;
	private SetOwnerCommand setOwnerCommand;
	private FreeCommand freeCommand;
	private EnableDisableCommand enableDisableCommand;
	private ReloadCommand reloadCommand;

	/**
	 * Called when server is launched or reloaded.
	 */
	@Override
	public void onEnable() {
		// Start enabling plugin.
		long startTime = System.currentTimeMillis();

		this.getLogger().info("Registering listeners...");

		playerInteractListener = new PlayerInteractListener(this);
		playerQuitListener = new PlayerQuitListener(this);

		PluginManager pm = getServer().getPluginManager();
		// Register listeners.
		pm.registerEvents(playerInteractListener, this);
		pm.registerEvents(playerQuitListener, this);

		extractParametersFromConfig(true);

		chatHeader = ChatColor.GRAY + "[" + ChatColor.GOLD + "\u265E" + ChatColor.GRAY + "] ";

		// Check for available plugin update.
		if (config.getBoolean("checkForUpdate", true)) {
			updateChecker = new UpdateChecker(this, "https://raw.githubusercontent.com/PyvesB/PetMaster/master/pom.xml",
					new String[] { "dev.bukkit.org/bukkit-plugins/pet-master/files",
							"spigotmc.org/resources/pet-master.15904" },
					"petmaster.admin", chatHeader);
			pm.registerEvents(updateChecker, this);
			updateChecker.launchUpdateCheckerTask();
		}

		helpCommand = new HelpCommand(this);
		infoCommand = new InfoCommand(this);
		setOwnerCommand = new SetOwnerCommand(this);
		freeCommand = new FreeCommand(this);
		enableDisableCommand = new EnableDisableCommand(this);
		reloadCommand = new ReloadCommand(this);

		boolean holographicDisplaysAvailable = Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays");

		// Checking whether user configured plugin to display hologram but HolographicsDisplays not available.
		if (hologramMessage && !holographicDisplaysAvailable) {
			successfulLoad = false;
			hologramMessage = false;
			actionBarMessage = true;
			this.getLogger().warning(
					"HolographicDisplays was not found; disabling usage of holograms and enabling action bar messages.");
		}

		if (successfulLoad) {
			this.getLogger().info("Plugin successfully enabled and ready to run! Took "
					+ (System.currentTimeMillis() - startTime) + "ms.");
		} else {
			this.getLogger().severe("Error(s) while loading plugin. Please view previous logs for more information.");
		}
	}

	/**
	 * Extract plugin parameters from the configuration file.
	 * 
	 * @param attemptUpdate
	 */
	public void extractParametersFromConfig(boolean attemptUpdate) {
		successfulLoad = true;
		Logger logger = this.getLogger();

		logger.info("Backing up and loading configuration files...");

		try {
			config = new CommentedYamlConfiguration("config.yml", this);
		} catch (IOException e) {
			this.getLogger().log(Level.SEVERE, "Error while loading configuration file: ", e);
			successfulLoad = false;
		} catch (InvalidConfigurationException e) {
			logger.severe("Error while loading configuration file, disabling plugin.");
			logger.log(Level.SEVERE,
					"Verify your syntax by visiting yaml-online-parser.appspot.com and using the following logs: ", e);
			successfulLoad = false;
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}

		try {
			lang = new CommentedYamlConfiguration(config.getString("languageFileName", "lang.yml"), this);
		} catch (IOException e) {
			this.getLogger().log(Level.SEVERE, "Error while loading language file: ", e);
			successfulLoad = false;
		} catch (InvalidConfigurationException e) {
			logger.severe("Error while loading language file, disabling plugin.");
			this.getLogger().log(Level.SEVERE,
					"Verify your syntax by visiting yaml-online-parser.appspot.com and using the following logs: ", e);
			successfulLoad = false;
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}

		try {
			config.backupConfiguration();
		} catch (IOException e) {
			this.getLogger().log(Level.SEVERE, "Error while backing up configuration file: ", e);
			successfulLoad = false;
		}

		try {
			lang.backupConfiguration();
		} catch (IOException e) {
			this.getLogger().log(Level.SEVERE, "Error while backing up language file: ", e);
			successfulLoad = false;
		}

		// Update configurations from previous versions of the plugin if server reloads or restarts.
		if (attemptUpdate) {
			updateOldConfiguration();
			updateOldLanguage();
		}

		// Extract options from the config.
		chatMessage = config.getBoolean("chatMessage", false);
		hologramMessage = config.getBoolean("hologramMessage", true);
		actionBarMessage = config.getBoolean("actionBarMessage", true);
		playerInteractListener.extractParameters();

		// Unregister events if user changed the option and did a /petm reload. Do not recheck for update on /petm
		// reload.
		if (!config.getBoolean("checkForUpdate", true)) {
			PlayerJoinEvent.getHandlerList().unregister(updateChecker);
		}
	}

	/**
	 * Update configuration file from older plugin versions by adding missing parameters. Upgrades from versions prior
	 * to 1.2 are not supported.
	 */
	private void updateOldConfiguration() {
		updatePerformed = false;

		updateSetting(lang, "languageFileName", "lang.yml", new String[] { "Name of the language file." });
		updateSetting(lang, "checkForUpdate", true,
				new String[] { "Check for update on plugin launch and notify when an OP joins the game." });
		updateSetting(lang, "changeOwnerPrice", 0,
				new String[] { "Price of the /petm setowner command (requires Vault)." });
		updateSetting(lang, "displayDog", true, new String[] { "Take dogs into account." });
		updateSetting(lang, "displayCat", true, new String[] { "Take cats into account." });
		updateSetting(lang, "displayHorse", true, new String[] { "Take horses into account." });
		updateSetting(lang, "displayLlama", true, new String[] { "Take llamas into account." });
		updateSetting(lang, "displayParrot", true, new String[] { "Take parrots into account." });
		updateSetting(lang, "actionBarMessage", false,
				new String[] { "Enable or disable action bar messages when right-clicking on a pet." });
		updateSetting(lang, "displayToOwner", false,
				new String[] { "Enable or disable showing ownership information for a player's own pets." });
		updateSetting(lang, "freePetPrice", 0, new String[] { "Price of the /petm free command (requires Vault)." });

		if (updatePerformed) {
			// Changes in the configuration: save and do a fresh load.
			try {
				config.saveConfiguration();
				config.loadConfiguration();
			} catch (IOException | InvalidConfigurationException e) {
				this.getLogger().log(Level.SEVERE, "Error while saving changes to the configuration file: ", e);
				successfulLoad = false;
			}
		}
	}

	/**
	 * Update language file from older plugin versions by adding missing parameters. Upgrades from versions prior to 1.2
	 * are not supported.
	 */
	private void updateOldLanguage() {
		updatePerformed = false;

		updateSetting(lang, "petmaster-command-setowner-hover",
				"You can only change the ownership of your own pets, unless you're admin!", NO_COMMENTS);
		updateSetting(lang, "petmaster-command-disable-hover",
				"The plugin will not work until next reload or /petm enable.", NO_COMMENTS);
		updateSetting(lang, "petmaster-command-enable-hover",
				"Plugin enabled by default. Use this if you entered /petm disable before!", NO_COMMENTS);
		updateSetting(lang, "petmaster-command-reload-hover", "Reload most settings in config.yml and lang.yml files.",
				NO_COMMENTS);
		updateSetting(lang, "petmaster-command-info-hover", "Some extra info about the plugin and its awesome author!",
				NO_COMMENTS);
		updateSetting(lang, "petmaster-tip", "&lHINT&r &8You can &7&n&ohover&r &8or &7&n&oclick&r &8on the commands!",
				NO_COMMENTS);
		updateSetting(lang, "change-owner-price", "You payed: AMOUNT !", NO_COMMENTS);
		updateSetting(lang, "petmaster-action-bar", "Pet owned by ", NO_COMMENTS);
		updateSetting(lang, "petmaster-command-free", "Free a pet.", NO_COMMENTS);
		updateSetting(lang, "petmaster-command-free-hover", "You can only free your own pets, unless you're admin!",
				NO_COMMENTS);
		updateSetting(lang, "pet-freed", "Say goodbye: this pet returned to the wild!", NO_COMMENTS);
		updateSetting(lang, "not-enough-money", "You do not have the required amount: AMOUNT !", NO_COMMENTS);

		if (updatePerformed) {
			// Changes in the language file: save and do a fresh load.
			try {
				lang.saveConfiguration();
				lang.loadConfiguration();
			} catch (IOException | InvalidConfigurationException e) {
				this.getLogger().log(Level.SEVERE, "Error while saving changes to the language file: ", e);
				successfulLoad = false;
			}
		}
	}

	/**
	 * Called when server is stopped or reloaded.
	 */
	@Override
	public void onDisable() {
		this.getLogger().info("PetMaster has been disabled.");
	}

	/**
	 * Called when a player or the console enters a command.
	 */
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (!"petm".equalsIgnoreCase(cmd.getName())) {
			return false;
		}

		if (args.length == 0 || args.length == 1 && "help".equalsIgnoreCase(args[0])) {
			helpCommand.getHelp(sender);
		} else if ("info".equalsIgnoreCase(args[0])) {
			infoCommand.getInfo(sender);
		} else if ("reload".equalsIgnoreCase(args[0])) {
			reloadCommand.reload(sender);
		} else if ("disable".equalsIgnoreCase(args[0])) {
			enableDisableCommand.setState(sender, false);
		} else if ("enable".equalsIgnoreCase(args[0])) {
			enableDisableCommand.setState(sender, true);
		} else if ("setowner".equalsIgnoreCase(args[0]) && sender instanceof Player) {
			setOwnerCommand.setOwner(((Player) sender), args);
		} else if ("free".equalsIgnoreCase(args[0]) && sender instanceof Player) {
			freeCommand.freePet(((Player) sender), args);
		} else {
			sender.sendMessage(chatHeader + lang.getString("misused-command", "Misused command. Please type /petm."));
		}
		return true;
	}

	/**
	 * Updates the configuration file to include a new setting with its default value and its comments.
	 * 
	 * @param file
	 * @param name
	 * @param value
	 * @param comments
	 */
	private void updateSetting(CommentedYamlConfiguration file, String name, Object value, String[] comments) {
		if (!file.getKeys(false).contains(name)) {
			file.set(name, value, comments);
			updatePerformed = true;
		}
	}

	/**
	 * Try to hook up with Vault, and log if this is called on plugin initialisation.
	 * 
	 * @param log
	 * @return true if Vault available, false otherwise
	 */
	public boolean setUpEconomy() {
		if (economy != null) {
			return true;
		}

		try {
			RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager()
					.getRegistration(net.milkbowl.vault.economy.Economy.class);
			if (economyProvider != null) {
				economy = economyProvider.getProvider();
			}
			return economy != null;
		} catch (NoClassDefFoundError e) {
			this.getLogger().warning("Attempt to hook up with Vault failed. Payment ignored.");
			return false;
		}
	}

	public boolean isSuccessfulLoad() {
		return successfulLoad;
	}

	public boolean isChatMessage() {
		return chatMessage;
	}

	public boolean isHologramMessage() {
		return hologramMessage;
	}

	public boolean isActionBarMessage() {
		return actionBarMessage;
	}

	public String getChatHeader() {
		return chatHeader;
	}

	public CommentedYamlConfiguration getPluginConfig() {
		return config;
	}

	public CommentedYamlConfiguration getPluginLang() {
		return lang;
	}

	public Economy getEconomy() {
		return economy;
	}

	public SetOwnerCommand getSetOwnerCommand() {
		return setOwnerCommand;
	}

	public FreeCommand getFreeCommand() {
		return freeCommand;
	}

	public EnableDisableCommand getEnableDisableCommand() {
		return enableDisableCommand;
	}
}
