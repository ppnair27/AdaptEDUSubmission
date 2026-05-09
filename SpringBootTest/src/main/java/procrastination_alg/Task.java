package procrastination_alg;

import java.time.LocalDateTime;

/**
 * Tracks individual task data
 */
public class Task {
    private String name; // Task name
    private String category; // Category for the task
    private LocalDateTime dueDate; // Task due date and time
    private int userPriority; // A 1-10 rating given by the user to influence priority, defaulting to 1
    private int estimatedTime; // Estimated time to complete in minutes
    private boolean completed; // Whether the task is completed
    private String description; // Optional description of the task
    private int minutesSpent; // Time spent on the task in minutes
    private int session; // The session number of the task
    private double priorityScore; // The priority score of the task
    private int maxSessionLength; // The maximum time for a session, in minutes, defaults to 120

    /**
     * Constructor for basic task with name and due date
     * 
     * @param name    the task name
     * @param dueDate the task due date
     */
    public Task(String name, LocalDateTime dueDate) {
        this.name = name;
        this.dueDate = dueDate;
        session = 1;
        userPriority = 1;
        calculateInitialPriorityScore();
        maxSessionLength = 120;
    }

    /**
     * Task Constructor
     * 
     * @param name          the task name
     * @param dueDate       the task due date
     * @param userPriority  the user priority score
     * @param estimatedTime the estimated duration of the task
     * @param completed     true if the task is completed
     */
    public Task(String name, LocalDateTime dueDate, int userPriority, int estimatedTime, boolean completed) {
        this.name = name;
        this.dueDate = dueDate;
        this.userPriority = userPriority;
        this.estimatedTime = estimatedTime;
        this.completed = completed;
        session = 1;
        calculateInitialPriorityScore();
        maxSessionLength = 120;
    }

    /**
     * Task Constructor
     * 
     * @param name          the task name
     * @param category      the task category
     * @param dueDate       the task due date
     * @param userPriority  the user priority score
     * @param estimatedTime the estimated duration of the task
     * @param completed     true if the task is completed
     * @param description   the task description
     */
    public Task(String name, String category, LocalDateTime dueDate, int userPriority, int estimatedTime,
            boolean completed, String description) {
        this.name = name;
        this.category = category;
        this.dueDate = dueDate;
        this.userPriority = userPriority;
        this.estimatedTime = estimatedTime;
        this.completed = completed;
        this.description = description;
        session = 1;
        calculateInitialPriorityScore();
        maxSessionLength = 120;
    }

    /**
     * Constructor for task with all attributes including description
     * 
     * @param name             the task name
     * @param category         the task category
     * @param dueDate          the task due date
     * @param userPriority     the user priority score
     * @param estimatedTime    the estimated duration of the task
     * @param completed        true if the task is completed
     * @param maxSessionLength the maximum length of a session, or -1 if the task
     *                         cannot be split into sections
     * @param description      the task description
     */
    public Task(String name, String category, LocalDateTime dueDate, int userPriority, int estimatedTime,
            boolean completed, int maxSessionLength, String description) {
        this.name = name;
        this.category = category;
        this.dueDate = dueDate;
        this.userPriority = userPriority;
        this.estimatedTime = estimatedTime;
        this.completed = completed;
        this.description = description;
        session = 1;
        calculateInitialPriorityScore();
        this.maxSessionLength = maxSessionLength;
    }

    // Getters and setters for task attributes
    /**
     * Returns the maximum session length of the task
     * 
     * @return the maximum session length, in minutes
     */
    public int getMaxSessionLength() {
        return maxSessionLength;
    }

    /**
     * Sets the estimated duration of the task
     * 
     * @param duration the estimated duration, in minutes
     */
    public void setEstimatedTime(int duration) {
        estimatedTime = duration;
    }

    /**
     * sets the session number of the task
     * 
     * @param i the session number
     */
    public void setSession(int i) {
        session = i;
    }

    /**
     * Returns the session number of the task
     * 
     * @return the session number
     */
    public int getSession() {
        return session;
    }

    /**
     * Returns the name of the task
     * 
     * @return the task name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the category of the task
     * 
     * @return the task category
     */
    public String getCategory() {
        return category;
    }

    /**
     * Returns the due date of the task
     * 
     * @return the task due date
     */
    public LocalDateTime getDueDate() {
        return dueDate;
    }

