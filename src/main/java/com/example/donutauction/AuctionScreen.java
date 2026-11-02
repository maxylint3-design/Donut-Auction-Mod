package com.example.donutauction;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The 3-step menu:  1 Item  ->  2 Price  ->  3 Time  ->  Start Auction
 * A "Preview" panel on the right always shows what you have chosen so far.
 */
public class AuctionScreen extends Screen {
    // ---- layout numbers (in GUI pixels) ----
    private static final int PANEL_W = 400;
    private static final int PANEL_H = 224;
    private static final int LEFT_W = 270;      // width of the step area
    private static final int COLS = 12;         // item grid columns
    private static final int ROWS = 5;          // item grid rows that fit on screen
    private static final int CELL = 22;         // size of one item slot
    private static final int ACCENT = 0xFFFFD21A; // yellow like the screenshot

    // ---- presets ----
    private static final String[] PRICE_LABELS = {"$100k", "$500k", "$1m", "$5m", "$10m", "$50m"};
    private static final long[] PRICE_VALUES = {100_000L, 500_000L, 1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L};

    private static final String[] TIME_LABELS = {"1 min", "5 min", "10 min", "30 min", "1 hour", "3 hours", "6 hours", "12 hours", "24 hours"};
    private static final int[] TIME_SECONDS = {60, 300, 600, 1800, 3600, 10800, 21600, 43200, 86400};

    private enum Filter { ALL, BLOCKS, ITEMS }

    // ---- everything the player has chosen (kept here so it survives window resizes) ----
    private int step = 1;
    private ItemStack selected = ItemStack.EMPTY;
    private int count = 1;
    private String priceText = "1m";
    private long minBid = 1_000_000L;
    private int durationSeconds = 60;
    private String customMinutes = "";
    private boolean announce = true;
    private Filter filter = Filter.ALL;
    private String search = "";
    private int scrollRow = 0;
    private String status = "";

    private final List<ItemStack> visible = new ArrayList<>();
    private ItemStack hovered = ItemStack.EMPTY;

    private EditBox searchField;
    private EditBox priceField;
    private EditBox countField;
    private EditBox customField;
    private Button backButton;
    private Button nextButton;
    private Button startButton;

    private int left;
    private int top;

    // List of every item in the game, built once.
    private static List<ItemStack> allItems;

