package procrastination_alg;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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


    @Value("${supabase.tasks-table:task}")
    private String tasksTable;

    @Value("${supabase.events-table:event}")
    private String eventsTable;

    @Value("${supabase.users-table:users}")
    private String usersTable;

    private final Map<String, String> resolvedTableCache = new java.util.concurrent.ConcurrentHashMap<>();

    private String getActiveTable(String table) {
        return resolvedTableCache.getOrDefault(table, table);
    }

    private void rememberActiveTable(String original, String resolved) {
        resolvedTableCache.put(original, resolved);
    }

    private String getAltTable(String table) {
        if ("task".equalsIgnoreCase(table)) return "tasks";
        if ("tasks".equalsIgnoreCase(table)) return "task";
        if ("event".equalsIgnoreCase(table)) return "events";
        if ("events".equalsIgnoreCase(table)) return "event";
        if ("users".equalsIgnoreCase(table)) return "user";
        if ("user".equalsIgnoreCase(table)) return "users";
        return null;
    }

    public void saveState(Long userId, List<Map<String, Object>> tasks, List<Map<String, Object>> events) throws IOException, InterruptedException {
        replaceTableForUser(tasksTable, userId, sanitizeTasks(userId, tasks));
        replaceTableForUser(eventsTable, userId, sanitizeEvents(userId, events));
    }

    public void saveState(List<Map<String, Object>> tasks, List<Map<String, Object>> events) throws IOException, InterruptedException {
        saveState(null, tasks, events);
    }

    public Path exportTasksCsv(Long userId) throws IOException, InterruptedException {
        String activeTasksTable = getActiveTable(tasksTable);
        List<Map<String, Object>> rows = userId != null
                ? fetchRowsWithQuery(activeTasksTable, "?user_id=eq." + userId + "&select=*")
                : fetchRows(activeTasksTable);

        Path tempFile = Files.createTempFile("adaptedu-tasks-", ".csv");
        StringBuilder out = new StringBuilder();
        out.append("name,category,dueDate,userPriority,estimatedTime,completed,maxSessionLength,description\n");

        for (Map<String, Object> row : rows) {
            String name = row.get("name") != null ? row.get("name").toString() : "";
            String category = row.get("category") != null ? row.get("category").toString() : "";

            Object rawDue = row.get("due_date") != null ? row.get("due_date") : row.get("dueDate");
            String dueDate = rawDue != null ? rawDue.toString() : "";

            // 👇 Using your robust helper methods exactly as intended 👇
            int priority = intValue(row, 5, "user_priority", "userPriority");
            int estTime = intValue(row, 60, "estimated_time", "estimatedTime");
            boolean completed = booleanValue(row, false, "completed");
            int maxSession = intValue(row, 120, "max_session_length", "maxSessionLength");

            String desc = row.get("description") != null ? row.get("description").toString() : "";

            out.append(csv(name)).append(',')
               .append(csv(category)).append(',')
               .append(csv(csvTimestamp(dueDate))).append(',')
               .append(priority).append(',')
               .append(estTime).append(',')
               .append(completed).append(',')
               .append(maxSession).append(',')
               .append(csv(desc)).append('\n');
        }
        Files.writeString(tempFile, out.toString(), StandardCharsets.UTF_8);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    public Path exportTasksCsv() throws IOException, InterruptedException {
        return exportTasksCsv(null);
    }

    public Path exportEventsCsv(Long userId) throws IOException, InterruptedException {
        String activeEventsTable = getActiveTable(eventsTable);
        List<Map<String, Object>> rows = userId != null
                ? fetchRowsWithQuery(activeEventsTable, "?user_id=eq." + userId + "&select=*")
                : fetchRows(activeEventsTable);
        Path tempFile = Files.createTempFile("adaptedu-events-", ".csv");
        StringBuilder out = new StringBuilder();
        out.append("name,startTime,endTime,duration,location,travelTime,status,category,description\n");

        for (Map<String, Object> row : rows) {
            String name = row.get("name") != null ? row.get("name").toString() : "";

            Object rawStart = row.get("start_time") != null ? row.get("start_time") : row.get("startTime");
            String startTime = csvTimestamp(rawStart != null ? rawStart.toString() : "");

            Object rawEnd = row.get("end_time") != null ? row.get("end_time") : row.get("endTime");
            String endTime = csvTimestamp(rawEnd != null ? rawEnd.toString() : "");

            // 👇 Using your robust helper methods exactly as intended 👇
            int duration = intValue(row, calculateDurationMinutes(startTime, endTime, 60), "duration");
            String location = row.get("location") != null ? row.get("location").toString() : "";
            int travel = intValue(row, 0, "travel_time", "travelTime");

            String status = row.get("status") != null ? row.get("status").toString() : "FIXED_EVENT";
            String category = row.get("category") != null ? row.get("category").toString() : "";
            String desc = row.get("description") != null ? row.get("description").toString() : "";

            out.append(csv(name)).append(',')
               .append(startTime).append(',')
               .append(endTime).append(',')
               .append(duration).append(',')
               .append(csv(location)).append(',')
               .append(travel).append(',')
               .append(status).append(',')
               .append(csv(category)).append(',')
               .append(csv(desc)).append('\n');
        }
        Files.writeString(tempFile, out.toString(), StandardCharsets.UTF_8);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    public Path exportEventsCsv() throws IOException, InterruptedException {
        return exportEventsCsv(null);
    }

    public List<Map<String, Object>> fetchUserTasks(Long userId) throws IOException, InterruptedException {
        if (userId == null) return List.of();
        return fetchRowsWithQuery(tasksTable, "?user_id=eq." + userId + "&select=*");
    }

    public List<Map<String, Object>> fetchUserEvents(Long userId) throws IOException, InterruptedException {
        if (userId == null) return List.of();
        return fetchRowsWithQuery(eventsTable, "?user_id=eq." + userId + "&select=*");
    }

    private void replaceTableForUser(String table, Long userId, List<Map<String, Object>> rows) throws IOException, InterruptedException {
        ensureConfigured();
        String activeTable = getActiveTable(table);
        deleteUserRows(activeTable, userId);
        if (rows.isEmpty()) {
            return;
        }

        HttpRequest request = baseRequest(activeTable)
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(rows)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            String alt = getAltTable(activeTable);
            if (alt != null) {
                System.out.println("Supabase table '" + activeTable + "' returned 404, retrying with fallback '" + alt + "'...");
                deleteUserRows(alt, userId);
                HttpRequest altReq = baseRequest(alt)
                        .header("Prefer", "return=minimal")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(rows)))
                        .build();
                HttpResponse<String> altResp = httpClient.send(altReq, HttpResponse.BodyHandlers.ofString());
                if (altResp.statusCode() < 300) {
                    rememberActiveTable(table, alt);
                    return;
                }
            }
        }
        if (response.statusCode() >= 300) {
            throw new IOException("Supabase insert failed for table '" + activeTable + "' (" + response.statusCode() + "): " + response.body());
        }
    }

    private void deleteUserRows(String table, Long userId) throws IOException, InterruptedException {
        String activeTable = getActiveTable(table);
        String filter = (userId != null) ? "?user_id=eq." + userId : "?id=not.is.null";
        URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/rest/v1/" + activeTable + filter);
        String key = apiKey();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("apikey", key)
                .header("Authorization", "Bearer " + key)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            String alt = getAltTable(activeTable);
            if (alt != null) {
                URI altUri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/rest/v1/" + alt + filter);
                HttpRequest altReq = HttpRequest.newBuilder(altUri)
                        .header("apikey", key)
                        .header("Authorization", "Bearer " + key)
                        .DELETE()
                        .build();
                HttpResponse<String> altResp = httpClient.send(altReq, HttpResponse.BodyHandlers.ofString());
                if (altResp.statusCode() < 300) {
                    rememberActiveTable(table, alt);
                    return;
                }
            }
        }
        if (response.statusCode() >= 300) {
            throw new IOException("Supabase delete failed for table '" + activeTable + "' (" + response.statusCode() + "): " + response.body());
        }
    }

    private List<Map<String, Object>> fetchRows(String table) throws IOException, InterruptedException {
        return fetchRowsWithQuery(table, "?select=*");
    }

    public List<Map<String, Object>> fetchRowsWithQuery(String table, String queryString) throws IOException, InterruptedException {
        ensureConfigured();
        String activeTable = getActiveTable(table);
        String key = apiKey();
        String query = queryString.startsWith("?") ? queryString : "?" + queryString;
        URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/rest/v1/" + activeTable + query);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("apikey", key)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            String alt = getAltTable(activeTable);
            if (alt != null) {
                URI altUri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/rest/v1/" + alt + query);
                HttpRequest altReq = HttpRequest.newBuilder(altUri)
                        .header("apikey", key)
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> altResp = httpClient.send(altReq, HttpResponse.BodyHandlers.ofString());
                if (altResp.statusCode() < 300) {
                    rememberActiveTable(table, alt);
                    String body = altResp.body();
                    if (body == null || body.isBlank()) {
                        return List.of();
                    }
                    return objectMapper.readValue(body, ROW_LIST);
                }
            }
        }
        if (response.statusCode() >= 300) {
            throw new IOException("Supabase fetch failed for table '" + activeTable + "' (" + response.statusCode() + "): " + response.body());
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(body, ROW_LIST);
    }

    private HttpRequest.Builder baseRequest(String table) {
        String key = apiKey();
        URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/rest/v1/" + getActiveTable(table) + "?select=*");
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

    // ── Supabase Auth & Users Table Methods ────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> signUpAuth(String email, String password, String username) {
        try {
            ensureConfigured();
            String key = apiKey();
            URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/auth/v1/signup");

            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("email", email);
            bodyMap.put("password", password);
            if (username != null && !username.isBlank()) {
                bodyMap.put("data", Map.of("username", username));
            }
            String jsonBody = objectMapper.writeValueAsString(bodyMap);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("apikey", key)
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() >= 400) {
                String errorMsg = "Registration failed.";
                try {
                    Map<String, Object> err = objectMapper.readValue(responseBody, new TypeReference<>() {});
                    if (err.containsKey("msg")) errorMsg = err.get("msg").toString();
                    else if (err.containsKey("error_description")) errorMsg = err.get("error_description").toString();
                    else if (err.containsKey("message")) errorMsg = err.get("message").toString();
                } catch (Exception ignored) {}

                if (errorMsg.toLowerCase().contains("already") || errorMsg.toLowerCase().contains("registered")) {
                    return Map.of("status", "error", "message", "An account with this email already exists. Please sign in.");
                }
                return Map.of("status", "error", "message", errorMsg);
            }

            // Ensure the user also exists in the public.users table
            Map<String, Object> publicUser = findOrCreateUser(email, password, username);
            Long userId = publicUser != null && publicUser.get("user_id") != null
                    ? Long.valueOf(publicUser.get("user_id").toString()) : null;

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("message", "Verification email sent! Please check your inbox and confirm your email to sign in.");
            result.put("needsVerification", true);
            result.put("email", email);
            result.put("username", username != null ? username : email.split("@")[0]);
            result.put("userId", userId != null ? userId : -1);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "error", "message", "Server error during registration: " + e.getMessage());
        }
    }

    public Map<String, Object> signUpAuth(String email, String password) {
        return signUpAuth(email, password, email.split("@")[0]);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loginAuth(String email, String password) {
        try {
            ensureConfigured();
            String key = apiKey();
            URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/auth/v1/token?grant_type=password");

            Map<String, String> bodyMap = Map.of("email", email, "password", password);
            String jsonBody = objectMapper.writeValueAsString(bodyMap);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("apikey", key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() >= 400) {
                String errorMsg = "Invalid email or password.";
                try {
                    Map<String, Object> err = objectMapper.readValue(responseBody, new TypeReference<>() {});
                    if (err.containsKey("error_description")) errorMsg = err.get("error_description").toString();
                    else if (err.containsKey("msg")) errorMsg = err.get("msg").toString();
                    else if (err.containsKey("message")) errorMsg = err.get("message").toString();
                } catch (Exception ignored) {}

                if (errorMsg.toLowerCase().contains("email not confirmed")) {
                    return Map.of(
                            "status", "error",
                            "error", "email_not_confirmed",
                            "message", "Please confirm your email address before signing in. Check your inbox for the confirmation link."
                    );
                }

                // Fallback check: check if user exists in public.users with matching password
                Map<String, Object> publicUser = findUserByEmail(email);
                if (publicUser != null) {
                    String storedPassword = stringValue(publicUser.get("password"));
                    if (password.equals(storedPassword)) {
                        Long userId = Long.valueOf(publicUser.get("user_id").toString());
                        String username = publicUser.get("username") != null ? stringValue(publicUser.get("username")) : email.split("@")[0];
                        return Map.of("status", "ok", "userId", userId, "email", email, "username", username);
                    }
                }

                return Map.of("status", "error", "message", errorMsg);
            }

            // Successfully authenticated via Supabase Auth!
            Map<String, Object> publicUser = findOrCreateUser(email, password);
            if (publicUser != null && publicUser.get("user_id") != null) {
                Long userId = Long.valueOf(publicUser.get("user_id").toString());
                String username = email.split("@")[0];
                if (publicUser.get("username") != null && !publicUser.get("username").toString().isBlank()) {
                    username = publicUser.get("username").toString();
                }
                try {
                    Map<String, Object> resMap = objectMapper.readValue(responseBody, new TypeReference<>() {});
                    if (resMap.get("user") instanceof Map<?, ?> userMeta) {
                        if (userMeta.get("user_metadata") instanceof Map<?, ?> meta && meta.get("username") != null) {
                            username = meta.get("username").toString();
                        }
                    }
                } catch (Exception ignored) {}

                Map<String, Object> result = new HashMap<>();
                result.put("status", "ok");
                result.put("userId", userId);
                result.put("email", email);
                result.put("username", username);
                return result;
            }

            return Map.of("status", "error", "message", "User verified, but could not retrieve user ID from database.");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "error", "message", "Server error during login: " + e.getMessage());
        }
    }

    public Map<String, Object> resendVerification(String email) {
        try {
            ensureConfigured();
            String key = apiKey();
            URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/auth/v1/resend");

            Map<String, String> bodyMap = Map.of("type", "signup", "email", email);
            String jsonBody = objectMapper.writeValueAsString(bodyMap);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("apikey", key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Map.of("status", "error", "message", "Failed to resend confirmation email: " + response.body());
            }

            return Map.of("status", "ok", "message", "Confirmation email resent! Please check your inbox.");
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "error", "message", "Server error resending email: " + e.getMessage());
        }
    }

    public Map<String, Object> findUserByEmail(String email) {
        try {
            List<Map<String, Object>> rows = fetchRowsWithQuery(usersTable, "?email=eq." + email + "&select=*");
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Map<String, Object> findOrCreateUser(String email, String password, String username) {
        try {
            Map<String, Object> existing = findUserByEmail(email);
            if (existing != null) {
                return existing;
            }

            // Insert new user into public.users
            Map<String, Object> newUser = new HashMap<>();
            newUser.put("email", email);
            newUser.put("password", password);
            newUser.put("created_at", Instant.now().toString());
            if (username != null && !username.isBlank()) {
                newUser.put("username", username);
            }

            HttpRequest request = baseRequest(usersTable)
                    .header("Prefer", "return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(List.of(newUser))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 300) {
                List<Map<String, Object>> created = objectMapper.readValue(response.body(), ROW_LIST);
                if (!created.isEmpty()) {
                    return created.get(0);
                }
            } else {
                // If table doesn't have 'username' column, fallback without username
                newUser.remove("username");
                HttpRequest fallbackReq = baseRequest(usersTable)
                        .header("Prefer", "return=representation")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(List.of(newUser))))
                        .build();
                HttpResponse<String> fallbackResp = httpClient.send(fallbackReq, HttpResponse.BodyHandlers.ofString());
                if (fallbackResp.statusCode() < 300) {
                    List<Map<String, Object>> created = objectMapper.readValue(fallbackResp.body(), ROW_LIST);
                    if (!created.isEmpty()) {
                        return created.get(0);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed in findOrCreateUser: " + e.getMessage());
        }
        return findUserByEmail(email);
    }

    public Map<String, Object> findOrCreateUser(String email, String password) {
        return findOrCreateUser(email, password, null);
    }

    public String getGoogleOAuthUrl(String redirectTo) {
        ensureConfigured();
        String base = supabaseUrl.replaceAll("/+$", "") + "/auth/v1/authorize?provider=google";
        if (redirectTo != null && !redirectTo.isBlank()) {
            base += "&redirect_to=" + URLEncoder.encode(redirectTo, StandardCharsets.UTF_8);
        }
        return base;
    }

    public Map<String, Object> processOAuthUser(String accessToken) {
        try {
            ensureConfigured();
            URI uri = URI.create(supabaseUrl.replaceAll("/+$", "") + "/auth/v1/user");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("apikey", apiKey())
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Map.of("status", "error", "message", "Invalid OAuth token: " + response.body());
            }

            Map<String, Object> userObj = objectMapper.readValue(response.body(), new TypeReference<>() {});
            String email = stringValue(userObj.get("email"));
            if (email.isBlank()) {
                return Map.of("status", "error", "message", "No email found in OAuth profile.");
            }

            String username = email.split("@")[0];
            if (userObj.get("user_metadata") instanceof Map<?, ?> meta) {
                if (meta.get("username") != null && !meta.get("username").toString().isBlank()) {
                    username = meta.get("username").toString();
                } else if (meta.get("full_name") != null && !meta.get("full_name").toString().isBlank()) {
                    username = meta.get("full_name").toString();
                } else if (meta.get("name") != null && !meta.get("name").toString().isBlank()) {
                    username = meta.get("name").toString();
                }
            }

            // Ensure row in public.users
            Map<String, Object> publicUser = findOrCreateUser(email, "oauth-google", username);
            Long userId = publicUser != null && publicUser.get("user_id") != null
                    ? Long.valueOf(publicUser.get("user_id").toString()) : -1L;

            Map<String, Object> res = new HashMap<>();
            res.put("status", "ok");
            res.put("userId", userId);
            res.put("email", email);
            res.put("username", username);
            res.put("accessToken", accessToken);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("status", "error", "message", "OAuth processing failed: " + e.getMessage());
        }
    }

    private String formatTimestamp(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return null;
        String trimmed = rawDate.trim();
        try {
            return Instant.parse(trimmed).toString();
        } catch (Exception ignored) {}
        try {
            return OffsetDateTime.parse(trimmed).toInstant().toString();
        } catch (Exception ignored) {}
        try {
            if (trimmed.length() == 16) {
                trimmed += ":00";
            }
            return LocalDateTime.parse(trimmed).atZone(ZoneId.systemDefault()).toInstant().toString();
        } catch (Exception ignored) {}
        if (trimmed.endsWith("Z")) return trimmed;
        if (trimmed.length() == 16) trimmed += ":00";
        if (trimmed.length() == 19) return trimmed + "Z";
        return trimmed;
    }

    public static String cleanValue(Object value) {
        if (value == null) return "";
        if (value instanceof Map<?, ?> map) {
            Object nameObj = map.get("name");
            if (nameObj != null) return cleanValue(nameObj);
            Object titleObj = map.get("title");
            if (titleObj != null) return cleanValue(titleObj);
            Object idObj = map.get("id");
            if (idObj != null) return cleanValue(idObj);
        }
        String str = value.toString().trim();
        int iterations = 0;
        while (str.startsWith("{") && iterations < 10) {
            iterations++;
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("name=([^,{}]+)").matcher(str);
            if (matcher.find()) {
                str = matcher.group(1).trim();
            } else {
                java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("id=([^,{}]+)").matcher(str);
                if (idMatcher.find()) {
                    str = idMatcher.group(1).trim();
                } else {
                    str = str.replaceAll("^[{}]+|[{}]+$", "").trim();
                    break;
                }
            }
        }
        return str;
    }

    public static String cleanId(Object value, String prefix) {
        if (value == null) return prefix + "_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        if (value instanceof Map<?, ?> map && map.containsKey("id")) {
            return cleanId(map.get("id"), prefix);
        }
        String str = value.toString().trim();
        int iterations = 0;
        while (str.startsWith("{") && iterations < 10) {
            iterations++;
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("id=([^,{}]+)").matcher(str);
            if (matcher.find()) {
                str = matcher.group(1).trim();
            } else {
                break;
            }
        }
        if (str.isBlank() || str.startsWith("{")) {
            return prefix + "_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        }
        return str;
    }


    private List<Map<String, Object>> sanitizeTasks(Long userId, List<Map<String, Object>> tasks) {
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> task : tasks) {
            Map<String, Object> row = new HashMap<>();
            if (userId != null) {
                row.put("user_id", userId);
            }
            String idVal = cleanId(task.get("id"), "task");
            row.put("id", idVal);
            row.put("name", stringValue(task, "name"));
            row.put("category", stringValue(task, "category"));
            // FIX: Format the due_date properly for Supabase timestamptz before uploading

            row.put("due_date", formatTimestamp(stringValue(task, "dueDate", "due_date")));
            row.put("user_priority", intValue(task, 5, "userPriority", "user_priority"));
            row.put("estimated_time", intValue(task, 60, "estimatedTime", "estimated_time"));
            row.put("completed", booleanValue(task, false, "completed"));
            row.put("max_session_length", intValue(task, 120, "maxSessionLength", "max_session_length"));
            row.put("description", task.get("description") != null ? task.get("description").toString() : "");
            row.put("minutes_spent", intValue(task, 0, "minutesSpent", "minutes_spent"));
            row.put("archived", booleanValue(task, false, "archived"));
            row.put("archived_at", longValue(task, null, "archivedAt", "archived_at"));

            sanitized.add(row);
        }
        return sanitized;
    }

    private List<Map<String, Object>> sanitizeTasks(List<Map<String, Object>> tasks) {
        return sanitizeTasks(null, tasks);
    }

    private List<Map<String, Object>> sanitizeEvents(Long userId, List<Map<String, Object>> events) {
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> event : events) {
            String startTime = stringValue(event, "startTime", "start_time");
            String endTime = stringValue(event, "endTime", "end_time");
            Map<String, Object> row = new HashMap<>();

            if (userId != null) {
                row.put("user_id", userId);
            }
            String idVal = cleanId(event.get("id"), "event");
            row.put("id", idVal);
            row.put("name", stringValue(event, "name"));
            // FIX: Format the start/end times properly for Supabase timestamptz before uploading

            row.put("start_time", formatTimestamp(startTime));
            row.put("end_time", formatTimestamp(endTime));
            row.put("duration", intValue(event, calculateDurationMinutes(startTime, endTime, 60), "duration"));
            row.put("location", event.get("location") != null ? event.get("location").toString() : "");
            row.put("travel_time", intValue(event, 0, "travelTime", "travel_time"));
            row.put("status", event.get("status") != null ? event.get("status").toString() : "FIXED_EVENT");
            row.put("category", event.get("category") != null ? event.get("category").toString() : "");
            row.put("description", event.get("description") != null ? event.get("description").toString() : "");
            row.put("reminder_enabled", booleanValue(event, false, "reminderEnabled", "reminder_enabled"));
            row.put("reminder_every_days", intValue(event, 1, "reminderEveryDays", "reminder_every_days"));
            row.put("archived", booleanValue(event, false, "archived"));
            row.put("archived_at", longValue(event, null, "archivedAt", "archived_at"));

            sanitized.add(row);
        }
        return sanitized;
    }

    private List<Map<String, Object>> sanitizeEvents(List<Map<String, Object>> events) {
        return sanitizeEvents(null, events);
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
        return cleanValue(value);
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
            return offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime().toString();
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
                String result = cleanValue(value);
                if (!result.isBlank()) {
                    return result;
                }
            }
        }
        return "";
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
