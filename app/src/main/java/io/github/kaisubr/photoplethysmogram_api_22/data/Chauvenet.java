package io.github.kaisubr.photoplethysmogram_api_22.data;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.List;

import static io.github.kaisubr.photoplethysmogram_api_22.PPGActivity.UNKNOWN;

/**
 * Photoplethysmogram_API_22, file created in io.github.kaisubr.photoplethysmogram_api_22.data by Kailash Sub.
 * Note: must be performed before filtration/smoothing.
 * Warning: will dismiss first d seconds of the input.
 */
public class Chauvenet {
    private NumberData input;
    private double[] cleanedData;

    public Chauvenet(NumberData input) {
        this.input = input;

        //Perform modified Chauvenet's Criterion Test on the RAW y-value data.
        cleanedData = new double[input.getRawData().length];

        //get RAW statistics.
        DescriptiveStatistics stats = new DescriptiveStatistics();

        for (int i = 0; i < input.getRawData().length; i++) {
            stats.addValue(input.getRawValue(i));
        }

        double mean = stats.getMean();
        double stdDeviation = stats.getStandardDeviation();

        NormalDistribution nd = new NormalDistribution(mean, stdDeviation);
        for (int i = 0; i < input.getRawData().length; i++) {
            if (i > 0) {
                double probabilityDensity = nd.density(input.getRawValue(i));
                double multRes = probabilityDensity * input.getRawData().length;
                if (multRes < 0.5) {
                    //don't remove -- set to UNKNOWN.
                    System.out.println(i + ", " + input.getRawValue(i) + " is an OUTLIER!");
                    cleanedData[i] = UNKNOWN;
                } else {
                    System.out.println(i + ", " + input.getRawValue(i) + " is not an outlier for a probability denisyt of " + probabilityDensity + " so  multRes = " + multRes);
                    cleanedData[i] = input.getRawValue(i);
                }
            } else {
                //skip
            }
        }
    }

    public double[] getCleanedData() {
        return cleanedData;
    }
}
