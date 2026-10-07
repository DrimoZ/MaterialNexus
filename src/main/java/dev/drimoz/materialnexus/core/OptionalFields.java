package dev.drimoz.materialnexus.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Forge 1.20.1 port: optional fields as strict as 1.21's {@code optionalFieldOf}. The DFU of 1.20.1 turns a present but
 * invalid value into "absent", which would silently drop a typo in a data or policy file instead of reporting it.
 */
public final class OptionalFields {
    private OptionalFields() { }

    /** Absent: empty; present: must decode. */
    public static <A> MapCodec<Optional<A>> strict(Codec<A> codec, String name) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null ? DataResult.success(Optional.empty()) : codec.parse(ops, value).map(Optional::of);
            }

            @Override
            public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return input.isPresent() ? prefix.add(name, codec.encodeStart(ops, input.get())) : prefix;
            }
        };
    }

    /** Absent: {@code defaultValue}; the default is not written back, as with {@code optionalFieldOf}. */
    public static <A> MapCodec<A> strict(Codec<A> codec, String name, A defaultValue) {
        return strict(codec, name).xmap(o -> o.orElse(defaultValue), a -> Objects.equals(a, defaultValue) ? Optional.empty() : Optional.of(a));
    }
}
