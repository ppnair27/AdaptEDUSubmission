package procrastination_alg;
// UUID is nessisary to generate unique identifiers for each event, which is important for managing and referencing events in the calendar application. It allows us to easily identify and manipulate specific events without confusion, especially when there are multiple events with similar names or attributes.

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class Event {

    private String id; // unique identifier for the event
    private String name; // name of event
    private LocalDateTime date; // date of the event
    private LocalDateTime startTime; // the start time of the event
    private LocalDateTime endTime; // the end time of the event
    private LocalDateTime dueDate; // the due date of the event, only if the event is an assignment
    private int duration; // duration of the event, in minutes.
    private String location; // location of the event
    private int travelTime; // travel time for the event
    private String status; // shows if this is a block that cannot move or if it is an optional event
    private String description; // description or notes about the event
    private String category; // category or color for visual organization
    private List<Integer> reminderMinutes; // reminder times in minutes before event
    private String recurrence; // recurrence pattern (NONE, DAILY, WEEKLY, MONTHLY)
    private int session;
    private double priorityScore; // priority score for incoming tasks

    public Event(String name, LocalDateTime date, LocalDateTime startTime, LocalDateTime endTime, int duration,
            String location, int travelTime, String status) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.location = location;
        this.travelTime = travelTime;
        this.status = status;
        this.reminderMinutes = new ArrayList<>();
        this.recurrence = "NONE";
        session = 1;
        priorityScore = 1;
        dueDate = LocalDateTime.MAX;
    }

    public Event(String name, LocalDateTime date, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime dueDate,
            int duration,
            String location, int travelTime, String status) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.location = location;
        this.travelTime = travelTime;
        this.status = status;
        this.reminderMinutes = new ArrayList<>();
        this.recurrence = "NONE";
        session = 1;
        priorityScore = 1;
        this.dueDate = dueDate;
    }

    public Event(String name, LocalDateTime date, LocalDateTime startTime, LocalDateTime endTime, int duration,
            String location, int travelTime, String status, double priorityScore) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.location = location;
        this.travelTime = travelTime;
        this.status = status;
        this.reminderMinutes = new ArrayList<>();
        this.recurrence = "NONE";
        session = 1;
        this.priorityScore = priorityScore;
        dueDate = LocalDateTime.MAX;
    }

    // Constructor for event with only name and date (all day event)
    public Event(String name, LocalDateTime date) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.date = date;
        this.reminderMinutes = new ArrayList<>();
        this.recurrence = "NONE";
        session = 1;
        priorityScore = 1;
        dueDate = LocalDateTime.MAX;
    }

    // Constructor for event with name, date, and start and end time
    public Event(String name, LocalDateTime date, LocalDateTime startTime, LocalDateTime endTime) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reminderMinutes = new ArrayList<>();
        this.recurrence = "NONE";
        session = 1;
        priorityScore = 1;
        dueDate = LocalDateTime.MAX;
    }

    public Event(String name, LocalDateTime date, LocalDateTime startTime, LocalDateTime endTime,
            double priorityScore) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reminderMinutes = new ArrayList<>();
        this.recurrence = "NONE";
        session = 1;
        this.priorityScore = priorityScore;
        dueDate = LocalDateTime.MAX;
    }

    public Event(String name, LocalDateTime date, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime dueDate,
            double priorityScore) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reminderMinutes = new ArrayList<>();
        this.recurrence = "NONE";
        session = 1;
        this.priorityScore = priorityScore;
        this.dueDate = dueDate;
    }

    // Constructor for event with name, date, and duration
    public Event(String name, LocalDateTime date, int duration) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.date = date;
        this.duration = duration;
        this.reminderMinutes = new ArrayList<>();
        this.recurrence = "NONE";
        session = 1;
        priorityScore = 1;
        dueDate = LocalDateTime.MAX;
    }

    // Getters and Setters for event attributes
    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    public void setSession(int i) {
        session = i;
    }

    public int getSession() {
        return session;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getTravelTime() {
        return travelTime;
    }

    public void setTravelTime(int travelTime) {
        this.travelTime = travelTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<Integer> getReminderMinutes() {
        return reminderMinutes;
    }

    public void addReminder(int minutes) {
        this.reminderMinutes.add(minutes);
    }

    public void removeReminder(int minutes) {
        this.reminderMinutes.remove(Integer.valueOf(minutes));
    }

    public String getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(String recurrence) {
        this.recurrence = recurrence;
    }

    // Calendar utility methods
    public boolean conflictsWith(Event other) {
        if (this.startTime == null || this.endTime == null ||
                other.startTime == null || other.endTime == null) {
            return false;
        }
        return !(this.endTime.isBefore(other.startTime) ||
                this.startTime.isAfter(other.endTime));
    }

    public boolean isAllDayEvent() {
        return this.startTime == null && this.endTime == null;
    }

    public boolean hasStarted(LocalDateTime now) {
        return now.isAfter(startTime) || now.isEqual(startTime);
    }

    public boolean hasEnded(LocalDateTime now) {
        return now.isAfter(endTime);
    }

    // public void generateLatestNight(Task latestTask) {

    // }

    @Override
    public String toString() {
        return "Event{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", date=" + date +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", location='" + location + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

}