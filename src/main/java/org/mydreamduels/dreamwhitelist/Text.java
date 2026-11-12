package org.mydreamduels.dreamwhitelist;

// PAPER-ONLY: only touched when Platform.ADVENTURE is true.

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MiniMessage with {@code <icon:...>} sprite icons and a shadow under the whole message.
 *
 *   <icon:nether_star|★>               an item texture, "★" on clients/servers without sprites
 *   <icon:minecraft:gui|icon/info|i>   any atlas and sprite path
 */
final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final boolean OBJECTS = has("net.kyori.adventure.text.object.ObjectContents");
    /** Items got their own atlas in 1.21.11 (and 26.x); before that they were in "blocks". */
    private static final Key ITEM_ATLAS = Key.key("minecraft", itemsAtlas() ? "items" : "blocks");
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private Text() {
    }

    private static boolean has(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean itemsAtlas() {
        try {
            String[] v = Bukkit.getMinecraftVersion().split("\\.");
            int major = Integer.parseInt(v[0]);
            if (major >= 26) return true;
            int minor = v.length > 1 ? Integer.parseInt(v[1]) : 0;
            int patch = v.length > 2 ? Integer.parseInt(v[2]) : 0;
            return minor > 21 || (minor == 21 && patch >= 11);
        } catch (Throwable t) {
            return true;
        }
    }

    private static final TagResolver ICON = TagResolver.resolver("icon", (args, ctx) -> {
        // <icon:name|fallback> or <icon:ns:atlas|path|fallback> - MiniMessage splits on ':' only
        StringBuilder sb = new StringBuilder();
        while (args.hasNext()) {
            if (sb.length() > 0) sb.append(':');
            sb.append(args.pop().value());
        }
        String[] parts = sb.toString().split("\\|");
        String fallback;
        Key atlas, sprite;
        if (parts.length >= 2 && parts[0].contains(":") && parts[1].contains("/")) {
            atlas = Key.key(parts[0]);
            sprite = Key.key(parts[1]);
            fallback = parts.length > 2 ? parts[2] : "•";
        } else {
            String name = parts[0];
            atlas = ITEM_ATLAS;
            sprite = Key.key(name.contains("/") ? name : "item/" + name);
            fallback = parts.length > 1 ? parts[1] : "•";
        }
        if (!OBJECTS) return Tag.selfClosingInserting(Component.text(fallback));
        try {
            return Tag.selfClosingInserting(Component.object(
                    net.kyori.adventure.text.object.ObjectContents.sprite(atlas, sprite)));
        } catch (Throwable t) {
            return Tag.selfClosingInserting(Component.text(fallback));
        }
    });

    static void clear() {
        CACHE.clear();
    }

    /** Parsed once per distinct text; the shadow (ARGB, null = none) goes under the whole line. */
    static Component render(String mini, Integer shadow) {
        return CACHE.computeIfAbsent((shadow == null ? "-" : Integer.toHexString(shadow)) + "|" + mini, k -> {
            Component c = MM.deserialize(mini, ICON);
            if (shadow == null) return c;
            try {
                return Component.text().shadowColor(ShadowColor.shadowColor(shadow)).append(c).build();
            } catch (LinkageError e) {
                return c;
            }
        });
    }

    static void send(CommandSender to, String mini, Integer shadow) {
        to.sendMessage(render(mini, shadow));
    }

    /** Paper: the "Unknown command" text Minecraft would show. */
    static void setUnknownMessage(org.bukkit.event.command.UnknownCommandEvent e, String mini, Integer shadow) {
        e.message(render(mini, shadow));
    }
}
