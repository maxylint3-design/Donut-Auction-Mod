package com.example.donutauction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * Draws the little auction box in the top-left corner of the screen:
 *
 *   [icon]  1x Elytra
 *           Highest: $5m by fartiq
 *           0:00 left
 */
public final class AuctionHud {
    private AuctionHud() {}

    private static final int WHITE = 0xFFFFFFFF;
    private static final int CYAN = 0xFF55FFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int BACKGROUND = 0xB0101014;

    public static void render(GuiGraphicsExtractor graphics) {
        Auction a = AuctionManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (a == null || mc.player == null) return;

        Font font = mc.font;

        String line1 = a.title();
        String line2 = (a.highestBidder == null)
                ? "No bids yet (min " + Format.money(a.minBid) + ")"
                : "Highest: " + Format.money(a.highestBid) + " by " + a.highestBidder;
        String line3 = Format.clock(a.millisLeft()) + " left";

        int textWidth = Math.max(font.width(line1), Math.max(font.width(line2), font.width(line3)));

        int x = 6;
        int y = 6;
        int iconSize = 32;
        int boxW = 4 + iconSize + 8 + textWidth + 8;
        int boxH = 44;

        // background + cyan bar on the left edge
        graphics.fill(x, y, x + boxW, y + boxH, BACKGROUND);
        graphics.fill(x, y, x + 2, y + boxH, CYAN);

        // item icon, drawn at 2x size
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + 6, y + (boxH - iconSize) / 2f);
        pose.scale(2f, 2f);
        graphics.item(a.stack, 0, 0);
        pose.popMatrix();

        int tx = x + 4 + iconSize + 8;
        graphics.text(font, line1, tx, y + 6, WHITE, true);
        graphics.text(font, line2, tx, y + 18, CYAN, true);
        graphics.text(font, line3, tx, y + 30, GRAY, true);
    }
}
