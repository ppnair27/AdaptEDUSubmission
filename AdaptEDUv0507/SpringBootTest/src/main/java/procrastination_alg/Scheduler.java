package procrastination_alg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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

// Assuming Task, Event, and the procrastination algorithm are in the classpath.
// You may need to ensure your project is set up to compile/access these files.

public class Scheduler {

    private TaskManager tasks = new TaskManager();

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
            if (time.getDurationInMinutes() > 0) {
                return time.start;
            }
        }
        return LocalDateTime.now();
    }

    /**
     * Generates a schedule by placing tasks into the free time between fixed
     * events.
     *
     * @param fixedEvents     A list of events that are already scheduled and cannot
     *                        be moved.
     * @param tasksToSchedule A list of tasks that need to be scheduled.
     * @param scheduleStart   The start of the time window for scheduling (e.g.,
     *                        beginning of the day).
     * @param scheduleEnd     The end of the time window for scheduling (e.g., end
     *                        of the day).
     * @return A list of Event objects, including the original fixed events and new
     *         events for the scheduled tasks.
     */
    public List<Event> generateSchedule(List<Event> fixedEvents,
            LocalDateTime scheduleStart, LocalDateTime scheduleEnd, String filePath) {

        tasks.insertTaskList(filePath);
        tasks.sortByDueDate();

        // 1. Find all available time slots
        List<TimeSlot> freeSlots = findFreeTimeSlots(fixedEvents, scheduleStart, scheduleEnd);

        // 2. Prioritize tasks to schedule the most important ones first

        tasks.sortByUrgency();

        List<Event> scheduledTaskEvents = new ArrayList<>();
        Map<Task, Double> remainingTimes = new LinkedHashMap<>();

        // 3. Fit tasks into free slots

        tasks.procrastinate();
        int sessionInDay = 0;
        while (tasks.getTasks().size() > 0) {
            LocalDateTime time = getFirstFreeTime(freeSlots);
            tasks.sortByUrgency(time);

            Task task = tasks.getTasks().get(0);
            System.out.println(sessionInDay);
            if (task.getPriorityScore() < -15 + 4 * sessionInDay) {
                task = new Task("Break", "BREAK", LocalDateTime.MAX, 0, 120, false, 120, "Break");
                sessionInDay = 0;
            }

            // Adjust estimated time for a more realistic duration using the procrastination
            // model
            // double remainingDuration =
            // ProcrastinationAlgorithm.getRealisticTimeInMinutes(task.getEstimatedTime());
            int remainingDuration = task.getEstimatedTime();
            List<Event> taskSessions = new ArrayList<>();

            // Find slots for the task, splitting if necessary

            for (TimeSlot slot : freeSlots) {

                long slotDuration = slot.getDurationInMinutes();
                // Makes sure it accounts for used up free slots
                if (slotDuration <= 0) {
                    continue;
                }
                if (task.getMaxSessionLength() == -1 && slotDuration < remainingDuration) {
                    continue;
                }
                // Take as much time as possible from the current slot
                LocalDateTime taskStart = slot.start;
                if (scheduledTaskEvents.size() > 0) {
                    System.out.println("Start of new: " + taskStart);
                    System.out.println(
                            "End of old: " + scheduledTaskEvents.get(scheduledTaskEvents.size() - 1).getEndTime());
                    System.out.println("Name of new: " + task.getName());
                    System.out.println(
                            "Name of old: " + scheduledTaskEvents.get(scheduledTaskEvents.size() - 1).getName());
                    if (taskStart.isEqual(scheduledTaskEvents.get(scheduledTaskEvents.size() - 1).getEndTime())
                            && task.getName()
                                    .equals(scheduledTaskEvents.get(scheduledTaskEvents.size() - 1).getName())) {
                        System.out.println("Break 10 scheduled");
                        task = new Task("Break 10", "BREAK", LocalDateTime.MAX, 0, 10, false, 10, "Break");
                        sessionInDay = 0;
                    }
                }
                int timeToTake;
                if (task.getMaxSessionLength() == -1)
                    timeToTake = remainingDuration;
                else
                    timeToTake = (int) Math.min(task.getMaxSessionLength(), Math.min(slotDuration, remainingDuration));

                LocalDateTime taskEnd = taskStart.plusMinutes(timeToTake);
                sessionInDay++;
                // Create a new Event to represent the scheduled task session

                if (taskEnd.getHour() > 21)
                    sessionInDay = 0;

                Event taskEvent = new Event(
                        task.getName(),
                        taskStart, // The 'Date' field in Event is a bit redundant, but we use start time
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
                tasks.removeTask(task);

                // Update the free slot by moving its start time forward
                slot.start = taskEnd;
                remainingDuration -= timeToTake;
                if (remainingDuration > 0.1 && !task.getCategory().equals("BREAK")) {
                    task.setSession(task.getSession() + 1);
                    task.setEstimatedTime(remainingDuration);
                    tasks.addTask(task);

                    tasks.sortByUrgency(time);

                    break;

                } else
                    break;

            }

            scheduledTaskEvents.addAll(taskSessions);
        }

        // 4. Combine fixed events with newly scheduled task events
        List<Event> fullSchedule = new ArrayList<>(fixedEvents);
        fullSchedule.addAll(scheduledTaskEvents);
        fullSchedule.sort(Comparator.comparing(Event::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        if (!remainingTimes.isEmpty()) {
            System.out.println("\nWarning: Could not fully schedule all tasks. Unscheduled remaining time:");
            for (Map.Entry<Task, Double> entry : remainingTimes.entrySet()) {
                System.out.printf("- %s (Remaining: %.0f min)\n", entry.getKey().getName(), entry.getValue());
            }
        }

        return fullSchedule;
    }

    /**
     * Identifies blocks of free time between a given start and end time, avoiding a
     * list of busy events.
     */
    private List<TimeSlot> findFreeTimeSlots(List<Event> events, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<TimeSlot> freeSlots = new ArrayList<>();

        // Filter events to be within our scheduling window and sort them
        // THIS IS THE SOURCE OF ISSUES - filtering out too many events - causing
        // overlaps
        List<Event> sortedEvents = events.stream()
                .filter(e -> e.getStartTime() != null && e.getEndTime() != null)
                .collect(Collectors.toList());

        LocalDateTime currentTime = LocalDateTime.now().plusDays(1).withHour(8);
        sortedEvents.sort((t1, t2) -> t1.getStartTime().compareTo(t2.getStartTime()));

        for (Event event : sortedEvents) {
            System.out.println("event: " + event + " ; " + currentTime);

            while (currentTime.isBefore(event.getEndTime())) {
                LocalDateTime setTimeWindow = currentTime.withHour(windowEnd.getHour());
                LocalDateTime setTimeEvent = event.getStartTime();
                LocalDateTime finalTime = event.getEndTime();
                if (setTimeEvent.getHour() < windowStart.getHour()) {
                    setTimeEvent = setTimeEvent.minusDays(1).withHour(windowEnd.getHour());
                } else if (setTimeEvent.getHour() > windowEnd.getHour()) {
                    setTimeEvent = setTimeEvent.withHour(windowEnd.getHour());
                }
                if (finalTime.getHour() < windowStart.getHour()) {
                    finalTime = finalTime.withHour(windowStart.getHour());
                } else if (finalTime.getHour() > windowEnd.getHour()) {
                    finalTime = finalTime.plusDays(1).withHour(windowStart.getHour());
                }
                if (setTimeWindow.isBefore(setTimeEvent)) {
                    freeSlots.add(new TimeSlot(currentTime, setTimeWindow));
                    System.out
                            .println("Freeslot: " + event.getName() + " --- " + currentTime + " --- " + setTimeWindow);
                    currentTime = currentTime.plusDays(1).withHour(windowStart.getHour());
                    if (currentTime.isAfter(event.getStartTime())) {
                        currentTime = finalTime;
                    }
                } else {
                    freeSlots.add(new TimeSlot(currentTime, setTimeEvent));
                    System.out.println("Freeslot: " + event.getName() + " --- " + currentTime + " --- " + setTimeEvent);
                    currentTime = finalTime;
                }
            }
        }

        // Add a final 7 free slots
        if (currentTime.getHour() < windowEnd.getHour()) {
            freeSlots.add(new TimeSlot(currentTime, currentTime.withHour(windowEnd.getHour())));
            System.out.println("Freeslot 1: " + currentTime + " --- " + currentTime.withHour(windowEnd.getHour()));
        }
        currentTime = currentTime.plusHours(windowEnd.getHour() - currentTime.getHour() + 10);
        for (int i = 1; i <= 70; i++) {
            // System.out.println("Current Time: " + currentTime);
            currentTime = currentTime.plusHours(24);
            freeSlots.add(new TimeSlot(currentTime, currentTime.plusHours(14)));
            System.out.println("Freeslot: " + currentTime + " --- " + currentTime.plusHours(14));
        }

        return freeSlots;
    }

    public static List<Event> loadEventsFromCSV(String filePath) {
        List<Event> loadedEvents = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                if (values.length >= 7) {
                    try {
                        LocalDateTime start = LocalDateTime.parse(values[1]);
                        Event e = new Event(
                                values[0], // name
                                start, // Date
                                start, // startTime
                                LocalDateTime.parse(values[2]), // endTime
                                Integer.parseInt(values[3]), // duration
                                values[4], // location
                                Integer.parseInt(values[5]), // travelTime
                                values[6] // status
                        );
                        if (values.length > 7 && !values[7].isEmpty())
                            e.setCategory(values[7]);
                        if (values.length > 8 && !values[8].isEmpty())
                            e.setDescription(values[8]);
                        loadedEvents.add(e);
                    } catch (Exception e) {
                        System.err.println("Skipping invalid event row: " + line);
                    }
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
        // The test data in the CSV spans from May 19 to May 25, 2024.
        // We test the schedule for a specific day from the dataset (e.g., Monday, May
        // 20)
        LocalDate testDate = LocalDate.of(2024, 5, 20);
        LocalDateTime scheduleStart = testDate.atTime(8, 0);
        LocalDateTime scheduleEnd = testDate.atTime(22, 0);

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
