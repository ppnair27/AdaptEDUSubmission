package com.example.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allows the frontend to communicate with the backend
public class ScheduleController {

    private final SupabaseService supabaseService;

    public ScheduleController(SupabaseService supabaseService) {
        this.supabaseService = supabaseService;
    }

    /**
     * Endpoint to receive state sync from the frontend UI.
     * Hooked up to syncStateToCsv() in script2.js.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/state/save-csv")
    public Map<String, String> saveStateToCsv(@RequestBody Map<String, Object> payload) {
        System.out.println("State synchronized from UI! Received: " + payload.keySet());

        try {
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) payload.getOrDefault("tasks", List.of());
            List<Map<String, Object>> events = (List<Map<String, Object>>) payload.getOrDefault("events", List.of());

            System.out.println("📦 RECEIVED TASKS COUNT: " + tasks.size());
            System.out.println("📦 RECEIVED EVENTS COUNT: " + events.size());

            supabaseService.saveState(tasks, events);
        } catch (Exception e) {
            System.out.println("🚨 CRITICAL SYNC ERROR: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to save to Supabase: " + e.getMessage());
            return errorResponse;
        }
    
    /**
     * Endpoint to fetch the fully scheduled blocks.
     */
    @GetMapping("/schedule")
    public List<Event> getSchedule(
            @RequestParam(defaultValue = "8") int startHour,
            @RequestParam(defaultValue = "22") int endHour) {
        try {
            Scheduler scheduler = new Scheduler();
            List<Event> fixedEvents = Scheduler.loadEventsFromCSV(supabaseService.exportEventsCsv().toString());

            // Setup a dynamic scheduling window starting from now (rounded to nearest 15 mins)
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            if (now.getMinute() % 15 != 0) {
                now = now.plusMinutes(15 - (now.getMinute() % 15));
            }

            LocalDateTime scheduleStart = now.getHour() < startHour ? now.withHour(startHour).withMinute(0) : now;
            if (scheduleStart.getHour() >= endHour) {
                scheduleStart = now.plusDays(1).withHour(startHour).withMinute(0);
            }
            LocalDateTime scheduleEnd = scheduleStart.withHour(endHour).withMinute(0);

            // Run the algorithm using a Supabase-backed temporary CSV export.
            List<Event> fullSchedule = scheduler.generateSchedule(fixedEvents, scheduleStart, scheduleEnd,
                    supabaseService.exportTasksCsv().toString());

            System.out.println("\n=== SCHEDULER OUTPUT ===");
            for (Event e : fullSchedule) {
                if ("SCHEDULED_TASK".equals(e.getStatus())) {
                    e.setName(e.getName() + " (Session " + e.getSession() + ")");
                    System.out.println("Scheduled Task Block: '" + e.getName() + "' | Scheduled for: " + e.getStartTime() + " to " + e.getEndTime());
                }
            }
            System.out.println("========================\n");

            return fullSchedule;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to load schedule data from Supabase", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to load schedule data from Supabase", e);
        }
    }
}