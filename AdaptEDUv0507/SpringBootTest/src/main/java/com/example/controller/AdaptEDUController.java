package com.example.controller;

import org.springframework.web.bind.annotation.*;
import procrastination_alg.Event;
import procrastination_alg.ProcrastinationAlgorithm;
import procrastination_alg.Scheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class TaskDTO {
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
}

class EventDTO {
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
    public List<TaskDTO> tasks;
    public List<EventDTO> events;
    public String scheduleStart;
    public String scheduleEnd;
}

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*")
public class AdaptEDUController {

    @PostMapping("/task-time-adjust")
    public TaskDTO adjustTaskTime(@RequestBody TaskDTO task) {
        double adjusted = ProcrastinationAlgorithm.getRealisticTimeInMinutes(task.estimatedTime);
        task.estimatedTime = (int) Math.round(adjusted);
        return task;
    }

    @PostMapping("/schedule")
    public List<EventDTO> generateSchedule(@RequestBody ScheduleRequest request) {
        List<Event> fixedEvents = new ArrayList<>();
        if (request.events != null) {
            for (EventDTO dto : request.events) {
                if (dto.archived) {
                    continue;
                }
                try {
                    Event event = new Event(
                            dto.name,
                            LocalDateTime.parse(dto.startTime),
                            LocalDateTime.parse(dto.startTime),
                            LocalDateTime.parse(dto.endTime)
                    );
                    if (dto.location != null) event.setLocation(dto.location);
                    if (dto.category != null) event.setCategory(dto.category);
                    if (dto.status != null) event.setStatus(dto.status);
                    fixedEvents.add(event);
                } catch (Exception e) {
                    System.err.println("Skipping invalid event: " + dto.name);
                }
            }
        }

        LocalDateTime start = LocalDateTime.parse(request.scheduleStart);
        LocalDateTime end = LocalDateTime.parse(request.scheduleEnd);

        Scheduler scheduler = new Scheduler();
        String taskCsvPath = resolveResourcePath("tasks.csv").toString();
        List<Event> schedule = scheduler.generateSchedule(fixedEvents, start, end, taskCsvPath);

        return schedule.stream().map(e -> {
            EventDTO dto = new EventDTO();
            dto.name = e.getName();
            dto.startTime = e.getStartTime() != null ? e.getStartTime().toString() : null;
            dto.endTime = e.getEndTime() != null ? e.getEndTime().toString() : null;
            dto.status = e.getStatus();
            dto.category = e.getCategory();
            dto.location = e.getLocation();
            return dto;
        }).collect(Collectors.toList());
    }

    //@PostMapping("/state/save-csv")
    //public Map<String, Object> saveStateCsv(@RequestBody CsvSyncRequest request) throws IOException {
      //  List<TaskDTO> tasks = request.tasks == null ? List.of() : request.tasks;
       // List<EventDTO> events = request.events == null ? List.of() : request.events;

       // writeTasksCsv(tasks, resolveResourcePath("tasks.csv"));
       // writeEventsCsv(events, resolveResourcePath("events.csv"));

       // return Map.of(
       
        //        "status", "ok",
          //      "tasksSaved", tasks.size(),
            //    "eventsSaved", events.size()
  //      );
 //   }

    private static Path resolveResourcePath(String fileName) {
        Path inModule = Paths.get("src", "main", "resources", fileName);
        if (Files.exists(inModule.getParent())) return inModule;

        Path fromRepoRoot = Paths.get("SpringBootTest", "src", "main", "resources", fileName);
        if (Files.exists(fromRepoRoot.getParent())) return fromRepoRoot;

        return inModule;
    }

}
