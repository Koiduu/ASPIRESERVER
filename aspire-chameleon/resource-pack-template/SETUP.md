# MecchaChameleon Resource Pack Setup

## Structure
```
resource-pack-template/
  pack.mcmeta                          -- Pack metadata (format 22 for 1.20.3+)
  assets/minecraft/
    models/item/
      leather_helmet.json              -- Override with CustomModelData 10001
      leather_chestplate.json          -- Override with CustomModelData 10001
      leather_leggings.json            -- Override with CustomModelData 10001
      leather_boots.json               -- Override with CustomModelData 10001
      chameleon_camo_helmet.json       -- Custom camo model for helmet
      chameleon_camo_chestplate.json   -- Custom camo model for chestplate
      chameleon_camo_leggings.json     -- Custom camo model for leggings
      chameleon_camo_boots.json        -- Custom camo model for boots
    textures/item/
      chameleon_camo.png               -- YOUR solid-color texture (16x16 placeholder)
```

## How It Works
1. The plugin sets CustomModelData=10001 on all leather armor pieces given to hiders
2. The resource pack overrides those items to use a flat solid-color model
3. The leather armor dye (RGB) still applies, giving each piece the chosen color
4. Result: player appears as a solid colored block shape that matches their environment

## Setup Steps
1. Create a 16x16 `chameleon_camo.png` texture (solid white, the dye handles coloring)
2. Place it in `assets/minecraft/textures/item/chameleon_camo.png`
3. Zip the entire folder (pack.mcmeta must be at the root of the zip)
4. Host the zip and set `resource-pack=<URL>` in `server.properties`
5. Optionally set `resource-pack-required=true` to force the pack
