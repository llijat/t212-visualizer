package com.llijat.t212visualizer.dto;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record CategoryNames(Map<String, String> names) {
    public Set<String> choices() {
        return names.keySet();
    }

    public String of(String category) {
        return names.getOrDefault(category, fromKey(category));
    }

    public static String fromKey(String category) {
        String words = category.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