    /**
     * Returns the user priority of the task
     * 
     * @return the task user priority, from 1 to 10
     */
    public int getUserPriority() {
        return userPriority;
    }

    /**
     * Returns the estimated duration of the task
     * 
     * @return the estimated task duration, in mimutes
     */
    public int getEstimatedTime() {
        return estimatedTime;
    }

    /**
     * Returns true if the task is completed
     * 
     * @return true if the task is completed
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Returns the description of the task
     * 
     * @return the task description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the number of minutes until the task is due
     * 
     * @return the number of minutes until the task is due
     */
    public long getMinutesTillDue() {
        LocalDateTime now = LocalDateTime.now();
        return java.time.Duration.between(now, dueDate).toMinutes();
    }

    /**
     * Returns the number of hours until the task is due, given a time.
     * 
     * @param day a given date
     * @return The number of hours between the day and the due date.
     */
    public long getHoursUntilDue(LocalDateTime day) {
        return java.time.Duration.between(day, dueDate).toHours();
    }

    /**
     * Calculates a time pressure value
     * 
     * @param day a given date
     * @return a double value representing the time pressure of the task
     */
    public double getTimePressure(LocalDateTime day) {
        long hours = getHoursUntilDue(day);
        if (hours <= 0) {
            return 10;
        } else {
            return 300.0 / (hours + 1) - 1;
        }
    }

    /**
     * Sets the priority to a priority score based on urgency, user priority, and
     * time between a given date and the due date
     * 
     * @param day a given date
     */
    public void calculatePriorityScore(LocalDateTime day) {
        long hoursUntilDue = getHoursUntilDue(day);
        if (hoursUntilDue <= 0) {
            priorityScore = userPriority + 2 * getTimePressure(day) - session / (getTimePressure(day) + 2) + 1000;
        } else {
            priorityScore = userPriority + 2 * getTimePressure(day) - session / (getTimePressure(day) + 2);
        }
    }

    /**
     * Sets the priority to a priority score based on urgency, user priority, and
     * time until due
     */
    private void calculateInitialPriorityScore() {
        LocalDateTime now = LocalDateTime.now();
        long hoursUntilDue = getHoursUntilDue(now);
        if (hoursUntilDue <= 0) {
            priorityScore = userPriority + 2 * getTimePressure(now) - session / (getTimePressure(now) + 2) + 1000;
        } else {
            priorityScore = userPriority + 2 * getTimePressure(now) - session / (getTimePressure(now) + 2);
        }
    }

    /**
     * Returns the priority score of the task
     * 
     * @return A double value representing the task priority.
     */
    public double getPriorityScore() {
        return priorityScore;
    }

    /**
     * Marks the task as completed
     */
    public void markAsCompleted() {
        this.completed = true;
    }

    /**
     * Tracks the task progress
     * 
     * @param minutes the number of minutes spent
     */
    public void addTimeSpent(int minutes) {
        this.minutesSpent += minutes;
    }

    /**
     * Returns the number of minutes remaining for the task
     * 
     * @return the number of remaining minutes
     */
    public int getMinutesRemaining() {
        return Math.max(0, estimatedTime - minutesSpent);
    }

    /**
     * Updates the user priority, ensuring it remains within 1 - 10
     * 
     * @param userPriority The user priority score
     */
    public void updateUserPriority(int userPriority) {
        this.userPriority = Math.max(1, Math.min(10, userPriority)); // Ensure user priority is between 1 and 10
    }

    /**
     * Updates the due date of the task
     * 
     * @param dueDate The new task due date
     */
    public void updateDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    /**
     * Updates the estimated time of the task, ensuring it remains positive
     * 
     * @param estimatedTime The new estimated time
     */
    public void updateEstimatedTime(int estimatedTime) {
        this.estimatedTime = Math.max(0, estimatedTime); // Ensure estimated time is non-negative
    }

    /**
     * Returns true if the task is overdue
     * 
     * @return true if the task is overdue
     */
    public boolean isOverdue() {
        return LocalDateTime.now().isAfter(dueDate);
    }

    @Override
    /**
     * Returns the task as a string
     */
    public String toString() {
        return (name + " " + dueDate + " " + getPriorityScore());
    }

}