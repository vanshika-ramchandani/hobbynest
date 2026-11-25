package com.hobbynest.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 style CSV encoding. Fields with commas, quotes or newlines are quoted,
 * so addresses like "B-12 Sunrise Hostel, Navrangpura" survive a save and load.
 */
public final class CsvCodec {

    private CsvCodec() {
    }

    public static String encode(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String f = fields.get(i) == null ? "" : fields.get(i);
            if (f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r")) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.toString();
    }

    /** Splits a CSV text into records, honouring quoted fields that span lines. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                any = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (any || field.length() > 0) {
                    row.add(field.toString());
                    rows.add(row);
                }
                row = new ArrayList<>();
                field.setLength(0);
                any = false;
            } else {
                field.append(c);
                any = true;
            }
        }
        if (any || field.length() > 0) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
