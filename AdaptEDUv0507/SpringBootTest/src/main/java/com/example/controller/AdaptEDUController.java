package com.example.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import procrastination_alg.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class TaskDTO {
    public String id; 
    public String name;
    public String category;
    public String dueDate;
    public int userPriority;
    public int estimatedTime;
    public boolean completed;
    public String description;
    public int minutesSpent;
    public boolean archived;
    public Long archivedAt;
    public int maxSessionLength; 
}

class EventDTO {
    public String id; 
    public String name;
    public String startTime;
    public String endTime;
    public String location;
    public String status;
    public String category;
    public boolean reminderEnabled;
    public Integer reminderEveryDays;
    public boolean archived;
    public Long archivedAt;
}

class CsvSyncRequest {
    public List<TaskDTO> tasks;
    public List<EventDTO> events;
}

class ScheduleRequest {
    public Long userId;
    public List<TaskDTO> tasks;
    public List<EventDTO> events;
    public String scheduleStart;
    public String scheduleEnd;
}

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class AdaptEDUController {

    private final SupabaseService supabaseService;

    @Autowired
    public AdaptEDUController(SupabaseService supabaseService) {
        this.supabaseService = supabaseService;
    }

    @PostMapping("/task-time-adjust")
    public TaskDTO adjustTaskTime(@RequestBody TaskDTO task) {
        double adjusted = ProcrastinationAlgorithm.getRealisticTimeInMinutes(task.estimatedTime);
        task.estimatedTime = (int) Math.round(adjusted);
        return task;
    }

    @PostMapping("/schedule")
    public List<EventDTO> generateSchedule(@RequestBody ScheduleRequest request) {
        // 1. AUTOMATICALLY SAVE TO SUPABASE EVERY TIME THE UI UPDATES
        try {
            List<Map<String, Object>> taskMaps = new ArrayList<>();
            if (request.tasks != null) {
                for (TaskDTO t : request.tasks) {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", t.id);
                    map.put("name", t.name != null ? t.name : "");
                    map.put("category", t.category != null ? t.category : "");
                    map.put("dueDate", t.dueDate != null ? t.dueDate : "");
                    map.put("userPriority", t.userPriority);
                    map.put("estimatedTime", t.estimatedTime);
                    map.put("completed", t.completed);
                    map.put("max_session_length", t.maxSessionLength);
                    map.put("description", t.description != null ? t.description : "");
                    taskMaps.add(map);
                }
            }

            List<Map<String, Object>> eventMaps = new ArrayList<>();
            if (request.events != null) {
                for (EventDTO e : request.events) {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", e.id);
                    map.put("name", e.name != null ? e.name : "");
                    map.put("startTime", e.startTime != null ? e.startTime : "");
                    map.put("endTime", e.endTime != null ? e.endTime : "");
                    map.put("location", e.location != null ? e.location : "");
                    map.put("status", e.status != null ? e.status : "FIXED");
                    map.put("category", e.category != null ? e.category : "");
                    eventMaps.add(map);
                }
            }

            System.out.println("🔄 AUTO-SYNCING TO SUPABASE BEFORE SCHEDULING..." + (request.userId != null ? (" for userId: " + request.userId) : ""));
            if (request.userId != null) {
                supabaseService.saveState(request.userId, taskMaps, eventMaps);
            } else {
                supabaseService.saveState(taskMaps, eventMaps);
            }
            System.out.println("🚀 CLOUD SYNC SUCCESS: Data written to Supabase tables!");

        } catch (Exception e) {
            System.out.println("🚨 AUTO-SYNC ERROR (Check your API Keys!): " + e.getMessage());
        }

        // 2. RUN THE EXISTING SCHEDULER ALGORITHM
        List<Event> fixedEvents = new ArrayList<>();
        if (request.events != null) {
            for (EventDTO dto : request.events) {
                if (dto.archived || dto.startTime == null || dto.endTime == null) continue;
                try {
                    Event event = new Event(
                            dto.name,
                            LocalDateTime.parse(dto.startTime),
                            LocalDateTime.parse(dto.startTime),
                            LocalDateTime.parse(dto.endTime)
                    );
                    if (dto.category != null) {
                        event.setCategory(dto.category);
                    }
                    fixedEvents.add(event);
                } catch (Exception e) {
                    System.err.println("Skipping invalid event formatting: " + dto.name);
                }
            }
        }

        LocalDateTime start = (request.scheduleStart != null) ? LocalDateTime.parse(request.scheduleStart) : LocalDateTime.now();
        LocalDateTime end = (request.scheduleEnd != null) ? LocalDateTime.parse(request.scheduleEnd) : LocalDateTime.now().plusDays(7);

        Scheduler scheduler = new Scheduler();
        List<Event> schedule = new ArrayList<>();

        String taskCsvData = null;
        try {
            taskCsvData = (request.userId != null)
                    ? supabaseService.exportTasksCsv(request.userId).toString()
                    : supabaseService.exportTasksCsv().toString();
        } catch (Exception e) {
            System.err.println("🚨 ERROR LOADING TASKS FROM SUPABASE FOR SCHEDULER: " + e.getMessage());
        }

        // Fallback or direct use: if Supabase export is unavailable or request has direct tasks
        if (taskCsvData == null && request.tasks != null && !request.tasks.isEmpty()) {
            StringBuilder csv = new StringBuilder();
            csv.append("name,category,dueDate,userPriority,estimatedTime,completed,maxSessionLength,description\n");
            for (TaskDTO t : request.tasks) {
                if (t.completed || t.archived) continue;
                csv.append(TaskManager.escapeCsv(t.name)).append(",")
                   .append(TaskManager.escapeCsv(t.category)).append(",")
                   .append(TaskManager.escapeCsv(t.dueDate)).append(",")
                   .append(t.userPriority).append(",")
                   .append(t.estimatedTime).append(",")
                   .append(t.completed).append(",")
                   .append(t.maxSessionLength > 0 ? t.maxSessionLength : 120).append(",")
                   .append(TaskManager.escapeCsv(t.description != null ? t.description : "")).append("\n");
            }
            taskCsvData = csv.toString();
        }

        if (taskCsvData != null) {
            schedule = scheduler.generateSchedule(fixedEvents, start, end, taskCsvData);
        }

        return schedule.stream().map(e -> {
            EventDTO dto = new EventDTO();
            dto.name = e.getName();
            dto.startTime = e.getStartTime() != null ? e.getStartTime().toString() : null;
            dto.endTime = e.getEndTime() != null ? e.getEndTime().toString() : null;
            dto.status = e.getStatus();
            dto.category = e.getCategory();
            return dto;
        }).collect(Collectors.toList());
    }
    
    @PostMapping("/state/save-csv")
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveStateCsv(@RequestBody Map<String, Object> payload) {
        System.out.println("📡 UI BACKGROUND SYNC TRIGGERED: Receiving data...");
        try {
            Long userId = null;
            if (payload.get("userId") != null) {
                userId = Long.valueOf(payload.get("userId").toString());
            } else if (payload.get("user_id") != null) {
                userId = Long.valueOf(payload.get("user_id").toString());
            }

            List<Map<String, Object>> tasks = (List<Map<String, Object>>) payload.getOrDefault("tasks", new ArrayList<>());
            List<Map<String, Object>> events = (List<Map<String, Object>>) payload.getOrDefault("events", new ArrayList<>());

            System.out.println("📦 RECEIVED TASKS COUNT: " + tasks.size() + (userId != null ? (" for userId: " + userId) : ""));
            System.out.println("📦 RECEIVED EVENTS COUNT: " + events.size() + (userId != null ? (" for userId: " + userId) : ""));

            if (userId != null) {
                supabaseService.saveState(userId, tasks, events);
            } else {
                supabaseService.saveState(tasks, events);
            }
            
            System.out.println("✅ CLOUD SYNC SUCCESS: Background save complete!");
            return Map.of("status", "ok", "message", "State saved successfully");

        } catch (Exception e) {
            System.err.println("🚨 BACKGROUND SYNC ERROR: " + e.getMessage());
            e.printStackTrace();
            return Map.of(
                "status", "error",
                "message", "Failed to sync to cloud database: " + e.getMessage()
            );
        }
    }

    @GetMapping("/state/load")
    public Map<String, Object> loadUserState(@RequestParam(required = false) Long userId) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            if (userId != null) {
                response.put("status", "ok");
                response.put("tasks", supabaseService.fetchUserTasks(userId));
                response.put("events", supabaseService.fetchUserEvents(userId));
            } else {
                response.put("status", "ok");
                response.put("tasks", List.of());
                response.put("events", List.of());
            }
            return response;
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return response;
        }
    }
}