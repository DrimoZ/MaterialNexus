# Graph Report - MaterialNexus  (2026-10-13)

## Corpus Check
- 270 files · ~161,571 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 12 file(s) not represented in the graph (top: (none) 4, .properties 2, .jar 1)

## Summary
- 1890 nodes · 6197 edges · 114 communities (83 shown, 31 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 339 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `494b9292`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Wiki: The Screen
- MaterialNexusGameTests.java
- FormId
- net.minecraft.client.gui.GuiGraphics
- PolicyEditor
- com.google.gson.JsonElement
- net.neoforged.bus.api.SubscribeEvent
- MnxJeiPlugin.java
- Banners
- ReviewDrawer
- PolicyEditor.java
- net.minecraft.network.codec.StreamCodec
- .onTagsUpdated
- MaterialDetailPayload
- Theme
- DiscoveryEvents.java
- GenerateLogo.java
- Layout
- MaterialsCommand
- .mouseClicked
- PendingChanges
- MaterialNexusGameTests
- com.google.gson.JsonObject
- GeneratedPack
- .plan
- net.minecraft.client.gui.Font
- ResolvedSnapshot
- .register
- Hits
- GenerateLogo
- MaterialId
- TriagePanel
- .add
- Spec
- ApplyHistory
- View
- net.minecraft.network.chat.Component
- net.minecraft.server.packs.resources.ResourceManager
- Kind
- net.minecraft.resources.ResourceLocation
- Changelog (0.1.0, 0.2.0)
- ScriptsPanel
- Entry
- 14 Backlog (MNX tickets)
- NexusScreen.java
- .convert
- Non-negotiable rules
- Apply flow (validate, backup, write policy, regenerate, /reload)
- Forms and item name patterns as data (material_nexus/forms)
- RecipeFamilyPayload
- .render
- Discovery (core/discovery)
- CreatedItemsClient
- ConversionEvents.java
- UnifiedItemsClient
- DataReadRequest
- RecipeFamilyRequest
- RecipeClassification
- com.mojang.serialization.Codec
- net.minecraft.network.FriendlyByteBuf
- Editor.java
- ADR-018 Process rules are written by example
- ResolvedForm
- MnxEmiPlugin.java
- ProcessPlannerTest
- Domain
- PreviewRequest
- 4. Design system
- 21 — Support backlog (user feedback)
- JEI plugin (MNX-013)
- Viewer
- NexusTerminalItem.java
- ItemConversions
- ProcessPayload
- DataListPayload
- CLAUDE.md operational contract
- Output and input rewriting
- ADR-005 No persistent history
- TagAudit
- 22 — UI redesign (analysis and design, MNX-093)
- gradlew
- DataReadPayload
- IntegrationAdapter
- .in
- net.minecraft.world.item.ItemStack
- Action
- list
- RecipeViewer
- RevertRequest
- Owner
- Screen settings (config/materialnexus/screen.json)
- 01 Architecture
- 02 Domain model
- 05 Recipe system
- 06 JEI / EMI
- 09 Reload and synchronisation
- 16 Architecture decisions
- .generated
- 9. Built to evolve
- .preview
- ProcessRequest
- .type
- SuggestionsRequest
- MatrixRequest
- button
- screen
- multilineeditbox
- .type
- editbox

## God Nodes (most connected - your core abstractions)
1. `FormId` - 94 edges
2. `NexusScreen` - 92 edges
3. `MaterialId` - 83 edges
4. `MaterialForm` - 56 edges
5. `PendingChanges` - 44 edges
6. `MaterialNexus` - 42 edges
7. `ResolutionPolicy` - 40 edges
8. `DetailTable` - 39 edges
9. `ResolvedForm` - 37 edges
10. `ResolvedSnapshot` - 34 edges

## Surprising Connections (you probably didn't know these)
- `MNX-092 — Feature: tags from the interface` --references--> `MaterialForm`  [INFERRED]
  docs/21-SUPPORT-BACKLOG.md → src/main/java/dev/drimoz/materialnexus/core/domain/MaterialForm.java
- `10.2 Data the views need and the server does not send` --references--> `Effect`  [INFERRED]
  docs/22-UI-REDESIGN.md → src/main/java/dev/drimoz/materialnexus/datapack/PackContent.java
- `6.4 Recipes tab` --references--> `Row`  [INFERRED]
  docs/22-UI-REDESIGN.md → src/main/java/dev/drimoz/materialnexus/datapack/RecipeFamilies.java
- `MNX-088 — UX: recipes shown "JEI style"` --references--> `RecipeFamilyPayload`  [INFERRED]
  docs/21-SUPPORT-BACKLOG.md → src/main/java/dev/drimoz/materialnexus/network/RecipeFamilyPayload.java
- `Material unification for modpack makers` --semantically_similar_to--> `Almost Unified domain split`  [INFERRED] [semantically similar]
  README.md → docs/wiki/Compatibility.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Datapack rule data kinds under material_nexus/** — docs_wiki_datapack_guide_forms_data, docs_wiki_datapack_guide_recipe_formats, docs_wiki_datapack_guide_process_templates, docs_wiki_datapack_guide_presets_data, docs_wiki_datapack_guide_material_aliases [EXTRACTED 1.00]
- **Decide, Preview, Apply, Undo flow** — docs_wiki_applying_changes_pending_change, docs_wiki_applying_changes_preview, docs_wiki_applying_changes_apply, docs_wiki_configuration_files_generated_datapack, docs_wiki_applying_changes_undo_and_history [EXTRACTED 1.00]
- **What Material Nexus writes into one generated tag file** — docs_04_tags_tag_removals, docs_04_tags_add_missing_tags, docs_04_tags_tag_edits, docs_04_tags_convention_tags [EXTRACTED 1.00]
- **Optional integrations (JEI/EMI, Almost Unified, KubeJS)** — docs_wiki_compatibility_jei_emi_hiding, docs_wiki_compatibility_almost_unified_domains, docs_wiki_compatibility_kubejs_binding, docs_wiki_compatibility_script_decisions_read [EXTRACTED 1.00]
- **Reading script decisions and taking over a scripted pack** — docs_20_script_decisions_beforerecipeloading, docs_20_script_decisions_scriptchanges, docs_20_script_decisions_deduction, docs_20_script_decisions_status, docs_20_script_decisions_seen_in, docs_07_gui_scripts_view, docs_16_decisions_adr_022 [EXTRACTED 1.00]
- **Preview, Apply, generated pack and reload pipeline** — docs_07_gui_review_and_apply, docs_08_config_datapack_policy_files, docs_01_architecture_generated_datapack, docs_08_config_datapack_manifest_json, docs_09_reload_sync_reload_sequence, docs_01_architecture_resolvedsnapshot [INFERRED 0.85]

## Communities (114 total, 31 thin omitted)

### Community 0 - "Wiki: The Screen"
Cohesion: 0.06
Nodes (95): Bug report issue template, Material or mod compatibility issue template, Issue template config (blank issues off, contact links), Feature request issue template, Design boundaries: requests that will be declined, Wiki footer, Wiki sidebar, Wiki: Applying Changes (+87 more)

### Community 1 - "MaterialNexusGameTests.java"
Cohesion: 0.11
Nodes (13): atomicboolean, atomicreference, com.mojang.serialization.DataResult, confidence, fakeplayerfactory, fmlpaths, modlist, net.minecraft.world.item.Items (+5 more)

### Community 2 - "FormId"
Cohesion: 0.14
Nodes (8): TagDiscovery, FormId, Override, Override, MaterialForm, ProcessRules, ResolutionPolicy, Specificity

### Community 3 - "net.minecraft.client.gui.GuiGraphics"
Cohesion: 0.07
Nodes (10): net.minecraft.client.gui.GuiGraphics, JsonObject, Override, NexusScreen, Tab, FORMS, MISSING, PROCESS (+2 more)

### Community 4 - "PolicyEditor"
Cohesion: 0.21
Nodes (4): Entry, PolicyEditor, PolicyEditorTest, PresetTest

### Community 5 - "com.google.gson.JsonElement"
Cohesion: 0.17
Nodes (7): com.google.gson.JsonElement, RecipeRewrites, Source, Format, FormatJson, RecipeFormats, RecipeRewritesTest

### Community 6 - "net.neoforged.bus.api.SubscribeEvent"
Cohesion: 0.10
Nodes (18): clienttickevent, creativemodetabs, deferredregister, dist, inputstream, Items, minecraft, nativeimage (+10 more)

### Community 7 - "MnxJeiPlugin.java"
Cohesion: 0.20
Nodes (7): mezz.jei.api.IModPlugin, mezz.jei.api.JeiPlugin, mezz.jei.api.runtime.IJeiRuntime, Override, MnxJeiPlugin, Viewer, vanillatypes

### Community 8 - "Banners"
Cohesion: 0.27
Nodes (4): java.awt.image.BufferedImage, Banners, Color, Graphics2D

### Community 9 - "ReviewDrawer"
Cohesion: 0.18
Nodes (3): Group, Override, ReviewDrawer

### Community 10 - "PolicyEditor.java"
Cohesion: 0.13
Nodes (26): arrays, assertnotnull, files, gsonbuilder, inputstreamreader, instant, ioexception, jsonarray (+18 more)

### Community 11 - "net.minecraft.network.codec.StreamCodec"
Cohesion: 0.18
Nodes (12): bytebufcodecs, decoderexception, io.netty.buffer.ByteBuf, net.minecraft.network.codec.StreamCodec, net.minecraft.network.protocol.common.custom.CustomPacketPayload, net.neoforged.bus.api.IEventBus, net.neoforged.fml.common.Mod, net.neoforged.fml.ModContainer (+4 more)

### Community 12 - ".onTagsUpdated"
Cohesion: 0.06
Nodes (26): dev.latvian.mods.kubejs.core.RecipeManagerKJS, dev.latvian.mods.kubejs.plugin.KubeJSPlugin, dev.latvian.mods.kubejs.recipe.RecipesKubeEvent, dev.latvian.mods.kubejs.script.BindingRegistry, net.minecraft.server.packs.resources.SimplePreparableReloadListener, net.neoforged.neoforge.event.TagsUpdatedEvent, Kind, ADDED (+18 more)

### Community 13 - "MaterialDetailPayload"
Cohesion: 0.09
Nodes (14): 10.2 Data the views need and the server does not send, 10. Feasibility notes (read only, nothing run in game), Edge, FamilyRelations, Relation, FormsFile, JsonElement, JsonObject (+6 more)

### Community 14 - "Theme"
Cohesion: 0.08
Nodes (10): asserttrue, Regions, ScreenSettings, Values, Theme, GRAPHITE, MIDNIGHT, PAPER (+2 more)

### Community 15 - "DiscoveryEvents.java"
Cohesion: 0.13
Nodes (17): gson, jsonops, jsonprimitive, logutils, net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.neoforge.event.AddReloadListenerEvent, org.slf4j.Logger (+9 more)

### Community 16 - "GenerateLogo.java"
Cohesion: 0.13
Nodes (14): awt, basicstroke, convolveop, ellipse2d, file, gradientpaint, imageio, kernel (+6 more)

### Community 18 - "MaterialsCommand"
Cohesion: 0.25
Nodes (7): CommandSourceStack, net.neoforged.neoforge.event.RegisterCommandsEvent, JsonElement, MinecraftServer, ResourceLocation, Row, MaterialsCommand

### Community 20 - "PendingChanges"
Cohesion: 0.09
Nodes (6): Component, Entry, JsonElement, JsonObject, PendingChanges, Change

### Community 21 - "MaterialNexusGameTests"
Cohesion: 0.14
Nodes (8): net.minecraft.gametest.framework.GameTest, net.minecraft.gametest.framework.GameTestHelper, net.minecraft.server.MinecraftServer, net.neoforged.neoforge.gametest.GameTestHolder, net.neoforged.neoforge.gametest.PrefixGameTestTemplate, Pattern, RecipeSources, MaterialNexusGameTests

### Community 22 - "com.google.gson.JsonObject"
Cohesion: 0.16
Nodes (7): com.google.gson.JsonObject, Presets, Override, MissingValue, ProcessTemplates, Template, Values

### Community 23 - "GeneratedPack"
Cohesion: 0.24
Nodes (4): net.minecraft.server.packs.PackType, net.neoforged.neoforge.event.AddPackFindersEvent, GeneratedPack, ResourceLocation

### Community 24 - ".plan"
Cohesion: 0.19
Nodes (6): com.google.gson.JsonArray, Route, Index, ProcessPlanner, Reading, Ref

### Community 25 - "net.minecraft.client.gui.Font"
Cohesion: 0.10
Nodes (9): fontset, function, net.minecraft.client.gui.components.AbstractWidget, net.minecraft.client.gui.Font, obfuscationreflectionhelper, DataPanel, Kind, Override (+1 more)

### Community 26 - "ResolvedSnapshot"
Cohesion: 0.10
Nodes (9): ResourceLocation, ResolvedSnapshot, DiagnosticsReport, Row, UnhandledTypes, ResourceLocation, NexusQueries, NexusQueriesTest (+1 more)

### Community 27 - ".register"
Cohesion: 0.08
Nodes (16): commands, net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent, net.neoforged.neoforge.network.handling.IPayloadContext, packetdistributor, ClientHooks, CanonicalChange, MnxNetwork, Override (+8 more)

### Community 28 - "Hits"
Cohesion: 0.09
Nodes (12): FormMatrix, Hit, Hits, Status, ABSENT, NOTHING, PENDING, RESET (+4 more)

### Community 29 - "GenerateLogo"
Cohesion: 0.36
Nodes (5): java.awt.Color, java.awt.geom.Path2D, java.awt.Graphics2D, java.awt.Shape, GenerateLogo

### Community 30 - "MaterialId"
Cohesion: 0.12
Nodes (9): org.junit.jupiter.api.Test, DiscoveredMaterials, Override, MaterialId, CanonicalResolverTest, LangParityTest, MaterialIdCodecTest, PolicyFilesTest (+1 more)

### Community 32 - ".add"
Cohesion: 0.13
Nodes (12): AbsentLine, DetailTable, FormLine, Group, CREATABLE, PENDING, SINGLE, TODO (+4 more)

### Community 33 - "Spec"
Cohesion: 0.23
Nodes (7): OnPress, Kind, DANGER, GHOST, PRIMARY, SECONDARY, Spec

### Community 35 - "View"
Cohesion: 0.20
Nodes (10): View, DATA, FORMS, HOME, MATERIALS, PRESETS, SCRIPTS, SETTINGS (+2 more)

### Community 36 - "net.minecraft.network.chat.Component"
Cohesion: 0.15
Nodes (5): chatformatting, formattedcharsequence, net.minecraft.network.chat.Component, ProcessPanel, Rule

### Community 37 - "net.minecraft.server.packs.resources.ResourceManager"
Cohesion: 0.24
Nodes (6): net.minecraft.server.packs.resources.ResourceManager, net.minecraft.util.profiling.ProfilerFiller, Override, Override, Override, Override

### Community 38 - "Kind"
Cohesion: 0.18
Nodes (8): EditableData, Entry, Kind, FORMS, MATERIALS, PRESETS, PROCESS_TEMPLATES, RECIPE_FORMATS

### Community 39 - "net.minecraft.resources.ResourceLocation"
Cohesion: 0.13
Nodes (12): net.minecraft.resources.ResourceLocation, Describe, Entry, Ownership, TagEdit, TagEdit, Content, Effect (+4 more)

### Community 40 - "Changelog (0.1.0, 0.2.0)"
Cohesion: 0.18
Nodes (14): Changelog (0.1.0, 0.2.0), 04 Tags, add_missing_tags (MNX-078), tag_edits player edits (MNX-079), Scripts view (MNX-076), 08 Configuration and generated datapack, ADR-010 Analysis sees the pre-MNX state, ADR-023 Missing convention tags added on request, where the pack uses them (+6 more)

### Community 41 - "ScriptsPanel"
Cohesion: 0.21
Nodes (3): Row, RecipesPanel, ScriptsPanel

### Community 42 - "Entry"
Cohesion: 0.27
Nodes (4): net.minecraft.world.item.Item, CreatedItem, Entry, CreatedItemsTest

### Community 43 - "14 Backlog (MNX tickets)"
Cohesion: 0.22
Nodes (13): Recipe editor (not implemented), not_same marks (MNX-050), 10 Compatibility, KubeJS read-only MaterialNexus binding (MNX-067), 13 Roadmap (phases 0 to 5), 14 Backlog (MNX tickets), ADR-001 No KubeJS dependency, ADR-014 A tag means usable as, not the same item (+5 more)

### Community 44 - "NexusScreen.java"
Cohesion: 0.13
Nodes (15): component, consumer, locale, net.minecraft.client.gui.components.Button, net.minecraft.client.gui.components.EditBox, net.minecraft.client.gui.screens.Screen, Btn, Field (+7 more)

### Community 45 - ".convert"
Cohesion: 0.25
Nodes (5): net.minecraft.world.Container, net.neoforged.neoforge.event.entity.EntityJoinLevelEvent, Open, PlayerLoggedInEvent, ConversionEvents

### Community 46 - "Non-negotiable rules"
Cohesion: 0.23
Nodes (12): Non-negotiable rules, No tick analysis, ResolvedSnapshot (read-only index), Conversion recipes (shapeless 1:1 to canonical), manifest.json (every written file, rule and reason), Post-/reload analysis sequence, Targeted network queries (permission-checked), 12 Performance (+4 more)

### Community 47 - "Apply flow (validate, backup, write policy, regenerate, /reload)"
Cohesion: 0.18
Nodes (12): Generated global datapack (config/materialnexus/generated), Policy precedence: Global < Material < Form < Explicit Resource Override, Canonical selection (resolver), Review and apply panel (Preview), Policy files (policies/global.json, materials/*.json), Presets (MNX-019), Apply flow (validate, backup, write policy, regenerate, /reload), Other mods failing during a live apply (IE arc recycling) (+4 more)

### Community 48 - "Forms and item name patterns as data (material_nexus/forms)"
Cohesion: 0.21
Nodes (12): Confidence rank (DiscoveryEvidence.Confidence), Form (FormId), Material (MaterialId, MaterialDefinition), Provider (concrete resource candidate), Recipe family and classification, Convention tags (c:<folder>/<material>), Tag removals (MNX-007, NeoForge remove entries), Duplicate classification (+4 more)

### Community 49 - "RecipeFamilyPayload"
Cohesion: 0.16
Nodes (8): Override, Type, RecipeFamilyPayload, Override, Status, Type, Row, ScriptsPayload

### Community 51 - "Discovery (core/discovery)"
Cohesion: 0.20
Nodes (11): 00 Project, Main cycle: Discover, Normalize, Analyze, Resolve Policy, Preview, Apply, Snapshot, Material Nexus (product positioning), Out of scope (inventory, history, chest scanning, logistics, balancing, scripting, worldgen), Discovery (core/discovery), Policy engine (core/policy, core/resolution), 03 Material system, almost_unified per-domain arbitration policy (+3 more)

### Community 53 - "ConversionEvents.java"
Cohesion: 0.20
Nodes (9): blockentity, compoundcontainer, identityhashmap, inventory, itementity, playercontainerevent, playerenderchestcontainer, playerevent (+1 more)

### Community 54 - "UnifiedItemsClient"
Cohesion: 0.18
Nodes (4): copyonwritearraylist, net.neoforged.neoforge.event.entity.player.ItemTooltipEvent, UnifiedItemsClient, UnifiedTooltips

### Community 55 - "DataReadRequest"
Cohesion: 0.67
Nodes (3): DataReadRequest, Override, Type

### Community 56 - "RecipeFamilyRequest"
Cohesion: 0.67
Nodes (3): Override, Type, RecipeFamilyRequest

### Community 57 - "RecipeClassification"
Cohesion: 0.22
Nodes (8): RecipeClassification, ALTERNATIVE, CANONICAL, CONFLICTING, DUPLICATE, GENERATED, UNSUPPORTED, RecipeAnalysis

### Community 58 - "com.mojang.serialization.Codec"
Cohesion: 0.26
Nodes (7): com.mojang.serialization.Codec, FormPolicy, GlobalPolicy, TagEdit, MaterialPolicy, PolicyFiles, Head

### Community 59 - "net.minecraft.network.FriendlyByteBuf"
Cohesion: 0.10
Nodes (16): net.minecraft.network.FriendlyByteBuf, DataListRequest, Override, Type, Override, Type, MaterialListPayload, Summary (+8 more)

### Community 60 - "Editor.java"
Cohesion: 0.24
Nodes (6): com.google.gson.TypeAdapter, 10.1 Minecraft 1.21.1 (checked in `neoforge-21.1.248-sources.jar`), jsonparseexception, net.minecraft.client.gui.components.MultiLineEditBox, Editor, Override

### Community 61 - "ADR-018 Process rules are written by example"
Cohesion: 0.29
Nodes (8): Missing recipe proposals (MNX-010), /materials command and Nexus Terminal item, Process rules (MNX-036), Process templates (MNX-037), ADR-003 Missing recipes are proposals, ADR-013 Authoring is a singleplayer/LAN activity, ADR-018 Process rules are written by example, 19 Example workflow

### Community 62 - "ResolvedForm"
Cohesion: 0.10
Nodes (19): biconsumer, enummap, enumset, Confidence, CONSERVATIVE_HEURISTIC, EXPLICIT_DEFINITION, INTEGRATION, MATERIAL_TAG (+11 more)

### Community 63 - "MnxEmiPlugin.java"
Cohesion: 0.18
Nodes (11): concurrenthashmap, dev.emi.emi.api.EmiEntrypoint, dev.emi.emi.api.EmiPlugin, dev.emi.emi.api.EmiRegistry, emiapi, emirecipe, emistack, guigraphics (+3 more)

### Community 65 - "Domain"
Cohesion: 0.25
Nodes (5): Domain, OUTPUT_REWRITE, RECIPE_DISABLE, TAGS, VIEWER_HIDING

### Community 66 - "PreviewRequest"
Cohesion: 0.29
Nodes (3): Override, Type, PreviewRequest

### Community 67 - "4. Design system"
Cohesion: 0.33
Nodes (6): 4.1 Theme, 4.2 Spacing and sizes (GUI px), 4.3 Text, 4.4 Statuses, 4.5 Components, 4. Design system

### Community 68 - "21 — Support backlog (user feedback)"
Cohesion: 0.06
Nodes (32): 21 — Support backlog (user feedback), Context to keep in mind, MNX-086 — Bug: electrotine alloy ingot cannot be unified to the wanted item, MNX-087 — Docs: commands and wiki link are too low, MNX-088 — UX: recipes shown "JEI style", MNX-089 — UX: "needs quite a lot of polish", MNX-090 — Compat: more mods natively, MNX-091 — Feature: created item textures without JSON (+24 more)

### Community 69 - "JEI plugin (MNX-013)"
Cohesion: 0.47
Nodes (6): EMI adapter (MNX-014), JEI plugin (MNX-013), UnifiedItemsPayload (applied alternatives, once per data sync), client/RecipeViewer (recipes drawn by the installed viewer), IntegrationAdapter boundary, ADR-004 JEI/EMI are adapters

### Community 71 - "NexusTerminalItem.java"
Cohesion: 0.36
Nodes (6): net.minecraft.world.entity.player.Player, net.minecraft.world.InteractionHand, net.minecraft.world.InteractionResultHolder, net.minecraft.world.level.Level, serverplayer, Override

### Community 73 - "ProcessPayload"
Cohesion: 0.47
Nodes (3): Override, Type, ProcessPayload

### Community 74 - "DataListPayload"
Cohesion: 0.38
Nodes (4): DataListPayload, Item, Override, Type

### Community 75 - "CLAUDE.md operational contract"
Cohesion: 0.60
Nodes (5): CLAUDE.md operational contract, 11 Tests, GameTest (runtime behavior), JUnit (pure logic), 15 Conventions (inherited from DrimoZ mods)

### Community 76 - "Output and input rewriting"
Cohesion: 0.50
Nodes (5): Unification actions, Output and input rewriting, Recipe formats (material_nexus/recipe_formats), ADR-011 Output rewriting is explicit and vanilla-only in V1, ADR-016 Recipe formats are data, not code

### Community 77 - "ADR-005 No persistent history"
Cohesion: 0.50
Nodes (5): Created items (items.json, MNX-039), ADR-005 No persistent history, ADR-009 Apply flow and single-level revert, ADR-019 Items for missing forms registered at startup from config, ADR-021 Applied states are kept and can be restored

### Community 79 - "22 — UI redesign (analysis and design, MNX-093)"
Cohesion: 0.11
Nodes (18): 07 GUI, 18 GUI layout (MNX-094 to MNX-102), client/ui/Regions (rail, list, table, inspector widths), 11. Open points, 1. Rules kept, 22 — UI redesign (analysis and design, MNX-093), 2. Who does what, how often, 3.1 Use of space (1280 × 685, Materials › Amethyst) (+10 more)

### Community 80 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 81 - "DataReadPayload"
Cohesion: 0.47
Nodes (3): DataReadPayload, Override, Type

### Community 83 - ".in"
Cohesion: 0.40
Nodes (3): IFocusGroup, IRecipeCategory, RecipeHolder

### Community 84 - "net.minecraft.world.item.ItemStack"
Cohesion: 0.21
Nodes (3): net.minecraft.world.item.ItemStack, Names, Override

### Community 85 - "Action"
Cohesion: 0.29
Nodes (6): Action, DISABLE, GENERATE, KEEP, MODIFY, RecipeChange

### Community 86 - "list"
Cohesion: 0.11
Nodes (32): arraylist, assertequals, assertions, builtinregistries, collection, collections, collectors, comparator (+24 more)

### Community 89 - "RevertRequest"
Cohesion: 0.67
Nodes (3): Override, Type, RevertRequest

### Community 90 - "Owner"
Cohesion: 0.67
Nodes (3): Owner, AU, MNX

### Community 103 - "9. Built to evolve"
Cohesion: 0.50
Nodes (4): 9.1 Seven invariants, 9.2 Interface improvements that fit afterwards, 9.3 Features that may be asked for, and where they go, 9. Built to evolve

### Community 104 - ".preview"
Cohesion: 0.17
Nodes (6): net.minecraft.server.level.ServerPlayer, Entry, JsonObject, Kind, PolicyHandler, PolicyWrite

### Community 105 - "ProcessRequest"
Cohesion: 0.67
Nodes (3): Override, Type, ProcessRequest

### Community 107 - "SuggestionsRequest"
Cohesion: 0.67
Nodes (3): Override, Type, SuggestionsRequest

### Community 108 - "MatrixRequest"
Cohesion: 0.67
Nodes (3): Override, Type, MatrixRequest

## Knowledge Gaps
- **141 isolated node(s):** `TODO`, `PENDING`, `UNIFIED`, `SINGLE`, `CREATABLE` (+136 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 364 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **31 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `NexusScreen` connect `net.minecraft.client.gui.GuiGraphics` to `.add`, `View`, `net.minecraft.network.chat.Component`, `ProcessPayload`, `DataListPayload`, `ScriptsPanel`, `NexusScreen.java`, `MaterialDetailPayload`, `DataReadPayload`, `RecipeFamilyPayload`, `.mouseClicked`, `PendingChanges`, `.render`, `.layoutDetail`, `net.minecraft.client.gui.Font`, `net.minecraft.network.FriendlyByteBuf`, `Hits`, `TriagePanel`?**
  _High betweenness centrality (0.095) - this node is a cross-community bridge._
- **Why does `kubejs.plugins.txt (KubeJS plugin registration)` connect `.onTagsUpdated` to `Wiki: The Screen`?**
  _High betweenness centrality (0.072) - this node is a cross-community bridge._
- **Are the 19 inferred relationships involving `FormId` (e.g. with `.creatable()` and `.almostUnifiedKeepsTheDomainsItWasNotGiven()`) actually correct?**
  _`FormId` has 19 INFERRED edges - model-reasoned connections that need verification._
- **Are the 16 inferred relationships involving `MaterialId` (e.g. with `.almostUnifiedKeepsTheDomainsItWasNotGiven()` and `.copperIngotRecipeFamilyIsListed()`) actually correct?**
  _`MaterialId` has 16 INFERRED edges - model-reasoned connections that need verification._
- **What connects `TODO`, `PENDING`, `UNIFIED` to the rest of the system?**
  _141 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Wiki: The Screen` be split into smaller, more focused modules?**
  _Cohesion score 0.05890257558790594 - nodes in this community are weakly interconnected._
- **Should `MaterialNexusGameTests.java` be split into smaller, more focused modules?**
  _Cohesion score 0.11375661375661375 - nodes in this community are weakly interconnected._