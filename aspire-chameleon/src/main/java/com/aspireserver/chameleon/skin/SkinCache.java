package com.aspireserver.chameleon.skin;

import com.aspireserver.chameleon.AspireChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.config.ConfigManager.SkinEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkinCache {

    private final AspireChameleon plugin;
    private final ConfigManager configManager;
    private final Map<String, CachedSkin> cache = new ConcurrentHashMap<>();

    public SkinCache(AspireChameleon plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void fetchAllSkins() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String mapId : configManager.getMapNames()) {
                for (SkinEntry entry : configManager.getSkinsForMap(mapId)) {
                    fetchSkin(entry.uuid(), entry.name());
                }
            }
            plugin.getLogger().info("[SkinCache] All skins fetched and cached.");
        });
    }

    private void fetchSkin(String uuid, String name) {
        if (cache.containsKey(uuid)) return;
        try {
            // Mojang session server profile endpoint
            URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() != 200) {
                plugin.getLogger().warning("[SkinCache] Failed to fetch skin for " + name + " (UUID: " + uuid + ") - HTTP " + conn.getResponseCode());
                return;
            }

            JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            JsonArray properties = json.getAsJsonArray("properties");

            for (JsonElement prop : properties) {
                JsonObject propObj = prop.getAsJsonObject();
                if ("textures".equals(propObj.get("name").getAsString())) {
                    String value = propObj.get("value").getAsString();
                    String signature = propObj.has("signature") ? propObj.get("signature").getAsString() : null;
                    cache.put(uuid, new CachedSkin(name, uuid, value, signature));
                    plugin.getLogger().info("[SkinCache] Cached skin: " + name);
                    break;
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("[SkinCache] Error fetching skin for " + name + ": " + e.getMessage());
        }
    }

    public CachedSkin getSkin(String uuid) {
        return cache.get(uuid);
    }

    public boolean isReady(String uuid) {
        return cache.containsKey(uuid);
    }

    public record CachedSkin(String name, String uuid, String textureValue, String textureSignature) {}
}
