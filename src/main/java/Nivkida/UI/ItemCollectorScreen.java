package Nivkida.UI;

import Nivkida.Manager.ConfigManager;
import Nivkida.Manager.FileExporter;
import Nivkida.Manager.SelectedItemsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ItemCollectorScreen extends Screen {
    private final Minecraft mc = Minecraft.getInstance();
    private final List<Item> allItems;
    private List<Item> filtered;
    private int cols;
    private int rows;
    private int cellSize = 20;
    private final int margin = 16;
    private int startIndex = 0;
    private EditBox fileNameField;
    private EditBox idSearchBox;
    private int fileFieldX, fileFieldY;
    private int searchFieldX, searchFieldY;
    private final List<String> modList;
    private int modTabStart = 0;
    private String selectedMod = "all";
    private final LinkedHashMap<String, Boolean> typeState = new LinkedHashMap<>();
    private final Map<String, Button> typeButtons = new HashMap<>();
    private String lastIdSearch = "";
    private String lastSelectedMod = "all";
    private Set<String> lastActiveTypes = new HashSet<>();
    private final int rightPanelWidth = 320;
    private final int rightPanelPadding = 12;
    private int gridY0;
    private int gridX0;
    private Button modScrollLeft;
    private Button modScrollRight;
    private int visibleModTabs = 8;
    private List<Component> currentHelpText = null;
    private final List<Button> modTabButtons = new ArrayList<>();
    private int modTabsAreaWidth;
    private int hoveredItemIndex = -1;
    private static final int PANEL_BG_COLOR = 0xDD000000;
    private static final int HIGHLIGHT_COLOR = 0x66FFFFFF;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int SELECTED_COLOR = 0x8033AA33;

    public ItemCollectorScreen() {
        super(Component.translatable("screen.itemidcollect.title"));
        ConfigManager.ConfigData cfg = ConfigManager.load();
        this.cols = Math.max(4, cfg.cols);
        this.rows = Math.max(4, cfg.rows);
        this.allItems = ForgeRegistries.ITEMS.getValues().stream().collect(Collectors.toList());
        this.allItems.sort(Comparator.comparing(i -> {
            ResourceLocation k = ForgeRegistries.ITEMS.getKey(i);
            return k == null ? new ItemStack(i).getHoverName().getString() : k.toString();
        }));
        this.filtered = new ArrayList<>(allItems);
        Set<String> mods = this.allItems.stream()
                .map(it -> {
                    ResourceLocation k = ForgeRegistries.ITEMS.getKey(it);
                    return k == null ? "minecraft" : k.getNamespace();
                })
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> ml = new ArrayList<>();
        ml.add("all");
        ml.addAll(mods);
        this.modList = ml;
        this.selectedMod = cfg.lastMod != null ? cfg.lastMod : "all";
        String[] types = new String[]{"Sword", "Tools", "Armor", "Food", "Block", "Bow", "Shield", "Potion", "Other"};
        Set<String> selectedTypesFromConfig = cfg.selectedTypes != null ? cfg.selectedTypes : new HashSet<>();
        for (String t : types)
            typeState.put(t, selectedTypesFromConfig.contains(t));
    }

    @Override
    protected void init() {
        super.init();

        int leftAreaWidth = this.width - rightPanelWidth - margin * 2;
        this.modTabsAreaWidth = leftAreaWidth;
        this.cellSize = Math.max(16, Math.min(24, (leftAreaWidth - 20) / cols));

        // Header buttons
        int headerY = 15;
        int buttonWidth = 90;
        int buttonSpacing = 8;

        this.addRenderableWidget(createStyledButton(
                Component.translatable("button.export_txt"),
                b -> exportTxt(),
                margin, headerY, buttonWidth, 20
        ));

        this.addRenderableWidget(createStyledButton(
                Component.translatable("button.export_json"),
                b -> exportJson(),
                margin + buttonWidth + buttonSpacing, headerY, buttonWidth, 20
        ));

        this.addRenderableWidget(createStyledButton(
                Component.translatable("button.clear_selection"),
                b -> SelectedItemsManager.clear(),
                margin + (buttonWidth + buttonSpacing) * 2, headerY, buttonWidth + 20, 20
        ));

        int currentY = headerY + 30;

        // Mod tabs with scrolling
        addModTabButtons(margin, currentY, leftAreaWidth);
        currentY += 25;

        // Type toggles
        int typeButtonWidth = 80;
        int typeButtonHeight = 18;
        int typeButtonSpacing = 4;
        int typeButtonsPerRow = Math.max(1, (leftAreaWidth - margin) / (typeButtonWidth + typeButtonSpacing));

        int tx = margin;
        int ty = currentY;

        for (String t : typeState.keySet()) {
            boolean on = typeState.get(t);
            Button btn = createToggleButton(t, on, tx, ty, typeButtonWidth, typeButtonHeight);
            typeButtons.put(t, btn);
            this.addRenderableWidget(btn);

            tx += typeButtonWidth + typeButtonSpacing;
            if (tx + typeButtonWidth > leftAreaWidth - margin) {
                tx = margin;
                ty += typeButtonHeight + 4;
            }
        }

        currentY = ty + typeButtonHeight + 10;

        // Search field
        searchFieldX = margin;
        searchFieldY = currentY;
        idSearchBox = new EditBox(this.font, searchFieldX, searchFieldY, leftAreaWidth - margin - 10, 20, Component.translatable("field.search"));
        idSearchBox.setValue(ConfigManager.load().lastSearch != null ? ConfigManager.load().lastSearch : "");
        idSearchBox.setResponder(s -> rebuildFilteredIfNeeded(true));
        this.addRenderableWidget(idSearchBox);

        currentY += 30;

        // Filename field
        fileFieldX = margin;
        fileFieldY = currentY;
        fileNameField = new EditBox(this.font, fileFieldX, fileFieldY, leftAreaWidth - margin - 10, 20, Component.translatable("field.filename"));
        fileNameField.setValue("items_dump");
        this.addRenderableWidget(fileNameField);

        currentY += 30;

        // Grid position
        gridX0 = margin + (leftAreaWidth - (cols * cellSize)) / 2;
        gridY0 = currentY;

        // Prev/Next buttons
        int btnW = 90;
        int btnSpacing = 12;
        int btnY = gridY0 + rows * cellSize + 12;
        int btnPanelWidth = btnW * 2 + btnSpacing;
        int btnPanelX = gridX0 + (cols * cellSize - btnPanelWidth) / 2;

        this.addRenderableWidget(createStyledButton(
                Component.translatable("button.prev"),
                b -> startIndex = Math.max(0, startIndex - cols * rows),
                btnPanelX, btnY, btnW, 20
        ));

        this.addRenderableWidget(createStyledButton(
                Component.translatable("button.next"),
                b -> startIndex = Math.min(Math.max(0, filtered.size() - cols * rows), startIndex + cols * rows),
                btnPanelX + btnW + btnSpacing, btnY, btnW, 20
        ));

        // Initial filter
        rebuildFilteredIfNeeded(true);
    }

    private Button createStyledButton(Component text, Button.OnPress action, int x, int y, int width, int height) {
        return Button.builder(text, action)
                .bounds(x, y, width, height)
                .createNarration(supplier -> text.copy())
                .build();
    }

    private Button createToggleButton(String text, boolean active, int x, int y, int width, int height) {
        Component displayText = Component.literal((active ? "§a✓ " : "§7○ ") + text);
        return Button.builder(displayText, b -> {
                    boolean cur = typeState.get(text);
                    typeState.put(text, !cur);
                    b.setMessage(Component.literal((!cur ? "§a✓ " : "§7○ ") + text));
                    saveConfig();
                    rebuildFilteredIfNeeded(true);
                })
                .bounds(x, y, width, height)
                .createNarration(supplier -> Component.literal(text).copy())
                .build();
    }

    private void addModTabButtons(int startX, int startY, int leftAreaWidth) {
        int x = startX;
        int y = startY;

        // Clear existing mod buttons
        for (Button btn : modTabButtons) {
            this.removeWidget(btn);
        }
        modTabButtons.clear();

        // Left scroll button
        modScrollLeft = createStyledButton(Component.literal("◀"), b -> {
            modTabStart = Math.max(0, modTabStart - 1);
            updateModTabButtons();
        }, x, y, 22, 18);
        this.addRenderableWidget(modScrollLeft);
        modTabButtons.add(modScrollLeft);
        x += 24;

        // Calculate how many mod tabs we can show
        int modTabWidth = 70;
        int modTabSpacing = 2;
        visibleModTabs = Math.min((leftAreaWidth - 48) / (modTabWidth + modTabSpacing), modList.size() - modTabStart);

        // Mod tabs
        for (int i = 0; i < visibleModTabs; i++) {
            final int idx = modTabStart + i;
            if (idx >= modList.size()) break;

            String modid = modList.get(idx);
            String displayName = modid.length() > 10 ? modid.substring(0, 10) + "..." : modid;
            boolean isSelected = modid.equals(selectedMod);
            Component label = Component.literal(isSelected ? "§6" + displayName : displayName);

            Button modBtn = createStyledButton(label, b -> {
                selectedMod = modid;
                saveConfig();
                rebuildFilteredIfNeeded(true);
                updateModTabButtons();
            }, x, y, modTabWidth, 18);

            this.addRenderableWidget(modBtn);
            modTabButtons.add(modBtn);
            x += modTabWidth + modTabSpacing;
        }

        // Right scroll button
        modScrollRight = createStyledButton(Component.literal("▶"), b -> {
            modTabStart = Math.min(modList.size() - visibleModTabs, modTabStart + 1);
            updateModTabButtons();
        }, x, y, 22, 18);
        this.addRenderableWidget(modScrollRight);
        modTabButtons.add(modScrollRight);

        // Update button states
        updateModTabButtons();
    }

    private void updateModTabButtons() {
        modScrollLeft.active = modTabStart > 0;
        modScrollRight.active = modTabStart + visibleModTabs < modList.size();

        // Remove all mod tab buttons except scroll buttons
        List<Button> buttonsToRemove = new ArrayList<>();
        for (Button btn : modTabButtons) {
            if (btn != modScrollLeft && btn != modScrollRight) {
                buttonsToRemove.add(btn);
                this.removeWidget(btn);
            }
        }
        modTabButtons.removeAll(buttonsToRemove);

        // Recreate mod tabs with updated selection
        int x = margin + 24;
        int y = 45;

        int modTabWidth = 70;
        int modTabSpacing = 2;

        for (int i = 0; i < visibleModTabs; i++) {
            final int idx = modTabStart + i;
            if (idx >= modList.size()) break;

            String modid = modList.get(idx);
            String displayName = modid.length() > 10 ? modid.substring(0, 10) + "..." : modid;
            boolean isSelected = modid.equals(selectedMod);
            Component label = Component.literal(isSelected ? "§6" + displayName : displayName);

            Button modBtn = createStyledButton(label, b -> {
                selectedMod = modid;
                saveConfig();
                rebuildFilteredIfNeeded(true);
                updateModTabButtons();
            }, x, y, modTabWidth, 18);

            this.addRenderableWidget(modBtn);
            modTabButtons.add(modBtn);
            x += modTabWidth + modTabSpacing;
        }
    }

    private void exportTxt() {
        File file = FileExporter.exportToTxt(fileNameField.getValue(), SelectedItemsManager.getAll());
        if (file != null && mc.player != null) {
            String path = file.getParentFile().getAbsolutePath();
            Component comp = Component.translatable("message.export_success", file.getName()).withStyle(s -> s.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path)));
            mc.player.sendSystemMessage(comp);
        } else if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("message.export_failed").withStyle(ChatFormatting.RED));
        }
    }

    private void exportJson() {
        File file = FileExporter.exportToJson(fileNameField.getValue(), SelectedItemsManager.getAll());
        if (file != null && mc.player != null) {
            String path = file.getParentFile().getAbsolutePath();
            Component comp = Component.translatable("message.export_success", file.getName()).withStyle(s -> s.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path)));
            mc.player.sendSystemMessage(comp);
        } else if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable("message.export_failed").withStyle(ChatFormatting.RED));
        }
    }

    private void saveConfig() {
        ConfigManager.ConfigData cd = new ConfigManager.ConfigData();
        cd.cols = this.cols;
        cd.rows = this.rows;
        cd.lastMod = this.selectedMod;
        cd.lastSearch = idSearchBox != null ? idSearchBox.getValue() : "";
        cd.selectedTypes = new HashSet<>(typeState.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toSet()));
        ConfigManager.save(cd);
    }

    private void rebuildFilteredIfNeeded(boolean force) {
        String idSearch = idSearchBox != null ? idSearchBox.getValue().trim() : "";
        Set<String> activeTypes = typeState.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toSet());
        boolean changed = force || !Objects.equals(idSearch, lastIdSearch) || !Objects.equals(selectedMod, lastSelectedMod) || !Objects.equals(activeTypes, lastActiveTypes);
        if (!changed) return;

        lastIdSearch = idSearch;
        lastSelectedMod = selectedMod;
        lastActiveTypes = new HashSet<>(activeTypes);

        filtered = allItems.stream().filter(item -> {
            ResourceLocation k = ForgeRegistries.ITEMS.getKey(item);
            String namespace = k == null ? "minecraft" : k.getNamespace();
            String id = k == null ? "" : k.toString();

            if (!"all".equals(selectedMod) && !namespace.equalsIgnoreCase(selectedMod)) return false;

            if (!idSearch.isEmpty()) {
                String display = new ItemStack(item).getHoverName().getString();
                if (!id.toLowerCase(Locale.ROOT).contains(idSearch.toLowerCase(Locale.ROOT)) &&
                        !display.toLowerCase(Locale.ROOT).contains(idSearch.toLowerCase(Locale.ROOT)))
                    return false;
            }

            if (!activeTypes.isEmpty()) {
                boolean ok = false;
                if (activeTypes.contains("Sword") && item instanceof SwordItem) ok = true;
                if (activeTypes.contains("Tools") && (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem || item instanceof HoeItem)) ok = true;
                if (activeTypes.contains("Armor") && item instanceof ArmorItem) ok = true;
                if (activeTypes.contains("Food") && item.isEdible()) ok = true;
                if (activeTypes.contains("Block") && item instanceof BlockItem) ok = true;
                if (activeTypes.contains("Bow") && (item instanceof BowItem || item instanceof CrossbowItem)) ok = true;
                if (activeTypes.contains("Shield") && item instanceof ShieldItem) ok = true;
                if (activeTypes.contains("Potion") && item instanceof PotionItem) ok = true;
                if (activeTypes.contains("Other") && !(item instanceof SwordItem) && !(item instanceof PickaxeItem) &&
                        !(item instanceof AxeItem) && !(item instanceof ShovelItem) && !(item instanceof HoeItem) &&
                        !(item instanceof ArmorItem) && !(item instanceof BlockItem) && !item.isEdible())
                    ok = true;
                if (!ok) return false;
            }
            return true;
        }).collect(Collectors.toList());

        int pageSize = cols * rows;
        if (startIndex >= filtered.size()) startIndex = Math.max(0, filtered.size() - pageSize);
    }

    @Override
    public void tick() {
        rebuildFilteredIfNeeded(false);
        super.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g);

        // Сбрасываем текст помощи и индекс наведенного предмета
        currentHelpText = null;
        hoveredItemIndex = -1;

        // Проверяем наведение на элементы интерфейса
        if (isMouseOver(mouseX, mouseY, fileFieldX, fileFieldY, fileNameField.getWidth(), 20)) {
            currentHelpText = List.of(
                    Component.translatable("help.filename.line1"),
                    Component.translatable("help.filename.line2")
            );
        } else if (isMouseOver(mouseX, mouseY, searchFieldX, searchFieldY, idSearchBox.getWidth(), 20)) {
            currentHelpText = List.of(
                    Component.translatable("help.search.line1"),
                    Component.translatable("help.search.line2")
            );
        } else {
            // Проверяем наведение на сетку предметов
            hoveredItemIndex = getIndexAt(mouseX, mouseY);
        }

        // Рисуем сетку предметов с улучшенным оформлением
        int idx = startIndex;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = gridX0 + c * cellSize;
                int y = gridY0 + r * cellSize;
                if (idx >= filtered.size()) {
                    idx++;
                    continue;
                }
                Item item = filtered.get(idx);
                ItemStack stack = new ItemStack(item);

                // Cell background with border
                g.fill(x - 1, y - 1, x + cellSize + 1, y + cellSize + 1, BORDER_COLOR);
                g.fill(x, y, x + cellSize, y + cellSize, 0xFF2D2D2D);

                // Hover highlight
                if (idx == hoveredItemIndex) {
                    g.fill(x, y, x + cellSize, y + cellSize, HIGHLIGHT_COLOR);
                }

                // Item render
                g.renderItem(stack, x + 2, y + 2);
                g.renderItemDecorations(this.font, stack, x + 2, y + 2);

                // Selection highlight
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                if (key != null && SelectedItemsManager.isSelected(key)) {
                    g.fill(x, y, x + cellSize, y + cellSize, SELECTED_COLOR);
                }
                idx++;
            }
        }

        // Draw widgets
        super.render(g, mouseX, mouseY, partialTicks);

        // RIGHT PANEL with improved styling
        int rightX = this.width - rightPanelWidth;
        int rightY = 0;
        int rightW = rightPanelWidth;
        int rightH = this.height;

        // Panel background with gradient
        g.fill(rightX, rightY, rightX + rightW, rightY + rightH, PANEL_BG_COLOR);
        g.fill(rightX, rightY, rightX + 2, rightY + rightH, 0xFF555555);

        // Panel title
        g.drawString(this.font, Component.translatable("panel.details.title").withStyle(ChatFormatting.GOLD),
                rightX + rightPanelPadding, rightY + rightPanelPadding, 0xFFFFFF, false);

        // Hovered item details or instructions
        int contentY = rightY + rightPanelPadding + 16;
        int lineH = this.font.lineHeight + 2;
        int maxTextWidth = rightW - rightPanelPadding * 2;

        if (hoveredItemIndex != -1 && hoveredItemIndex < filtered.size()) {
            Item item = filtered.get(hoveredItemIndex);
            ItemStack stack = new ItemStack(item);
            List<Component> info = buildDetailedInfo(item, stack);

            for (Component line : info) {
                if (contentY > rightY + rightH - 20) break;

                List<Component> wrappedLines = wrapText(line, maxTextWidth);
                for (Component wrappedLine : wrappedLines) {
                    if (contentY > rightY + rightH - 20) break;
                    g.drawString(this.font, wrappedLine, rightX + rightPanelPadding, contentY, 0xFFFFFF, false);
                    contentY += lineH;
                }
                contentY += 2; // Add spacing between paragraphs
            }

            // Add selection status
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null) {
                boolean isSelected = SelectedItemsManager.isSelected(key);
                Component status = isSelected ?
                        Component.translatable("status.selected").withStyle(ChatFormatting.GREEN) :
                        Component.translatable("status.not_selected").withStyle(ChatFormatting.GRAY);

                g.drawString(this.font, status, rightX + rightPanelPadding, contentY, 0xFFFFFF, false);
                contentY += lineH + 4;

                // Add click hint
                Component hint = Component.translatable("hint.click_to_toggle").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);
                g.drawString(this.font, hint, rightX + rightPanelPadding, contentY, 0xAAAAAA, false);
            }
        } else if (currentHelpText != null) {
            for (Component line : currentHelpText) {
                if (contentY > rightY + rightH - 20) break;

                List<Component> wrappedLines = wrapText(line, maxTextWidth);
                for (Component wrappedLine : wrappedLines) {
                    if (contentY > rightY + rightH - 20) break;
                    g.drawString(this.font, wrappedLine, rightX + rightPanelPadding, contentY, 0xBBBBBB, false);
                    contentY += lineH;
                }
                contentY += 4;
            }
        } else {
            List<Component> help = List.of(
                    Component.translatable("panel.details.empty_line1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("panel.details.empty_line2").withStyle(ChatFormatting.GRAY),
                    Component.literal(""),
                    Component.translatable("panel.details.empty_line3").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY)
            );

            for (Component line : help) {
                if (contentY > rightY + rightH - 20) break;

                List<Component> wrappedLines = wrapText(line, maxTextWidth);
                for (Component wrappedLine : wrappedLines) {
                    if (contentY > rightY + rightH - 20) break;
                    g.drawString(this.font, wrappedLine, rightX + rightPanelPadding, contentY, 0xBBBBBB, false);
                    contentY += lineH;
                }
                contentY += 2;
            }
        }
    }

    private List<Component> wrapText(Component text, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        String plainText = text.getString();
        StringBuilder currentLine = new StringBuilder();

        for (String word : plainText.split(" ")) {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;

            if (this.font.width(testLine) > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(Component.literal(currentLine.toString()));
                    currentLine = new StringBuilder(word);
                } else {
                    // Single word is too long, split it
                    int splitIndex = 0;
                    for (int i = 1; i <= word.length(); i++) {
                        if (this.font.width(word.substring(0, i)) > maxWidth) {
                            lines.add(Component.literal(word.substring(0, splitIndex)));
                            currentLine = new StringBuilder(word.substring(splitIndex));
                            break;
                        }
                        splitIndex = i;
                    }
                    if (splitIndex == word.length()) {
                        lines.add(Component.literal(word));
                        currentLine = new StringBuilder();
                    }
                }
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(Component.literal(currentLine.toString()));
        }

        return lines;
    }

    private static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private List<Component> buildDetailedInfo(Item item, ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        // Display name
        lines.add(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));

        // ID
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        lines.add(Component.literal("ID: " + (id == null ? "unknown" : id.toString())).withStyle(ChatFormatting.GRAY));

        // Model path
        if (id != null) lines.add(Component.literal("Model path: " + id.getNamespace() + ":item/" + id.getPath()).withStyle(ChatFormatting.GRAY));

        return lines;
    }

    private int getIndexAt(int mouseX, int mouseY) {
        if (mouseY < gridY0 || mouseY > gridY0 + rows * cellSize) return -1;
        if (mouseX < gridX0 || mouseX > gridX0 + cols * cellSize) return -1;

        int cx = (mouseX - gridX0) / cellSize;
        int cy = (mouseY - gridY0) / cellSize;

        if (cx < 0 || cx >= cols || cy < 0 || cy >= rows) return -1;

        int idx = startIndex + cy * cols + cx;
        if (idx < 0 || idx >= filtered.size()) return -1;

        return idx;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int idx = getIndexAt((int) mouseX, (int) mouseY);
        if (idx != -1 && idx < filtered.size()) {
            Item item = filtered.get(idx);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null) {
                SelectedItemsManager.toggle(id);
                saveConfig();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        saveConfig();
        super.onClose();
    }
}