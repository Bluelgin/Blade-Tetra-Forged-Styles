package dev.bladetetra.export;

import com.mojang.logging.LogUtils;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

final class BladeSealScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private enum Page {
        BLADE("blade"), ADDON("addon"), APPEARANCE("appearance"), TEXT("text"), PROJECT("project");

        final String key;

        Page(String key) {
            this.key = "blade_tetra.export.page." + key;
        }
    }

    private final EnumMap<Page, List<AbstractWidget>> pageControls = new EnumMap<>(Page.class);
    private final EnumMap<Page, Button> pageTabs = new EnumMap<>(Page.class);
    private Page page = Page.BLADE;
    private boolean advanced;
    private Button modeSwitch;
    private Path draftFile;
    private BladeStudioProject lastDraft;
    private boolean draftInitialized;
    private int draftTicks;
    private BladeStudioViewport viewport;
    private boolean previewDragging;
    private final BladeSealPackets.Preview preview;
    private final BladeSealWorkbenchScreen workbench;
    private final BladeExportSnapshot projectedStats;
    private EditBox name;
    private EditBox modTitle;
    private EditBox chineseName;
    private MultiLineEditBox lore;
    private MultiLineEditBox chineseLore;
    private EditBox logo;
    private EditBox addon;
    private EditBox version;
    private EditBox author;
    private EditBox description;
    private Button confirm;
    private Button previewButton;
    private Button awakeningConsent;
    private boolean awakeningAcknowledged;
    private Button backplateModeButton;
    private EditBox backplatePath;
    private CustomBackplateImage.Mode backplateMode = CustomBackplateImage.Mode.ORIGINAL;
    private CustomBackplateImage.Selection requestedBackplate;
    private BladeExportSnapshot approvedSnapshot;
    private ItemStack approvedBlade;
    private boolean confirmed;
    private BladeExportArchive.Metadata requestedMetadata;
    private Component status;
    private Path published;
    private String acquireCommand;
    private int left;
    private int top;
    private int scrollPixels;
    private int panelWidth;
    private int bodyOffset;
    private static final int BODY_HEIGHT = 238;

    private int bodyTop() {
        return top + bodyOffset;
    }

    private BladeSealScreen(BladeSealPackets.Preview preview, BladeSealWorkbenchScreen workbench) {
        super(Component.translatable("blade_tetra.export.studio"));
        this.preview = preview;
        this.workbench = workbench;
        projectedStats = preview.projection().isEmpty() ? null : BladeSealPackets.decodeSnapshot(preview.projection());
    }

    static void open(BladeSealPackets.Preview preview) {
        var client = Minecraft.getInstance();
        if (client.screen instanceof BladeSealWorkbenchScreen workbench
                && workbench.getMenu().containerId == preview.menuId()) {
            client.setScreen(new BladeSealScreen(preview, workbench));
        }
    }

    @Override
    protected void init() {
        scrollPixels = 0;
        panelWidth = Math.min(PANEL_WIDTH, width - 12);
        left = (width - panelWidth) / 2;
        int columns = panelWidth >= 218 ? Page.values().length : 3;
        bodyOffset = columns == 3 ? 70 : 47;
        top = Math.max(4, (height - bodyOffset - BODY_HEIGHT) / 2);
        modeSwitch = addRenderableWidget(Button.builder(modeLabel(), button -> {
            advanced = !advanced;
            button.setMessage(modeLabel());
            if (advanced && viewport != null) viewport.release(minecraft);
            previewDragging = false;
            updateVisibleControls();
        }).bounds(left + panelWidth - 72, top + 3, 72, 16).build());

        pageControls.clear();
        pageTabs.clear();
        for (var tab : Page.values()) {
            int column = tab.ordinal() % columns;
            int row = tab.ordinal() / columns;
            int start = left + panelWidth * column / columns;
            int end = left + panelWidth * (column + 1) / columns;
            pageTabs.put(tab, addRenderableWidget(Button.builder(Component.translatable(tab.key),
                    button -> showPage(tab)).bounds(start, top + 22 + 22 * row, end - start - 2, 20).build()));
        }

        previewButton = pageButton(Page.BLADE, Component.translatable("blade_tetra.export.preview"),
                this::openPreview, left, 0, panelWidth);
        previewButton.active = preview.ready();

        modTitle = pageField(Page.ADDON, 0, 100, "mod_name", modTitle, "");
        addon = pageField(Page.ADDON, 1, 50, "id", addon, "blade_" + preview.projectId().toString().replace("-", ""));
        version = pageField(Page.ADDON, 2, 30, "version", version, "1.0.0");
        author = pageField(Page.ADDON, 3, 64, "author", author,
                minecraft.player == null ? "" : minecraft.player.getGameProfile().getName());
        description = pageField(Page.ADDON, 4, 800, "description", description, "");

        int browseWidth = Math.min(60, panelWidth / 3);
        logo = pageField(Page.APPEARANCE, 0, panelWidth - browseWidth - 4, 1024, "logo", logo, "");
        pageButton(Page.APPEARANCE, Component.translatable("blade_tetra.export.backplate.browse"),
                () -> chooseImage(true), left + panelWidth - browseWidth, 10, browseWidth);
        backplatePath = pageField(Page.APPEARANCE, 2, panelWidth - browseWidth - 4, 1024,
                "backplate_file", backplatePath, "");
        pageButton(Page.APPEARANCE, Component.translatable("blade_tetra.export.backplate.browse"),
                () -> chooseImage(false), left + panelWidth - browseWidth, 64, browseWidth);
        backplateModeButton = pageButton(Page.APPEARANCE, backplateLabel(),
                this::cycleBackplateMode, left, 91, panelWidth);

        name = pageField(Page.TEXT, 0, 100, "name", name, preview.title().getString());
        modTitle.setHint(Component.literal(name.getValue()));
        name.setResponder(value -> modTitle.setHint(Component.literal(value)));
        chineseName = pageField(Page.TEXT, 1, 100, "chinese_name", chineseName, "");
        lore = storyField("lore", 66, lore);
        chineseLore = storyField("chinese_lore", 117, chineseLore);

        pageButton(Page.PROJECT, Component.translatable("blade_tetra.export.project.save"),
                this::saveProject, left, 36, panelWidth);
        pageButton(Page.PROJECT, Component.translatable("blade_tetra.export.project.open"),
                this::confirmOpenProject, left, 61, panelWidth);

        if (preview.omitsAwakening()) {
            awakeningConsent = addRenderableWidget(Button.builder(awakeningConsentLabel(), button -> {
                awakeningAcknowledged = !awakeningAcknowledged;
                awakeningConsent.setMessage(awakeningConsentLabel());
                confirm.active = !confirmed && preview.ready() && awakeningAcknowledged;
            }).bounds(left, bodyTop() + 142, panelWidth, 20).build());
        }
        confirm = addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.confirm"),
                button -> request()).bounds(left, bodyTop() + 165, panelWidth, 20).build());
        confirm.active = !confirmed && preview.ready() && (!preview.omitsAwakening() || awakeningAcknowledged);
        if (published != null) addResultButtons();
        if (!draftInitialized) loadDraft();
        showPage(page);
    }

    private void showPage(Page selected) {
        page = selected;
        setFocused(null);
        updateVisibleControls();
    }

    private Component modeLabel() {
        return Component.translatable(advanced ? "blade_tetra.export.mode.simple" : "blade_tetra.export.mode.advanced");
    }

    private void updateVisibleControls() {
        setFocused(null);
        pageTabs.forEach((tab, button) -> {
            button.visible = advanced;
            button.active = page != tab;
        });
        pageControls.forEach((tab, controls) ->
                controls.forEach(control -> control.visible = advanced && tab == page));
        if (!advanced) {
            name.setX(simpleTextX());
            description.setX(simpleTextX());
            name.setWidth(simpleTextWidth());
            description.setWidth(simpleTextWidth());
            name.setY(bodyTop() + 12);
            description.setY(bodyTop() + 51);
            name.visible = true;
            description.visible = true;
        } else {
            name.setX(left);
            description.setX(left);
            name.setWidth(panelWidth);
            description.setWidth(panelWidth);
            name.setY(bodyTop() + 12);
            description.setY(bodyTop() + 120);
        }
    }

    private void track(Page tab, AbstractWidget control) {
        pageControls.computeIfAbsent(tab, ignored -> new ArrayList<>()).add(control);
    }

    private EditBox pageField(Page tab, int row, int limit, String key, EditBox previous, String fallback) {
        return pageField(tab, row, panelWidth, limit, key, previous, fallback);
    }

    private EditBox pageField(Page tab, int row, int width, int limit, String key, EditBox previous, String fallback) {
        var field = field(left, bodyTop() + 12 + row * 27, width, limit,
                "blade_tetra.export." + key, previous == null ? fallback : previous.getValue());
        track(tab, field);
        return field;
    }

    private MultiLineEditBox storyField(String key, int y, MultiLineEditBox previous) {
        var label = Component.translatable("blade_tetra.export." + key);
        var field = addRenderableWidget(new MultiLineEditBox(font, left, bodyTop() + y, panelWidth, 33,
                Component.empty(), label));
        field.setCharacterLimit(800);
        if (previous != null) field.setValue(previous.getValue());
        track(Page.TEXT, field);
        return field;
    }

    private Button pageButton(Page tab, Component label, Runnable action, int x, int y, int width) {
        var button = addRenderableWidget(Button.builder(label, ignored -> action.run())
                .bounds(x, bodyTop() + y, width, 20).build());
        track(tab, button);
        return button;
    }

    private void confirmOpenProject() {
        if (confirmed && published == null) return;
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(open -> {
            minecraft.setScreen(this);
            if (open) openProject();
        }, Component.translatable("blade_tetra.export.project.replace_form"), Component.literal(addon.getValue())));
    }

    private void addResultButtons() {
        int resultRow = bodyTop() + 213;
        String[] labels = {"directory", "path", "command"};
        Runnable[] actions = {
                () -> net.minecraft.Util.getPlatform().openFile(published.getParent().toFile()),
                () -> minecraft.keyboardHandler.setClipboard(published.toString()),
                () -> minecraft.keyboardHandler.setClipboard(acquireCommand)
        };
        for (int index = 0; index < labels.length; index++) {
            Runnable action = actions[index];
            int start = left + panelWidth * index / labels.length;
            int end = left + panelWidth * (index + 1) / labels.length;
            addRenderableWidget(Button.builder(Component.translatable("blade_tetra.export.result." + labels[index]),
                    button -> action.run()).bounds(start, resultRow, end - start - 2, 20).build());
        }
    }

    private Component awakeningConsentLabel() {
        return Component.translatable(awakeningAcknowledged
                ? "blade_tetra.export.awakening.acknowledged"
                : "blade_tetra.export.awakening.acknowledge");
    }

    private Component backplateLabel() {
        return Component.translatable("blade_tetra.export.backplate."
                + backplateMode.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void cycleBackplateMode() {
        backplateMode = switch (backplateMode) {
            case ORIGINAL -> CustomBackplateImage.Mode.REPLACE;
            case REPLACE -> CustomBackplateImage.Mode.OVERLAY;
            case OVERLAY -> CustomBackplateImage.Mode.ORIGINAL;
        };
        backplateModeButton.setMessage(backplateLabel());
    }

    private void chooseImage(boolean logoOnly) {
        Thread picker = new Thread(() -> {
            try {
                FileDialog dialog = new FileDialog((Frame) null, null, FileDialog.LOAD);
                dialog.setVisible(true);
                String filename = dialog.getFile();
                String directory = dialog.getDirectory();
                dialog.dispose();
                if (filename != null && directory != null) {
                    String selected = Path.of(directory, filename).toAbsolutePath().toString();
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().screen != this) return;
                        if (logoOnly) {
                            logo.setValue(selected);
                        } else {
                            backplatePath.setValue(selected);
                            if (backplateMode == CustomBackplateImage.Mode.ORIGINAL) cycleBackplateMode();
                        }
                    });
                }
            } catch (RuntimeException | java.awt.AWTError failure) {
                Minecraft.getInstance().execute(() ->
                    status = Component.literal("Image dialog unavailable; type a file path instead"));
            }
        }, "BladeSealBackplatePicker");
        picker.setDaemon(true);
        picker.start();
    }

    private EditBox field(int x, int y, int width, int limit, String key, String value) {
        var field = new EditBox(font, x, y, width, 15, Component.translatable(key));
        field.setMaxLength(limit);
        field.setValue(value);
        return addRenderableWidget(field);
    }

    private BladeStudioProject project() {
        return new BladeStudioProject(1, addon.getValue().strip(), version.getValue().strip(),
                modTitle.getValue().strip(), name.getValue().strip(), chineseName.getValue().strip(),
                author.getValue().strip(), description.getValue().strip(), lore.getValue().strip(),
                chineseLore.getValue().strip(), logo.getValue().strip(), backplateMode,
                backplatePath.getValue().strip());
    }

    private void loadDraft() {
        draftInitialized = true;
        try {
            draftFile = BladeStudioProject.draftFile(minecraft.gameDirectory.toPath(), preview.projectId());
            if (Files.exists(draftFile, LinkOption.NOFOLLOW_LINKS)) {
                applyProject(BladeStudioProject.loadDraft(draftFile));
                lastDraft = project();
            }
        } catch (IOException | IllegalArgumentException failure) {
            draftFile = null;
            status = Component.translatable("blade_tetra.export.mode.draft_error");
            LogUtils.getLogger().warn("Could not open Blade Studio draft", failure);
        }
    }

    private void saveDraft() {
        if (draftFile == null) return;
        BladeStudioProject current;
        try {
            current = project();
        } catch (IllegalArgumentException incompleteForm) {
            return;
        }
        if (current.equals(lastDraft)) return;
        try {
            current.save(draftFile, true);
            lastDraft = current;
        } catch (IOException failure) {
            draftFile = null;
            status = Component.translatable("blade_tetra.export.mode.draft_error");
            LogUtils.getLogger().warn("Could not save Blade Studio draft", failure);
        }
    }

    private void saveProject() {
        if (confirmed && published == null) return;
        try {
            var project = project();
            var output = BladeStudioProject.file(minecraft.gameDirectory.toPath(), project.addon());
            if (java.nio.file.Files.exists(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(replace -> {
                    minecraft.setScreen(this);
                    if (replace) saveProject(project, output, true);
                }, Component.translatable("blade_tetra.export.project.replace"), Component.literal(project.addon())));
            } else {
                saveProject(project, output, false);
            }
        } catch (IOException | IllegalArgumentException failure) {
            status = Component.translatable("blade_tetra.export.blocked", failure.getMessage());
        }
    }

    private void saveProject(BladeStudioProject project, Path file, boolean replace) {
        try {
            project.save(file, replace);
            status = Component.translatable("blade_tetra.export.project.saved", project.addon());
        } catch (IOException failure) {
            status = Component.translatable("blade_tetra.export.blocked", failure.getMessage());
        }
    }

    private void openProject() {
        try {
            var input = BladeStudioProject.file(minecraft.gameDirectory.toPath(), addon.getValue().strip());
            var project = BladeStudioProject.load(input);
            applyProject(project);
            status = Component.translatable("blade_tetra.export.project.opened", project.addon());
        } catch (IOException | IllegalArgumentException failure) {
            status = Component.translatable("blade_tetra.export.blocked", failure.getMessage());
        }
    }

    private void applyProject(BladeStudioProject project) {
        addon.setValue(project.addon());
        version.setValue(project.version());
        modTitle.setValue(project.title());
        name.setValue(project.bladeName());
        chineseName.setValue(project.chineseName());
        lore.setValue(project.lore());
        chineseLore.setValue(project.chineseLore());
        logo.setValue(project.logoPath());
        author.setValue(project.author());
        description.setValue(project.description());
        backplateMode = project.backplateMode();
        backplateModeButton.setMessage(backplateLabel());
        backplatePath.setValue(project.backplatePath());
    }

    private void openPreview() {
        try {
            var selection = new CustomBackplateImage.Selection(backplateMode, backplatePath.getValue().strip());
            minecraft.setScreen(new BladeStudioPreviewScreen(this, preview, workbench.getMenu().blade(), selection));
        } catch (IllegalArgumentException failure) {
            status = Component.translatable("blade_tetra.export.blocked", failure.getMessage());
        }
    }

    boolean workbenchOpen() {
        return minecraft != null && minecraft.player != null && minecraft.player.containerMenu == workbench.getMenu();
    }

    private void request() {
        if (confirmed || !preview.ready() || preview.omitsAwakening() && !awakeningAcknowledged) return;
        try {
            var metadata = new BladeExportArchive.Metadata("bt_export_" + addon.getValue().strip(),
                    version.getValue().strip(), modTitle.getValue().isBlank() ? name.getValue().strip()
                            : modTitle.getValue().strip(), author.getValue().strip(),
                    description.getValue().strip(), name.getValue().strip(), chineseName.getValue().strip(),
                    lore.getValue().strip(), chineseLore.getValue().strip(),
                    CustomBackplateImage.readLogo(logo.getValue().strip()));
            if (net.minecraftforge.fml.ModList.get().isLoaded(metadata.modId())) {
                throw new IllegalArgumentException(Component.translatable("blade_tetra.export.id_installed").getString());
            }
            requestedMetadata = metadata;
            requestedBackplate = new CustomBackplateImage.Selection(backplateMode, backplatePath.getValue().strip());
            if (backplateMode != CustomBackplateImage.Mode.ORIGINAL && requestedBackplate.filename().isBlank()) {
                throw new IllegalArgumentException(
                        Component.translatable("blade_tetra.export.backplate.picture_required").getString());
            }
            confirmed = true;
            confirm.active = false;
            status = Component.translatable("blade_tetra.export.waiting");
            if (approvedSnapshot != null && approvedBlade != null) {
                exportApproved();
            } else {
                ModNetwork.CHANNEL.sendToServer(
                        new BladeSealPackets.Confirm(preview.token(), awakeningAcknowledged));
            }
        } catch (IOException | IllegalArgumentException error) {
            status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
        }
    }

    static void receive(BladeSealPackets.Result result) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || !(client.screen instanceof BladeSealScreen screen)
                || !screen.preview.token().equals(result.token()) || screen.requestedMetadata == null) return;
        if (result.snapshot().isEmpty()) {
            screen.confirmed = false;
            screen.status = Component.translatable("blade_tetra.export.rejection."
                    + result.rejection().name().toLowerCase(java.util.Locale.ROOT));
            // A rejected/expired server authorization cannot be reused. Open a new seal.
            screen.confirm.active = false;
            return;
        }
        try {
            screen.approvedSnapshot = BladeSealPackets.decodeSnapshot(result.snapshot());
            screen.approvedBlade = new ItemStack(ModItems.MODULAR_SLASHBLADE.get());
            screen.approvedBlade.setTag(TagParser.parseTag(result.appearanceTag()));
            screen.exportApproved();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException error) {
            LogUtils.getLogger().warn("Failed to decode authorized blade", error);
            screen.status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
            screen.confirmed = false;
            screen.confirm.active = false;
        }
    }

    /** Reuse only the server-approved frozen stack on local packaging failures. */
    private void exportApproved() {
        if (approvedSnapshot == null || approvedBlade == null
                || requestedMetadata == null) return;
        try {
            var resources = Minecraft.getInstance().getResourceManager();
            var art = BladeExportArt.bake(requestedMetadata.modId(), approvedSnapshot,
                    approvedBlade, resources, requestedBackplate);
            if (art.requiresResourceNotice(requestedMetadata)) {
                minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(accept -> {
                    if (!workbenchOpen()) {
                        minecraft.setScreen(null);
                        return;
                    }
                    minecraft.setScreen(this);
                    if (accept) publishApproved(resources, art);
                    else {
                        confirmed = false;
                        confirm.active = preview.ready() && (!preview.omitsAwakening() || awakeningAcknowledged);
                        status = Component.translatable("blade_tetra.export.resource_notice.cancelled");
                    }
                }, Component.translatable("blade_tetra.export.resource_notice.title"),
                        Component.translatable("blade_tetra.export.resource_notice.body"),
                        Component.translatable("blade_tetra.export.resource_notice.confirm"),
                        net.minecraft.network.chat.CommonComponents.GUI_CANCEL));
            } else {
                publishApproved(resources, art);
            }
        } catch (IOException | RuntimeException error) {
            packagingFailed(error);
        }
    }

    private void publishApproved(net.minecraft.server.packs.resources.ResourceManager resources, BladeExportArt art) {
        try {
            var file = StandaloneBladeExport.write(Minecraft.getInstance().gameDirectory.toPath(),
                    resources, approvedSnapshot, requestedMetadata, art);
            published = file.toAbsolutePath().normalize();
            acquireCommand = "/bladeseal give @s " + requestedMetadata.modId() + ":sealed_blade";
            status = Component.translatable("blade_tetra.export.path", published.getFileName());
            addResultButtons();
            minecraft.setScreen(new BladeInscriptionScreen(this, preview, requestedBackplate,
                    Component.literal(name.getValue()), description.getValue(), published, acquireCommand));
            requestedMetadata = null;
            requestedBackplate = null;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.translatable("blade_tetra.export.path", file.toAbsolutePath().normalize()));
            }
            approvedSnapshot = null;
            approvedBlade = null;
        } catch (IOException | RuntimeException error) {
            packagingFailed(error);
        }
    }

    private void packagingFailed(Exception error) {
        LogUtils.getLogger().warn("Failed to seal blade", error);
        status = Component.translatable("blade_tetra.export.blocked", error.getMessage());
        confirmed = false;
        confirm.active = preview.ready() && (!preview.omitsAwakening() || awakeningAcknowledged);
    }

    /** Keep every field reachable at GUI scale 4 and smaller, without shrinking fonts. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inSimplePreview(mouseX, mouseY)) {
            viewport.zoom(delta);
            return true;
        }
        int maximum = Math.max(0, bodyOffset + BODY_HEIGHT - height + 12);
        if (super.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (maximum == 0) return false;
        int next = Mth.clamp(scrollPixels - (int) Math.round(delta * 24), 0, maximum);
        int shift = scrollPixels - next;
        if (shift == 0) return true;
        scrollPixels = next;
        top += shift;
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) {
                widget.setY(widget.getY() + shift);
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu == workbench.getMenu()) {
            minecraft.setScreen(workbench);
        } else {
            super.onClose();
        }
    }

    @Override
    public void tick() {
        if (!workbenchOpen()) {
            minecraft.setScreen(null);
            return;
        }
        if (++draftTicks == 20) {
            draftTicks = 0;
            saveDraft();
        }
        previewButton.active = preview.ready() && (!confirmed || published != null);
        modeSwitch.active = !confirmed || published != null;
        if (!advanced && preview.ready()) {
            if (viewport == null) viewport = new BladeStudioViewport(preview);
            viewport.prepare(minecraft, new CustomBackplateImage.Selection(backplateMode,
                    backplatePath.getValue().strip()));
        }
        if (advanced && page == Page.TEXT) {
            lore.tick();
            chineseLore.tick();
        }
    }

    boolean hasInlinePreview() {
        return !advanced && viewport != null && viewport.ready();
    }

    Component exportStatus() {
        return status;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        BladeStudioStyle.panel(graphics, left - 5, top, left + panelWidth + 5, bodyTop() + BODY_HEIGHT);
        super.render(graphics, mouseX, mouseY, delta);
        if (advanced && left >= 103 && !workbench.getMenu().blade().isEmpty()) {
            var stack = workbench.getMenu().blade();
            int x = left - 54;
            int y = Math.max(50, height / 2);
            graphics.fill(left - 99, y - 48, left - 9, y + 48, 0xe8251d18);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(4, 4, 1);
            graphics.renderItem(stack, -8, -8);
            graphics.pose().popPose();
            if (mouseX >= left - 99 && mouseX < left - 9 && Math.abs(mouseY - y) < 48) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
        graphics.drawString(font, title, left, top + 7, 0xffffff);
        if (!advanced) {
            renderSimple(graphics);
        } else switch (page) {
            case BLADE -> renderOverview(graphics);
            case ADDON -> drawFieldLabels(graphics, "mod_name", "id", "version", "author", "description");
            case APPEARANCE -> {
                drawFieldLabels(graphics, "logo", "", "backplate_file");
                graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.page.appearance_help"),
                        left, bodyTop() + 115, panelWidth, 0xffd6bb9a);
            }
            case TEXT -> {
                drawFieldLabels(graphics, "name", "chinese_name");
                graphics.drawString(font, Component.translatable("blade_tetra.export.lore"),
                        left, bodyTop() + 55, 0xffffff);
                graphics.drawString(font, Component.translatable("blade_tetra.export.chinese_lore"),
                        left, bodyTop() + 106, 0xffffff);
            }
            case PROJECT -> graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.page.project_id",
                    addon.getValue()), left, bodyTop() + 7, panelWidth, 0xffd6bb9a);
        }
        if (status != null) {
            graphics.drawWordWrap(font, status, left, bodyTop() + 189, panelWidth, 0xffffff);
        }
    }

    private int simplePreviewWidth() { return panelWidth * 3 / 5; }
    private int simpleTextX() { return left + simplePreviewWidth() + 9; }
    private int simpleTextWidth() { return panelWidth - simplePreviewWidth() - 9; }

    private void renderSimple(GuiGraphics graphics) {
        int y = bodyTop();
        int previewWidth = simplePreviewWidth();
        graphics.fill(left, y - 15, left + previewWidth, y + 138, 0xff181513);
        if (viewport != null && viewport.ready()) {
            viewport.render(graphics, left, y - 15, left + previewWidth, y + 138);
        } else {
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(left + previewWidth / 2F, y + 36, 0);
                pose.scale(3, 3, 1);
                graphics.renderItem(workbench.getMenu().blade(), -8, -8);
            } finally {
                pose.popPose();
            }
        }
        int textX = simpleTextX();
        int textWidth = simpleTextWidth();
        if (projectedStats != null) {
            graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.page.stats",
                    projectedStats.attack(), projectedStats.durability()), textX, y + 81, textWidth, 0xffd6bb9a);
        }
        Component message = preview.omitsAwakening()
                ? Component.translatable("blade_tetra.export.awakening.notice")
                : Component.translatable(preview.ready() ? "blade_tetra.export.mode.ready"
                        : "blade_tetra.export.mode.blocked");
        graphics.drawWordWrap(font, message, textX, y + 111, textWidth,
                preview.omitsAwakening() || !preview.ready() ? 0xffffd480 : 0xffa6e3b0);
        graphics.drawString(font, Component.translatable("blade_tetra.export.name"), textX, y, 0xffeee3ce);
        graphics.drawString(font, Component.translatable("blade_tetra.export.description"), textX, y + 39, 0xffeee3ce);
    }

    private boolean inSimplePreview(double x, double y) {
        return !advanced && viewport != null && viewport.ready() && x >= left && x < left + simplePreviewWidth()
                && y >= bodyTop() - 15 && y < bodyTop() + 138;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inSimplePreview(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            setFocused(null);
            previewDragging = true;
        } else if (button == 1) {
            viewport.toggleDrawn();
        } else if (button == 2) {
            viewport.resetCamera();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0 || !previewDragging) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        viewport.rotate(dx, dy);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || !previewDragging) return super.mouseReleased(mouseX, mouseY, button);
        previewDragging = false;
        return true;
    }

    @Override
    public void removed() {
        saveDraft();
        previewDragging = false;
        if (viewport != null) viewport.release(minecraft);
    }

    private void drawFieldLabels(GuiGraphics graphics, String... labels) {
        for (int row = 0; row < labels.length; row++) {
            if (labels[row].isEmpty()) continue;
            graphics.drawString(font, Component.translatable("blade_tetra.export." + labels[row]),
                    left, bodyTop() + 1 + row * 27, 0xffffff);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        graphics.drawWordWrap(font, Component.translatable(preview.ready()
                        ? "blade_tetra.export.preflight.ready" : "blade_tetra.export.preflight.blocked", preview.summary()),
                left, bodyTop() + 25, panelWidth, preview.ready() ? 0xffa6e3b0 : 0xffff9c9c);
        if (projectedStats != null) {
            graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.page.stats",
                            projectedStats.attack(), projectedStats.durability()),
                    left, bodyTop() + 64, panelWidth, 0xffd6bb9a);
        }
        if (preview.omitsAwakening()) {
            graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.awakening.notice"),
                    left, bodyTop() + 79, panelWidth, 0xffffd480);
        } else {
            graphics.drawWordWrap(font, Component.translatable("blade_tetra.export.acquisition"),
                    left, bodyTop() + 103, panelWidth, 0xffd6bb9a);
        }
    }
}
