# Draft issue for Immersive Engineering: arc recycling crashes on a Silent Gear ingredient during a data reload

**Versions:** Minecraft 1.21.1, NeoForge 21.1.248, Immersive Engineering 12.4.2-194, Silent Gear 4.2.1.1 (singleplayer).

**What happens:** sometimes, when data is reloaded in a running world (a datapack change followed by a reload), `ArcRecyclingCalculator` throws while iterating recipes in `OnDatapackSyncEvent`:

```
java.lang.NullPointerException: Data resource not present: null
    at net.silentchaos512.gear.api.util.DataResource.get(DataResource.java:82)
    at net.silentchaos512.gear.gear.material.MaterialInstance.get(MaterialInstance.java:118)
    at net.silentchaos512.gear.crafting.ingredient.PartMaterialIngredient.lambda$getItems$10(PartMaterialIngredient.java:203)
    at net.minecraft.world.item.crafting.Ingredient.getItems(Ingredient.java:101)
    at blusunrize.immersiveengineering.common.crafting.ArcRecyclingCalculator$RecipeIterator.getRecycleCalculation(ArcRecyclingCalculator.java:199)
    at blusunrize.immersiveengineering.common.crafting.ArcRecyclingCalculator.run(ArcRecyclingCalculator.java:62)
    at blusunrize.immersiveengineering.common.crafting.ArcRecyclingCalculator$1.onDatapackSync(ArcRecyclingCalculator.java:118)
    at net.minecraft.server.players.PlayerList.reloadResources(PlayerList.java:920)
```

**Impact:** the exception escapes `OnDatapackSyncEvent`, so `PlayerList.reloadResources` stops before sending the updated tags and recipes to players: clients keep the old ones until they reconnect.

**Intermittent:** a plain `/reload` did not reproduce it in our test; it happened on the first reload of some sessions and not on the next one.

**Suggestion:** catch exceptions per recipe in `ArcRecyclingCalculator` (one unreadable ingredient from another mod should skip that recipe, not abort the sync), and/or avoid resolving ingredients during the sync event.

(Seen while developing Material Nexus, which triggers reloads when applying changes; it now resends tags and recipes itself when a sync listener throws.)
