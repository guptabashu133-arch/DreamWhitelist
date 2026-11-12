package org.mydreamduels.dreamwhitelist;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.command.UnknownCommandEvent;

/** The player-facing messages: MiniMessage + sprite icons on Paper/Folia, & codes on Spigot. */
final class Messages {

    private volatile String noSuch = "", blocked = "", legacyNoSuch = "", legacyBlocked = "";
    private volatile Integer shadow;
    private volatile boolean replaceUnknown = true;

    void load(FileConfiguration c) {
        String prefix = c.getString("messages.prefix", "");
        noSuch = c.getString("messages.no-such-command", "{prefix}<red>No such command.").replace("{prefix}", prefix);
        blocked = c.getString("messages.blocked-subcommand", "{prefix}<red>No such command.").replace("{prefix}", prefix);
        legacyNoSuch = color(c.getString("messages.legacy-no-such-command", "&cNo such command."));
        legacyBlocked = color(c.getString("messages.legacy-blocked-subcommand", "&cNo such command."));
        shadow = parseShadow(c.getString("messages.text-shadow", "#000000"));
        replaceUnknown = c.getBoolean("settings.replace-unknown-command", true);
        if (Platform.ADVENTURE) Text.clear();
    }

    boolean replaceUnknown() {
        return replaceUnknown;
    }

    void noSuchCommand(Player p) {
        if (Platform.ADVENTURE) Text.send(p, noSuch, shadow);
        else p.sendMessage(legacyNoSuch);
    }

    void blockedSubcommand(Player p) {
        if (Platform.ADVENTURE) Text.send(p, blocked, shadow);
        else p.sendMessage(legacyBlocked);
    }

    @SuppressWarnings("deprecation")
    void unknown(UnknownCommandEvent e, Player p) {
        if (Platform.ADVENTURE) Text.setUnknownMessage(e, noSuch, shadow);
        else e.setMessage(legacyNoSuch);
    }

    @SuppressWarnings("deprecation")
    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    /** "#000000" / "#000000FF" (RRGGBBAA) -> ARGB; "none" -> null. */
    static Integer parseShadow(String raw) {
        if (raw == null) return null;
        String v = raw.trim().replace("#", "");
        if (v.isEmpty() || v.equalsIgnoreCase("none") || v.equalsIgnoreCase("off") || v.equalsIgnoreCase("false")) return null;
        try {
            if (v.length() == 6) return 0xFF000000 | Integer.parseInt(v, 16);
            if (v.length() == 8) {
                long rgba = Long.parseLong(v, 16);
                return (int) (((rgba & 0xFF) << 24) | (rgba >>> 8));
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFF000000;
    }
}
