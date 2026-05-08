package procrastination_alg;

import java.time.LocalDateTime;

public class Task {
    private String name; // Task name
    private String category; // Category for the task
    private LocalDateTime dueDate; // Task due date and time
    private int userPriority; //
    private int estimatedTime; // Estimated time to complete in minutes
    private boolean completed; // Whether the task is completed
    private String description; // Optional description of the task
    private int minutesSpent; // Time spent on the task in minutes
    private int session;
    private double priorityScore;
    private int maxSessionLength; // The maximum time for a session, in minutes, defaults to 120

    // Constructor for basic task with name and due date
    public Task(String name, LocalDateTime dueDate) {
        this.name = name;
        this.dueDate = dueDate;
        session = 1;
        calculateInitialPriorityScore();
        maxSessionLength = 120;
    }

    // Constructor for task with all attributes
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

    // Constructor for task with all attributes including description
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
    public int getMaxSessionLength() {
        return maxSessionLength;
    }

    public void setEstimatedTime(int duration) {
        estimatedTime = duration;
    }

    public void setSession(int i) {
        session = i;
    }

    public int getSession() {
        return session;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public int getUserPriority() {
        return userPriority;
    }

    public int getEstimatedTime() {
        return estimatedTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getDescription() {
        return description;
    }

    // Returns the number of minutes until the task is due
    public long getMinutesTillDue() {
        LocalDateTime now = LocalDateTime.now();
        return java.time.Duration.between(now, dueDate).toMinutes();
    }

    // Returns the number of hours until the task is due
    public long getHoursUntilDue(LocalDateTime day) {
        // Temporary time for testing: 4/25/2024
        // LocalDateTime now = LocalDateTime.now();
        // LocalDateTime now = LocalDateTime.of(2024, 4, 25, 12, 30);
        return java.time.Duration.between(day, dueDate).toHours();
    }

    // Calculates a priority score based on urgency, user priority, and time until
    // due date
    public double getTimePressure(LocalDateTime day) {
        long hours = getHoursUntilDue(day);
        if (hours <= 0) {
            return 10;
        } else {
            return 300.0 / (hours + 1) - 1;
        }
    }

    public void calculatePriorityScore(LocalDateTime day) {
        long hoursUntilDue = getHoursUntilDue(day);
        if (hoursUntilDue <= 0) {
            priorityScore = userPriority + 2 * getTimePressure(day) - session / (getTimePressure(day) + 2) + 1000;
        } else {
            priorityScore = userPriority + 2 * getTimePressure(day) - session / (getTimePressure(day) + 2);
        }
    }

    private void calculateInitialPriorityScore() {
        LocalDateTime now = LocalDateTime.now();
        long hoursUntilDue = getHoursUntilDue(now);
        if (hoursUntilDue <= 0) {
            priorityScore = userPriority + 2 * getTimePressure(now) - session / (getTimePressure(now) + 2) + 1000;
        } else {
            priorityScore = userPriority + 2 * getTimePressure(now) - session / (getTimePressure(now) + 2);
        }
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    // Marks the task as completed
    public void markAsCompleted() {
        this.completed = true;
    }

    // progress tracking
    public void addTimeSpent(int minutes) {
        this.minutesSpent += minutes;
    }

    public int getMinutesRemaining() {
        return Math.max(0, estimatedTime - minutesSpent);
    }

    public void updateUserPriority(int userPriority) {
        this.userPriority = Math.max(1, Math.min(10, userPriority)); // Ensure user priority is between 1 and 10
    }

    public void updateDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public void updateEstimatedTime(int estimatedTime) {
        this.estimatedTime = Math.max(0, estimatedTime); // Ensure estimated time is non-negative
    }

    // overdo
    public boolean isOverdue() {
        return LocalDateTime.now().isAfter(dueDate);
    }

    @Override
    public String toString() {
        return (name + " " + dueDate + " " + getPriorityScore());
    }

}