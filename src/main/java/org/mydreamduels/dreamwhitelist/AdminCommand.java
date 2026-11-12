package org.mydreamduels.dreamwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * /dreamwhitelist (/dwl, /cw)
 *   reload                       re-read config.yml
 *   add <group> <command>        allow a command (creates the group if needed)
 *   remove <group> <command>     take it out again
 *   block <group> <command sub>  block a subcommand ("pv admin")
 *   unblock <group> <command sub>
 *   list [group]                 what a group allows
 *   groups                       every group and its permission
 *   test <player> <command>      would that player be allowed?
 *   dump                         every command on the server -> commands.txt
 */
final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("reload", "add", "remove", "block", "unblock", "list", "groups", "test", "dump");

    private final DreamWhitelist plugin;

    AdminCommand(DreamWhitelist plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    private static void msg(CommandSender s, String text) {
        s.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e&lDreamWhitelist &8» &7" + text));
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (a.length == 0) {
            msg(s, "&f/" + label + " reload &7| &fadd &7| &fremove &7| &fblock &7| &funblock &7| &flist &7| &fgroups &7| &ftest &7| &fdump");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                Platform.refreshAll(plugin);
                msg(s, "Reloaded - " + plugin.whitelist().groups().size() + " groups.");
            }
            case "add", "remove", "block", "unblock" -> edit(s, a);
            case "list" -> list(s, a);
            case "groups" -> {
                for (Whitelist.Group g : plugin.whitelist().groups()) {
                    msg(s, "&f" + g.name() + " &8- &7" + (g.permission() == null ? "everyone" : g.permission())
                            + " &8(&f" + g.commands().size() + "&7 commands, &f" + g.blockedSubcommands().size() + "&7 blocked&8)");
                }
            }
            case "test" -> test(s, a);
            case "dump" -> dump(s);
            default -> msg(s, "Unknown option. /" + label + " for help.");
        }
        return true;
    }

    private void edit(CommandSender s, String[] a) {
        String op = a[0].toLowerCase(Locale.ROOT);
        boolean sub = op.equals("block") || op.equals("unblock");
        if (a.length < 3) {
            msg(s, "Usage: /dwl " + op + " <group> <" + (sub ? "command subcommand" : "command") + ">");
            return;
        }
        String group = a[1].toLowerCase(Locale.ROOT);
        String value = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)).toLowerCase(Locale.ROOT).trim();
        if (value.startsWith("/")) value = value.substring(1);
        if (sub && !value.contains(" ")) {
            msg(s, "Write the command AND the part to block, e.g. &fpv admin");
            return;
        }
        String path = "groups." + group + (sub ? ".blocked-subcommands" : ".commands");
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(path));
        boolean adding = op.equals("add") || op.equals("block");
        if (adding) {
            if (list.contains(value)) {
                msg(s, "&f" + value + "&7 is already in &f" + group + "&7.");
                return;
            }
            list.add(value);
        } else if (!list.remove(value)) {
            msg(s, "&f" + value + "&7 is not in &f" + group + "&7.");
            return;
        }
        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        plugin.reloadAll();
        Platform.refreshAll(plugin);
        msg(s, (adding ? (sub ? "Blocked " : "Added ") : (sub ? "Unblocked " : "Removed ")) + "&f" + value + "&7 "
                + (adding ? "in" : "from") + " &f" + group + "&7."
                + (group.equals(Whitelist.DEFAULT_GROUP) ? "" : " Permission: &fdreamwhitelist.group." + group));
    }

    private void list(CommandSender s, String[] a) {
        String name = a.length > 1 ? a[1] : Whitelist.DEFAULT_GROUP;
        Whitelist.Group g = plugin.whitelist().group(name);
        if (g == null) {
            msg(s, "No group &f" + name + "&7. /dwl groups");
            return;
        }
        msg(s, "&f" + g.name() + "&7 (" + g.commands().size() + "): &f" + String.join("&7, &f", g.commands()));
        if (!g.blockedSubcommands().isEmpty()) msg(s, "Blocked: &c" + String.join("&7, &c", g.blockedSubcommands()));
    }

    private void test(CommandSender s, String[] a) {
        if (a.length < 3) {
            msg(s, "Usage: /dwl test <player> <command ...>");
            return;
        }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) {
            msg(s, "&f" + a[1] + "&7 is not online.");
            return;
        }
        String line = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
        if (line.startsWith("/")) line = line.substring(1);
        String root = line.split(" ")[0];
        List<Whitelist.Group> groups = plugin.whitelist().groupsOf(p);
        List<String> names = new ArrayList<>();
        for (Whitelist.Group g : groups) names.add(g.name());
        boolean bypass = Whitelist.bypass(p);
        boolean ok = bypass || (plugin.whitelist().allowed(groups, root) && !plugin.whitelist().blockedSubcommand(groups, line));
        msg(s, "&f/" + line + "&7 for &f" + p.getName() + "&7: " + (ok ? "&aallowed" : "&cblocked")
                + "&7 (" + (bypass ? "bypass" : "groups: " + String.join(", ", names)) + ")");
    }

    private void dump(CommandSender s) {
        Map<String, List<String>> byPlugin = new TreeMap<>();
        try {
            for (Map.Entry<String, Command> e : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
                String label = e.getKey();
                if (label.indexOf(':') >= 0) continue;
                String owner = "minecraft / server";
                if (e.getValue() instanceof org.bukkit.command.PluginIdentifiableCommand pic) owner = pic.getPlugin().getName();
                byPlugin.computeIfAbsent(owner, k -> new ArrayList<>()).add(label);
            }
        } catch (Throwable t) {
            msg(s, "Could not read the command list: " + t);
            return;
        }
        StringBuilder out = new StringBuilder("# Every command on the server, by plugin (namespaced forms left out)\n");
        for (Map.Entry<String, List<String>> e : byPlugin.entrySet()) {
            e.getValue().sort(String::compareTo);
            out.append("\n").append(e.getKey()).append(":\n");
            for (String l : e.getValue()) out.append("  - ").append(l).append('\n');
        }
        File file = new File(plugin.getDataFolder(), "commands.txt");
        try {
            Files.writeString(file.toPath(), out.toString(), StandardCharsets.UTF_8);
            msg(s, "Wrote " + byPlugin.values().stream().mapToInt(List::size).sum() + " commands to &fplugins/DreamWhitelist/commands.txt");
        } catch (IOException e) {
            msg(s, "Could not write commands.txt: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String label, String[] a) {
        if (a.length == 1) return filter(SUBS, a[0]);
        String op = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            if (op.equals("test")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, a[1]);
            }
            if (List.of("add", "remove", "block", "unblock", "list").contains(op)) {
                List<String> names = new ArrayList<>();
                ConfigurationSection groups = plugin.getConfig().getConfigurationSection("groups");
                if (groups != null) names.addAll(groups.getKeys(false));
                return filter(names, a[1]);
            }
        }
        if (a.length == 3) {
            if (op.equals("add") || op.equals("test")) return filter(Whitelist.serverCommands(), a[2]);
            if (op.equals("remove")) return filter(plugin.getConfig().getStringList("groups." + a[1].toLowerCase(Locale.ROOT) + ".commands"), a[2]);
            if (op.equals("unblock")) return filter(plugin.getConfig().getStringList("groups." + a[1].toLowerCase(Locale.ROOT) + ".blocked-subcommands"), a[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> in, String start) {
        String st = start.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : in) if (s.toLowerCase(Locale.ROOT).startsWith(st)) out.add(s);
        if (out.size() > 100) return out.subList(0, 100);
        return out;
    }
}
