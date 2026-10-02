package com.yagay.ypower.diag;

import java.util.Locale;

/** Locale-neutral rule model. Localized copy is supplied by Android resources. */
public final class DetectionRuleDefinition {
    public final String id;
    public final String category;
    public final String title;
    public final String whyDetected;
    public final String projectExplanation;
    public final String remediation;
    public final String reference;

    public DetectionRuleDefinition(
            String id,
            String category,
            String localizedTitle,
            String localizedWhyDetected,
            String localizedProjectExplanation,
            String localizedRemediation,
            String reference
    ) {
        this.id = id;
        this.category = category;
        this.title = valueOr(localizedTitle, englishTitle(id, category));
        this.whyDetected = valueOr(localizedWhyDetected,
                "The target app performed this " + englishCategory(category)
                        + " check during the diagnostic session. Review the recorded input, result, and call stack to determine what the app was testing.");
        this.projectExplanation = valueOr(localizedProjectExplanation,
                "This rule is treated as one diagnostic signal. A check by itself is not considered proof unless the recorded result actually matches the rule and the event is supported by the runtime timeline.");
        this.remediation = valueOr(localizedRemediation,
                "Review the concrete input, result, call stack, and timing around the exit or failure. Reproduce the same action before treating this signal as a primary cause.");
        this.reference = reference;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String englishTitle(String id, String category) {
        String readable = id == null || id.isBlank() ? "Unknown check" : id
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ');
        StringBuilder title = new StringBuilder();
        boolean upperNext = true;
        for (int i = 0; i < readable.length(); i++) {
            char c = readable.charAt(i);
            if (upperNext && Character.isLetter(c)) {
                title.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                title.append(c);
                if (c == ' ') upperNext = true;
            }
        }
        return title + " " + englishCategory(category) + " check";
    }

    private static String englishCategory(String category) {
        if (category == null || category.isBlank()) return "environment";
        return category.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
