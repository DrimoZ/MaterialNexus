package dev.drimoz.materialnexus.client;

import dev.drimoz.materialnexus.client.ui.Theme;
import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.policy.PolicyPrecedence;
import dev.drimoz.materialnexus.core.resolution.ResolvedForm;
import dev.drimoz.materialnexus.datapack.CanonicalChange;
import dev.drimoz.materialnexus.item.CreatedItems;
import dev.drimoz.materialnexus.network.MaterialDetailPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The forms of a material, or the materials of a form (MNX-095, docs/22 section 6.3). On the left a table in groups:
 * what is to decide first, what asks nothing folded. On the right the inspector of the selected row: every candidate
 * with its name, its mod, its use in recipes and why it is kept or left alone, and each action as a labelled button.
 * Clicking an item in the table still keeps it at once; right click and Shift + right click remain as shortcuts.
 */
final class DetailTable {
    private static final int HEAD = Theme.ROW_TEXT;
    private static final int GROUP = 20;
    private static final int FOOT = 28;

    private enum Group { TODO, PENDING, UNIFIED, SINGLE, CREATABLE }

    /** Groups the player folded or opened by hand, kept while the game runs. */
    private static final Set<Group> TOGGLED = EnumSet.noneOf(Group.class);

    /** What asks nothing starts folded, unless the player set these groups to start open (MNX-101). */
    private static boolean folded(Group group) {
        boolean byDefault = (group == Group.SINGLE || group == Group.CREATABLE) && !dev.drimoz.materialnexus.client.ui.ScreenSettings.get().openGroups();
        return byDefault != TOGGLED.contains(group);
    }

    private sealed interface Line permits GroupLine, FormLine, AbsentLine { }
    private record GroupLine(Group group, int count) implements Line { }
    private record FormLine(MaterialDetailPayload.FormView view) implements Line { }
    private record AbsentLine(String form) implements Line { }

    private final boolean readOnly;
    private final BiConsumer<String, String> openRecipes;
    private final Ui.Hits tableHits = new Ui.Hits();
    private final Ui.Hits inspectorHits = new Ui.Hits();
    private MaterialDetailPayload detail;
    private int x, y, width, height;
    private int ix, iy, iw, ih;
    private boolean overlay;
    /** Under the wide layout the inspector covers the table: it opens on a selection and can be closed. */
    private boolean inspectorOpen;
    private String selected;
    private double scroll, inspectorScroll;
    private int inspectorContent;

    DetailTable(boolean readOnly, BiConsumer<String, String> openRecipes) {
        this.readOnly = readOnly;
        this.openRecipes = openRecipes;
    }

    void show(MaterialDetailPayload detail) {
        if (this.detail == null || !this.detail.material().equals(detail.material()) || this.detail.byForm() != detail.byForm()) {
            scroll = 0;
            inspectorScroll = 0;
            selected = null;
            inspectorOpen = false;
        }
        this.detail = detail;
    }

