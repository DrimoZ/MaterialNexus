# Port to Forge 1.20.1

Branch `forge-1.20.1` (worktree `../MaterialNexus-forge-1.20.1`), from `main` at MNX-074. `main` stays NeoForge 1.21.1.

## Target

| | main | this branch |
|---|---|---|
| Minecraft | 1.21.1 | 1.20.1 |
| Loader | NeoForge 21.1.248 | Forge 47.4.26 |
| Java | 21 | 17 |
| Gradle plugin | ModDevGradle `moddev` | ModDevGradle `legacyforge` (same DSL, reobf to SRG) |
| Convention tags | `c:ingots/tin` | `forge:ingots/tin` |
| Datapack folders | `recipe/`, `tags/item/` | `recipes/`, `tags/items/` |
| Recipe result stack | `{"id": ...}` | `{"item": ...}` |
| Load conditions | `neoforge:conditions` | `forge:conditions` |
| pack_format | 48 | 15 |

## Steps

1. **Build**: `legacyforge` plugin, Forge 47.4.26, Parchment 1.20.1, Java 17, `mods.toml`; 1.20.1 dev pack
   (JEI 15, Jade 11, Mekanism 10.4, Create 6 for 1.20.1, IE 10.2, AE2 15). Modern Industrialization has no
   Forge 1.20.1 build: it leaves the dev pack, and its recipe format file stays as harmless data.
2. **Mechanical API**: `ResourceLocation.fromNamespaceAndPath/parse/withDefaultNamespace` to constructors,
   NeoForge packages to Forge (`net.minecraftforge.*`), `@EventBusSubscriber` to `@Mod.EventBusSubscriber`,
   `DeferredItem` to `RegistryObject`, Java 21 collection methods to Java 17.
3. **Network**: local `CustomPacketPayload` / `StreamCodec` shims keep every payload unchanged; `MnxNetwork` registers
   them on one `SimpleChannel` (main-thread handlers, server-side permission check unchanged).
4. **Recipes**: `RecipeHolder` to `Recipe#getId`, `getResultItem(RegistryAccess)`.
5. **Data layout**: folder names, result keys, condition keys, pack metadata, `forge:` convention namespace,
   shipped resources (`data/c/tags/item` to `data/forge/tags/items`, recipe format files).
6. **Client**: `mouseScrolled(x, y, delta)`, `renderBackground(GuiGraphics)`, `TickEvent.ClientTickEvent`,
   color handlers, tooltip event.
7. **Integrations**: JEI 15 and EMI 1.20.1 APIs, KubeJS 2001 plugin API.
8. **Verify**: `./gradlew build` (compile + JUnit), `runGameTestServer`, then a client run when possible.
9. **Docs**: README/wiki note the 1.20.1 differences (tag namespace, folders).

Each step ends compiling; commits use the `MNX-PORT:` prefix.