    private static List<ItemStack> allItems() {
        if (allItems == null) {
            allItems = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR) {
                    allItems.add(new ItemStack(item));
                }
            }
        }
        return allItems;
    }

    public AuctionScreen() {
        super(Component.literal("Auction Mod"));
        refilter();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // don't freeze the game while the menu is open
    }

    // ================================================================== building the widgets

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        int cy = top + 36; // top of the step content

        searchField = null;
        priceField = null;
        countField = null;
        customField = null;

        if (step == 1) buildItemStep(cy);
        if (step == 2) buildPriceStep(cy);
        if (step == 3) buildTimeStep(cy);

        // ---- bottom bar: Back / Next ----
        backButton = addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            if (step > 1) {
                step--;
                rebuildWidgets();
            }
        }).bounds(left, top + PANEL_H - 24, 70, 20).build());

        nextButton = addRenderableWidget(Button.builder(Component.literal("Next"), b -> {
            if (step < 3 && canAdvance()) {
                step++;
                rebuildWidgets();
            }
        }).bounds(left + LEFT_W - 70, top + PANEL_H - 24, 70, 20).build());

        // ---- Start Auction button (under the preview) ----
        startButton = addRenderableWidget(Button.builder(Component.literal("Start Auction"), b -> startAuction())
                .bounds(left + LEFT_W + 10, top + PANEL_H - 24, 120, 20).build());

        refreshButtons();
    }

    private void buildItemStep(int cy) {
        searchField = new EditBox(this.font, left, cy, 128, 18, Component.literal("Search"));
        searchField.setHint(Component.literal("Search every item..."));
        searchField.setMaxLength(40);
        searchField.setValue(search);
        searchField.setResponder(text -> {
            search = text;
            scrollRow = 0;
            refilter();
        });
        addRenderableWidget(searchField);

        Filter[] filters = Filter.values();
        String[] names = {"All", "Blocks", "Items"};
        int[] widths = {34, 48, 42};
        int x = left + 134;
        for (int i = 0; i < filters.length; i++) {
            Filter f = filters[i];
            Component label = Component.literal(names[i])
                    .withStyle(filter == f ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            addRenderableWidget(Button.builder(label, b -> {
                filter = f;
                scrollRow = 0;
                refilter();
                rebuildWidgets();
            }).bounds(x, cy, widths[i], 18).build());
            x += widths[i] + 2;
        }

        addRenderableWidget(Button.builder(Component.literal("Use held item"), b -> useHeldItem())
                .bounds(left + LEFT_W - 110, top + PANEL_H - 50, 110, 20).build());
    }

    private void buildPriceStep(int cy) {
        priceField = new EditBox(this.font, left, cy + 14, 110, 18, Component.literal("Minimum bid"));
        priceField.setHint(Component.literal("e.g. 1m or 500k"));
        priceField.setMaxLength(20);
        priceField.setValue(priceText);
        priceField.setResponder(text -> {
            priceText = text;
            minBid = Format.parseMoney(text);
            refreshButtons();
        });
        addRenderableWidget(priceField);

        countField = new EditBox(this.font, left + 130, cy + 14, 50, 18, Component.literal("Count"));
        countField.setMaxLength(4);
        countField.setValue(Integer.toString(count));
        countField.setResponder(text -> {
            try {
                count = Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                count = 0;
            }
            refreshButtons();
        });
        addRenderableWidget(countField);

        for (int i = 0; i < PRICE_LABELS.length; i++) {
            long value = PRICE_VALUES[i];
            addRenderableWidget(Button.builder(Component.literal(PRICE_LABELS[i]), b -> {
                priceText = Format.shortNumber(value);
                minBid = value;
                priceField.setValue(priceText);
                refreshButtons();
            }).bounds(left + i * 44, cy + 46, 42, 20).build());
        }
    }

    private void buildTimeStep(int cy) {
        for (int i = 0; i < TIME_LABELS.length; i++) {
            int seconds = TIME_SECONDS[i];
            boolean chosen = customMinutes.isEmpty() && durationSeconds == seconds;
            Component label = Component.literal(TIME_LABELS[i])
                    .withStyle(chosen ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            addRenderableWidget(Button.builder(label, b -> {
                durationSeconds = seconds;
                customMinutes = "";
                rebuildWidgets();
            }).bounds(left + (i % 4) * 67, cy + 14 + (i / 4) * 24, 64, 20).build());
        }

        customField = new EditBox(this.font, left + 67, cy + 14 + 48, 130, 18, Component.literal("Custom minutes"));
        customField.setHint(Component.literal("Custom (minutes)"));
        customField.setMaxLength(8);
        customField.setValue(customMinutes);
        customField.setResponder(text -> {
            customMinutes = text;
            if (!text.isBlank()) {
                try {
                    durationSeconds = (int) Math.round(Double.parseDouble(text.trim()) * 60.0);
                } catch (NumberFormatException e) {
                    durationSeconds = -1;
                }
            }
            refreshButtons();
        });
        addRenderableWidget(customField);

        addRenderableWidget(Button.builder(announceLabel(), b -> {
            announce = !announce;
            rebuildWidgets();
        }).bounds(left, cy + 14 + 80, 190, 20).build());
    }

    private Component announceLabel() {
        return Component.literal("Announce in chat: " + (announce ? "ON" : "OFF"));
    }

    // ================================================================== logic

    private void refilter() {
        visible.clear();
        String needle = search.trim().toLowerCase(Locale.ROOT);
        for (ItemStack stack : allItems()) {
            boolean isBlock = stack.getItem() instanceof BlockItem;
            if (filter == Filter.BLOCKS && !isBlock) continue;
            if (filter == Filter.ITEMS && isBlock) continue;
            if (!needle.isEmpty()) {
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                if (!name.contains(needle) && !id.contains(needle)) continue;
            }
            visible.add(stack);
        }
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    private int maxScroll() {
        int totalRows = (visible.size() + COLS - 1) / COLS;
        return Math.max(0, totalRows - ROWS);
    }

    private void useHeldItem() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        ItemStack held = this.minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            status = "Your hand is empty!";
            return;
        }
        selected = held.copy();
        count = held.getCount();
        status = "";
        refreshButtons();
    }

    private boolean canAdvance() {
        if (step == 1) return !selected.isEmpty();
        if (step == 2) return minBid > 0 && count >= 1;
        return false;
    }

    /** Returns an error message, or null if everything is OK. */
    private String problem() {
        if (selected.isEmpty()) return "Pick an item first";
        if (minBid <= 0) return "Enter a valid minimum bid";
        if (count < 1) return "Count must be 1 or more";
        if (durationSeconds < 10) return "Time must be at least 10 seconds";
        if (durationSeconds > 7 * 86400) return "Max time is 7 days";
        return null;
    }

    private void refreshButtons() {
        if (backButton != null) backButton.active = step > 1;
        if (nextButton != null) {
            nextButton.visible = step < 3;
            nextButton.active = canAdvance();
        }
        if (startButton != null) startButton.active = problem() == null;
    }

    private void startAuction() {
        if (problem() != null) return;
        AuctionManager.start(selected, count, minBid, durationSeconds, announce);
        onClose();
    }

    // ================================================================== input

    @Override
    public boolean keyPressed(KeyEvent event) {
       if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
           import org.lwjgl.glfw.GLFW;
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (event.button() == 0) {
            // click on a tab
            if (mouseY >= top + 12 && mouseY <= top + 32 && mouseX >= left && mouseX <= left + PANEL_W) {
                int target = (int) ((mouseX - left) / (PANEL_W / 3.0)) + 1;
                target = Math.max(1, Math.min(3, target));
                if (target <= step || !selected.isEmpty()) {
                    if (target != step) {
                        step = target;
                        rebuildWidgets();
                    }
                    return true;
                }
            }

            // click on an item in the grid
            if (step == 1) {
                int gx = left;
                int gy = top + 58;
                int col = (int) Math.floor((mouseX - gx) / CELL);
                int row = (int) Math.floor((mouseY - gy) / CELL);
                if (col >= 0 && row >= 0 && col < COLS && row < ROWS) {
                    int index = (scrollRow + row) * COLS + col;
                    if (index < visible.size()) {
                        selected = visible.get(index).copy();
                        count = 1;
                        status = "";
                        refreshButtons();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (step == 1) {
            scrollRow -= (int) Math.signum(scrollY);
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ================================================================== drawing

    /** We draw our own background, so this is intentionally empty. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hovered = ItemStack.EMPTY;

        // dark overlay + panel
        graphics.fill(0, 0, width, height, 0xB0000000);
        graphics.fill(left - 10, top - 10, left + PANEL_W + 10, top + PANEL_H + 10, 0xF00C0C10);

        drawHeader(graphics);
        if (step == 1) drawItemGrid(graphics, mouseX, mouseY);
        if (step == 2) drawPriceLabels(graphics);
        if (step == 3) drawTimeLabels(graphics);
        drawPreview(graphics);

        // buttons and text boxes
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // tooltip goes last so it is on top
        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
        }
    }

    // ---- small drawing helpers ----

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, s, x, y, color, true);
    }

    private void centered(GuiGraphicsExtractor g, String s, int centerX, int y, int color) {
        g.text(this.font, s, centerX - this.font.width(s) / 2, y, color, true);
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        text(g, "Auction Mod", left, top - 2, 0xFFFFFFFF);
        String hint = "Right Shift to close";
        text(g, hint, left + PANEL_W - this.font.width(hint), top - 2, 0xFF8A8A8A);

        String[] tabs = {"1  Item", "2  Price", "3  Time"};
        int tabW = PANEL_W / 3;
        for (int i = 0; i < 3; i++) {
            boolean active = (i + 1) == step;
            int cx = left + i * tabW + tabW / 2;
            centered(g, tabs[i], cx, top + 14, active ? 0xFFFFFFFF : 0xFF8A8A8A);
            g.fill(left + i * tabW + 2, top + 27, left + (i + 1) * tabW - 2, top + 28, active ? ACCENT : 0xFF2A2A30);
        }
    }

    private void drawItemGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int gx = left;
        int gy = top + 58;

        g.fill(gx - 2, gy - 2, gx + COLS * CELL + 2, gy + ROWS * CELL + 2, 0x70000000);

        if (visible.isEmpty()) {
            centered(g, "No items match your search", gx + (COLS * CELL) / 2, gy + 45, 0xFF8A8A8A);
        }

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int index = (scrollRow + r) * COLS + c;
                if (index >= visible.size()) break;

                ItemStack stack = visible.get(index);
                int ix = gx + c * CELL;
                int iy = gy + r * CELL;

                boolean isSelected = !selected.isEmpty() && selected.getItem() == stack.getItem();
                boolean isHovered = mouseX >= ix && mouseX < ix + CELL && mouseY >= iy && mouseY < iy + CELL;

                if (isSelected) {
                    g.fill(ix, iy, ix + CELL, iy + CELL, 0x40FFD21A);
                    g.outline(ix, iy, CELL, CELL, ACCENT);
                }
                if (isHovered) {
                    g.fill(ix, iy, ix + CELL, iy + CELL, 0x40FFFFFF);
                    hovered = stack;
                }
                g.item(stack, ix + 3, iy + 3);
            }
        }

        // scroll bar
        int max = maxScroll();
        if (max > 0) {
            int trackH = ROWS * CELL;
            int thumbH = Math.max(10, trackH * ROWS / ((visible.size() + COLS - 1) / COLS));
            int thumbY = gy + (trackH - thumbH) * scrollRow / max;
            int sx = gx + COLS * CELL + 4;
            g.fill(sx, gy, sx + 3, gy + trackH, 0x40FFFFFF);
            g.fill(sx, thumbY, sx + 3, thumbY + thumbH, 0xFFAAAAAA);
        }

        String hint = status.isEmpty() ? "Click an item, or hold one." : status;
        int color = status.isEmpty() ? 0xFF8A8A8A : 0xFFFF5555;
        text(g, hint, left, top + PANEL_H - 44, color);
    }

    private void drawPriceLabels(GuiGraphicsExtractor g) {
        int cy = top + 36;
        text(g, "Minimum bid", left, cy + 2, 0xFFFFFFFF);
        text(g, "Item count", left + 130, cy + 2, 0xFFFFFFFF);
        text(g, "Quick picks", left, cy + 36, 0xFF8A8A8A);

        if (minBid > 0) {
            text(g, "= " + Format.moneyFull(minBid), left, cy + 74, 0xFF55FF55);
        } else {
            text(g, "Type an amount like 1m, 500k or 2.5b", left, cy + 74, 0xFFFF5555);
        }
        text(g, "Bids below this are ignored.", left, cy + 90, 0xFF8A8A8A);
    }

    private void drawTimeLabels(GuiGraphicsExtractor g) {
        int cy = top + 36;
        text(g, "How long should the auction last?", left, cy + 2, 0xFFFFFFFF);
        text(g, "Posts the auction, bids and winner in public chat.", left, cy + 14 + 104, 0xFF8A8A8A);
    }

    private void drawPreview(GuiGraphicsExtractor g) {
        int px = left + LEFT_W + 10;
        int py = top + 36;
        int pw = 120;
        int ph = PANEL_H - 36 - 30;

        g.fill(px, py, px + pw, py + ph, 0x30FFFFFF);
        text(g, "PREVIEW", px + 6, py + 6, 0xFF8A8A8A);

        // item slot
        g.fill(px + 6, py + 20, px + 42, py + 56, 0x60000000);
        if (selected.isEmpty()) {
            centered(g, "?", px + 24, py + 34, 0xFF8A8A8A);
        } else {
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(px + 8, py + 22);
            pose.scale(2f, 2f);
            g.item(selected, 0, 0);
            pose.popMatrix();
        }

        String title = selected.isEmpty() ? "No item yet" : count + "x " + selected.getHoverName().getString();
        text(g, this.font.plainSubstrByWidth(title, pw - 52), px + 48, py + 34,
                selected.isEmpty() ? 0xFF8A8A8A : 0xFFFFFFFF);

        row(g, px, py + 64, pw, "Min bid", minBid > 0 ? Format.money(minBid) : "?");
        row(g, px, py + 78, pw, "Duration", durationSeconds > 0 ? Format.duration(durationSeconds) : "?");
        row(g, px, py + 92, pw, "Announce", announce ? "On" : "Off");

        String problem = problem();
        if (problem != null) {
            drawWrapped(g, problem, px + 6, py + 112, pw - 12, 0xFFFF5555);
        } else {
            drawWrapped(g, "Ready to go!", px + 6, py + 112, pw - 12, 0xFF55FF55);
        }
    }

    /** Draws text and wraps it onto several lines (word by word) if it is wider than maxWidth. */
    private void drawWrapped(GuiGraphicsExtractor g, String message, int x, int y, int maxWidth, int color) {
        StringBuilder line = new StringBuilder();
        for (String word : message.split(" ")) {
            String attempt = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(attempt) > maxWidth && line.length() > 0) {
                text(g, line.toString(), x, y, color);
                y += 10;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(attempt);
            }
        }
        if (line.length() > 0) text(g, line.toString(), x, y, color);
    }

    private void row(GuiGraphicsExtractor g, int px, int y, int pw, String label, String value) {
        text(g, label, px + 6, y, 0xFF8A8A8A);
        text(g, value, px + pw - 6 - this.font.width(value), y, 0xFFFFFFFF);
    }
}
