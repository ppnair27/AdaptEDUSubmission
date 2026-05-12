package procrastination_alg;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allows the frontend to communicate with the backend
public class ScheduleController {

    /**
     * REST controller serving lightweight UI endpoints used by the static
     * frontend.
     * 
     * 
     * What it does:
     * - Persist UI state (tasks/events) to CSV files used by the app and by
     * the scheduling engine.
     * - Provide a thin HTTP endpoint `/api/schedule` that loads fixed events
     * from CSV, prepares a scheduling window, and delegates to
     * `procrastination_alg.Scheduler` to produce scheduled task blocks.
     *
     * The controller intentionally contains only IO and DTO transformation
     * logic
     * All scheduling math is in `Scheduler`.
     */

    private static final String EVENTS_CSV = "src/main/java/procrastination_alg/events.csv";
    private static final String TASKS_CSV = "src/main/java/procrastination_alg/tasks.csv";

    /**
     * Endpoint to receive state sync from the frontend UI.
     * Hooked up to syncStateToCsv() in script2.js.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/state/save-csv")
    public Map<String, String> saveStateToCsv(@RequestBody Map<String, Object> payload) {
        // Log the incoming keys for debugging the sync flow between UI and server
        System.out.println("State synchronized from UI! Received: " + payload.keySet());

        try {
            if (payload.containsKey("events")) {
                List<Map<String, Object>> events = (List<Map<String, Object>>) payload.get("events");
                writeEventsToCsv(events, EVENTS_CSV);
            }

            if (payload.containsKey("tasks")) {
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) payload.get("tasks");
                writeTasksToCsv(tasks, TASKS_CSV);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to save CSV: " + e.getMessage());
            return errorResponse;
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    /**
     * Endpoint to fetch the fully scheduled blocks.
     */
    @GetMapping("/schedule")
    public List<Event> getSchedule(
            @RequestParam(defaultValue = "8") int startHour,
            @RequestParam(defaultValue = "22") int endHour) {
        Scheduler scheduler = new Scheduler();

        // Load the fixed events directly from the fresh CSV storage
        List<Event> fixedEvents = Scheduler.loadEventsFromCSV(EVENTS_CSV);

        // Setup a dynamic scheduling window starting from now (rounded to nearest 15
        // mins)
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        if (now.getMinute() % 15 != 0) {
            now = now.plusMinutes(15 - (now.getMinute() % 15));
        }
        /*
         * LocalDateTime scheduleStart = now.getHour() < startHour ?
         * now.withHour(startHour).withMinute(0) : now;
         * if (scheduleStart.getHour() >= endHour) {
         * scheduleStart = now.plusDays(1).withHour(startHour).withMinute(0);
         * }
         * LocalDateTime scheduleEnd = scheduleStart.withHour(endHour).withMinute(0);
         */
        LocalDateTime scheduleStart = now.withHour(startHour).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime scheduleEnd = now.withHour(endHour).withMinute(0).withSecond(0).withNano(0);
        // Runs the scheduling. We only prepare inputs here (fixed events
        // and a start/end window) and pass the path to the tasks CSV. The
        // returned list contains both the original fixed events and newly
        // scheduled task blocks marked with status "SCHEDULED_TASK".
        List<Event> fullSchedule = scheduler.generateSchedule(fixedEvents, scheduleStart, scheduleEnd, TASKS_CSV);

        System.out.println("\n=== SCHEDULER OUTPUT ===");
        for (Event e : fullSchedule) {
            if ("SCHEDULED_TASK".equals(e.getStatus())) {
                e.setName(e.getName() + " (Session " + e.getSession() + ")");
                System.out.println("Scheduled Task Block: '" + e.getName() + "' | Scheduled for: " + e.getStartTime()
                        + " to " + e.getEndTime());
            }
        }
        System.out.println("========================\n");

        return fullSchedule;
    }

    // --- CSV Writing Helpers ---

    private void writeEventsToCsv(List<Map<String, Object>> events, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("name,startTime,endTime,duration,location,travelTime,status,category,description");
            for (Map<String, Object> e : events) {
                String name = parseString(e.get("name"));
                String startTime = parseString(e.get("startTime"));
                String endTime = parseString(e.get("endTime"));
                int duration = parseInt(e.get("duration"), 60);
                String location = parseString(e.get("location"));
                int travelTime = parseInt(e.get("travelTime"), 0);
                String status = parseString(e.get("status"), "FIXED_EVENT");
                String category = parseString(e.get("category"));
                String description = parseString(e.get("description"));

                writer.printf("%s,%s,%s,%d,%s,%d,%s,%s,%s\n",
                        escapeCsv(name), startTime, endTime, duration, escapeCsv(location), travelTime, status,
                        escapeCsv(category), escapeCsv(description));
            }
        }
    }

    private void writeTasksToCsv(List<Map<String, Object>> tasks, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("name,category,dueDate,userPriority,estimatedTime,completed,maxSessionLength,description");
            for (Map<String, Object> t : tasks) {
                String name = parseString(t.get("name"));
                String category = parseString(t.get("category"));
                String dueDate = parseString(t.get("dueDate"));
                int userPriority = parseInt(t.get("userPriority"), 5);
                int estimatedTime = parseInt(t.get("estimatedTime"), 60);
                boolean completed = parseBoolean(t.get("completed"), false);
                int maxSessionLength = parseInt(t.get("maxSessionLength"), 120);
                String description = parseString(t.get("description"));

                writer.printf("%s,%s,%s,%d,%d,%b,%d,%s\n",
                        escapeCsv(name), escapeCsv(category), dueDate, userPriority, estimatedTime, completed,
                        maxSessionLength, escapeCsv(description));
            }
        }
    }

    private String parseString(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private String parseString(Object obj, String def) {
        return obj != null ? obj.toString() : def;
    }

    private int parseInt(Object obj, int def) {
        if (obj == null)
            return def;
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private boolean parseBoolean(Object obj, boolean def) {
        if (obj == null)
            return def;
        return Boolean.parseBoolean(obj.toString());
    }

    private String escapeCsv(String val) {
        if (val == null)
            return "";
        if (val.contains(",") || val.contains("\"")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
