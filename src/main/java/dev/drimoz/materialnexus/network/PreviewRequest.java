package dev.drimoz.materialnexus.network;

import dev.drimoz.materialnexus.MaterialNexus;
import dev.drimoz.materialnexus.datapack.CanonicalChange;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.policy.ProcessRules;
import dev.drimoz.materialnexus.datapack.PolicyEditor;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Client to server: pending canonical choices, process rule edits (form name → rule, MNX-036) and items to create
 * ("material/form", MNX-039) and data edits ("kind|namespace:path" to text, or empty to restore the original, MNX-046),
 * either to preview or (with {@code apply}) to write.
 */
public record PreviewRequest(List<CanonicalChange> changes, boolean apply, java.util.Optional<ResourceLocation> preset,
                             java.util.Map<String, ProcessRules.Rule> processes, List<String> creations,
                             java.util.Map<String, java.util.Optional<String>> data)
        implements CustomPacketPayload {
    public static final Type<PreviewRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaterialNexus.MOD_ID, "preview_request"));
    public static final StreamCodec<FriendlyByteBuf, PreviewRequest> STREAM_CODEC =
            StreamCodec.of(PreviewRequest::write, PreviewRequest::read);

    public PreviewRequest {
        changes = List.copyOf(changes);
        processes = java.util.Map.copyOf(processes);
        creations = List.copyOf(creations);
        data = java.util.Map.copyOf(data);
    }

    public PreviewRequest(List<CanonicalChange> changes, boolean apply) {
        this(changes, apply, java.util.Optional.empty(), java.util.Map.of(), List.of(), java.util.Map.of());
    }

    private static final int MAX_RULES = 256;
    private static final int MAX_ROUTES = 32;

    static void writeRule(FriendlyByteBuf buf, ProcessRules.Rule rule) {
        buf.writeCollection(rule.routes(), (b, r) -> {
            b.writeResourceLocation(r.machine());
            b.writeUtf(r.input().name(), NexusQueries.MAX_QUERY);
            b.writeVarInt(r.in());
            b.writeVarInt(r.out());
        });
        buf.writeBoolean(rule.exclusive());
        buf.writeBoolean(rule.enforceRatio());
    }

    /** Untrusted: bounded sizes, valid form names and ratios, or the packet is rejected. */
    static ProcessRules.Rule readRule(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ROUTES) throw new DecoderException("Too many routes: " + count);
        List<ProcessRules.Route> routes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation machine = buf.readResourceLocation();
            FormId input = FormId.read(buf.readUtf(NexusQueries.MAX_QUERY)).result().orElseThrow(() -> new DecoderException("Bad form"));
            try {
                routes.add(new ProcessRules.Route(machine, input, buf.readVarInt(), buf.readVarInt()));
            } catch (IllegalArgumentException e) {
                throw new DecoderException(e.getMessage());
            }
        }
        return new ProcessRules.Rule(routes, buf.readBoolean(), buf.readBoolean());
    }

    @Override public Type<PreviewRequest> type() { return TYPE; }

    private static void write(FriendlyByteBuf buf, PreviewRequest req) {
        buf.writeVarInt(req.changes().size());
        for (CanonicalChange c : req.changes()) {
            buf.writeUtf(c.material(), NexusQueries.MAX_QUERY);
            buf.writeUtf(c.form(), NexusQueries.MAX_QUERY);
            buf.writeResourceLocation(c.provider());
        }
        buf.writeBoolean(req.apply());
        buf.writeOptional(req.preset(), FriendlyByteBuf::writeResourceLocation);
        buf.writeMap(req.processes(), (b, form) -> b.writeUtf(form, NexusQueries.MAX_QUERY), PreviewRequest::writeRule);
        buf.writeCollection(req.creations(), (b, c) -> b.writeUtf(c, NexusQueries.MAX_QUERY * 2));
        buf.writeMap(req.data(), (b, k) -> b.writeUtf(k, 256),
                (b, t) -> b.writeOptional(t, (bb, s) -> bb.writeUtf(s, dev.drimoz.materialnexus.datapack.EditableData.MAX_TEXT)));
    }

    private static PreviewRequest read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > PolicyEditor.MAX_CHANGES) throw new DecoderException("Too many changes: " + count);
        List<CanonicalChange> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            changes.add(new CanonicalChange(buf.readUtf(NexusQueries.MAX_QUERY), buf.readUtf(NexusQueries.MAX_QUERY), buf.readResourceLocation()));
        }
        boolean apply = buf.readBoolean();
        var preset = buf.readOptional(FriendlyByteBuf::readResourceLocation);
        int rules = buf.readVarInt();
        if (rules < 0 || rules > MAX_RULES) throw new DecoderException("Too many process rules: " + rules);
        java.util.Map<String, ProcessRules.Rule> processes = new java.util.HashMap<>();
        for (int i = 0; i < rules; i++) {
            String form = buf.readUtf(NexusQueries.MAX_QUERY);
            if (FormId.read(form).result().isEmpty()) throw new DecoderException("Bad form: " + form);
            processes.put(form, readRule(buf));
        }
        int creations = buf.readVarInt();
        if (creations < 0 || creations > MAX_RULES) throw new DecoderException("Too many items to create: " + creations);
        List<String> created = new ArrayList<>(creations);
        for (int i = 0; i < creations; i++) created.add(buf.readUtf(NexusQueries.MAX_QUERY * 2));
        int edits = buf.readVarInt();
        if (edits < 0 || edits > dev.drimoz.materialnexus.datapack.EditableData.MAX_EDITS) throw new DecoderException("Too many data edits: " + edits);
        java.util.Map<String, java.util.Optional<String>> data = new java.util.HashMap<>();
        for (int i = 0; i < edits; i++) {
            data.put(buf.readUtf(256), buf.readOptional(b -> b.readUtf(dev.drimoz.materialnexus.datapack.EditableData.MAX_TEXT)));
        }
        return new PreviewRequest(changes, apply, preset, processes, created, data);
    }
}