    /** The table, and the inspector beside it or, when {@code overlay}, over its right part. */
    void layout(int x, int y, int width, int height, int ix, int iy, int iw, int ih, boolean overlay) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.ix = ix; this.iy = iy; this.iw = iw; this.ih = ih;
        this.overlay = overlay;
    }

    static Ui.Status status(MaterialDetailPayload.FormView view) {
        ResolvedForm f = view.resolved();
        Optional<ResourceLocation> pending = PendingChanges.get(view.material(), view.form());
        if (pending.isPresent()) return pending.get().equals(CanonicalChange.RESET) ? Ui.Status.RESET : Ui.Status.PENDING;
        if (f.alternatives().isEmpty()) return f.canonical().isPresent() ? Ui.Status.SINGLE : Ui.Status.NOTHING;
        return f.source() == PolicyPrecedence.DEFAULT ? Ui.Status.SUGGESTION : Ui.Status.UNIFIED;
    }

    private static Group group(Ui.Status status) {
        return switch (status) {
            case SUGGESTION -> Group.TODO;
            case PENDING, RESET -> Group.PENDING;
            case UNIFIED -> Group.UNIFIED;
            case SINGLE, NOTHING, ABSENT -> Group.SINGLE;
        };
    }

    private static String key(MaterialDetailPayload.FormView view) { return view.material() + "/" + view.form(); }

    private List<String> creatable() {
        if (detail == null || detail.byForm()) return List.of();
        Set<FormId> present = new HashSet<>();
        detail.forms().forEach(v -> present.add(new FormId(v.form())));
        return CreatedItems.creatable(present).stream().map(FormId::name).toList();
    }

    private Map<Group, List<MaterialDetailPayload.FormView>> grouped() {
        Map<Group, List<MaterialDetailPayload.FormView>> by = new EnumMap<>(Group.class);
        for (MaterialDetailPayload.FormView view : detail.forms()) by.computeIfAbsent(group(status(view)), g -> new ArrayList<>()).add(view);
        return by;
    }

    private List<Line> lines() {
        List<Line> out = new ArrayList<>();
        grouped().forEach((group, views) -> {
            out.add(new GroupLine(group, views.size()));
            if (!folded(group)) views.forEach(v -> out.add(new FormLine(v)));
        });
        List<String> absent = creatable();
        if (!absent.isEmpty()) {
            out.add(new GroupLine(Group.CREATABLE, absent.size()));
            if (!folded(Group.CREATABLE)) absent.forEach(f -> out.add(new AbsentLine(f)));
        }
        return out;
    }

    private static int height(Line line) { return line instanceof GroupLine ? GROUP : Theme.ROW_ITEM; }

    /** The selected row; with none, the first one that matters, so the inspector is never empty. */
    private Optional<MaterialDetailPayload.FormView> selectedView() {
        Optional<MaterialDetailPayload.FormView> found = detail.forms().stream().filter(v -> key(v).equals(selected)).findFirst();
        if (found.isPresent()) return found;
        return grouped().values().stream().findFirst().map(list -> list.get(0));
    }

    private boolean inspectorShown() { return !overlay || inspectorOpen; }

    private boolean overInspector(double mx, double my) {
        return inspectorShown() && mx >= ix && mx < ix + iw && my >= iy && my < iy + ih;
    }

    void scroll(double mx, double my, double delta) {
        if (detail == null) return;
        if (overInspector(mx, my)) {
            inspectorScroll = net.minecraft.util.Mth.clamp(inspectorScroll - delta * Theme.ROW_ITEM, 0, Math.max(0, inspectorContent));
            return;
        }
        int content = lines().stream().mapToInt(DetailTable::height).sum();
        scroll = net.minecraft.util.Mth.clamp(scroll - delta * Theme.ROW_ITEM, 0, Math.max(0, content - (height - HEAD)));
    }

    boolean click(double mx, double my, int button) {
        if (overInspector(mx, my)) {
            inspectorHits.click(mx, my, button);
            return true;
        }
        return my >= y + HEAD && my < y + height && tableHits.click(mx, my, button);
    }

    // ---- table ---------------------------------------------------------------------------------------------

    private int labelWidth() { return net.minecraft.util.Mth.clamp(width / 5, 96, 132); }

    private int keptWidth() { return net.minecraft.util.Mth.clamp(width / 4, 120, 170); }

    void render(GuiGraphics g, Font font, int mx, int my) {
        tableHits.clear();
        inspectorHits.clear();
        if (detail == null) return;
        int kx = x + labelWidth();
        int ox = kx + keptWidth();
        g.drawString(font, Component.translatable(detail.byForm() ? "screen.materialnexus.col.material" : "screen.materialnexus.col.form"), x + 16, y + 4, Theme.MUTED, false);
        g.drawString(font, Component.translatable("screen.materialnexus.col.kept"), kx, y + 4, Theme.MUTED, false);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.materialnexus.col.others").getString(), x + width - ox - 8), ox, y + 4, Theme.MUTED, false);

        List<Line> lines = lines();
        int content = lines.stream().mapToInt(DetailTable::height).sum();
        int view = height - HEAD;
        scroll = net.minecraft.util.Mth.clamp(scroll, 0, Math.max(0, content - view));
        boolean inTable = !overInspector(mx, my) && my >= y + HEAD && my < y + height;
        g.enableScissor(x, y + HEAD, x + width, y + height);
        int top = y + HEAD - (int) scroll;
        for (Line line : lines) {
            int h = height(line);
            if (top + h > y + HEAD && top < y + height) {
                if (line instanceof GroupLine group) groupRow(g, font, group, top, mx, my, inTable);
                else if (line instanceof FormLine form) formRow(g, font, form.view(), top, kx, ox, mx, my, inTable);
                else if (line instanceof AbsentLine absent) absentRow(g, font, absent.form(), top, kx, mx, my, inTable);
            }
            top += h;
        }
        g.disableScissor();
        if (content > view) {
            g.fill(x + width - 4, y + HEAD, x + width, y + height, Theme.RAISED);
            int bar = Math.max(12, view * view / content);
            int at = (int) (scroll * (view - bar) / (content - view));
            g.fill(x + width - 4, y + HEAD + at, x + width, y + HEAD + at + bar, Theme.CTRL);
        }
        if (inspectorShown()) renderInspector(g, font, mx, my);
        if (overInspector(mx, my)) inspectorHits.tooltip(g, font, mx, my);
        else if (inTable) tableHits.tooltip(g, font, mx, my);
    }

    private void groupRow(GuiGraphics g, Font font, GroupLine line, int top, int mx, int my, boolean inTable) {
        boolean folded = folded(line.group());
        boolean hover = inTable && my >= top && my < top + GROUP && mx >= x && mx < x + width;
        Component title = Component.translatable("screen.materialnexus.rows." + Names.lowerName(line.group()), line.count());
        Ui.caret(g, x + 5, top + 9, !folded, Theme.MUTED);
        g.drawString(font, title, x + 16, top + 8, hover ? Theme.TEXT : Theme.MUTED, false);
        Group group = line.group();
        if (inTable) tableHits.add(x, top, width, GROUP, () -> { if (!TOGGLED.remove(group)) TOGGLED.add(group); }, null, List.of());
    }

    private void formRow(GuiGraphics g, Font font, MaterialDetailPayload.FormView view, int top, int kx, int ox, int mx, int my, boolean inTable) {
        ResolvedForm f = view.resolved();
        String material = view.material();
        boolean isSelected = selectedView().map(v -> key(v).equals(key(view))).orElse(false);
        boolean hover = inTable && my >= top && my < top + Theme.ROW_ITEM && mx >= x && mx < x + width;
        if (isSelected) g.fill(x, top, x + width, top + Theme.ROW_ITEM, Theme.SEL);
        else if (hover) g.fill(x, top, x + width, top + Theme.ROW_ITEM, Theme.RAISED);
        // The content keeps its 22 px, centred in a taller row.
        int rowTop = top;
        top += Theme.ROW_PAD;

        Ui.Status status = status(view);
        Ui.mark(g, status, x + 5, top + 8);
        Component label = detail.byForm() ? Names.material(material) : Names.form(view.form());
        g.drawString(font, font.plainSubstrByWidth(label.getString(), labelWidth() - 20), x + 16, top + 7, Theme.TEXT, false);

        Optional<ResourceLocation> pending = PendingChanges.get(material, view.form()).filter(p -> !p.equals(CanonicalChange.RESET));
        boolean decided = f.source() != PolicyPrecedence.DEFAULT && !f.alternatives().isEmpty();
        Optional<ResourceLocation> kept = pending.or(f::canonical);
        if (kept.isPresent()) {
            ResourceLocation k = kept.get();
            slot(g, view, k, kx, top + 2, pending.isPresent() ? Theme.INFO : 0, false, decided, inTable);
            // The name only: the mod is in the tooltip of the icon and in the inspector.
            g.drawString(font, font.plainSubstrByWidth(Names.stack(k).getHoverName().getString(), keptWidth() - 30), kx + 24, top + 7, Theme.TEXT, false);
        }

        List<ResourceLocation> others = new ArrayList<>();
        f.canonical().filter(c -> !c.equals(kept.orElse(null))).ifPresent(others::add);
        f.alternatives().stream().filter(a -> !a.equals(kept.orElse(null))).forEach(others::add);
        int sx = ox;
        int right = x + width - 28;
        int hidden = 0;
        for (ResourceLocation item : others) {
            if (sx + 18 > right) { hidden++; continue; }
            slot(g, view, item, sx, top + 2, 0, false, decided, inTable);
            sx += 20;
        }
        if (!f.notUnified().isEmpty() && sx + 26 <= right) {
            g.fill(sx + 2, top + 4, sx + 3, top + Theme.ROW_ITEM - 4, Theme.CTRL);
            sx += 6;
        }
        for (ResolvedForm.NotUnified n : f.notUnified()) {
            if (sx + 18 > right) { hidden++; continue; }
            slot(g, view, n.item(), sx, top + 2, 0, true, decided, inTable);
            sx += 20;
        }
        if (hidden > 0) g.drawString(font, "+" + hidden, sx + 2, top + 7, Theme.FAINT, false);
        if (inTable) tableHits.add(x, rowTop, width, Theme.ROW_ITEM, () -> select(view), null, List.of());
    }

    /** One item of a row: click keeps it and selects the row, right click marks it, Shift + right click edits the tag. */
    private void slot(GuiGraphics g, MaterialDetailPayload.FormView view, ResourceLocation item, int sx, int sy, int outline, boolean dim, boolean decided, boolean inTable) {
        Ui.slot(g, item, sx, sy, outline, dim);
        marks(g, view.material(), view.form(), item, sx, sy);
        if (!inTable) return;
        tableHits.add(sx, sy, 18, 18, () -> {
            select(view);
            choose(view.material(), view.form(), item, view.resolved(), decided);
        }, shortcut(view.material(), view.form(), item), List.of(Names.stack(item).getHoverName(), Component.literal(NexusScreen.modName(item.getNamespace())).withStyle(s -> s.withColor(Theme.TIP_MUTED)),
                Component.literal(item.toString()).withStyle(s -> s.withColor(Theme.TIP_FAINT))));
    }

    private void select(MaterialDetailPayload.FormView view) {
        if (!key(view).equals(selected)) inspectorScroll = 0;
        selected = key(view);
        inspectorOpen = true;
    }

    private void absentRow(GuiGraphics g, Font font, String form, int top, int kx, int mx, int my, boolean inTable) {
        String material = detail.material();
        top += Theme.ROW_PAD;
        g.drawString(font, font.plainSubstrByWidth(Names.form(form).getString(), labelWidth() - 20), x + 16, top + 7, Theme.MUTED, false);
        if (readOnly) return;
        boolean pending = PendingChanges.creating(material, form);
        Component label = Component.translatable(pending ? "screen.materialnexus.create.pending" : "screen.materialnexus.create.button");
        int bw = font.width(label) + 12;
        boolean over = inTable && mx >= kx && mx < kx + bw && my >= top + 3 && my < top + 19;
        if (pending) g.renderOutline(x + 5, top + 8, 6, 6, Theme.INFO);
        Ui.button(g, font, label, kx, top + 3, bw, 16, false, over, true);
        if (inTable) {
            tableHits.add(kx, top + 3, bw, 16, () -> PendingChanges.toggleCreation(material, form), null,
                    List.of(Component.translatable("screen.materialnexus.create.tooltip", "materialnexus:" + material + "_" + form)));
        }
    }

    /** MNX-050, MNX-079: right click marks an item "not the same", with Shift it takes it out of this form's tag. */
    private Runnable shortcut(String material, String form, ResourceLocation item) {
        return readOnly ? null : () -> {
            if (Screen.hasShiftDown()) PendingChanges.toggleTagRemoval(material, form, item);
            else PendingChanges.toggleNotSame(item);
        };
    }

    /** A pending "not the same" is a cross over the item, a pending tag edit a bar under it. */
    private static void marks(GuiGraphics g, String material, String form, ResourceLocation item, int x, int y) {
        if (PendingChanges.globalChanged("tag_edits") && PendingChanges.tagRemoved(material, form, item)) g.fill(x, y + 16, x + 18, y + 18, Theme.WARN);
        if (!PendingChanges.globalChanged("not_same") || !PendingChanges.notSame(item)) return;
        for (int i = 0; i < 18; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, Theme.BAD);
            g.fill(x + 17 - i, y + i, x + 18 - i, y + i + 1, Theme.BAD);
        }
    }

    /** Pick an item as the pending kept one; picking the pending one again, or the already decided one, cancels. */
    private void choose(String material, String form, ResourceLocation item, ResolvedForm f, boolean decided) {
        if (readOnly) return;
        boolean samePending = PendingChanges.get(material, form).map(item::equals).orElse(false);
        boolean alreadyDecided = decided && f.canonical().map(item::equals).orElse(false);
        if (samePending || alreadyDecided) PendingChanges.clear(material, form);
        else PendingChanges.set(material, form, item);
    }

    // ---- inspector -----------------------------------------------------------------------------------------

    private void renderInspector(GuiGraphics g, Font font, int mx, int my) {
        g.fill(ix, iy, ix + iw, iy + ih, Theme.BG);
        g.fill(ix, iy, ix + 1, iy + ih, Theme.BORDER);
        Optional<MaterialDetailPayload.FormView> shown = selectedView();
        if (shown.isEmpty()) {
            g.drawString(font, Component.translatable("screen.materialnexus.insp.none"), ix + 12, iy + 10, Theme.MUTED, false);
            return;
        }
        MaterialDetailPayload.FormView view = shown.get();
        ResolvedForm f = view.resolved();
        String material = view.material();
        String form = view.form();
        int left = ix + 12;
        int inner = iw - 24;

        g.drawString(font, Component.translatable("screen.materialnexus.triage.item", Names.material(material), Names.form(form)).withStyle(ChatFormatting.BOLD),
                left, iy + 8, Theme.TEXT, false);
        if (overlay) {
            Component close = Component.translatable("screen.materialnexus.close");
            int cw = font.width(close) + 12;
            boolean over = mx >= ix + iw - 8 - cw && mx < ix + iw - 8 && my >= iy + 4 && my < iy + 20;
            Ui.button(g, font, close, ix + iw - 8 - cw, iy + 4, cw, 16, false, over, true);
            inspectorHits.add(ix + iw - 8 - cw, iy + 4, cw, 16, () -> inspectorOpen = false, null, List.of());
        }
        Ui.Status status = status(view);
        Ui.mark(g, status, left, iy + 23);
        g.drawString(font, status.label(), left + 12, iy + 22, status.color(), false);

        int bodyTop = iy + 36;
        int bodyBottom = iy + ih - FOOT;
        g.enableScissor(ix + 1, bodyTop, ix + iw, bodyBottom);
        int cy = bodyTop - (int) inspectorScroll;
        Component why = Component.translatable("screen.materialnexus.why", Component.translatable(f.reasonKey(), f.reasonArgs().toArray()),
                Component.translatable("materialnexus.source." + Names.lowerName(f.source())),
                Component.translatable("materialnexus.confidence." + Names.lowerName(f.confidence())));
        for (var line : font.split(why, inner)) {
            g.drawString(font, line, left, cy, Theme.MUTED, false);
            cy += 10;
        }
        cy += 6;
        // MNX-118: datapacks of the pack that already edit this form's tag, by name, with what they put in or take out.
        var formTag = dev.drimoz.materialnexus.core.discovery.TagDiscovery.conventionTag(new dev.drimoz.materialnexus.core.domain.MaterialId(material),
                new dev.drimoz.materialnexus.core.domain.FormId(form));
        for (var edit : formTag.map(t -> detail.packTags().getOrDefault(t, List.of())).orElse(List.of())) {
            for (var line : font.split(packTagLine(edit), inner)) {
                g.drawString(font, line, left, cy, Theme.MUTED, false);
                cy += 10;
            }
            cy += 6;
        }

        Optional<ResourceLocation> pending = PendingChanges.get(material, form).filter(p -> !p.equals(CanonicalChange.RESET));
        boolean decided = f.source() != PolicyPrecedence.DEFAULT && !f.alternatives().isEmpty();
        Optional<ResourceLocation> kept = pending.or(f::canonical);
        List<ResourceLocation> candidates = new ArrayList<>();
        kept.ifPresent(candidates::add);
        f.canonical().filter(c -> !candidates.contains(c)).ifPresent(candidates::add);
        f.alternatives().stream().filter(a -> !candidates.contains(a)).forEach(candidates::add);

        g.drawString(font, Component.translatable("screen.materialnexus.insp.candidates", candidates.size()), left, cy, Theme.MUTED, false);
        cy += 12;
        for (ResourceLocation item : candidates) {
            boolean isKept = kept.map(item::equals).orElse(false);
            Component tag = !isKept ? Component.empty() : pending.isPresent() ? Ui.Status.PENDING.label()
                    : status == Ui.Status.SUGGESTION ? Component.translatable("screen.materialnexus.insp.suggested") : Component.translatable("screen.materialnexus.col.kept");
            int tagColor = !isKept ? 0 : pending.isPresent() ? Theme.INFO : status == Ui.Status.SUGGESTION ? Theme.WARN : Theme.OK;
            // The kept item needs no button, unless it is only suggested (confirm it) or not applied yet (cancel it).
            boolean offerKeep = !isKept || pending.isPresent() || status == Ui.Status.SUGGESTION;
            cy = card(g, font, view, item, null, tag, tagColor, isKept && pending.isPresent(), offerKeep, decided, cy, mx, my, bodyTop, bodyBottom) + 4;
        }
        if (!f.notUnified().isEmpty()) {
            cy += 4;
            g.drawString(font, Component.translatable("screen.materialnexus.insp.left_alone", f.notUnified().size()), left, cy, Theme.MUTED, false);
            cy += 12;
            for (ResolvedForm.NotUnified n : f.notUnified()) {
                Component reason = Component.translatable(n.reasonKey(), n.reasonArgs().toArray());
                cy = card(g, font, view, n.item(), reason, Component.empty(), 0, false, true, decided, cy, mx, my, bodyTop, bodyBottom) + 4;
            }
        }
        g.disableScissor();
        inspectorContent = cy + (int) inspectorScroll - bodyBottom;

        // Footer: what concerns the whole row.
        g.fill(ix + 1, bodyBottom, ix + iw, bodyBottom + 1, Theme.BORDER);
        int bx = action(g, font, Component.translatable("screen.materialnexus.insp.recipes"), left, bodyBottom + 6, mx, my, () -> openRecipes.accept(material, form));
        if (!readOnly && (f.source() == PolicyPrecedence.EXPLICIT_RESOURCE_OVERRIDE || f.ignoredOverride().isPresent())) {
            boolean resetting = status == Ui.Status.RESET;
            action(g, font, Component.translatable(resetting ? "screen.materialnexus.reset.cancel" : "screen.materialnexus.insp.reset"), bx, bodyBottom + 6, mx, my, () -> {
                if (resetting) PendingChanges.clear(material, form);
                else PendingChanges.set(material, form, CanonicalChange.RESET);
            });
        }
    }

    /** "The datapack my_tweaks replaces this tag; puts in A; takes out B." Items by name, as everywhere in the inspector. */
    private static Component packTagLine(dev.drimoz.materialnexus.datapack.PackEdits.TagEdit edit) {
        List<String> parts = new ArrayList<>();
        if (edit.replace()) parts.add(Component.translatable("screen.materialnexus.insp.pack_tag.replace").getString());
        if (!edit.added().isEmpty()) parts.add(Component.translatable("screen.materialnexus.insp.pack_tag.add", names(edit.added())).getString());
        if (!edit.removed().isEmpty()) parts.add(Component.translatable("screen.materialnexus.insp.pack_tag.remove", names(edit.removed())).getString());
        return Component.translatable("screen.materialnexus.insp.pack_tag", Names.pack(edit.pack()), String.join("; ", parts));
    }

    private static String names(List<ResourceLocation> items) {
        return String.join(", ", items.stream().map(i -> Names.withMod(i).getString()).toList());
    }

    /** Reasons that are guesses of the resolver, which "same item after all" overrules (MNX-086). */
    private static final java.util.Set<String> GUESSES = java.util.Set.of("materialnexus.not_unified.names_other", "materialnexus.not_unified.named_variant",
            "materialnexus.not_unified.same_mod", "materialnexus.not_unified.more_specific");

    /** One candidate: who it is, how central it is, why it is where it is, and what can be done with it. Returns the y below. */
    private int card(GuiGraphics g, Font font, MaterialDetailPayload.FormView view, ResourceLocation item, Component reason, Component tag, int tagColor,
                     boolean isPending, boolean offerKeep, boolean decided, int top, int mx, int my, int clipTop, int clipBottom) {
        String material = view.material();
        String form = view.form();
        int left = ix + 12;
        int right = ix + iw - 12;
        int tx = left + 32;
        int textWidth = right - tx - 8;
        List<net.minecraft.util.FormattedCharSequence> reasonLines = reason == null ? List.of() : font.split(reason, textWidth);

        List<Component> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (!readOnly && offerKeep) {
            labels.add(Component.translatable(isPending ? "screen.materialnexus.insp.cancel" : "screen.materialnexus.insp.keep"));
            actions.add(() -> choose(material, form, item, view.resolved(), decided));
        }
        if (!readOnly) {
            // MNX-086: an item set aside on a guess, or forced back in, gets the opposite mark offered.
            boolean guess = view.resolved().notUnified().stream().anyMatch(n -> n.item().equals(item) && GUESSES.contains(n.reasonKey()));
            if (guess || PendingChanges.same(item)) {
                labels.add(Component.translatable(PendingChanges.same(item) ? "screen.materialnexus.insp.same.undo" : "screen.materialnexus.insp.same"));
                actions.add(() -> PendingChanges.toggleSame(item));
            } else {
                labels.add(Component.translatable(PendingChanges.notSame(item) ? "screen.materialnexus.insp.not_same.undo" : "screen.materialnexus.insp.not_same"));
                actions.add(() -> PendingChanges.toggleNotSame(item));
            }
            boolean edited = PendingChanges.tagRemoved(material, form, item) || PendingChanges.tagAdded(material, form, item);
            labels.add(Component.translatable(edited ? "screen.materialnexus.insp.tag_undo" : "screen.materialnexus.insp.tag_remove"));
            actions.add(() -> PendingChanges.toggleTagRemoval(material, form, item));
        }
        // Buttons flow and wrap inside the card.
        int rows = labels.isEmpty() ? 0 : 1;
        int fx = tx;
        for (Component label : labels) {
            int bw = font.width(label) + 12;
            if (fx > tx && fx + bw > right - 8) { rows++; fx = tx; }
            fx += bw + 4;
        }
        // MNX-101: the technical id is a setting; most players only need the name and the mod.
        int idLine = dev.drimoz.materialnexus.client.ui.ScreenSettings.get().showIds() ? 10 : 0;
        int h = 8 + 30 + idLine + reasonLines.size() * 10 + (rows == 0 ? 0 : 4 + rows * 18) + 6;

        g.fill(left, top, right, top + h, Theme.SURFACE);
        Ui.slot(g, item, left + 8, top + 8, isPending ? Theme.INFO : 0, reason != null);
        marks(g, material, form, item, left + 8, top + 8);
        int tagWidth = font.width(tag);
        String name = font.plainSubstrByWidth(Names.stack(item).getHoverName().getString(), textWidth - tagWidth - 12);
        g.drawString(font, Component.literal(name).withStyle(ChatFormatting.BOLD), tx, top + 8, Theme.TEXT, false);
        if (tagWidth > 0) g.drawString(font, tag, right - 8 - tagWidth, top + 8, tagColor, false);
        var usage = detail.usage().get(item);
        String mod = NexusScreen.modName(item.getNamespace());
        g.drawString(font, font.plainSubstrByWidth(mod, textWidth), tx, top + 18, Theme.MUTED, false);
        if (usage != null) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.materialnexus.insp.usage", usage.produced(), usage.used()).getString(), textWidth),
                    tx, top + 28, Theme.MUTED, false);
        }
        if (idLine > 0) g.drawString(font, font.plainSubstrByWidth(item.toString(), textWidth), tx, top + 38, Theme.FAINT, false);
        int ly = top + 38 + idLine;
        for (var line : reasonLines) {
            g.drawString(font, line, tx, ly, Theme.MUTED, false);
            ly += 10;
        }
        int bx = tx;
        int by = ly + 4;
        for (int i = 0; i < labels.size(); i++) {
            int bw = font.width(labels.get(i)) + 12;
            if (bx > tx && bx + bw > right - 8) { bx = tx; by += 18; }
            boolean visible = by >= clipTop && by + 16 <= clipBottom;
            boolean over = visible && mx >= bx && mx < bx + bw && my >= by && my < by + 16;
            Ui.button(g, font, labels.get(i), bx, by, bw, 16, false, over, true);
            if (visible) inspectorHits.add(bx, by, bw, 16, actions.get(i), null, List.of());
            bx += bw + 4;
        }
        return top + h;
    }

    private int action(GuiGraphics g, Font font, Component label, int bx, int by, int mx, int my, Runnable run) {
        int bw = font.width(label) + 12;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + 16;
        Ui.button(g, font, label, bx, by, bw, 16, false, over, true);
        inspectorHits.add(bx, by, bw, 16, run, null, List.of());
        return bx + bw + 4;
    }
}
