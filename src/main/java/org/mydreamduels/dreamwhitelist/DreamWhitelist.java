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
        addNewPlayerCommands();
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

    /**
     * Player commands added in newer versions go into an existing config's default group once
     * (an admin who removes one later keeps it removed: the version number says it was done).
     */
    private void addNewPlayerCommands() {
        var c = getConfig();
        int done = c.getInt("settings.defaults-version", 0);
        if (done >= 3 || !c.isConfigurationSection("groups.default")) return;
        java.util.List<String> list = new java.util.ArrayList<>(c.getStringList("groups.default.commands"));
        java.util.List<String> added = new java.util.ArrayList<>();
        for (String cmd : java.util.List.of("back", "enderchest", "hosting", "yt", "worth", "shulkerloader", "crates", "rtpback",
                "claim", "extendclaim", "transferclaim", "untrustall", "quests")) {
            if (!list.contains(cmd)) {
                list.add(cmd);
                added.add(cmd);
            }
        }
        list.remove("mediarank");
        c.set("groups.default.commands", list);
        c.set("settings.defaults-version", 3);
        saveConfig();
        if (!added.isEmpty()) getLogger().info("Added the new player commands to the default group: " + String.join(", ", added));
    }
}
