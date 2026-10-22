package com.uabea.mobile;

import java.util.Locale;

/** Utilitas kecil untuk tampilan. */
public final class Util {
    private Util() {
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "asset";
        }
        String s = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        if (s.isEmpty()) {
            s = "asset";
        }
        if (s.length() > 80) {
            s = s.substring(0, 80);
        }
        return s;
    }

    public static String errorMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isEmpty()) {
            m = t.getClass().getSimpleName();
        }
        return m;
    }
}
