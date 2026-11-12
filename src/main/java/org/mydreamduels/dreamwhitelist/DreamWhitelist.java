package org.mydreamduels.dreamwhitelist;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DreamWhitelist - players only see and use the commands listed in config.yml.
 * Paper, Folia and Spigot from one jar.
 */
public class DreamWhitelist extends JavaPlugin {

    private final Whitelist whitelist = new Whitelist();
    private final Messages messages = new Messages();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAll();
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        PluginCommand cmd = getCommand("dreamwhitelist");
        if (cmd != null) {
            AdminCommand admin = new AdminCommand(this);
            cmd.setExecutor(admin);
            cmd.setTabCompleter(admin);
        }
        // commands registered after us (or by a /reload) - read the list again once the server ticks
        Platform.runLater(this, () -> {
            whitelist.rebuild();
            Platform.refreshAll(this);
        }, 20L);
        int count = 0;
        for (Whitelist.Group g : whitelist.groups()) count += g.commands().size();
        getLogger().info("Loaded " + whitelist.groups().size() + " groups, " + count + " commands ("
                + (Platform.FOLIA ? "Folia" : Platform.ADVENTURE ? "Paper" : "Spigot") + ").");
    }

    @Override
    public void onDisable() {
        // show everyone the full command list again
        try {
            for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
                if (!Platform.FOLIA) p.updateCommands();
            }
        } catch (Throwable ignored) {
        }
    }

    void reloadAll() {
        reloadConfig();
        whitelist.load(getConfig());
        messages.load(getConfig());
    }

    Whitelist whitelist() {
        return whitelist;
    }

    Messages messages() {
        return messages;
    }
}
