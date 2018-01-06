package io.github.kaisubr.photoplethysmogram_api_22.data;

import android.graphics.PointF;
import android.util.Log;
import io.github.kaisubr.photoplethysmogram_api_22.PPGActivity;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.fitting.HarmonicCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.github.kaisubr.photoplethysmogram_api_22.PPGActivity.UNKNOWN;

/**
 * Photoplethysmogram_API_22, file created in io.github.kaisubr.photoplethysmogram_api_22 by Kailash Sub.
 * This is a useful class for properly eliminating outliers, filtering and smoothing points (and interpreting
 * its derivative), and other various statistics.
 */
public class NumberData {

    private static final double EPSILON_C = 2e-4;
    private double[] data; //raw data.
    private PointF[] rfData; //recently filtered data. use this for most processing!

    private DescriptiveStatistics stats;
    private double mean, min, max, stdDev;
    private Double[] x;
    private Double[] y;
    private static final String TAG = "NumberData";
    private static final double CUBIC_SPLINE_RESOLUTION = 4; //each time frame will be split into this many points. MUST BE WHOLE NUMBER.

    public NumberData(double[] dataSet) {
        this.data = dataSet;
        Chauvenet chauvenetOutlierTest = new Chauvenet(this);
        this.data = chauvenetOutlierTest.getCleanedData();

        stats = new DescriptiveStatistics();
        //int j = 0; //true "i" value
        List<Double> xList = new ArrayList<>();
        List<Double> yList = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            //TODO: simplify by doing !(dataSet[i] == UNKNOWN).
            if (data[i] == UNKNOWN) {
                //unnecessary.
                //Do not use x.length to find length of raw data because it leaves out ("skips") irrelevant or unknown values
                    //so x could be like [1, 2, 5, 7, 8, 9] and y WILL correspond to each x, not by index.
            } else {
                System.out.println(xList.size());
                xList.add((double) i);
                yList.add(data[i]);
                stats.addValue(data[i]);
                //x[j] = i;
                //y[j] = data[i]; //corresponds to that x value.
                //stats.addValue(data[i]);
                //j++;
            }
        }
        x = new Double[xList.size()];
        xList.toArray(x);
        y = new Double[yList.size()];
        yList.toArray(y);

