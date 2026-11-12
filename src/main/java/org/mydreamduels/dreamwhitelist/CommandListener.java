package org.mydreamduels.dreamwhitelist;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.command.UnknownCommandEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.List;
import java.util.Locale;

/**
 * Hides, blocks and answers. All four events fire on the player's own thread, so this works
 * the same on Folia's region threads as on Paper/Spigot's main thread.
 */
final class CommandListener implements Listener {

    private final DreamWhitelist plugin;

    CommandListener(DreamWhitelist plugin) {
        this.plugin = plugin;
    }

    /** What the client may tab-complete: only allowed commands are sent. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSend(PlayerCommandSendEvent e) {
        Player p = e.getPlayer();
        if (Whitelist.bypass(p)) return;
        List<Whitelist.Group> groups = plugin.whitelist().groupsOf(p);
        e.getCommands().removeIf(label -> !plugin.whitelist().allowed(groups, label));
    }

    /** Typed commands: anything not allowed gets "No such command". */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (Whitelist.bypass(p)) return;
        String line = e.getMessage();
        if (line.startsWith("/")) line = line.substring(1);
        line = line.trim();
        if (line.isEmpty()) return;
        int space = line.indexOf(' ');
        String label = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        List<Whitelist.Group> groups = plugin.whitelist().groupsOf(p);
        if (!plugin.whitelist().allowed(groups, label)) {
            e.setCancelled(true);
            plugin.messages().noSuchCommand(p);
            return;
        }
        if (plugin.whitelist().blockedSubcommand(groups, line)) {
            e.setCancelled(true);
            plugin.messages().blockedSubcommand(p);
        }
    }

    /** Tab-complete of arguments: nothing for hidden commands, no blocked subcommands. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTab(TabCompleteEvent e) {
        if (!(e.getSender() instanceof Player p) || Whitelist.bypass(p)) return;
        String buffer = e.getBuffer();
        if (!buffer.startsWith("/")) return;
        String line = buffer.substring(1);
        int space = line.indexOf(' ');
        if (space < 0) return; // the command name itself - the client's list already handles it
        List<Whitelist.Group> groups = plugin.whitelist().groupsOf(p);
        String label = line.substring(0, space);
        if (!plugin.whitelist().allowed(groups, label)) {
            e.setCompletions(new java.util.ArrayList<>());
            e.setCancelled(true);
            return;
        }
        int lastSpace = line.lastIndexOf(' ');
        String before = line.substring(0, lastSpace + 1);
        List<String> completions = new java.util.ArrayList<>(e.getCompletions());
        if (completions.removeIf(c -> plugin.whitelist().blockedSubcommand(groups, before + c))) {
            e.setCompletions(completions);
        }
    }

    /** Commands that don't exist at all: our message instead of Minecraft's red text. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUnknown(UnknownCommandEvent e) {
        CommandSender sender = e.getSender();
        if (!(sender instanceof Player p) || !plugin.messages().replaceUnknown()) return;
        plugin.messages().unknown(e, p);
    }

    /** Plugins register commands late: re-read the command list once everything is loaded. */
    @EventHandler
    public void onLoad(ServerLoadEvent e) {
        plugin.whitelist().rebuild();
        Platform.refreshAll(plugin);
    }
}
