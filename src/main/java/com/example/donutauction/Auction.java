package com.example.donutauction;

import net.minecraft.world.item.ItemStack;

/** Holds everything about ONE running (or just-finished) auction. */
public final class Auction {
    /** One copy of the item, used for the icon and name. */
    public final ItemStack stack;
    /** How many of the item are being sold ("1x Elytra"). */
    public final int count;
    /** Payments below this are ignored. */
    public final long minBid;
    /** When the auction ends (System.currentTimeMillis style). */
    public final long endTimeMs;
    /** Should we post the auction / winner in public chat? */
    public final boolean announce;

    public long highestBid = 0;
    public String highestBidder = null;
    public boolean finished = false;
    public long finishedAtMs = 0;

    public Auction(ItemStack stack, int count, long minBid, long durationSeconds, boolean announce) {
        this.stack = stack.copyWithCount(1);
        this.count = count;
        this.minBid = minBid;
        this.endTimeMs = System.currentTimeMillis() + durationSeconds * 1000L;
        this.announce = announce;
    }

    public long millisLeft() {
        return Math.max(0, endTimeMs - System.currentTimeMillis());
    }

    /** "1x Elytra" */
    public String title() {
        return count + "x " + stack.getHoverName().getString();
    }
}
