package dev.drimoz.materialnexus.network;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Forge 1.20.1 port: the part of 1.21's {@code StreamCodec} the payloads use (encoder and decoder over a buffer). */
public interface StreamCodec<B, T> {
    void encode(B buf, T value);

    T decode(B buf);

    static <B, T> StreamCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new StreamCodec<>() {
            @Override public void encode(B buf, T value) { encoder.accept(buf, value); }
            @Override public T decode(B buf) { return decoder.apply(buf); }
        };
    }

    /** A message without content: always decodes to {@code instance}. */
    static <B, T> StreamCodec<B, T> unit(T instance) {
        return of((buf, value) -> { }, buf -> instance);
    }
}
