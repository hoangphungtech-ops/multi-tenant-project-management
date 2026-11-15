package com.cloudsaas.tv3;

import java.util.List;

/** Builds RFC 4180-style CSV with spreadsheet formula-injection mitigation. */
public final class CsvBuilder {
    private CsvBuilder() {}
    public static String create(List<ExportWorker.Task> tasks) {
        StringBuilder csv = new StringBuilder("task_id,title,status\r\n");
        for (ExportWorker.Task task : tasks) {
            csv.append(cell(task.id().toString())).append(',')
               .append(cell(task.title())).append(',')
               .append(cell(task.state())).append("\r\n");
        }
        return csv.toString();
    }
    private static String cell(String input) {
        String val = input == null ? "" : input;
        String trimmed = val.stripLeading();
        if (!trimmed.isEmpty() && (trimmed.charAt(0) == '=' || trimmed.charAt(0) == '+'
                || trimmed.charAt(0) == '-' || trimmed.charAt(0) == '@')) {
            val = "'" + val;
        }
        if (val.contains(",") || val.contains("\"") || val.contains("\r") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
