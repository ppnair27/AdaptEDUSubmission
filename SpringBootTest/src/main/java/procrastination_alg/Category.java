package procrastination_alg;

/**
 * Tracks the category name, average estimated time, typ
 * Category
 */
public class Category {
    private String name; // The category name
    private int avgEstimatedTime; // The average estimated time, in minutes
    private int instanceEstimatedTime; // The estimated time of this instance of the category, in minutes
    private int avgRealTime; // The average real time, in minutes
    private int instanceRealTime; // The real time of this instance of the category, in minutes
    private double avgProcrastinationFactor; // The average procrastination factor, as a multimplier to estimated time
                                             // to get real time
    private double instanceProcrastinationFactor; // The procrastination factor of this instance of the gategory
    private int numRecordedInstances; // The number of times data for this category has been recorded

    /**
     * Creates the category
     * 
     * @param name                     category name
     * @param avgEstimatedTime         average estimated time for the category, in
     *                                 minutes
     * @param avgRealTime              average real time for the category, in
     *                                 minutes
     * @param avgProcrastinationFactor average procrastination factor, as a
     *                                 multimplier to estimated time to get real
     *                                 time
     * @param numRecordedInstances     total number of times data for this category
     *                                 has been recorded
     */
    public Category(String name, int avgEstimatedTime, int avgRealTime, double avgProcrastinationFactor,
            int numRecordedInstances) {
        this.name = name;
        this.avgEstimatedTime = avgEstimatedTime;
        instanceEstimatedTime = avgEstimatedTime;
        this.avgRealTime = avgRealTime;
        instanceRealTime = avgRealTime;
        this.avgProcrastinationFactor = avgProcrastinationFactor;
        instanceProcrastinationFactor = avgProcrastinationFactor;
        this.numRecordedInstances = numRecordedInstances;
    }

    private void enterEstimatedTime(int estimatedTime) {
        instanceEstimatedTime = (int) (estimatedTime / avgProcrastinationFactor); // Divides by the previously recorded
                                                                                  // procrastination factor, as this
                                                                                  // assumes that the inputted value is
                                                                                  // the original estimated time
                                                                                  // multiplied by the procrastination
                                                                                  // factor, due to the way in which we
                                                                                  // are storing data.
    }

    private void enterRealTime(int realTime) {
        instanceRealTime = realTime;
    }

    private void calculateProcrastinationFactor() {
        instanceProcrastinationFactor = 1.0 * instanceEstimatedTime / instanceRealTime;
    }

    private void calculateAverages() {
        avgEstimatedTime = ((numRecordedInstances * avgEstimatedTime) + (instanceEstimatedTime))
                / (numRecordedInstances + 1);
        avgRealTime = ((numRecordedInstances * avgRealTime) + (instanceRealTime)) / (numRecordedInstances + 1);
        avgProcrastinationFactor = ((numRecordedInstances * avgProcrastinationFactor) + (instanceProcrastinationFactor))
                / (numRecordedInstances + 1);
        numRecordedInstances++;
    }

    /**
     * captures and updates category data
     * 
     * @param estimatedTime in minutes
     * @param realTime      in minutes
     */
    public void captureData(int estimatedTime, int realTime) {
        enterEstimatedTime(estimatedTime);
        enterRealTime(realTime);
        calculateProcrastinationFactor();
        calculateAverages();
    }

    /**
     * get data
     * 
     * @return category name
     */
    public String getName() {
        return name;
    }

    /**
     * get data
     * 
     * @return avereage estimated time, in minutes
     */
    public int getAvgEstimatedTime() {
        return avgEstimatedTime;
    }

    /**
     * get data
     * 
     * @return average real time, in minutes
     */
    public int getAvgRealTime() {
        return avgRealTime;
    }

    /**
     * gtet data
     * 
     * @return average procrastination factor
     */
    public double getAvgProcrastinationFactor() {
        return avgProcrastinationFactor;
    }

    /**
     * get data
     * 
     * @return number of recorded instances of the category
     */
    public int getNumRecordedInstances() {
        return numRecordedInstances;
    }
}
