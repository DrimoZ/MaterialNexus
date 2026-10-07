package dev.drimoz.materialnexus.item;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.core.discovery.DiscoveredMaterials;
import dev.drimoz.materialnexus.core.discovery.TagDiscovery;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;
import dev.drimoz.materialnexus.core.resolution.ResolvedMaterial;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Items Material Nexus creates for a material that lacks a form (MNX-039), e.g. a netherite rod. Listed in
 * {@code config/materialnexus/items.json} and registered at startup: registries are frozen afterwards, so a new
 * entry needs a restart, and on a server every client needs the same file (ship it with the modpack). Removing an
 * entry deletes that item from every world that has it.
 */
public final class CreatedItems {
    public static final String FILE = "items.json";
    /** Forms with a template texture; anything else would be an invisible item. */
    public static final Set<FormId> FORMS = Set.of(new FormId("ingot"), new FormId("nugget"), new FormId("dust"), new FormId("plate"),
            new FormId("rod"), new FormId("gear"), new FormId("wire"));
    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code colorFrom}: an item of the material whose texture gives the tint, unless {@code color} (0xRRGGBB) is set. */
    public record Entry(MaterialId material, FormId form, Optional<ResourceLocation> colorFrom, Optional<Integer> color) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                MaterialId.CODEC.fieldOf("material").forGetter(Entry::material),
                FormId.CODEC.fieldOf("form").forGetter(Entry::form),
                ResourceLocation.CODEC.optionalFieldOf("color_from").forGetter(Entry::colorFrom),
                Codec.STRING.xmap(s -> Integer.parseInt(s.replace("#", ""), 16), c -> String.format("#%06X", c))
                        .optionalFieldOf("color").forGetter(Entry::color)
        ).apply(i, Entry::new));

        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, material.name() + "_" + form.name());
        }

        /** The convention tag that makes discovery see the item as this material's form. */
        public ResourceLocation tag() {
            return TagDiscovery.conventionTag(material, form).orElseThrow();
        }
    }

    private static final Codec<List<Entry>> FILE_CODEC = Entry.CODEC.listOf().optionalFieldOf("items", List.of()).codec();

    private CreatedItems() { }

    /** Never fails the game: an invalid file is reported and creates nothing. Unsupported forms are skipped. */
    public static List<Entry> load(Path file) {
        if (!Files.isRegularFile(file)) return List.of();
        try {
            List<Entry> entries = FILE_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)))
                    .getOrThrow(IllegalArgumentException::new);
            List<Entry> valid = new ArrayList<>();
            for (Entry e : entries) {
                if (!FORMS.contains(e.form())) LOGGER.warn("{}: form '{}' has no template, {} not created", FILE, e.form().name(), e.id());
                else if (valid.stream().noneMatch(v -> v.id().equals(e.id()))) valid.add(e);
            }
            return List.copyOf(valid);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Invalid {}: no item created", file, e);
            return List.of();
        }
    }

    /** Adds entries to the file, keeping the existing ones (and their order). */
    public static void add(Path file, List<Entry> added) throws IOException {
        List<Entry> all = new ArrayList<>(load(file));
        for (Entry e : added) if (all.stream().noneMatch(a -> a.id().equals(e.id()))) all.add(e);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create()
                .toJson(FILE_CODEC.encodeStart(JsonOps.INSTANCE, all).getOrThrow()), StandardCharsets.UTF_8);
    }

    /**
     * A creation request from the GUI, checked against discovery: the material exists, lacks the form, and the form
     * has a template. The tint comes from the material's ingot, gem or first canonical item.
     */
    public static Optional<Entry> validate(DiscoveredMaterials discovered, ResolvedMaterial resolved, String material, String form) {
        Optional<MaterialId> m = MaterialId.read(material).result();
        Optional<FormId> f = FormId.read(form).result();
        if (m.isEmpty() || f.isEmpty() || resolved == null || !resolved.material().equals(m.get()) || !FORMS.contains(f.get())) return Optional.empty();
        if (!discovered.providers(m.get(), f.get()).isEmpty()) return Optional.empty();
        Optional<ResourceLocation> colorFrom = Stream.of("ingot", "gem")
                .map(name -> resolved.forms().get(new FormId(name)))
                .filter(Objects::nonNull)
                .flatMap(x -> x.canonical().stream())
                .findFirst()
                .or(() -> resolved.forms().values().stream().flatMap(x -> x.canonical().stream()).findFirst());
        return Optional.of(new Entry(m.get(), f.get(), colorFrom, Optional.empty()));
    }
}
