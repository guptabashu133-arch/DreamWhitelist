package org.mydreamduels.dreamwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The groups from config.yml, with every whitelisted command expanded to all of its names.
 *
 * Built once per reload (and again when the server finishes starting, because plugins register
 * commands late). Lookups are plain set checks, so the per-command cost is a handful of
 * permission checks - safe on any thread, including Folia's region threads.
 */
public final class Whitelist {

    public static final String DEFAULT_GROUP = "default";

    /** One group: what its players may run. */
    public static final class Group {
        final String name;
        final String permission;          // null for "default"
        final List<String> raw;           // as written in config.yml
        final Set<String> labels = new HashSet<>();     // exact names, aliases included
        final List<String> prefixes = new ArrayList<>(); // from "something*"
        final List<String> blockedRaw;    // as written
        final List<String> blocked = new ArrayList<>();  // "root sub", every alias of root

        Group(String name, List<String> raw, List<String> blockedRaw) {
            this.name = name;
            this.permission = name.equals(DEFAULT_GROUP) ? null : "dreamwhitelist.group." + name;
            this.raw = raw;
            this.blockedRaw = blockedRaw;
        }

        public String name() {
            return name;
        }

        public String permission() {
            return permission;
        }

        public List<String> commands() {
            return raw;
        }

        public List<String> blockedSubcommands() {
            return blockedRaw;
        }
    }

    private volatile Map<String, Group> groups = Map.of();
    private volatile boolean includeAliases = true;
    private volatile boolean hideNamespaced = true;

    // ------------------------------------------------------------------ building

    public void load(FileConfiguration config) {
        includeAliases = config.getBoolean("settings.include-aliases", true);
        hideNamespaced = config.getBoolean("settings.hide-namespaced", true);
        Map<String, Group> out = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("groups");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                String key = name.toLowerCase(Locale.ROOT);
                List<String> cmds = clean(section.getStringList(name + ".commands"));
                List<String> blocked = clean(section.getStringList(name + ".blocked-subcommands"));
                out.put(key, new Group(key, cmds, blocked));
            }
        }
        out.putIfAbsent(DEFAULT_GROUP, new Group(DEFAULT_GROUP, new ArrayList<>(), new ArrayList<>()));
        groups = out;
        rebuild();
    }

    /** Re-reads the server's command list: call when plugins may have added commands. */
    public void rebuild() {
        Map<String, Set<String>> names = aliasIndex();
        for (Group g : groups.values()) {
            Set<String> labels = new HashSet<>();
            List<String> prefixes = new ArrayList<>();
            for (String entry : g.raw) {
                if (entry.endsWith("*")) {
                    prefixes.add(entry.substring(0, entry.length() - 1));
                    continue;
                }
                labels.add(entry);
                if (includeAliases) {
                    Set<String> same = names.get(entry);
                    if (same != null) labels.addAll(same);
                }
            }
            List<String> blocked = new ArrayList<>();
            for (String entry : g.blockedRaw) {
                int space = entry.indexOf(' ');
                if (space < 0) {
                    blocked.add(entry); // a whole command: same as not listing it
                    continue;
                }
                String root = entry.substring(0, space), rest = entry.substring(space);
                Set<String> roots = new HashSet<>();
                roots.add(root);
                Set<String> same = names.get(root);
                if (same != null) roots.addAll(same);
                for (String r : roots) blocked.add(r + rest);
            }
            synchronized (g) {
                g.labels.clear();
                g.labels.addAll(labels);
                g.prefixes.clear();
                g.prefixes.addAll(prefixes);
                g.blocked.clear();
                g.blocked.addAll(blocked);
            }
        }
    }

    /** name -> every non-namespaced name of the same command ("home" -> {home, homes}). */
    private static Map<String, Set<String>> aliasIndex() {
        Map<String, Set<String>> out = new HashMap<>();
        Map<String, Command> known;
        try {
            known = Bukkit.getCommandMap().getKnownCommands();
        } catch (Throwable t) {
            return out;
        }
        Map<Command, Set<String>> byCommand = new IdentityHashMap<>();
        for (Map.Entry<String, Command> e : new ArrayList<>(known.entrySet())) {
            String label = e.getKey().toLowerCase(Locale.ROOT);
            if (label.indexOf(':') >= 0 || e.getValue() == null) continue;
            byCommand.computeIfAbsent(e.getValue(), c -> new HashSet<>()).add(label);
        }
        for (Set<String> same : byCommand.values()) {
            for (String label : same) out.computeIfAbsent(label, k -> new HashSet<>()).addAll(same);
        }
        return out;
    }

    private static List<String> clean(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (t.startsWith("/")) t = t.substring(1);
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    // ------------------------------------------------------------------ checks

    public Collection<Group> groups() {
        return groups.values();
    }

    public Group group(String name) {
        return groups.get(name.toLowerCase(Locale.ROOT));
    }

    public static boolean bypass(Player p) {
        return p.hasPermission("dreamwhitelist.bypass");
    }

    /** The groups this player is in ("default" always). */
    public List<Group> groupsOf(Player p) {
        List<Group> out = new ArrayList<>();
        for (Group g : groups.values()) {
            if (g.permission == null || p.hasPermission(g.permission)) out.add(g);
        }
        return out;
    }

    /** Can this player see / run the command called {@code label} (no slash, any case)? */
    public boolean allowed(Player p, String label) {
        if (bypass(p)) return true;
        return allowed(groupsOf(p), label);
    }

    public boolean allowed(List<Group> groups, String label) {
        String l = label.toLowerCase(Locale.ROOT);
        if (l.startsWith("/")) l = l.substring(1);
        boolean namespaced = l.indexOf(':') >= 0;
        for (Group g : groups) {
            synchronized (g) {
                if (g.labels.contains(l)) return true;
                if (namespaced && hideNamespaced) continue; // only exact full names
                String bare = namespaced ? l.substring(l.indexOf(':') + 1) : l;
                if (namespaced && g.labels.contains(bare)) return true;
                for (String prefix : g.prefixes) if (bare.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    /** Is this whole command line (no slash) a blocked subcommand for these groups? */
    public boolean blockedSubcommand(List<Group> groups, String line) {
        String l = line.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        if (l.startsWith("/")) l = l.substring(1);
        int colon = l.indexOf(':');
        int space = l.indexOf(' ');
        if (colon >= 0 && (space < 0 || colon < space)) l = l.substring(colon + 1); // minecraft:tp x -> tp x
        for (Group g : groups) {
            synchronized (g) {
                for (String b : g.blocked) {
                    if (l.equals(b) || l.startsWith(b + " ")) return true;
                }
            }
        }
        return false;
    }

    /** Every non-namespaced command name on the server, sorted. */
    public static List<String> serverCommands() {
        List<String> out = new ArrayList<>();
        try {
            for (String label : Bukkit.getCommandMap().getKnownCommands().keySet()) {
                if (label.indexOf(':') < 0) out.add(label.toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
        }
        out.sort(String::compareTo);
        return out;
    }
}
