# 20 - Release

## Checklist

1. `main` green: `./gradlew test`, `./gradlew runGameTestServer`, `./gradlew runGameTestServer -Pvanilla` (and `-Pau`, `-Pkubejs` when those parts changed).
2. Bump `mod_version` in `gradle.properties`, add the version to `CHANGELOG.md`.
3. `./gradlew build`; the jar is `build/libs/materialnexus-<version>+1.21.1.jar`. Check it contains no dev mod and no `run*/` content.
4. Tag: `git tag v<version>` and push the tag.
5. Upload the jar to Modrinth and CurseForge with the page text below (by hand: accounts and publishing stay with the author).

## Page text (Modrinth / CurseForge)

**Summary:** Visual, data-driven material unification for modpacks: choose which copper ingot stays, preview every change, apply in one click.

**Categories:** Utility, Library (Modrinth); API and Library, Utility & QoL (CurseForge). Loader NeoForge, Minecraft 1.21.1. Environment: required on the server, optional on clients (needed on clients for the screen, tooltips and created items).

**Description:** use the "What it does", "Getting started", "Multiplayer" and "Compatibility" sections of `README.md` as they are.

**Optional dependencies to list:** JEI, EMI, Almost Unified, KubeJS.

## Upstream report to file

`docs/upstream/immersiveengineering-silentgear-reload.md`: the crash seen when data is reloaded with Immersive Engineering and Silent Gear together. To post on the Immersive Engineering tracker by the author.
