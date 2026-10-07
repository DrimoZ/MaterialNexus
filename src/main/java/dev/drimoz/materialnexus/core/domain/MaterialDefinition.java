package dev.drimoz.materialnexus.core.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record MaterialDefinition(
        MaterialId id,
        List<FormId> forms,
        List<String> aliases) {
    public static final Codec<MaterialDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            MaterialId.CODEC.fieldOf("id").forGetter(MaterialDefinition::id),
            dev.drimoz.materialnexus.core.OptionalFields.strict(FormId.CODEC.listOf(), "forms", List.of()).forGetter(MaterialDefinition::forms),
            dev.drimoz.materialnexus.core.OptionalFields.strict(Codec.STRING.listOf(), "aliases", List.of()).forGetter(MaterialDefinition::aliases)
    ).apply(i, MaterialDefinition::new));

    public MaterialDefinition {
        forms = List.copyOf(forms);
        aliases = List.copyOf(aliases);
    }
}
