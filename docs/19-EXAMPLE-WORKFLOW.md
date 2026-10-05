# 19 — Example workflow

1. Install Material Nexus in a Create + Mekanism + Thermal pack.
2. In singleplayer (cheats on), run `/materials` or use the creative "Nexus Terminal" item.
3. Open `Copper`.
4. Filter `Plate`.
5. Choose `create:copper_sheet` as canonical.
6. The mod shows existing recipes and their classification.
7. The pack author disables only the duplicate recipes they approve, and optionally enables output rewriting (vanilla recipe types).
8. A missing recipe is proposed, but not created automatically.
9. Preview shows the affected tags, recipes and JEI/EMI entries.
10. Apply writes the policy to `config/materialnexus/policies/`, regenerates `config/materialnexus/generated/` and reloads.
11. "Revert last apply" restores the previous policy if needed.
12. The author ships `config/materialnexus/` with the modpack.
13. Diagnostics explain every choice via `Why?`.
