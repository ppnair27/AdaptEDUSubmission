package procrastination_alg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintains a list of tasks
 */
public class TaskManager {

    private ArrayList<Task> tasks; // List of tasks
    private ArrayList<Event> events; // List of events

    /**
     * Initializes the task manager
     */
    public TaskManager() {
        tasks = new ArrayList<>();
        events = new ArrayList<>();
    }

    /**
     * Inserts the list of tasks rom a CSV file
     * 
     * @param filePath the path to the CSV file
     */
    public void insertTaskList(String filePath) {
        for (Task task : loadTasksFromCSV(filePath)) {
            tasks.add(task);
        }
    }

    /**
     * pulls task list data from the CSV file
     * Gemini was used to assist with troubleshooting this section of code:
     * https://gemini.google.com
     * "Help me integrate the csv cleanly with the algorithm, where the algorithm
     * calls the csv values and stores them"
     * 
     * @param filePath the path to the CSV file
     * @return
     */
    private static List<Task> loadTasksFromCSV(String filePath) {
        List<Task> loadedTasks = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] values = parseCsvLine(line);
                if (values.length >= 8) {
                    try {
                        loadedTasks.add(new Task(
                                values[0], // name
                                values[1], // category
                                LocalDateTime.parse(values[2]), // dueDate
                                Integer.parseInt(values[3]), // userPriority
                                Integer.parseInt(values[4]), // estimatedTime
                                Boolean.parseBoolean(values[5]), // completed
                                Integer.parseInt(values[6]), // maximum session length
                                values[7] // description
                        ));
                    } catch (Exception e) {
                        System.err.println("Skipping invalid task row: " + line);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load tasks from " + filePath + " (" + e.getMessage() + ")");
        }
        return loadedTasks;
    }

    /**
     * Parses each line of the task csv file
     * 
     * @param line a single line of the csv
     * @return a list of data values
     */
    public static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    // Task methods
    /**
     * Adds a task to the task manager
     * 
     * @param task the new task
     */
    public void addTask(Task task) {
        tasks.add(task);
    }

    /**
     * removes a task from the task manager
     * 
     * @param task the task to remove
     */
    public void removeTask(Task task) {
        tasks.remove(task);
    }

    /**
     * Returns the list of tasks held by the task manager
     * 
     * @return the list of tasks
     */
    public List<Task> getTasks() {
        return tasks;
    }

    // Event methods
    /**
     * 
     * @param event
     */
    public void addEvent(Event event) {
        events.add(event);
    }

    public void removeEvent(Event event) {
        events.remove(event);
    }

    public List<Event> getEvents() {
        return events;
    }

    // Additional utility methods
    public void printTasksByUrgency() {
        tasks.sort((t1, t2) -> Double.compare(t2.getPriorityScore(), t1.getPriorityScore()));
        for (Task task : tasks) {
            System.out.println(task.getName() + " - Urgency: " + task.getPriorityScore());
        }
    }

    public void printTasksByDueDate() {
        tasks.sort((t1, t2) -> t1.getDueDate().compareTo(t2.getDueDate()));
        for (Task task : tasks) {
            System.out.println(task.getName() + " - Due: " + task.getDueDate());
        }
    }

    public Task getMostUrgentTask() {
        if (tasks.isEmpty())
            return null;
        Task urgent = tasks.get(0);
        for (Task task : tasks) {
            if (task.getPriorityScore() > urgent.getPriorityScore()) {
                urgent = task;
            }
        }
        return urgent;
    }

    public void sortByUrgency() {
        tasks.sort((t1, t2) -> Double.compare(t2.getPriorityScore(), t1.getPriorityScore()));
    }

    public void sortByUrgency(LocalDateTime day) {
        for (Task task : tasks) {
            task.calculatePriorityScore(day);
        }
        tasks.sort((t1, t2) -> Double.compare(t2.getPriorityScore(), t1.getPriorityScore()));
    }

    public void sortByDueDate() {
        tasks.sort((t2, t1) -> t1.getDueDate().compareTo(t2.getDueDate()));
    }

    public void printTasks() {
        for (Task task : tasks) {
            System.out.println(task.getName() + " - Due: " + task.getDueDate());
        }
    }

    public void procrastinate() {
        for (Task task : tasks) {
            task.setEstimatedTime(
                    (int) Math.round(ProcrastinationAlgorithm.getRealisticTimeInMinutes(task.getEstimatedTime())));
        }
    }

    @Override
    public String toString() {
        String toReturn = "";
        for (Task a : tasks) {
            toReturn += a.toString() + " ; ";
        }
        return toReturn;
    }
}