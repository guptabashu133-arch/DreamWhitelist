package org.mydreamduels.dreamwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Paper / Folia / Spigot differences in one place. */
final class Platform {

    /** Folia: a thread per region, no main thread. */
    static final boolean FOLIA = has("io.papermc.paper.threadedregions.RegionizedServer");
    /** Paper (and forks): per-entity schedulers. */
    static final boolean ENTITY_SCHEDULER = has("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
    /** Native Adventure + MiniMessage (Paper and forks). Plain Spigot gets legacy text. */
    static final boolean ADVENTURE = has("net.kyori.adventure.text.minimessage.MiniMessage")
            && has("io.papermc.paper.text.PaperComponents");

    private Platform() {
    }

    private static boolean has(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Runs on the player's own thread (their region on Folia, the main thread elsewhere). */
    static void runFor(Plugin plugin, Player player, Runnable task) {
        if (ENTITY_SCHEDULER) {
            try {
                player.getScheduler().run(plugin, t -> task.run(), null);
                return;
            } catch (RuntimeException e) {
                if (FOLIA) throw e; // Folia has no main thread to fall back to
                // a server (or test mock) without per-entity schedulers after all - fall through
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Runs once the server is up (global region on Folia). */
    static void runLater(Plugin plugin, Runnable task, long ticks) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, ticks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        }
    }

    /** Re-sends every online player's command list (what tab shows) after the whitelist changed. */
    static void refreshAll(Plugin plugin) {
        for (Player p : Bukkit.getOnlinePlayers()) runFor(plugin, p, p::updateCommands);
    }
}
