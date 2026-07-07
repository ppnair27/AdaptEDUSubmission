package procrastination_alg;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupabaseService {

    private static final TypeReference<List<Map<String, Object>>> ROW_LIST = new TypeReference<>() {
    };

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${SUPABASE_URL}")
    private String supabaseUrl;

    @Value("${SUPABASE_SERVICE_ROLE_KEY}")
    private String serviceRoleKey;

    // You actually don't need the anonKey in the backend if you are using the service role!
    // But if you want to keep it:
    @Value("${SUPABASE_ANON_KEY:}")
    private String anonKey;

    @Value("${supabase.tasks-table:tasks}")
    private String tasksTable;

    @Value("${supabase.events-table:events}")
    private String eventsTable;

    public void saveState(List<Map<String, Object>> tasks, List<Map<String, Object>> events) throws IOException, InterruptedException {
        replaceTable(tasksTable, sanitizeTasks(tasks));
        replaceTable(eventsTable, sanitizeEvents(events));
    }

    public Path exportTasksCsv() throws IOException, InterruptedException {
        List<Map<String, Object>> rows = fetchRows(tasksTable);
        Path tempFile = Files.createTempFile("adaptedu-tasks-", ".csv");
        StringBuilder out = new StringBuilder();
        out.append("name,category,dueDate,userPriority,estimatedTime,completed,maxSessionLength,description\n");
        for (Map<String, Object> row : rows) {
            out.append(csv(stringValue(row, "name"))).append(',')
                    .append(csv(stringValue(row, "category"))).append(',')
                    .append(csv(csvTimestamp(stringValue(row, "due_date", "dueDate")))).append(',')
                    .append(intValue(row, 5, "user_priority", "userPriority")).append(',')
                    .append(intValue(row, 60, "estimated_time", "estimatedTime")).append(',')
                    .append(booleanValue(row, false, "completed")).append(',')
                    .append(intValue(row, 120, "max_session_length", "maxSessionLength")).append(',')
                    .append(csv(stringValue(row, "description"))).append('\n');
        }
        Files.writeString(tempFile, out.toString(), StandardCharsets.UTF_8);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    public Path exportEventsCsv() throws IOException, InterruptedException {
        List<Map<String, Object>> rows = fetchRows(eventsTable);
        Path tempFile = Files.createTempFile("adaptedu-events-", ".csv");
        StringBuilder out = new StringBuilder();
        out.append("name,startTime,endTime,duration,location,travelTime,status,category,description\n");
        for (Map<String, Object> row : rows) {
            String startTime = csvTimestamp(stringValue(row, "start_time", "startTime"));
            String endTime = csvTimestamp(stringValue(row, "end_time", "endTime"));
            out.append(csv(stringValue(row, "name"))).append(',')
                    .append(startTime).append(',')
                    .append(endTime).append(',')
                    .append(intValue(row, calculateDurationMinutes(startTime, endTime, 60), "duration")).append(',')
                    .append(csv(stringValue(row, "location"))).append(',')
                    .append(intValue(row, 0, "travel_time", "travelTime")).append(',')
                    .append(stringValue(row, "status", "FIXED_EVENT")).append(',')
                    .append(csv(stringValue(row, "category"))).append(',')
                    .append(csv(stringValue(row, "description"))).append('\n');
        }
        Files.writeString(tempFile, out.toString(), StandardCharsets.UTF_8);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    private void replaceTable(String table, List<Map<String, Object>> rows) throws IOException, InterruptedException {
        ensureConfigured();
        deleteAllRows(table);
        if (rows.isEmpty()) {
            return;
        }

        HttpRequest request = baseRequest(table)
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(rows)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Supabase insert failed for table '" + table + "' (" + response.statusCode() + "): " + response.body());
        }
    }

    private void deleteAllRows(String table) throws IOException, InterruptedException {
        URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/rest/v1/" + table + "?id=not.is.null");
        String key = apiKey();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("apikey", key)
                .header("Authorization", "Bearer " + key)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Supabase delete failed for table '" + table + "' (" + response.statusCode() + "): " + response.body());
        }
    }

    private List<Map<String, Object>> fetchRows(String table) throws IOException, InterruptedException {
        ensureConfigured();
        HttpRequest request = baseRequest(table)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException("Supabase fetch failed for table '" + table + "' (" + response.statusCode() + "): " + response.body());
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(body, ROW_LIST);
    }

    private HttpRequest.Builder baseRequest(String table) {
        String key = apiKey();
        URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/rest/v1/" + table + "?select=*");
        return HttpRequest.newBuilder(uri)
                .header("apikey", key)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private void ensureConfigured() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new IllegalStateException("SUPABASE_URL is not configured.");
        }
        if (apiKey().isBlank()) {
            throw new IllegalStateException("SUPABASE_SERVICE_ROLE_KEY or SUPABASE_ANON_KEY is not configured.");
        }
    }

    private String apiKey() {
        if (serviceRoleKey != null && !serviceRoleKey.isBlank()) {
            return serviceRoleKey;
        }
        return anonKey == null ? "" : anonKey;
    }

    // --- ADD THIS HELPER METHOD ---
    private String formatTimestamp(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return null;
        String fixed = rawDate.trim();
        
        // If it's missing seconds (e.g., 2026-07-06T08:00) -> 16 chars
        if (fixed.length() == 16) {
            fixed += ":00";
        }
        
        // If it already has seconds but is missing the timezone Z -> 19 chars
        if (fixed.length() == 19 && !fixed.endsWith("Z")) {
            fixed += "Z";
        }
        
        // Final fallback safety check
        if (!fixed.endsWith("Z")) {
            fixed += "Z";
        }
        return fixed;
    }

    private List<Map<String, Object>> sanitizeTasks(List<Map<String, Object>> tasks) {
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> task : tasks) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", stringValue(task, "name"));
            row.put("category", stringValue(task, "category"));
            // FIX: Format the due_date properly for Supabase timestamptz before uploading
            row.put("due_date", formatTimestamp(stringValue(task, "dueDate", "due_date")));
            row.put("user_priority", intValue(task, 5, "userPriority", "user_priority"));
            row.put("estimated_time", intValue(task, 60, "estimatedTime", "estimated_time"));
            row.put("completed", booleanValue(task, false, "completed"));
            row.put("max_session_length", intValue(task, 120, "maxSessionLength", "max_session_length"));
            row.put("description", stringValue(task, "description"));
            row.put("minutes_spent", intValue(task, 0, "minutesSpent", "minutes_spent"));
            row.put("archived", booleanValue(task, false, "archived"));
            row.put("archived_at", longValue(task, null, "archivedAt", "archived_at"));
            sanitized.add(row);
        }
        return sanitized;
    }

    private List<Map<String, Object>> sanitizeEvents(List<Map<String, Object>> events) {
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> event : events) {
            String startTime = stringValue(event, "startTime", "start_time");
            String endTime = stringValue(event, "endTime", "end_time");
            Map<String, Object> row = new HashMap<>();
            row.put("name", stringValue(event, "name"));
            // FIX: Format the start/end times properly for Supabase timestamptz before uploading
            row.put("start_time", formatTimestamp(startTime));
            row.put("end_time", formatTimestamp(endTime));
            row.put("duration", intValue(event, calculateDurationMinutes(startTime, endTime, 60), "duration"));
            row.put("location", stringValue(event, "location"));
            row.put("travel_time", intValue(event, 0, "travelTime", "travel_time"));
            row.put("status", stringValue(event, "status", "FIXED_EVENT"));
            row.put("category", stringValue(event, "category"));
            row.put("description", stringValue(event, "description"));
            row.put("reminder_enabled", booleanValue(event, false, "reminderEnabled", "reminder_enabled"));
            row.put("reminder_every_days", intValue(event, 1, "reminderEveryDays", "reminder_every_days"));
            row.put("archived", booleanValue(event, false, "archived"));
            row.put("archived_at", longValue(event, null, "archivedAt", "archived_at"));
            sanitized.add(row);
        }
        return sanitized;
    }

    private int calculateDurationMinutes(String startTime, String endTime, int fallback) {
        if (startTime == null || startTime.isBlank() || endTime == null || endTime.isBlank()) {
            return fallback;
        }
        try {
            return (int) java.time.Duration.between(java.time.LocalDateTime.parse(startTime), java.time.LocalDateTime.parse(endTime)).toMinutes();
        } catch (Exception e) {
            return fallback;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String stringValue(Object value, String fallback) {
        String result = stringValue(value);
        return result.isBlank() ? fallback : result;
    }

    private String csvTimestamp(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return "";
        }

        String trimmed = rawDate.trim();
        try {
            Instant instant = Instant.parse(trimmed);
            return instant.atZone(ZoneId.systemDefault()).toLocalDateTime().toString();
        } catch (Exception ignored) {
        }

        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(trimmed);
            return offsetDateTime.toLocalDateTime().toString();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed).toString();
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    private String stringValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                String result = value.toString();
                if (!result.isBlank()) {
                    return result;
                }
            }
        }
        return "";
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int intValue(Map<String, Object> row, int fallback, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            try {
                if (value instanceof Number number) {
                    return number.intValue();
                }
                return Integer.parseInt(value.toString());
            } catch (Exception ignored) {
                // try the next key
            }
        }
        return fallback;
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private boolean booleanValue(Map<String, Object> row, boolean fallback, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
    }

    private Long longValue(Map<String, Object> row, Long fallback, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            try {
                if (value instanceof Number number) {
                    return number.longValue();
                }
                return Long.parseLong(value.toString());
            } catch (Exception ignored) {
                // try the next key
            }
        }
        return fallback;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}