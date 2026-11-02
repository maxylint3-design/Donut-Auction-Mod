package com.example.donutauction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small helper methods for turning money / time into text and back again.
 * Nothing in here touches Minecraft, so it is easy to read.
 */
public final class Format {
    private Format() {}

    // Accepts things like: 1m, $5m, 500k, 2.5b, 1,000,000
    private static final Pattern MONEY = Pattern.compile(
            "^\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?|\\.[0-9]+)\\s*([kmbtKMBT]?)$");

    /** Turns "5m" into 5000000. Returns -1 if the text is not a valid amount. */
    public static long parseMoney(String text) {
        if (text == null) return -1;
        Matcher m = MONEY.matcher(text.trim());
        if (!m.matches()) return -1;
        try {
            BigDecimal number = new BigDecimal(m.group(1).replace(",", ""));
            number = number.multiply(multiplier(m.group(2)));
            return number.setScale(0, RoundingMode.DOWN).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    private static BigDecimal multiplier(String suffix) {
        switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k": return BigDecimal.valueOf(1_000L);
            case "m": return BigDecimal.valueOf(1_000_000L);
            case "b": return BigDecimal.valueOf(1_000_000_000L);
            case "t": return BigDecimal.valueOf(1_000_000_000_000L);
            default:  return BigDecimal.ONE;
        }
    }

    /** 5000000 -> "5m" (no dollar sign). */
    public static String shortNumber(long value) {
        if (value >= 1_000_000_000_000L) return trim(value, 1_000_000_000_000L) + "t";
        if (value >= 1_000_000_000L) return trim(value, 1_000_000_000L) + "b";
        if (value >= 1_000_000L) return trim(value, 1_000_000L) + "m";
        if (value >= 1_000L) return trim(value, 1_000L) + "k";
        return Long.toString(value);
    }

    /** 5000000 -> "$5m". */
    public static String money(long value) {
        return "$" + shortNumber(value);
    }

    /** 5000000 -> "$5,000,000". */
    public static String moneyFull(long value) {
        return String.format(Locale.ROOT, "$%,d", value);
    }

    private static String trim(long value, long divisor) {
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(divisor), 2, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString();
    }

    /** Milliseconds -> "1:05" or "1:02:03" (used for the "time left" line). */
    public static String clock(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000); // round up so 0:00 only appears at the very end
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** Seconds -> "1 min", "2 hours", "1m 30s" (used in menus and announcements). */
    public static String duration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds % 3600 == 0) {
            long h = seconds / 3600;
            return h + (h == 1 ? " hour" : " hours");
        }
        if (seconds % 60 == 0) return (seconds / 60) + " min";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
