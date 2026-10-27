package dev.lunoralabs.pulso.starter;

import java.time.Duration;

/**
 * A monitor to create in Pulso, derived from a {@code @Monitored @Scheduled} method. Exactly one of
 * {@code cron} and {@code period} is set.
 */
public record MonitorRegistration(String slug, String name, String cron, String zone, Duration period, Duration grace) {

    String toJson() {
        StringBuilder json = new StringBuilder("{");
        field(json, "slug", slug);
        field(json, "name", name);
        if (cron != null) {
            field(json, "scheduleType", "CRON");
            field(json, "cron", cron);
            if (zone != null) {
                field(json, "timezone", zone);
            }
        } else {
            field(json, "scheduleType", "PERIOD");
            json.append("\"periodSeconds\":").append(Math.max(1, period.toSeconds())).append(',');
        }
        if (grace != null) {
            json.append("\"graceSeconds\":").append(grace.toSeconds()).append(',');
        }
        json.setLength(json.length() - 1);
        return json.append('}').toString();
    }

    private static void field(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":\"").append(escape(value)).append("\",");
    }

    // Minimal JSON string escaping: the starter avoids requiring Jackson in the application
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
