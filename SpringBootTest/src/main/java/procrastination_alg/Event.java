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
    private int session; // Tracks the session number of the event, used when translating tasks to
                         // events.
    private double priorityScore; // priority score for incoming tasks

    /**
     * Constructs an event
     * 
     * @param name       The name of the event
     * @param date       The date associated with the event
     * @param startTime  The starting time and date of the event
     * @param endTime    The ending time and date of the event
     * @param duration   The duration of the event
     * @param location   A string representing the location of the event
     * @param travelTime The travel time for the event
     * @param status     The movable status of the event
     */
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

    /**
     * Constructs an event
     * 
     * @param name       The name of the event
     * @param date       The date associated with the event
     * @param startTime  The starting time and date of the event
     * @param endTime    The ending time and date of the event
     * @param dueDate    The due date of the event, for task-event conversion.
     * @param duration   The duration of the event
     * @param location   A string representing the location of the event
     * @param travelTime The travel time for the event
     * @param status     The movable status of the event
     */
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

    /**
     * Constructs an event
     * 
     * @param name          The name of the event
     * @param date          The date associated with the event
     * @param startTime     The starting time and date of the event
     * @param endTime       The ending time and date of the event
     * @param duration      The duration of the event
     * @param location      A string representing the location of the event
     * @param travelTime    The travel time for the event
     * @param status        The movable status of the event
     * @param priorityScore The priority score assosciated with the event, used for
     *                      task-event conversion.
     */
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

    /**
     * Constructor for event with only name and date (all day event)
     * 
     * @param name The name of the event
     * @param date The date associated with the event
     */
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

    /**
     * Constructor for event with name, date, and start and end time
     * 
     * @param name      The name of the event
     * @param date      The date associated with the event
     * @param startTime The starting time and date of the event
     * @param endTime   The ending time and date of the event
     */
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

    /**
     * Constructs an event.
     * 
     * @param name          The name of the event
     * @param date          The date associated with the event
     * @param startTime     The starting time and date of the event
     * @param endTime       The ending time and date of the event
     * @param priorityScore The priority score assosciated with the event, used for
     *                      task-event conversion.
     */
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

    /**
     * Constructs an event.
     * 
     * @param name          The name of the event
     * @param date          The date associated with the event
     * @param startTime     The starting time and date of the event
     * @param endTime       The ending time and date of the event
     * @param dueDate       The due date of the event, for task-event conversion.
     * @param priorityScore The priority score assosciated with the event, used for
     *                      task-event conversion.
     */
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

    /**
     * Constructor for event with name, date, and duration
     * 
     * @param name     The name of the event
     * @param date     The date associated with the event
     * @param duration The duration of the event
     */
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
    /**
     * Returns the due date of the event.
     * 
     * @return The due date, as a LocalDateTime
     */
    public LocalDateTime getDueDate() {
        return dueDate;
    }

    /**
     * Returns the priority score
     * 
     * @return
     */
    public double getPriorityScore() {
        return priorityScore;
    }

    /**
     * Sets the session number of the event
     * 
     * @param i The session number, an integer greater than 0
     */
    public void setSession(int i) {
        session = i;
    }

    /**
     * Returns the session number of the event
     * 
     * @return the session number, an integer greater than 0
     */
    public int getSession() {
        return session;
    }

    /**
     * Returns the ID of the event
     * 
     * @return A unique string representing the event.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the name of the event
     * 
     * @return the name of the event
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the event
     * 
     * @param name the name of the event
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the date of the event
     * 
     * @return the date of the event, as a LocalDateTime
     */
    public LocalDateTime getDate() {
        return date;
    }

    /**
     * Sets the date of the event
     * 
     * @param date The date at which the event is ocurring, as a LocalDateTime
     */
    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    /**
     * Returns the start date and time of the event
     * 
     * @return The start date and time of the event, as a LocalDateTime
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * Sets the start date and time of the event
     * 
     * @param startTime The start date and time of the event, as a LocalDateTime
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * Returns the end date and time of the event
     * 
     * @return The start end and time of the event, as a LocalDateTime
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /**
     * Sets the end date and time of the event
     * 
     * @param endTime The end date and time of the event, as a LocalDateTime
     */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    /**
     * Returns the duration of the event, in minutes
     * 
     * @return the duration in minutes
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Sets the duration of the event, in minutes
     * 
     * @param duration the duration in minutes
     */
    public void setDuration(int duration) {
        this.duration = duration;
    }

    /**
     * Returns the location of the event
     * 
     * @return the location, as a string
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the location of the event
     * 
     * @param location the location, as a string
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Returns the planned travel time of the event, in minutes
     * 
     * @return the planned travel time, in minutes
     */
    public int getTravelTime() {
        return travelTime;
    }

    /**
     * Sets the planned travel time of the event, in minutes
     * 
     * @param travelTime the planned travel time, in minutes
     */
    public void setTravelTime(int travelTime) {
        this.travelTime = travelTime;
    }

    /**
     * Returns the status of the event
     * 
     * @return the status, as a string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status of the event
     * 
     * @param status the status, as a string
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the event descriptiom
     * 
     * @return the event description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the event description
     * 
     * @param description the event description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the category of the event
     * 
     * @return the category, as a string
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the category of the event
     * 
     * @param category The category, as a string
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Returns the reminder times of the event
     * 
     * @return the reminder times, as a list of integers
     */
    public List<Integer> getReminderMinutes() {
        return reminderMinutes;
    }

    /**
     * Adds a reminder time to the list of reminders
     * 
     * @param minutes The number of minutes before the event
     */
    public void addReminder(int minutes) {
        this.reminderMinutes.add(minutes);
    }

    /**
     * Removes a reminder time from the list of integers
     * 
     * @param minutes The number of minutes before the event
     */
    public void removeReminder(int minutes) {
        this.reminderMinutes.remove(Integer.valueOf(minutes));
    }

    /**
     * Returns the recurrence of the event
     * 
     * @return the recurrence, as a string (NONE, DAILY, WEEKLY, MONTHLY)
     */
    public String getRecurrence() {
        return recurrence;
    }

    /**
     * Sets the recurrence of the event
     * 
     * @param recurrence the recurrence, as a string (NONE, DAILY, WEEKLY, MONTHLY)
     */
    public void setRecurrence(String recurrence) {
        this.recurrence = recurrence;
    }

    // Calendar utility methods
    /**
     * Checks is this event conflicts with a given event.
     * 
     * @param other
     * @return true if the events conflict
     */
    public boolean conflictsWith(Event other) {
        if (this.startTime == null || this.endTime == null ||
                other.startTime == null || other.endTime == null) {
            return false;
        }
        return !(this.endTime.isBefore(other.startTime) ||
                this.startTime.isAfter(other.endTime));
    }

    /**
     * Checks if the event lasts all day
     * 
     * @return true if the event lasts all day
     */
    public boolean isAllDayEvent() {
        return this.startTime == null && this.endTime == null;
    }

    /**
     * Checks if the event has already started
     * 
     * @param now the current time
     * @return true if the current time is after or at the start of the event
     */
    public boolean hasStarted(LocalDateTime now) {
        return now.isAfter(startTime) || now.isEqual(startTime);
    }

    /**
     * Checks if the event has ended
     * 
     * @param now the current time
     * @return true if the current time is after the event end time
     */
    public boolean hasEnded(LocalDateTime now) {
        return now.isAfter(endTime);
    }

    @Override
    /**
     * Will return a string representing the event, reporting the id, name, date,
     * start time, end time, location, and status of the event
     */
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