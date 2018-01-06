package io.github.kaisubr.photoplethysmogram_api_22;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import io.github.kaisubr.photoplethysmogram_api_22.cam.CameraPreview;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class PPGActivity extends AppCompatActivity {

    private static final String TAG = "PPGActivity";

    public Camera mCam;
    private Button startStop;
    private static CameraPreview mCameraPreview;
    private FrameLayout imgPreviewFrameLayout;
    public static ImageView bitmapFrame;
    public static boolean started = false;

    public static LineChart lineChart;

    public static Handler UIHandler;


    public static final String SUBLIST_DATA_KEY = "sublist";
    public static final String RAW_DATA_KEY = "raw";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ppg);

        UIHandler = new Handler(Looper.getMainLooper());

        if (!hasCamera()) {
            new AlertDialog.Builder(this)
                    .setTitle("Device not supported")
                    .setMessage("You need to have a camera to use this app")
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).show();
        } else {
            //set up camera for previewing purposes
            mCam = getCamera();
            mCameraPreview = new CameraPreview(this, mCam);
            lineChart = (LineChart) findViewById(R.id.hsvChart);
        }

        Toast t = Toast.makeText(PPGActivity.this, "Place finger on back camera and press \"Start\"",
                Toast.LENGTH_LONG);
        t.setGravity(Gravity.TOP, Gravity.CENTER_HORIZONTAL, 125);
        //get toast layout, then get its corresponding TextView, then increase the size
        ((TextView) ((LinearLayout) t.getView()).getChildAt(0)).setTextSize(15);
        t.show();

        startStop = (Button) findViewById(R.id.button_start_stop);
        startStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!started) {
                    startStop.setText("Stop and Compile NumberData");
                    readCamera();
                    //started = true; set by readCamera();
                } else {
                    //stop analysis
                    startStop.setText("Please wait...");
                    startStop.setEnabled(false);
                    abruptStopReadingCamera();
                    sendDataForCompilation(entries); //eg avg heart rate, myocardial ischemia
                }
            }
        });
    }

    private static float[] hsv;
    private final Timer grapher = new Timer();
    private final List<Double> entries = new ArrayList<>();

    public static final double TIME_SECONDS_CUTOFF = 30;
    final int    TIME_INCREMENT_MS  = 143; //testing purposes: 33  ||67(r),15,143(y)
    final double TIME_INCREMENT_SEC = TIME_INCREMENT_MS * 0.0010101; //testing purposes: 0.033333 //time increment constant: every 33 ms. (30 times a second) (every 0.03333 seconds)

    private void readCamera() {
        //the frame
        imgPreviewFrameLayout = (FrameLayout) findViewById(R.id.camera_preview);

        //bitmap child
        bitmapFrame = (ImageView) findViewById(R.id.bitmap_frame_preview);

        Log.d(TAG, "Started");
        started = true;
        imgPreviewFrameLayout.addView(mCameraPreview);

        final List<Entry> entryList = new ArrayList<>();

        final LineDataSet hueLineDataSet = new LineDataSet(null, "Hue");
        //final LineDataSet brightnessDataSet = new LineDataSet(null, "Brightness");
        final LineData hueLineData = new LineData(hueLineDataSet);
        //final LineData brightnessLineData = new LineData(brightnessDataSet);

        hueLineDataSet.setDrawValues(false); hueLineDataSet.setDrawCircles(false);
        //brightnessDataSet.setDrawValues(false); brightnessDataSet.setDrawCircles(false);

        hueLineDataSet.addEntry(new Entry((float) 0, 0));
        //brightnessDataSet.addEntry(new Entry((float) 0, 0));

        lineChart.setData(hueLineData); //lineChart.setData(brightnessLineData);
        lineChart.invalidate();


        final double[] time = {0}; //time in SECONDS.
        final float[] lastRecordedHue = {-1};
        grapher.schedule(new TimerTask() {
            @Override
            public void run() {
                if (hsv == null) return;

                //move to x, for previous time value

                //update
                time[0] += TIME_INCREMENT_SEC;
                float recordedHue = hsv[2] * 100; //hue (intensity), saturation ('whiteness'), value (brightness). testing purposes: hsv[0]
                if (lastRecordedHue[0] == -1){
                    //not initialized yet
                    lastRecordedHue[0] = recordedHue;
                    //entry added later.
                } else if (lastRecordedHue[0] == recordedHue) {
                    //a repeat, thus an unknown which will be classified by the cubic spline transform. Make the value null.
                    entries.add(null);
                    return;
                } else {
                    //its different.
                    lastRecordedHue[0] = recordedHue;
                    //entry added later.
                }

                //lineData.addEntry(new Entry((float) time[0], 1), 0);
                hueLineDataSet.addEntry(new Entry((float) time[0], recordedHue));
                //brightnessDataSet.addEntry(new Entry((float) time[0], hsv[2]));
                entries.add((double) recordedHue);

                hueLineData.notifyDataChanged();
                //brightnessLineData.notifyDataChanged();

                lineChart.notifyDataSetChanged();

                //Move the line chart once x reaches over 20
                lineChart.setVisibleXRangeMaximum(200); //200
                lineChart.setVisibleYRangeMaximum(50, YAxis.AxisDependency.LEFT); //500
                lineChart.setVisibleYRangeMinimum(30, YAxis.AxisDependency.LEFT); //300
                lineChart.moveViewToX(hueLineData.getEntryCount());

                runOnUI(new Runnable() {
                    @Override
                    public void run() {
                        lineChart.invalidate();
                    }
                });

                //check every 0.5 seconds if hrv is computable, stop after 30,000 ms //FIXME: outdated.
                if (time[0] <= TIME_SECONDS_CUTOFF /* seconds */) {
                    System.out.println(time[0] + " > " + (20) + " ... ");
                    if (hsv[2] <= 0.15) {
                        abruptStopReadingCamera();
                        //grapher.cancel(); is called by abruptStopReadingCamera();

                        final AlertDialog.Builder b = new AlertDialog.Builder(PPGActivity.this)
                                .setCancelable(false)
                                .setTitle("Lighting is insufficient")
                                .setMessage("The PPG cannot be drawn in dark environments. Try again in a brighter environment.\nhsv[2] = " + hsv[2] + " <= 15%")
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int id) {
                                        Intent i = new Intent(PPGActivity.this, PPGActivity.class);
                                        PPGActivity.this.startActivity(i);
                                    }
                                });

                        runOnUI(new Runnable() {
                            @Override
                            public void run() {
                                b.show();
                            }
                        });

                    } else if (hsv[2] >= 0.50) {
                        abruptStopReadingCamera();

                        final AlertDialog.Builder b = new AlertDialog.Builder(PPGActivity.this)
                                .setCancelable(false)
                                .setTitle("Reposition finger")
                                .setMessage("Please reposition your finger to completely cover the camera lens. Press firmly to avoid excess light being picked up by the camera. \nhsv[2] = " + hsv[2] + " >= 50%")
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int id) {
                                        Intent i = new Intent(PPGActivity.this, PPGActivity.class);
                                        PPGActivity.this.startActivity(i);
                                    }
                                });

                        runOnUI(new Runnable() {
                            @Override
                            public void run() {
                                b.show();
                            }
                        });
                    } else {
                        Log.d(TAG, String.valueOf(entries.get(entries.size() - 1)));
                    }
                } else {
                    //30 sec elapsed. analyze data.
                    Log.d(TAG, "DONE!!!");

                    abruptStopReadingCamera();
                    runOnUI(new Runnable() {
                        @Override
                        public void run() {
                            sendDataForCompilation(entries);
                        }
                    });
                }
            }
        }, 0, TIME_INCREMENT_MS); //30 times a second (repeat every 33 milliseconds)(1000/30)
    }

    private static Bitmap lCurrentBitmap;
    public static void onNewFrame() {
        if (!started) return; //ensures that readCamera() was called

        bitmapFrame.setImageBitmap(mCameraPreview.currentBitmap); //opt

        lCurrentBitmap = mCameraPreview.currentBitmap;
        //now we can do what we want!
        int rgb = getAverageRGB(lCurrentBitmap);
        int red = Color.red(rgb);
        int green = Color.green(rgb);
        int blue = Color.blue(rgb);

        //convert to hsv
        hsv = new float[3];
        Color.RGBToHSV(red, green, blue, hsv); //hue (intensity), saturation ('whiteness'), value (brightness)

        //Log.d(TAG, "RGB: " + red + " " + green + " " + blue + " ; HSV: " + hsv[0] + " " + hsv[1] + " " + hsv[2]);
        //System.out.println(hsv[0]);

    }

    private static int getAverageRGB(Bitmap lCurrentBitmap) { //120x160 pixels
        //crop into circle, replacae RGB with
        long red = 0;
        long green = 0;
        long blue = 0;
        long totalPixelCount = 0;

        long totalSkipCount = 0;

        StringBuilder pposList = new StringBuilder("{");
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        lCurrentBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
//        String encoded64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
//
//        logdlong(TAG, "\n\nCurrent, unadultered bitmap-frame, encoded64: \n\n" + encoded64);

        for (int y = 0; y < lCurrentBitmap.getHeight(); y++) {
            for (int x = 0; x < lCurrentBitmap.getWidth(); x++) {
                int c = lCurrentBitmap.getPixel(x, y);
                //do not include RGB for pixels with brightness over 0.15.
//                float[] temphsv = new float[3];
//                Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), temphsv);
//                System.out.println("\n");
//                int[] rgb = {Color.red(c), Color.green(c), Color.blue(c)};
//
//                if (rgb[0] <= 75) {
//                    //pposList.append("(*" + (totalSkipCount + 1) + "*)").append("{").append(x).append(",").append(lCurrentBitmap.getHeight() - y).append("},");
//                    //System.out.print(("(*" + (totalSkipCount + 1) + "*)").concat("{").concat(String.valueOf(x)).concat(",").concat(String.valueOf(lCurrentBitmap.getHeight() - y)).concat("},"));
//                    totalSkipCount++;
//                } else {
                    red += Color.red(c); //rgb[0];
                    green += Color.green(c);//rgb[1];
                    blue += Color.blue(c);//rgb[2];

                    totalPixelCount++;
//                }
            }
        }

        //logdlong(TAG, "Removed " + totalSkipCount + " pixels; New, pixels that were removed pposList:\n");
        //logdlong(TAG, pposList.toString());

//        if (totalSkipCount > ((lCurrentBitmap.getHeight() * lCurrentBitmap.getWidth())/3)) {
//            logdlong(TAG, "unusual amount of skipped pixels (" + totalSkipCount + ")");
//        }

        return Color.rgb((int) (red/totalPixelCount), (int) (green/totalPixelCount), (int) (blue/totalPixelCount));
    }

    private static void logdlong(String TAG, String veryLongString) {
        int maxLogSize = 1000;
        System.out.println();
        if (veryLongString.length() > 4000) {
            System.out.println(veryLongString.substring(0, 4000));
            logdlong(TAG, veryLongString.substring(4000));
        } else {
            System.out.println(veryLongString);
        }
//        for(int i = 0; i <= veryLongString.length() / maxLogSize; i++) {
//            int start = i * maxLogSize;
//            int end = (i+1) * maxLogSize;
//            end = end > veryLongString.length() ? veryLongString.length() : end;
//            //Log.d(TAG, veryLongString.substring(start, end));
//            System.out.print(veryLongString.substring(start, end));
//        }
    }


    private void abruptStopReadingCamera() {
        Log.w(TAG, "Stopping camera abruptly");
        grapher.cancel();
        runOnUI(new Runnable() {
            @Override
            public void run() {
                imgPreviewFrameLayout.removeAllViews();
                mCameraPreview.destroyDrawingCache(); //free up resources
                mCam.stopPreview();
            }
        });
    }


    public static final int ERRONEOUS_INIT_SEC = 5;
    public static final double UNKNOWN = -1;
    /**
     * Sends data to the AnalysisActivity.
     * @param RAW_DATA The raw data, unaltered.
     */
    private void sendDataForCompilation(List<Double> RAW_DATA) {
        Toast t = Toast.makeText(PPGActivity.this, "NumberData successfully collected; analyzing...", Toast.LENGTH_LONG);
        ((TextView) ((LinearLayout) t.getView()).getChildAt(0)).setTextSize(15);
        t.show();

        Intent analyze = new Intent(PPGActivity.this, AnalysisActivity.class);

        final List<Double> SUBLIST = entries.subList(
                0,
                (int) (ERRONEOUS_INIT_SEC/TIME_INCREMENT_SEC)); //(int) (ERRONEOUS_INIT_SEC/TIME_INCREMENT_SEC), testing purposes: entries.size() - 1//TODO: MAKE THIS DEFAULT.
        Double[] sub = SUBLIST.toArray(new Double[SUBLIST.size()]);
        Double[] raw = RAW_DATA.toArray(new Double[RAW_DATA.size()]);
        double[] subPrim = new double[sub.length];
        double[] rawPrim = new double[raw.length];
        for (int i = 0; i < subPrim.length; i++) {
            //could be null (aka no value found)
            if (sub[i] == null) {
                subPrim[i] = UNKNOWN;
            } else {
                subPrim[i] = (double) sub[i];
            }
        }
        for (int i = 0; i < rawPrim.length; i++) {
            if (raw[i] == null) {
                rawPrim[i] = UNKNOWN;
            } else {
                rawPrim[i] = (double) raw[i];
            }
        }

        subPrim = rawPrim; //FIXME: comment this out.

        System.out.println(Arrays.toString(SUBLIST.toArray(new Double[SUBLIST.size()])));
        analyze.putExtra(SUBLIST_DATA_KEY,  subPrim);
        analyze.putExtra(RAW_DATA_KEY,      rawPrim);

        startActivity(analyze);
    }

    private boolean openCam(int cameraID) {
        boolean available = false;

        try {
            releaseCamAndPreview();
            mCam = Camera.open(cameraID);
            available = (mCam != null);
        } catch (Exception e) {
            e.printStackTrace(); //failed to open camera
        }

        return available;
    }

    private void releaseCamAndPreview() {

    }

    /**
     * Checks if the device has a camera
     * @return whether the device has a camera
     */
    private boolean hasCamera() {
        if (this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }

    /**
     * Gets a camera instance
     * @return Camera instance, null if camera is unavailable
     */
    public static Camera getCamera() {
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return c;
    }

    public static void runOnUI(Runnable runnable) {
        UIHandler.post(runnable);
    }
}
