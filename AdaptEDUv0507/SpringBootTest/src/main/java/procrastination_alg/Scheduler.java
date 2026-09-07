package procrastination_alg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;

// Assuming Task, Event, and the procrastination algorithm are in the classpath.
// You may need to ensure your project is set up to compile/access these files.

public class Scheduler {

    /**
     * A simple private class to represent a block of free time.
     */
    private static class TimeSlot {
        LocalDateTime start;
        LocalDateTime end;

        TimeSlot(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        public long getDurationInMinutes() {
            return Duration.between(start, end).toMinutes();
        }

        @Override
        public String toString() {
            return "TimeSlot{" +
                    "start=" + start +
                    ", end=" + end +
                    ", duration=" + getDurationInMinutes() + "min" +
                    '}';
        }
    }

    public LocalDateTime getFirstFreeTime(List<TimeSlot> freeSlots) {
        for (TimeSlot time : freeSlots) {
            if (time.getDurationInMinutes() >= 10) {
                return time.start;
            }
        }
        return LocalDateTime.now();
    }

    /**
     * Generates a schedule by placing tasks into the free time between fixed
     * events.
     */
    public List<Event> generateSchedule(List<Event> fixedEvents,
            LocalDateTime scheduleStart, LocalDateTime scheduleEnd, List<Task> taskList) {

        TaskManager tm = new TaskManager();
        if (taskList != null) {
            for (Task t : taskList) {
                if (t != null && !t.isCompleted()) {
                    tm.addTask(t);
                }
            }
        }
        tm.sortByDueDate();

        // 1. Find all available time slots
        List<TimeSlot> freeSlots = findFreeTimeSlots(fixedEvents, scheduleStart, scheduleEnd);

        // 2. Prioritize tasks to schedule the most important ones first
        tm.sortByUrgency();

        List<Event> scheduledTaskEvents = new ArrayList<>();
        Map<Task, Double> remainingTimes = new LinkedHashMap<>();

        // 3. Fit tasks into free slots
        tm.procrastinate();
        int sessionInDay = 0;
        int maxLoopSafety = 1000;
        while (tm.getTasks().size() > 0 && --maxLoopSafety > 0) {
            LocalDateTime time = getFirstFreeTime(freeSlots);
            tm.sortByUrgency(time);

            Task task = tm.getTasks().get(0);
            if (task.getPriorityScore() < -15 + 4 * sessionInDay) {
                task = new Task("Break", "BREAK", LocalDateTime.MAX, 0, 120, false, 120, "Break");
                sessionInDay = 0;
            }

            int remainingDuration = task.getEstimatedTime();
            List<Event> taskSessions = new ArrayList<>();
            boolean placed = false;

            for (TimeSlot slot : freeSlots) {
                long slotDuration = slot.getDurationInMinutes();
                if (slotDuration < 10) {
                    continue;
                }
                if (task.getMaxSessionLength() == -1 && slotDuration < remainingDuration) {
                    continue;
                }

                LocalDateTime taskStart = slot.start;
                if (!scheduledTaskEvents.isEmpty()) {
                    Event lastEvent = scheduledTaskEvents.get(scheduledTaskEvents.size() - 1);
                    if (taskStart.isEqual(lastEvent.getEndTime()) && task.getName().equals(lastEvent.getName())) {
                        task = new Task("Break 10", "BREAK", LocalDateTime.MAX, 0, 10, false, 10, "Break");
                        sessionInDay = 0;
                    }
                }

                int timeToTake;
                if (task.getMaxSessionLength() == -1) {
                    timeToTake = remainingDuration;
                } else {
                    timeToTake = (int) Math.min(task.getMaxSessionLength(), Math.min(slotDuration, remainingDuration));
                }
                if (timeToTake <= 0) continue;

                LocalDateTime taskEnd = taskStart.plusMinutes(timeToTake);
                sessionInDay++;
                if (taskEnd.getHour() > 21) {
                    sessionInDay = 0;
                }

                Event taskEvent = new Event(
                        task.getName(),
                        taskStart,
                        taskStart,
                        taskEnd,
                        task.getDueDate(),
                        task.getPriorityScore());
                taskEvent.setStatus("SCHEDULED_TASK");
                taskEvent.setDescription("Scheduled block for task: " + task.getName());
                taskEvent.setSession(task.getSession());
                if (task.getCategory() != null) {
                    taskEvent.setCategory(task.getCategory());
                }

                taskSessions.add(taskEvent);
                tm.removeTask(task);
                placed = true;

                slot.start = taskEnd;
                remainingDuration -= timeToTake;
                if (remainingDuration > 0.1 && !task.getCategory().equalsIgnoreCase("BREAK")) {
                    task.setSession(task.getSession() + 1);
                    task.setEstimatedTime(remainingDuration);
                    tm.addTask(task);
                    tm.sortByUrgency(time);
                }
                break;
            }

            if (!placed) {
                tm.removeTask(task);
                remainingTimes.put(task, (double) remainingDuration);
            }

            scheduledTaskEvents.addAll(taskSessions);
        }

        // 4. Combine fixed events with newly scheduled task events
        List<Event> fullSchedule = new ArrayList<>();
        if (fixedEvents != null) {
            fullSchedule.addAll(fixedEvents);
        }
        fullSchedule.addAll(scheduledTaskEvents);
        fullSchedule.sort(Comparator.comparing(
            (Event event) -> event.getStartTime(),
            Comparator.nullsLast(Comparator.naturalOrder())));

        if (!remainingTimes.isEmpty()) {
            System.out.println("\nWarning: Could not fully schedule all tasks. Unscheduled remaining time:");
            for (Map.Entry<Task, Double> entry : remainingTimes.entrySet()) {
                System.out.printf("- %s (Remaining: %.0f min)\n", entry.getKey().getName(), entry.getValue());
            }
        }

        return fullSchedule;
    }

