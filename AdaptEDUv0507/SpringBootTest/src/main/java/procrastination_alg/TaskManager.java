package procrastination_alg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskManager {

    private ArrayList<Task> tasks; // List of tasks
    private ArrayList<Event> events; // List of events

    public TaskManager() {
        tasks = new ArrayList<>();
        events = new ArrayList<>();
    }

    // Inserts the list of tasks rom a CSV file
    public void insertTaskList(String filePath) {
        for (Task task : loadTasksFromCSV(filePath)) {
            tasks.add(task);
        }
    }

    public static LocalDateTime parseLocalDateTime(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        try {
            if (s.endsWith("Z") || s.matches(".*[+-]\\d\\d:\\d\\d$")) {
                return java.time.OffsetDateTime.parse(s).toLocalDateTime();
            }
            if (s.contains(" ")) {
                s = s.replace(" ", "T");
            }
            if (!s.contains("T")) {
                return java.time.LocalDate.parse(s).atTime(23, 59, 59);
            }
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            try {
                return java.time.Instant.parse(s).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    // pulls task list data from the CSV file
    public static List<Task> loadTasksFromCSV(String filePath) {
        List<Task> loadedTasks = new ArrayList<>();
        if (filePath == null || filePath.trim().isEmpty()) {
            return loadedTasks;
        }
        Reader reader;
        if (filePath.contains("\n") || filePath.contains(",")) {
            reader = new StringReader(filePath);
        } else {
            try {
                reader = new FileReader(filePath);
            } catch (Exception e) {
                System.err.println("Warning: Could not open file " + filePath + " (" + e.getMessage() + ")");
                return loadedTasks;
            }
        }
        try (BufferedReader br = new BufferedReader(reader)) {
            String headerLine = br.readLine();
            if (headerLine == null) return loadedTasks;

            String[] headers = parseCsvLine(headerLine);
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String clean = headers[i].trim().toLowerCase().replaceAll("[_\\-\\s]", "");
                colMap.put(clean, i);
            }

            boolean hasHeaderMap = colMap.containsKey("name") || colMap.containsKey("task") || colMap.containsKey("duedate");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = parseCsvLine(line);
                if (values.length < 3) continue;

                try {
                    String name = "Task";
                    String category = "school";
                    LocalDateTime dueDate = null;
                    int userPriority = 5;
                    int estimatedTime = 60;
                    boolean completed = false;
                    int maxSessionLength = 120;
                    String description = "";

                    if (hasHeaderMap) {
                        int nameIdx = colMap.getOrDefault("name", colMap.getOrDefault("task", 0));
                        int catIdx = colMap.getOrDefault("category", -1);
                        int dueIdx = colMap.getOrDefault("duedate", colMap.getOrDefault("due", -1));
                        int prioIdx = colMap.getOrDefault("userpriority", colMap.getOrDefault("priority", -1));
                        int estIdx = colMap.getOrDefault("estimatedtime", colMap.getOrDefault("duration", -1));
                        int compIdx = colMap.getOrDefault("completed", -1);
                        int sessIdx = colMap.getOrDefault("maxsessionlength", colMap.getOrDefault("sessionlength", -1));
                        int descIdx = colMap.getOrDefault("description", colMap.getOrDefault("desc", -1));

                        if (nameIdx >= 0 && nameIdx < values.length && !values[nameIdx].trim().isEmpty()) {
                            name = values[nameIdx].trim();
                        }
                        if (catIdx >= 0 && catIdx < values.length && !values[catIdx].trim().isEmpty()) {
                            category = values[catIdx].trim();
                        }
                        String dueStr = (dueIdx >= 0 && dueIdx < values.length) ? values[dueIdx].trim() : "";
                        dueDate = parseLocalDateTime(dueStr);
                        if (dueDate == null) dueDate = LocalDateTime.now().plusDays(2);

                        if (prioIdx >= 0 && prioIdx < values.length && !values[prioIdx].trim().isEmpty()) {
                            userPriority = Integer.parseInt(values[prioIdx].trim());
                        }
                        if (estIdx >= 0 && estIdx < values.length && !values[estIdx].trim().isEmpty()) {
                            estimatedTime = Integer.parseInt(values[estIdx].trim());
                        }
                        if (compIdx >= 0 && compIdx < values.length && !values[compIdx].trim().isEmpty()) {
                            completed = Boolean.parseBoolean(values[compIdx].trim());
                        }
                        if (sessIdx >= 0 && sessIdx < values.length && !values[sessIdx].trim().isEmpty()) {
                            maxSessionLength = Integer.parseInt(values[sessIdx].trim());
                        }
                        if (descIdx >= 0 && descIdx < values.length) {
                            description = values[descIdx].trim();
                        }
                    } else {
                        int offset = (values.length >= 9) ? 1 : 0;
                        if (values.length > offset && !values[offset].trim().isEmpty()) {
                            name = values[offset].trim();
                        }
                        if (values.length > offset + 1 && !values[offset + 1].trim().isEmpty()) {
                            category = values[offset + 1].trim();
                        }
                        String dueStr = values.length > offset + 2 ? values[offset + 2].trim() : "";
                        dueDate = parseLocalDateTime(dueStr);
                        if (dueDate == null) dueDate = LocalDateTime.now().plusDays(2);

                        if (values.length > offset + 3 && !values[offset + 3].trim().isEmpty()) {
                            userPriority = Integer.parseInt(values[offset + 3].trim());
                        }
                        if (values.length > offset + 4 && !values[offset + 4].trim().isEmpty()) {
                            estimatedTime = Integer.parseInt(values[offset + 4].trim());
                        }
                        if (values.length > offset + 5 && !values[offset + 5].trim().isEmpty()) {
                            completed = Boolean.parseBoolean(values[offset + 5].trim());
                        }
                        if (values.length > offset + 6 && !values[offset + 6].trim().isEmpty()) {
                            maxSessionLength = Integer.parseInt(values[offset + 6].trim());
                        }
                        if (values.length > offset + 7) {
                            description = values[offset + 7].trim();
                        }
                    }

                    loadedTasks.add(new Task(
                            name,
                            category,
                            dueDate,
                            userPriority,
                            estimatedTime,
                            completed,
                            maxSessionLength,
                            description
                    ));
                } catch (Exception e) {
                    System.err.println("Skipping invalid task row: " + line + " (" + e.getMessage() + ")");
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load tasks from " + filePath + " (" + e.getMessage() + ")");
        }
        return loadedTasks;
    }

    public static String escapeCsv(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }

    public static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    // Task methods
    public void addTask(Task task) {
        tasks.add(task);
    }

    public void removeTask(Task task) {
        tasks.remove(task);
    }

    public List<Task> getTasks() {
        return tasks;
    }

    // Event methods
    public void addEvent(Event event) {
        events.add(event);
    }

    public void removeEvent(Event event) {
        events.remove(event);
    }

    public List<Event> getEvents() {
        return events;
    }

    // Additional utility methods
    public void printTasksByUrgency() {
        tasks.sort((t1, t2) -> Double.compare(t2.getPriorityScore(), t1.getPriorityScore()));
        for (Task task : tasks) {
            System.out.println(task.getName() + " - Urgency: " + task.getPriorityScore());
        }
    }

    public void printTasksByDueDate() {
        tasks.sort((t1, t2) -> t1.getDueDate().compareTo(t2.getDueDate()));
        for (Task task : tasks) {
            System.out.println(task.getName() + " - Due: " + task.getDueDate());
        }
    }

    public Task getMostUrgentTask() {
        if (tasks.isEmpty())
            return null;
        Task urgent = tasks.get(0);
        for (Task task : tasks) {
            if (task.getPriorityScore() > urgent.getPriorityScore()) {
                urgent = task;
            }
        }
        return urgent;
    }

    public void sortByUrgency() {
        tasks.sort((t1, t2) -> Double.compare(t2.getPriorityScore(), t1.getPriorityScore()));
    }

    public void sortByUrgency(LocalDateTime day) {
        for (Task task : tasks) {
            task.calculatePriorityScore(day);
        }
        tasks.sort((t1, t2) -> Double.compare(t2.getPriorityScore(), t1.getPriorityScore()));
    }

    public void sortByDueDate() {
        tasks.sort((t2, t1) -> t1.getDueDate().compareTo(t2.getDueDate()));
    }

    public void printTasks() {
        for (Task task : tasks) {
            System.out.println(task.getName() + " - Due: " + task.getDueDate());
        }
    }

    public void procrastinate() {
        for (Task task : tasks) {
            task.setEstimatedTime(
                    (int) Math.round(ProcrastinationAlgorithm.getRealisticTimeInMinutes(task.getEstimatedTime())));
        }
    }

    @Override
    public String toString() {
        String toReturn = "";
        for (Task a : tasks) {
            toReturn += a.toString() + " ; ";
        }
        return toReturn;
    }
}