# Port to Forge 1.20.1

Branch `forge-1.20.1`, from `main` at MNX-074, main tickets carried over up to MNX-081. `main` stays NeoForge 1.21.1; this branch is the same mod for
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
| Script recipe edits (MNX-076) | `beforeRecipeLoading(RecipesKubeEvent…)`, `KubeRecipe` (with `sourceLine`) | `injectRuntimeRecipes(RecipesEventJS…)`, called after scripts, `RecipeJS` (no script line: created recipes have no source) |
| File tags (MNX-076) | a fresh `TagLoader.loadAndBuild` | `ScriptSources.load` mirrors Forge's `TagLoader.load` then calls `build`: KubeJS 2001 hooks `load` and resets its shared tag context even for a loader of its own |
| Tag audit (MNX-080) | flags recipes still on `forge:` tags | flags recipes on `c:` tags (`TagAudit.OTHER_NAMESPACE`), usually empty on Forge; record fields keep main's names |
| Recipes of the manager | `RecipeHolder.id()` | `Recipe.getId()` |

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

- `./gradlew build`: compiles, 32 JUnit tests green (MNX-081); `build/libs/materialnexus-0.1.0+1.20.1.jar` is reobfuscated to SRG.
- `./gradlew runGameTestServer`: 18/18 GameTests green with the dev pack (JEI, Jade, Mekanism, Create, IE, AE2,
  GuideME, Create Crafts & Additions, Create Metallurgy).
- After MNX-081, with the big dev pack: 18/19 GameTests green; `full_preview_on_the_dev_pack_is_fast` still fails (6.7 s, limit 5 s), as noted below.
- `./gradlew runClient`: boots to the title screen with the dev pack.
- `./gradlew runGameTestServer -Pkubejs` (MNX-076/077, big dev pack): the KubeJS bindings and `scriptDecisionsAreRead`
  pass (Thermal tin ingot and nickel dust decided, placed on the fixture lines). `full_preview_on_the_dev_pack_is_fast`
  fails with or without KubeJS (about 10 s, limit 5 s): it predates MNX-076 (no decision, no extra preview work) and comes
  with the big dev pack, to look at separately. GregTech CEu adds about 18000 item tag entries in memory: counted in the
  report, never evidence.
- Not verified at runtime: a message large enough to be split (an apply with many in-game data edits); the Material Nexus screens themselves (no dev world on this branch yet), EMI
  (`-Pemi`), Almost Unified (`-Pau`).

## Carrying a main ticket over

Most code is identical. Watch for: tag namespace and folder names in new data, 1.21-only API (`Math.clamp`,
`List.getFirst`, `Component.withColor`, `DataResult.getOrThrow()`, `JsonObject.isEmpty()`, item components), new
payload codecs (`ByteBufCodecs` has no 1.20.1 equivalent: write `StreamCodec.of`), and screen widgets
(`mouseScrolled` has one delta, `ObjectSelectionList` takes `y0, y1`). Also: `MutableComponent.withColor` is `withStyle(s -> s.withColor(...))`, a `listOf().xmap(Set::copyOf, ...)` may need its type
(`.<Set<ResourceLocation>>xmap`), and user-facing text (lang, wiki, changelog, README, store page) says `forge:ingots/tin`
where main says `c:ingots/tin`; design notes in `docs/` keep `c:`.