    public List<Event> generateSchedule(List<Event> fixedEvents,
            LocalDateTime scheduleStart, LocalDateTime scheduleEnd, String filePath) {
        TaskManager tm = new TaskManager();
        if (filePath != null && !filePath.trim().isEmpty()) {
            tm.insertTaskList(filePath);
        }
        return generateSchedule(fixedEvents, scheduleStart, scheduleEnd, tm.getTasks());
    }

    /**
     * Identifies blocks of free time between a given start and end time, avoiding a
     * list of busy events. Waking hours are set to 8:00 AM – 10:00 PM (14 hours/day).
     */
    private List<TimeSlot> findFreeTimeSlots(List<Event> events, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<TimeSlot> freeSlots = new ArrayList<>();

        LocalDate startDate = (windowStart != null) ? windowStart.toLocalDate() : LocalDate.now();
        LocalDate endDate = (windowEnd != null) ? windowEnd.toLocalDate() : startDate.plusDays(7);
        if (endDate.isBefore(startDate)) {
            endDate = startDate.plusDays(7);
        }

        List<Event> validEvents = (events != null) ? events.stream()
                .filter(e -> e.getStartTime() != null && e.getEndTime() != null && e.getEndTime().isAfter(e.getStartTime()))
                .collect(Collectors.toList()) : new ArrayList<>();

        LocalDate curr = startDate;
        while (!curr.isAfter(endDate)) {
            LocalDateTime dayStart = curr.atTime(8, 0);
            LocalDateTime dayEnd = curr.atTime(22, 0);

            // If today, don't schedule in the past
            if (curr.equals(LocalDate.now())) {
                LocalDateTime now = LocalDateTime.now();
                if (now.isAfter(dayStart)) {
                    dayStart = now.plusMinutes(5);
                }
            }

            if (!dayStart.isBefore(dayEnd)) {
                curr = curr.plusDays(1);
                continue;
            }

            final LocalDateTime dStart = dayStart;
            final LocalDateTime dEnd = dayEnd;

            // Collect and clamp busy intervals on this day
            List<TimeSlot> busyIntervals = new ArrayList<>();
            for (Event ev : validEvents) {
                if (ev.getEndTime().isAfter(dStart) && ev.getStartTime().isBefore(dEnd)) {
                    LocalDateTime bStart = ev.getStartTime().isBefore(dStart) ? dStart : ev.getStartTime();
                    LocalDateTime bEnd = ev.getEndTime().isAfter(dEnd) ? dEnd : ev.getEndTime();
                    if (bEnd.isAfter(bStart)) {
                        busyIntervals.add(new TimeSlot(bStart, bEnd));
                    }
                }
            }

            busyIntervals.sort(Comparator.comparing(slot -> slot.start));

            // Merge overlapping busy intervals
            List<TimeSlot> mergedBusy = new ArrayList<>();
            for (TimeSlot slot : busyIntervals) {
                if (mergedBusy.isEmpty()) {
                    mergedBusy.add(slot);
                } else {
                    TimeSlot prev = mergedBusy.get(mergedBusy.size() - 1);
                    if (!slot.start.isAfter(prev.end)) {
                        if (slot.end.isAfter(prev.end)) {
                            prev.end = slot.end;
                        }
                    } else {
                        mergedBusy.add(slot);
                    }
                }
            }

            // Extract free gaps between busy intervals
            LocalDateTime cursor = dStart;
            for (TimeSlot busy : mergedBusy) {
                if (busy.start.isAfter(cursor)) {
                    long durationMins = Duration.between(cursor, busy.start).toMinutes();
                    if (durationMins >= 10) {
                        freeSlots.add(new TimeSlot(cursor, busy.start));
                    }
                }
                if (busy.end.isAfter(cursor)) {
                    cursor = busy.end;
                }
            }

            if (dEnd.isAfter(cursor)) {
                long durationMins = Duration.between(cursor, dEnd).toMinutes();
                if (durationMins >= 10) {
                    freeSlots.add(new TimeSlot(cursor, dEnd));
                }
            }

            curr = curr.plusDays(1);
        }

        return freeSlots;
    }

