# Port to Forge 1.20.1

Branch `forge-1.20.1`, from `main` at MNX-074. `main` stays NeoForge 1.21.1; this branch is the same mod for
Forge 1.20.1. Behaviour is unchanged: what differs is the game's and the loader's API and data layout.

## Target

| | main | this branch |
|---|---|---|
| Minecraft | 1.21.1 | 1.20.1 |
| Loader | NeoForge 21.1.248 | Forge 47.4.26 (minimum 47.3.30: `ResourceLocation` 1.21 factories are backported there) |
| Java | 21 | 17 |
| Gradle plugin | ModDevGradle `moddev` | ModDevGradle `legacyforge` (same DSL, jar reobfuscated to SRG) |
| Metadata | `neoforge.mods.toml`, `type = "required"` | `mods.toml`, `mandatory = true`, `pack.mcmeta` (format 15) |
| Convention tags | `c:ingots/tin` | `forge:ingots/tin` (`TagDiscovery.CONVENTION_NAMESPACE`) |
| Datapack folders | `recipe/`, `tags/item/`, `structure/` | `recipes/`, `tags/items/`, `structures/` |
| Item stack in a recipe | `{"id": ..., "count": n}` | `{"item": ..., "count": n}`; cooking results are a bare id |
| Load conditions | `neoforge:conditions`, `neoforge:false` | `conditions`, `forge:false` |
| Recipe validation | `Recipe.CODEC` | `RecipeManager.fromJson` (`RecipeSources.decodeError`) |
| Optional codec fields | `optionalFieldOf` (strict) | `OptionalFields.strict` (1.20.1 DFU `optionalFieldOf` is lenient) |
| Network | payload registrar, NeoForge splits large payloads | one `SimpleChannel`; local `CustomPacketPayload` / `StreamCodec` / `PacketDistributor` keep every payload as on main; messages over 32000 bytes to the server (1 MB to the client) travel as bounded, ordered parts |
| KubeJS | 2101 `KubeJSPlugin` / `BindingRegistry` | 2001 `KubeJSPlugin` class / `BindingsEvent` |

## Mod formats (shipped process templates and recipe formats)

Checked against the recipes inside the 1.20.1 jars, and by the `shippedProcessTemplatesDecode` GameTest with the mods loaded.

| Mod (1.20.1) | Sized input | Output |
|---|---|---|
| Create 6.0.8 | `ingredients: [...]`, `processingTime` | `results: [{"item", "count"}]` |
| Mekanism 10.4.16 | `input: {"amount": n, "ingredient": ...}` | `output: {"item", "count"}`; keys `mainOutput`, `secondaryOutput`, `itemOutput` |
| Immersive Engineering 10.2 | `input: {"base_ingredient": ..., "count": n}` | `result: {"item", "count"}` |
| Create Crafts & Additions 1.3.3 | `input: ...` | `result: {"item", "count"}` |
| Create Metallurgy 1.0.1 | as Create | as Create |
| Vanilla | `ingredient` | crafting `{"item", "count"}`; smelting/blasting a bare id (one item); stonecutting id + top-level `count` |

Modern Industrialization and Oritech have no Forge 1.20.1 build: their process templates are not shipped here.
Their recipe format files stay (they only name recipe types, harmless when absent).

## Status

- `./gradlew build`: compiles, 26 JUnit tests green; `build/libs/materialnexus-0.1.0+1.20.1.jar` is reobfuscated to SRG.
- `./gradlew runGameTestServer`: 18/18 GameTests green with the dev pack (JEI, Jade, Mekanism, Create, IE, AE2,
  GuideME, Create Crafts & Additions, Create Metallurgy).
- `./gradlew runClient`: boots to the title screen with the dev pack.
- Not verified at runtime: a message large enough to be split (an apply with many in-game data edits); the Material Nexus screens themselves (no dev world on this branch yet), EMI
  (`-Pemi`), KubeJS (`-Pkubejs`), Almost Unified (`-Pau`).

## Carrying a main ticket over

Most code is identical. Watch for: tag namespace and folder names in new data, 1.21-only API (`Math.clamp`,
`List.getFirst`, `Component.withColor`, `DataResult.getOrThrow()`, `JsonObject.isEmpty()`, item components), new
payload codecs (`ByteBufCodecs` has no 1.20.1 equivalent: write `StreamCodec.of`), and screen widgets
(`mouseScrolled` has one delta, `ObjectSelectionList` takes `y0, y1`).
