package com.aspireserver.chameleon.skin;

import com.aspireserver.chameleon.MecchaChameleon;
import com.aspireserver.chameleon.config.ConfigManager;
import com.aspireserver.chameleon.config.ConfigManager.SkinEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkinCache {

    private final MecchaChameleon plugin;
    private final ConfigManager configManager;
    private final Map<String, CachedSkin> cache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SkinCache(MecchaChameleon plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void fetchAllSkins() {
        for (ConfigManager.MapData mapData : configManager.getMaps().values()) {
            for (SkinEntry entry : mapData.skins) {
                fetchSkinByUuid(entry.uuid(), entry.name());
            }
        }
    }

    public void fetchSkinByUuid(String uuid, String name) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fetchSkin(uuid, name));
    }

    private void fetchSkin(String uuid, String name) {
        try {
            String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[SkinCache] Failed to fetch skin for " + name + " (HTTP " + response.statusCode() + ")");
                return;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject properties = json.getAsJsonArray("properties").get(0).getAsJsonObject();
            String value = properties.get("value").getAsString();
            String signature = properties.get("signature").getAsString();

            cache.put(uuid, new CachedSkin(name, uuid, value, signature));
            plugin.getLogger().info("[SkinCache] Cached skin: " + name);
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