    public static List<Event> loadEventsFromCSV(String filePath) {
        List<Event> loadedEvents = new ArrayList<>();
        if (filePath == null || filePath.trim().isEmpty()) {
            return loadedEvents;
        }
        Reader reader;
        if (filePath.contains("\n") || filePath.contains(",")) {
            reader = new StringReader(filePath);
        } else {
            try {
                reader = new FileReader(filePath);
            } catch (Exception e) {
                System.err.println("Warning: Could not open file " + filePath + " (" + e.getMessage() + ")");
                return loadedEvents;
            }
        }
        try (BufferedReader br = new BufferedReader(reader)) {
            String headerLine = br.readLine();
            if (headerLine == null) return loadedEvents;

            String[] headers = TaskManager.parseCsvLine(headerLine);
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String clean = headers[i].trim().toLowerCase().replaceAll("[_\\-\\s]", "");
                colMap.put(clean, i);
            }

            boolean hasHeaderMap = colMap.containsKey("name") || colMap.containsKey("starttime") || colMap.containsKey("start");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = TaskManager.parseCsvLine(line);
                if (values.length < 3) continue;

                try {
                    String name = "Event";
                    LocalDateTime start = null;
                    LocalDateTime end = null;
                    int duration = 60;
                    String location = "";
                    int travelTime = 0;
                    String status = "FIXED";
                    String category = "other";
                    String description = "";

                    if (hasHeaderMap) {
                        int nameIdx = colMap.getOrDefault("name", 0);
                        int startIdx = colMap.getOrDefault("starttime", colMap.getOrDefault("start", 1));
                        int endIdx = colMap.getOrDefault("endtime", colMap.getOrDefault("end", 2));
                        int durIdx = colMap.getOrDefault("duration", -1);
                        int locIdx = colMap.getOrDefault("location", -1);
                        int travelIdx = colMap.getOrDefault("traveltime", colMap.getOrDefault("travel", -1));
                        int statusIdx = colMap.getOrDefault("status", -1);
                        int catIdx = colMap.getOrDefault("category", -1);
                        int descIdx = colMap.getOrDefault("description", colMap.getOrDefault("desc", -1));

                        if (nameIdx >= 0 && nameIdx < values.length && !values[nameIdx].trim().isEmpty()) {
                            name = values[nameIdx].trim();
                        }
                        String startStr = (startIdx >= 0 && startIdx < values.length) ? values[startIdx].trim() : "";
                        start = TaskManager.parseLocalDateTime(startStr);
                        if (start == null) continue;

                        String endStr = (endIdx >= 0 && endIdx < values.length) ? values[endIdx].trim() : "";
                        end = TaskManager.parseLocalDateTime(endStr);
                        if (end == null) end = start.plusHours(1);

                        if (durIdx >= 0 && durIdx < values.length && !values[durIdx].trim().isEmpty()) {
                            duration = Integer.parseInt(values[durIdx].trim());
                        } else {
                            duration = (int) Duration.between(start, end).toMinutes();
                        }
                        if (locIdx >= 0 && locIdx < values.length) location = values[locIdx].trim();
                        if (travelIdx >= 0 && travelIdx < values.length && !values[travelIdx].trim().isEmpty()) {
                            travelTime = Integer.parseInt(values[travelIdx].trim());
                        }
                        if (statusIdx >= 0 && statusIdx < values.length && !values[statusIdx].trim().isEmpty()) {
                            status = values[statusIdx].trim();
                        }
                        if (catIdx >= 0 && catIdx < values.length && !values[catIdx].trim().isEmpty()) {
                            category = values[catIdx].trim();
                        }
                        if (descIdx >= 0 && descIdx < values.length) description = values[descIdx].trim();
                    } else {
                        int offset = (values.length >= 8 && !values[0].contains("-") && !values[0].contains("T")) ? 1 : 0;
                        if (values.length > offset && !values[offset].trim().isEmpty()) {
                            name = values[offset].trim();
                        }
                        start = TaskManager.parseLocalDateTime(values[offset + 1].trim());
                        if (start == null) continue;
                        end = (values.length > offset + 2) ? TaskManager.parseLocalDateTime(values[offset + 2].trim()) : null;
                        if (end == null) end = start.plusHours(1);

                        if (values.length > offset + 3 && !values[offset + 3].trim().isEmpty()) {
                            duration = Integer.parseInt(values[offset + 3].trim());
                        } else {
                            duration = (int) Duration.between(start, end).toMinutes();
                        }
                        if (values.length > offset + 4) location = values[offset + 4].trim();
                        if (values.length > offset + 5 && !values[offset + 5].trim().isEmpty()) {
                            travelTime = Integer.parseInt(values[offset + 5].trim());
                        }
                        if (values.length > offset + 6 && !values[offset + 6].trim().isEmpty()) {
                            status = values[offset + 6].trim();
                        }
                        if (values.length > offset + 7 && !values[offset + 7].trim().isEmpty()) {
                            category = values[offset + 7].trim();
                        }
                        if (values.length > offset + 8) description = values[offset + 8].trim();
                    }

                    Event e = new Event(name, start, start, end, duration, location, travelTime, status);
                    if (category != null && !category.isEmpty()) e.setCategory(category);
                    if (description != null && !description.isEmpty()) e.setDescription(description);
                    loadedEvents.add(e);
                } catch (Exception e) {
                    System.err.println("Skipping invalid event row: " + line + " (" + e.getMessage() + ")");
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load events from " + filePath + " (" + e.getMessage() + ")");
        }
        return loadedEvents;
    }

