package procrastination_alg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProcrastinationAlgorithm {

    // --- Model Coefficients ---
    // These values are derived from analyzing the JOSSE dataset.
    // They represent the relationship between estimated and actual effort in hours.
    // NOTE: Run the `analyze_effort.py` script to get the most accurate values from
    // your data.

    // The SLOPE (or "underestimation factor"). A value of 1.25 means tasks
    // typically take 25% longer than estimated, plus the fixed intercept time.
    private static final double EFFORT_SLOPE = 1.5000; // Updated to a procrastination multiplier

    // The INTERCEPT (in hours). This represents a fixed "startup cost" for any
    // task,
    // regardless of its size (e.g., context switching, setup, understanding the
    // task).
    // A value of 0.49 means about 29 minutes of fixed overhead.
    private static final double EFFORT_INTERCEPT_HOURS = 0.0000;

    /**
     * Adjusts a user's estimated time for a task to a more realistic duration.
     * The model is based on a linear regression of thousands of real-world software
     * tasks.
     *
     * @param estimatedTimeInMinutes The user's estimate of how long the task will
     *                               take, in minutes.
     * @return A more realistic task duration in minutes, accounting for common
     *         estimation biases.
     */
    public static double getRealisticTimeInMinutes(double estimatedTimeInMinutes) {
        if (estimatedTimeInMinutes <= 0) {
            return 0;
        }

        // Convert the user's estimate from minutes to hours to match the model's units.
        double estimatedTimeInHours = estimatedTimeInMinutes / 60.0;

        // Apply the linear regression formula: y = mx + b
        // realistic_hours = slope * estimated_hours + intercept
        double realisticTimeInHours = (estimatedTimeInHours * EFFORT_SLOPE) + EFFORT_INTERCEPT_HOURS;

        // Convert the realistic time back to minutes.
        double realisticTimeInMinutes = realisticTimeInHours * 60.0;

        // Ensure time is not less than original, and round up to the nearest 15 minutes
        // This keeps the scheduling chunks cleanly aligned to the quarter-hour.
        double finalMinutes = Math.max(estimatedTimeInMinutes, realisticTimeInMinutes);
        return Math.ceil(finalMinutes / 15.0) * 15.0;
    }

    /**
     * Adjusts a user's estimated time for a task to a more realistic duration.
     * The model is based on stored category data thet the user inputs over time.
     *
     * @param estimatedTimeInMinutes The user's estimate of how long the task will
     *                               take, in minutes.
     * @param categoryName           The name of the category of the task
     * @return A more realistic task duration in minutes, accounting for common
     *         estimation biases.
     */
    public static double getRealisticTimeInMinutes(double estimatedTimeInMinutes, String categoryName) {
        if (estimatedTimeInMinutes <= 0) {
            return 0;
        }
        List<Category> loadedCategories = loadEventsFromCSV(
                "SpringBootTest/src/main/java/procrastination_alg/categoryProcrastinationRates.csv");
        boolean containsCategory = false;
        double procrastinationRate = EFFORT_SLOPE;
        for (Category cat : loadedCategories) {
            if (cat.getName() == categoryName) {
                procrastinationRate = cat.getAvgProcrastinationFactor();
                containsCategory = true;
            }
        }
        if (!containsCategory) {
            return getRealisticTimeInMinutes(estimatedTimeInMinutes);
        }
        double realisticTimeInMinutes = estimatedTimeInMinutes * procrastinationRate;
        // Ensure time is not less than original, and round up to the nearest 15 minutes
        // This keeps the scheduling chunks cleanly aligned to the quarter-hour.
        double finalMinutes = Math.max(estimatedTimeInMinutes, realisticTimeInMinutes);
        return Math.ceil(finalMinutes / 15.0) * 15.0;
    }

    /**
     * Loads a list of events from a CSV file.
     * Gemini was used to assist with troubleshooting this section of code:
     * https://gemini.google.com
     * "Help me integrate the csv cleanly with the algorithm, where the algorithm
     * calls the csv values and stores them"
     * Later manually adapted to use a category class
     * 
     * @param filePath The file path to access the CSV file
     * @return
     */
    public static List<Category> loadEventsFromCSV(String filePath) {
        List<Category> loadedCategories = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                if (values.length >= 5) {
                    try {
                        int avEsTi = Integer.parseInt(values[2]);
                        int avReTi = Integer.parseInt(values[3]);
                        double avFa = Double.parseDouble(values[4]);
                        int numRec = Integer.parseInt(values[1]);
                        Category e = new Category(
                                values[0], // name
                                avEsTi, // Average estimated time
                                avReTi, // Average real time
                                avFa, // Average factor
                                numRec // number of data points
                        );
                        loadedCategories.add(e);
                    } catch (Exception e) {
                        System.err.println("Skipping invalid event row: " + line);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load events from " + filePath + " (" + e.getMessage() + ")");
        }
        return loadedCategories;
    }

    public static void main(String[] args) {
        System.out.println("--- Effort Adjustment Model ---");
        System.out.println(
                "This model adjusts a user's time estimate to a more realistic value based on real-world data.");
        System.out.printf("Based on the formula: realistic_minutes = (estimated_minutes * %.2f) + %.0f minutes\n\n",
                EFFORT_SLOPE, EFFORT_INTERCEPT_HOURS * 60);

        int[] testEstimatesInMinutes = { 15, 30, 60, 90, 120, 240 };

        for (int estimate : testEstimatesInMinutes) {
            double adjustedTime = getRealisticTimeInMinutes(estimate);
            double difference = adjustedTime - estimate;

            System.out.printf("User Estimate: %d minutes (~%.1f hours)\n", estimate, estimate / 60.0);
            System.out.printf(" -> Adjusted Realistic Time: %.0f minutes (~%.1f hours)\n", adjustedTime,
                    adjustedTime / 60.0);
            System.out.printf("    (An increase of %.0f minutes)\n\n", difference);
        }
    }
}