package io.github.kaisubr.photoplethysmogram_api_22.data;

import android.content.Context;

/**
 * Photoplethysmogram_API_22, file created in io.github.kaisubr.photoplethysmogram_api_22.data by Kailash Sub.
 */
public class AtherosclerosisTester {
    public static final double CORRECTION_MILD = 10, CORRECTION_M2 = 20, CORRECTION_G = 40;
    private double heartRate, peak, average, age, avgbp, systolic = 0, diastolic = 110;
    int score;

    public AtherosclerosisTester(int age, double heartRate, double peak, double average) {
        this.heartRate = heartRate;
        this.peak = peak;
        this.average = average;
        this.age = age;
        this.avgbp = 116.525*(Math.pow(getHeartRateVariability(), -0.119197));
        score();
    }

    public AtherosclerosisTester(double heartRate, double peak, double average) {
        this.heartRate = heartRate;
        this.peak = peak;
        this.average = average;
        this.avgbp = 116.525*(Math.pow(getHeartRateVariability(), -0.119197));
        score();
        while (!(systolic >= diastolic + (avgbp/1.778) )) {
            systolic++;
            diastolic = (2 * getAverageBP()) - systolic;
        }

    }

    public double getHeartRateVariability() {
        return (peak - average) * 2;
    }

    public double getAverageBP() {
        return avgbp;
    }

    public static final int NORMAL = 0, PREHYPERTENSION = 1, HYPERTENSION_1 = 2, HYPERTENSION_2 = 3, HYPERTENSION_E = 4;

    public int score() {
        //hypertension > 140/90
        if (getAverageBP() >= 145) {
            score = HYPERTENSION_E;
        } else if (getAverageBP() >= 130) {
            score = HYPERTENSION_2;
        } else if (getAverageBP() >=115) {
            score = HYPERTENSION_1;
        } else if (getAverageBP() >= 100) {
            score = PREHYPERTENSION;
        } else {
            score = NORMAL;
        }
        return score;
    }
    public int getScore() {
        return score;
    }

    public int getSystolic() { return (int) Math.round(systolic); }

    public int getDiastolic() { return (int) Math.round(diastolic); }


}
