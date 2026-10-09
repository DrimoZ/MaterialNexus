# Wiki source

These files are the **source** of the [GitHub wiki](https://github.com/DrimoZ/MaterialNexus/wiki).
Edit them here, in the same commit as the change they describe, then publish: a wiki edited only
through the web interface drifts from the mod, and nothing reviews it.

GitHub wikis are a separate git repository. To publish:

```bash
git clone https://github.com/DrimoZ/MaterialNexus.wiki.git /tmp/mnx-wiki
cp docs/wiki/*.md /tmp/mnx-wiki/
cp -r docs/wiki/images /tmp/mnx-wiki/
rm /tmp/mnx-wiki/README.md
cd /tmp/mnx-wiki && git add -A && git commit -m "Sync from docs/wiki" && git push
```

The wiki must have been initialised once through the web interface: GitHub does not create the
wiki repository until a first page exists.

Images are cut from the store captures (`./gradlew runUiShots -Pstore`, then
`java tools/Banners.java run-ui/screenshots docs/store-art`) and copied into `images/`.

Pages name no game version on purpose: the mod is meant to exist for several, and each download
says which one it is for.
