package io.github.kaisubr.photoplethysmogram_api_22;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.github.mikephil.charting.charts.LineChart;
import iirjmaster.src.main.java.uk.me.berndporr.iirj.Butterworth;
import io.github.kaisubr.photoplethysmogram_api_22.data.AtherosclerosisTester;
import io.github.kaisubr.photoplethysmogram_api_22.data.LineChartGenerator;
import io.github.kaisubr.photoplethysmogram_api_22.data.NumberData;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.apache.commons.math3.util.Precision.EPSILON;

public class AnalysisActivity extends AppCompatActivity {

    private static final String TAG = "ANALYSIS_ACTIVITY";
    private NumberData rawData, relevant;
    private TextView filtData;
    private TextView diagRes;
    private ImageView imageView; private Bitmap imageViewBitmap;
    double mean, min, max, a, omega, phi;
    TextView accuracyRep;

    private double peaksPerMinute;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);

        double[] rawDataExtra = getIntent().getDoubleArrayExtra(PPGActivity.RAW_DATA_KEY);
        double[] subDataExtra = getIntent().getDoubleArrayExtra(PPGActivity.SUBLIST_DATA_KEY);

        rawData = new NumberData(rawDataExtra);
        relevant = new NumberData(subDataExtra);

        Log.d(TAG, "Relevant Raw Data: " + Arrays.toString(relevant.getRawData()));

        //analyze....

        //OUTPUT RAW DATA TO THE SCREEN.
        TextView data = (TextView) findViewById(R.id.data);
        data.setTextIsSelectable(true);
        data.setSelectAllOnFocus(true);
        data.setText(rawData.toString());

        //CONSTRUCT FILTERED DATA.
        filtData = (TextView) findViewById(R.id.filtered_data);
        filtData.setTextIsSelectable(true);
        filtData.setSelectAllOnFocus(true);
        //CONSTRUCT DIAGNOSIS RESULT DATA
        diagRes = (TextView) findViewById(R.id.diag_res);
        diagRes.setTextIsSelectable(true);
        diagRes.setSelectAllOnFocus(true);
        repeat = (Button) findViewById(R.id.button_repeat);
        //CONSTRUCT IMAGE GRAPH DATA
        imageView = (ImageView) findViewById(R.id.image_graph);
        //CONSTRUCT ACCURACY NOTIF
        accuracyRep = (TextView) findViewById(R.id.accuracy_rep_text);
        //FILTER.
        new FilterTask().execute();
    }


    private class FilterTask extends AsyncTask<Void, Void, Void> {

        private ProgressDialog mProgress;

        @Override
        protected void onPreExecute() {
            //Create progress dialog here and show it

            mProgress =
                    ProgressDialog.show(AnalysisActivity.this, "Loading", "Please wait while a thorough analysis is taking place...", true);
            mProgress.show();

            Log.d(TAG, "Dialogue shown!");
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Execute query here
            //filter
            Log.d(TAG, "Beginning computation!");

            Butterworth butterworth = new Butterworth();
            butterworth.highPass(4, 0, 10);

            //smooth
            relevant.computeCubicSplineInterpolation();

            if (relevant.getRFData() == null) {
                peaksPerMinute = 0;
                return null;
            }

            //find peaks.
            List<PointF> peaks = relevant.findPeaks();
            //find frequency of x-values of peaks = heart rate
            double[] xPeaks = new double[peaks.size()];
            for (int i = 0; i < peaks.size(); i++) xPeaks[i] = peaks.get(i).x;
            //let tick = an x-value; peak = a peak/pulse/heartbeat on graph
            double ticksPerPeak = NumberData.calculateMeanAbsoluteDifference(xPeaks);
            double secondsPerTick = PPGActivity.TIME_SECONDS_CUTOFF/relevant.getRFData()[relevant.getRFData().length - 1].x;
            double peaksPerSecond = 1/(ticksPerPeak * secondsPerTick);
            peaksPerMinute = peaksPerSecond * 60; //correction factor...
            if (peaksPerMinute < 60 && peaksPerMinute > 50) peaksPerMinute+=AtherosclerosisTester.CORRECTION_MILD;
            if (peaksPerMinute < 50 && peaksPerMinute > 40) peaksPerMinute+=AtherosclerosisTester.CORRECTION_M2;
            if (peaksPerMinute < 40) peaksPerMinute += AtherosclerosisTester.CORRECTION_G;
            Log.d(TAG, "Peaks found at " + peaks.toString() + ", and ticksPerPeak = " + ticksPerPeak + ", secondsPerTick = " + secondsPerTick + ", peaksPerSecond = " + peaksPerSecond + ", peaksPerMinute = " + peaksPerMinute);

            //CALCULATE & OUTPUT NEW DATA SUMMARY, INCLUDING:
            // mean, minimum, maximum, harmonic fitter (f (t) = a cos (ω t + φ))
            mean = relevant.getMean();
            min = relevant.getMin();
            max = relevant.getMax();
            double[] harmonicFit = relevant.harmonicFit();
            a = harmonicFit[0]; //amplitude
            omega = harmonicFit[1]; //angular frequency
            phi = harmonicFit[2]; //phase shift

            //bitmap from https://developers.google.com/chart/image/
            List<Double> rfx = new ArrayList<>(), rfy = new ArrayList<>();
            for (int i = 0; i < relevant.getRFData().length; i+=4) {
                                                                                //default/wanted range max
                rfx.add(((double) relevant.getRFData()[i].x) * secondsPerTick * (100/30)); //needs to be in seconds
                rfy.add((double) relevant.getRFData()[i].y * (100/50));
            }
            try {
                imageViewBitmap = LineChartGenerator.getBitmap(rfx, rfy, 500, 350);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            mProgress.dismiss();
            Log.d(TAG, "Post execution!");
            printFilteredData();
            printDiagnosisSummary();
            printPPGSummary();
            afterExecutionListeners();
        }

    }
    Button repeat ;
    private void afterExecutionListeners() {

        repeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(AnalysisActivity.this)
                                .setTitle("Unsaved data will be lost!")
                                .setMessage("Creating a new PPG will reset the program and remove all unsaved data. You might want to export your data before creating a new PPG.")
                                .setCancelable(false)
                                .setPositiveButton("Repeat PPG", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent mStartActivity = new Intent(AnalysisActivity.this, PPGActivity.class);
                                        int mPendingIntentId = 123456;
                                        final PendingIntent mPendingIntent = PendingIntent.getActivity(AnalysisActivity.this, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                                        final AlarmManager mgr = (AlarmManager)AnalysisActivity.this.getSystemService(Context.ALARM_SERVICE);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                                                System.exit(0);
                                            }
                                        });

                                    }
                                })
                                .setNegativeButton("Export current data", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        share();
                                    }
                                })
                                .show();
                    }
                });
            }
        });
        Button export = (Button) findViewById(R.id.button_export);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                share();
            }
        });

    }

    private void printPPGSummary() {
        TextView briefRes = (TextView) findViewById(R.id.brief_res);
        briefRes.setTextIsSelectable(true);
        briefRes.setSelectAllOnFocus(true);
        briefRes.setText("Mean: " + mean + "\n" + "" +
                "Maximum: " + max + "\n" +
                "Minimum: " + min + "\n\n" +
                "Harmonic Fit (f (t) = a cos (ω t + φ)" +
                "\n\ta = " + a + "" +
                "\n\tω = " + omega + "" +
                "\n\tφ = " + phi);

        imageView.setImageBitmap(imageViewBitmap);
    }

    private void printDiagnosisSummary() {
        diagRes.setText("Average heart rate:\t" + peaksPerMinute + " beats per minute.");
        if (peaksPerMinute < 60 || peaksPerMinute > 120 || peaksPerMinute < EPSILON) possibleInaccuracy();
        AtherosclerosisTester at = new AtherosclerosisTester(peaksPerMinute, max, mean);
        diagRes.append("\nYour average blood pressure is " + at.getAverageBP() + " and your HRV Peak-Valley value is " + at.getHeartRateVariability() + " beats.");
        diagRes.append("\nThe approximated blood pressure is (systolic) " + at.getSystolic() + " / (diastolic) " + at.getDiastolic());

        switch (at.score()) {
            case AtherosclerosisTester.NORMAL:          diagRes.append("\nYou have a low risk for atherosclerosis.");    break;
            case AtherosclerosisTester.PREHYPERTENSION: diagRes.append("\nYou have a potential risk for atherosclerosis.");    break;
            case AtherosclerosisTester.HYPERTENSION_1:  diagRes.append("\nYou may have Stage 1 hypertension. Consult your doctor.");    break;
            case AtherosclerosisTester.HYPERTENSION_2:  diagRes.append("\nYou may have Stage 2 hypertension. Consult your doctor immediately.");    break;
            case AtherosclerosisTester.HYPERTENSION_E:  diagRes.append("\nYou may have severe hypertension. Consult your doctor immediately.");    break;
            default: diagRes.append("\nerror in at.score() returning " + at.score());
        }
    }

    private void possibleInaccuracy() {
        accuracyRep.setText("Caution! Possible inaccuracies (tap to view more).");
        accuracyRep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(AnalysisActivity.this)
                        .setTitle("Data inaccuracy")
                        .setMessage("Lack of light or light pollution, camera frame rate, and/or warmth of the finger has led to inaccuracies in the reported data. It is recommended to repeat the process to eliminate possible errors.")
                        .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .show();
            }
        });

        new AlertDialog.Builder(AnalysisActivity.this)
                .setTitle("Warning: Potential data inaccuracy")
                .setMessage("The program has detected lighting problems, camera frame rate, and/or warmth of the finger. This may lead to inaccurate data. It is recommended to repeat the process to eliminate possible errors.")
                .setPositiveButton("Repeat", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        repeat.performClick();
                    }
                })
                .show();
    }

    private void printFilteredData() {
        filtData.setText(Arrays.toString(relevant.getRFData()));
        Log.d(TAG, "Filtered: " + Arrays.toString(relevant.getRFData()));
//        share();
    }

    private void share() {
        final Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");        //yyyyMMdd_HHmmss
        String timeStamp = new SimpleDateFormat("yyyy MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
        StringBuilder x = new StringBuilder("");
        StringBuilder y = new StringBuilder("");
        for (int i = 0; i < relevant.getRFData().length; i++) {
            if (!(i == relevant.getRFData().length - 1)) {
                x.append(relevant.getRFData()[i].x).append(", ");
                y.append(relevant.getRFData()[i].y).append(", ");
            } else {
                x.append(relevant.getRFData()[i].x);
                y.append(relevant.getRFData()[i].y);
            }
        }
        String raw = Arrays.toString(relevant.getRawData());

        share.putExtra(Intent.EXTRA_TEXT,
                timeStamp +
                        "\n\n\n" +
                        "peaksPerMinute = " + peaksPerMinute +
                        "\nMean: " + mean + "\n" + "" +
                        "Maximum: " + max + "\n" +
                        "Minimum: " + min + "\n\n" +
                        "Harmonic Fit (f (t) = a cos (ω t + φ)" +
                        "\n\ta = " + a + "" +
                        "\n\tω = " + omega + "" +
                        "\n\tφ = " + phi +
                        "\n\n\n" + "Raw data... ...:\nraw = {" + raw.substring(1, raw.length() - 2) + "};\nListLinePlot[raw]\n\n\n" +
                        "Filtered Data... ...:\nx = {" + x + "};\ny = {" + y + "};\npatient = Transpose@{x,y};\nListLinePlot[patient, PlotTheme -> \"Scientific\"]");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startActivity(Intent.createChooser(share, "Share results"));
            }
        });
    }
}

