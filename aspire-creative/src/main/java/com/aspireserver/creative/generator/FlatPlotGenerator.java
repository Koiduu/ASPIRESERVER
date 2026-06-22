package com.aspireserver.creative.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class FlatPlotGenerator extends ChunkGenerator {

    private static final int FLOOR_HEIGHT = 64;
    private static final Material FLOOR_MATERIAL = Material.SMOOTH_STONE;
    private static final Material ROAD_MATERIAL = Material.GRAY_CONCRETE;
    private static final Material BORDER_MATERIAL = Material.BEDROCK;

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random,
                                 int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        int startX = chunkX * 16;
        int startZ = chunkZ * 16;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;

                chunkData.setBlock(x, 0, z, Material.BEDROCK);

                for (int y = 1; y < FLOOR_HEIGHT; y++) {
                    chunkData.setBlock(x, y, z, Material.STONE);
                }

                Material surface = getSurfaceMaterial(worldX, worldZ);
                chunkData.setBlock(x, FLOOR_HEIGHT, z, surface);
            }
        }
    }

    private Material getSurfaceMaterial(int worldX, int worldZ) {
        int plotSpacing = 82;

        int relX = Math.floorMod(worldX, plotSpacing);
        int relZ = Math.floorMod(worldZ, plotSpacing);

        if (relX < 75 && relZ < 75) {
            if (relX == 0 || relX == 74 || relZ == 0 || relZ == 74) {
                return BORDER_MATERIAL;
            }
            return FLOOR_MATERIAL;
        }

        return ROAD_MATERIAL;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