        mean = stats.getMean();
        min = stats.getMin();
        max = stats.getMax();
        stdDev = stats.getStandardDeviation();
    }

    public double getRawValue(int index) {
        return data[index];
    }

    public double[] getRawData() {
        return data;
    }

    @Deprecated
    public void setData(double[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return Arrays.toString(data);
    }

    public double getMean() {
        return mean;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStandardDeviation() {
        return stdDev;
    }

    public double[] harmonicFit() {
        final WeightedObservedPoints obs = new WeightedObservedPoints();
        //obs does not allow for input
        for (int i = 0; i < data.length; i++) {
            obs.add(i, data[i]);
        }

        HarmonicCurveFitter hcf = HarmonicCurveFitter.create();

        return hcf.fit(obs.toList());
    }

    private PolynomialSplineFunction psf;
    public PointF[] computeCubicSplineInterpolation() {
        Log.w(TAG, "Calling this function may hold up the main process. Make sure intensive tasks are in a new thread.");
        Log.d(TAG, "Relevant ex x Data: " + Arrays.toString(x));
        Log.d(TAG, "Relevant ex y Data: " + Arrays.toString(y));
        SplineInterpolator si = new SplineInterpolator();

        //SEE https://en.wikipedia.org/wiki/Spline_(mathematics)#/media/File:Parametic_Cubic_Spline.svg AND https://en.wikipedia.org/wiki/Spline_(mathematics) AND https://en.wikipedia.org/wiki/Spline_interpolation
        //let psf be a Polynomial Spline Function consisting of x.length cubic polynomials, where x[i] are 'knot points'
        //let (xi, yi) be an 'interpolated' point.
        double[] xPrim = ArrayUtils.toPrimitive(x);
        double[] yPrim = ArrayUtils.toPrimitive(y);
        try {
            psf = si.interpolate(xPrim, yPrim);
        } catch (Exception e) {
            rfData = null;
            return null;
        }
        double xi[] = new double[((int)getExcludedX()[getExcludedX().length-1] * (int)CUBIC_SPLINE_RESOLUTION) - ((int)CUBIC_SPLINE_RESOLUTION-1)]; //mult. by 2 increases wanted #s by 1.
        //divide x into 2, increment i by 1
        for (int i = 0; i < xi.length; i++) {
            xi[i] = (i/CUBIC_SPLINE_RESOLUTION); //FORCE DOUBLE DIVISION //...step half the amount x did, so that xi[0] = 0.5, xi[1] = 1, and so on. (AKA x_i[i] = i/2)
            System.out.println("at " + i + ", " + xi[i]);
        }

        Log.d(TAG, Arrays.toString(xi));

        double yi[] = new double[xi.length];
        PointF[] preciseData = new PointF[xi.length];
        for (int i = 0; i < xi.length; i++) {
            yi[i] = psf.value(xi[i]);
            System.out.println(xi[i] + ", " + yi[i]);
            preciseData[i] = new PointF((float) xi[i], (float) yi[i]);
        }

        Log.d(TAG, Arrays.toString(yi));

        //rfData = yi;
        rfData = preciseData;

        return rfData;
    }

    public PointF[] getRFData() {
        return rfData;
    }

    /**
     * X and Y make up the "excluded raw data"
     */
    public double[] getExcludedX() {
        return ArrayUtils.toPrimitive(x);
    }
    public double[] getExcludedY() {
        return ArrayUtils.toPrimitive(y);
    }

    /**
     * The PolynomialSplineFunction is determined by the Cubic Spline Interpolation.
     * @return PolynomialSplineFunction assuming computeCubicSplineInterpolation() has already been called.
     */
    public PolynomialSplineFunction getPolynomialSplineFunction() {
        Log.w(TAG, "Obtaining the polynomial spline function will fail if cubic spline interpolation " +
                "was not properly calculated!");
        return psf;
    }

    /**
     * Finds the peaks within Recently Filtered (RF) Data
     * @return peaks with accuracy CUBIC_SPLINE_RESOLUTION, assuming cubic spline transform had been completed
     */
    public List<PointF> findPeaks() {
        Log.w(TAG, "Peak detection will not properly occur if the polynomial spline function was not calculated!");
        UnivariateFunction d1_psf = getPolynomialSplineFunction().derivative();
        //if d1_psf changes from positive to 0 or 0 to negative, it is a peak!
        List<PointF> peaks = new ArrayList<>();
        double pdatp = 0, cdatp; //pdatp = previous derivative at the point, cdatp = current derivative at the point
        for (double i = 0; i < (getRFData()[getRFData().length - 1]).x; i += (1/CUBIC_SPLINE_RESOLUTION)) {
            if (i == 0) {
                pdatp = d1_psf.value(i);    //((i + 1)/4, d1_psf.value(i)) <-- the point
            } else {
                cdatp = d1_psf.value(i); //math.abs required to check if close to 0...

                //if d changes from positive to negative only.
                if ((pdatp > 0) && (cdatp < 0)) {
                    System.out.println("!! NEW PEAK FOUND at x = " + (i) + " since pdatp = " + pdatp + ", cdatp = " + cdatp + " which is thusly " +
                            "(" + (pdatp > 0) + "&&" + (cdatp <= EPSILON_C) + ")" + " || " + "(" + (pdatp <= EPSILON_C) + "&&" + (cdatp < 0) + ")");
                    peaks.add(new PointF((float) i, (float) d1_psf.value(i)));
                } else {
                    System.out.println(":( NOT considered a peak at x = " + (i) + " since pdatp = " + pdatp + ", cdatp = " + cdatp + " which is thusly " +
                            "(" + (pdatp > 0) + "&&" + (cdatp <= EPSILON_C) + ")" + " || " + "(" + (pdatp <= EPSILON_C) + "&&" + (cdatp < 0) + ")");
                }

//                if ( ((pdatp > 0) && (Math.abs(cdatp) <= EPSILON_C)) || ((Math.abs(pdatp) <= EPSILON_C) && (cdatp < 0)) ) {
//                    System.out.println("!! NEW PEAK FOUND at x = " + (i) + " since pdatp = " + pdatp + ", cdatp = " + cdatp + " which is thusly " +
//                            "(" + (pdatp > 0) + "&&" + (cdatp <= EPSILON_C) + ")" + " || " + "(" + (pdatp <= EPSILON_C) + "&&" + (cdatp < 0) + ")");
//                    peaks.add(new PointF((float) i, (float) d1_psf.value(i)));
//                } else {
//                    //Not considered a peak?
//                    System.out.println(":( NOT considered a peak at x = " + (i) + " since pdatp = " + pdatp + ", cdatp = " + cdatp + " which is thusly " +
//                            "(" + (pdatp > 0) + "&&" + (cdatp <= EPSILON_C) + ")" + " || " + "(" + (pdatp <= EPSILON_C) + "&&" + (cdatp < 0) + ")");
//                }

                //after calculations, the current datp becomes the new previous datp.
                pdatp = cdatp;
            }
        }
        return peaks; //peaks.toArray(new PointF[peaks.size()]);
    }

    public static double calculateMeanAbsoluteDifference(double[] input) {
        DescriptiveStatistics differences = new DescriptiveStatistics();
        for (int i = 0; i < input.length; i++) {
            if (i == 0) {
                differences.addValue(input[i]);
            } else {
                differences.addValue(input[i] - input[i - 1]);
            }

        }
        return differences.getMean();
    }
}
