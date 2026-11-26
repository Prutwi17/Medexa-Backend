package com.medexa.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class SlugUtils {

    private SlugUtils() {
    }

    public static String toSlug(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? fallback : normalized;
    }

    public static String toUniqueSlug(String raw, String fallback, Predicate<String> existsCheck) {
        String base = toSlug(raw, fallback);
        String candidate = base;
        int suffix = 1;
        while (existsCheck.test(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
