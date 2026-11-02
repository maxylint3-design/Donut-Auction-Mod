package com.example.donutauction;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The "brain" of the mod. It:
 *  - starts / cancels auctions
 *  - reads chat for payments ("fartiq paid you $5M")
 *  - decides who the highest bidder is
 *  - ends the auction when the time runs out
 */
public final class AuctionManager {
    private AuctionManager() {}

    /**
     * ====== THE PAYMENT MESSAGE PATTERN ======
     * Donut SMP tells you "Player paid you $5M" (or $5K, $1.5B, $1,000 ...).
     * If the server ever changes the wording, this is the ONLY line you need to edit.
     * Group 1 = player name, group 2 = number, group 3 = K/M/B/T letter.
     *
     * It must match the WHOLE line, which stops other players faking a payment by typing
     * "fartiq paid you $5m" in chat (their chat line would start with their own name/prefix).
     */
    private static final Pattern PAID_YOU = Pattern.compile(
            "^\\W*([A-Za-z0-9_.]{3,17}) paid you \\$([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbBtT]?)\\W*$");

    /** How long a finished auction stays on screen before it disappears. */
    private static final long KEEP_AFTER_END_MS = 60_000;

    private static Auction current;
    private static boolean debug = false;

    public static Auction get() {
        return current;
    }

    public static void setDebug(boolean on) {
        debug = on;
    }

    public static boolean isDebug() {
        return debug;
    }

    // ------------------------------------------------------------------ start / cancel

    public static void start(ItemStack stack, int count, long minBid, long durationSeconds, boolean announce) {
        Minecraft mc = Minecraft.getInstance();
        current = new Auction(stack, count, minBid, durationSeconds, announce);

        info("Auction started: " + current.title() + " | min bid " + Format.money(minBid)
                + " | " + Format.duration(durationSeconds));

        if (announce && mc.player != null) {
            String me = mc.getUser().getName();
            say("AUCTION: " + current.title() + " | Min bid " + Format.money(minBid)
                    + " | " + Format.duration(durationSeconds) + " | /pay " + me + " <amount> to bid");
        }
    }

    public static void cancel() {
        if (current == null) {
            info("There is no auction running.");
            return;
        }
        current = null;
        info("Auction cancelled.");
    }

    // ------------------------------------------------------------------ every tick

    /** Called 20 times a second from AuctionMod. */
    public static void tick() {
        Auction a = current;
        if (a == null) return;

        long now = System.currentTimeMillis();

        if (!a.finished && now >= a.endTimeMs) {
            a.finished = true;
            a.finishedAtMs = now;
            if (a.highestBidder == null) {
                info("Auction over: " + a.title() + " got no bids.");
                if (a.announce) say("AUCTION ENDED: " + a.title() + " got no bids.");
            } else {
                info("Auction over! " + a.highestBidder + " won " + a.title() + " for " + Format.money(a.highestBid));
                if (a.announce) {
                    say("SOLD! " + a.highestBidder + " won " + a.title() + " for " + Format.money(a.highestBid));
                }
            }
        }

        // Remove the overlay a little while after the auction finished
        if (a.finished && now - a.finishedAtMs > KEEP_AFTER_END_MS) {
            current = null;
        }
    }

    // ------------------------------------------------------------------ chat

    /** Called for every system chat line the server sends us. */
    public static void onChatLine(String line) {
        if (line == null) return;

        Matcher m = PAID_YOU.matcher(line.trim());
        if (m.matches()) {
            long amount = Format.parseMoney(m.group(2) + m.group(3));
            if (amount > 0) {
                handlePayment(m.group(1), amount);
            }
        } else if (debug && line.toLowerCase().contains("paid")) {
            info("[debug] Saw a line with 'paid' but it did not match the pattern: " + line);
        }
    }

    /** Someone paid us. Is it a valid bid? */
    public static void handlePayment(String player, long amount) {
        Auction a = current;
        if (a == null || a.finished) return;

        if (amount < a.minBid) {
            info(player + " paid " + Format.money(amount) + " (below the minimum of " + Format.money(a.minBid) + ", ignored)");
            return;
        }

        // Must beat the current highest bid. The very first valid bid counts even if it equals the minimum.
        if (a.highestBidder == null || amount > a.highestBid) {
            a.highestBid = amount;
            a.highestBidder = player;
            info("New highest bid: " + Format.money(amount) + " by " + player);
            if (a.announce) {
                say("NEW BID: " + Format.money(amount) + " by " + player + " on " + a.title());
            }
        } else {
            info(player + " paid " + Format.money(amount) + " but the highest bid is " + Format.money(a.highestBid));
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Shows a private message in YOUR chat only. */
    public static void info(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Auction] " + message).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Sends a PUBLIC chat message as you. */
    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendChat(message);
        }
    }
}
