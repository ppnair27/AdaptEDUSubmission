package procrastination_alg;

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