    public static void main(String[] args) {
        Scheduler scheduler = new Scheduler();

        // --- 1. Load Data From CSV ---
        List<Event> fixedEvents = loadEventsFromCSV("src/main/java/procrastination_alg/events.csv");
        // --- 2. Define the scheduling window ---
        // The test data in the CSV spans July 6 to July 10, 2026.
        LocalDate testDate = LocalDate.of(2026, 7, 6);
        LocalDateTime scheduleStart = testDate.atTime(8, 0);
        LocalDateTime scheduleEnd = testDate.plusDays(4).atTime(22, 0);

        // --- 3. Generate and print the schedule ---
        System.out.println("Generating Schedule for " + testDate + "...\n");
        List<Event> fullSchedule = scheduler.generateSchedule(fixedEvents, scheduleStart, scheduleEnd,
                "src/main/java/procrastination_alg/tasks.csv");

        System.out.println("--- Final Daily Schedule ---");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

        // to detect overlaps:
        boolean overlap = false;
        boolean sleepLoss = false;
        boolean overDue = false;
        int index = 0;
        Event overlapped = new Event("Placeholder", LocalDateTime.now());

        LocalDateTime date = LocalDateTime.MIN;
        for (Event e : fullSchedule) {
            if (e.getDate().toLocalDate().isAfter(date.toLocalDate())) {
                date = e.getDate();
                System.out.println(date.toLocalDate().format(dateFormatter) + " - " + date.getDayOfWeek());
            }
            String type = "FIXED_EVENT";
            String extraInfo = "";

            if ("SCHEDULED_TASK".equals(e.getStatus())) {
                type = "SCHEDULED_TASK";
            }

            System.out.printf("[%s] %s to %s - %s (Session %s) %s%s\n",
                    type, e.getStartTime().format(timeFormatter), e.getEndTime().format(timeFormatter), e.getName(),
                    e.getSession(),
                    e.getPriorityScore(),
                    extraInfo);
            if (index > 0) {
                if (/* e.getStartTime().isBefore(fullSchedule.get(index - 1).getEndTime()) */ e.getStartTime()
                        .getDayOfYear() <= fullSchedule.get(index - 1).getEndTime().getDayOfYear()
                        & e.getStartTime().getHour() < fullSchedule.get(index - 1).getEndTime().getHour()) {
                    if (e.getStatus().equals("SCHEDULED_TASK")
                            || fullSchedule.get(index - 1).getStatus().equals("SCHEDULED_TASK")) {
                        overlap = true;
                        overlapped = e;
                    }
                    System.out.println("^^^^^ OVERLAP ^^^^^");
                }

            }
            if (e.getDueDate().isBefore(e.getEndTime())) {
                overDue = true;
                System.out.println("!!! PREDICTED OVERDUE !!!");
            }
            if (e.getStartTime().getHour() < 8 || e.getEndTime().getHour() > 22) {
                sleepLoss = true;
                System.out.println("~~~ PREDICTED LOSS OF SLEEP ~~~ " + e.getStartTime().getHour() + " - "
                        + e.getEndTime().getHour());
            }
            index++;
        }
        if (overlap) {
            System.out.println("OVERLAP DETECTED AT " + overlapped.getName());
        }
        if (overDue) {
            System.out.println("OVERDUE ASSIGNMENT DETECTED");
        }
        if (sleepLoss) {
            System.out.println("LOSS OF SLEEP DETECTED");
        }
        index = 0;
    }
}
