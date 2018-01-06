package io.github.kaisubr.photoplethysmogram_api_22.cam;

import android.content.Context;
import android.graphics.*;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import io.github.kaisubr.photoplethysmogram_api_22.PPGActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Photoplethysmogram_API_22, file created in io.github.kaisubr.photoplethysmogram_api_22.cam by Kailash Sub.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

    private static final String TAG = "CameraPreview";
    public int previewCount = 0; //the current frame for bitmap
    public Bitmap currentBitmap; //the current bitmap at previewCount frame

    Camera mCamera;
    SurfaceHolder mHolder;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        //bitmapRenderingFrameCounter();

        mCamera = camera;

        //Surface holder
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        //preview is drawn onto holder
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //our preview should be modified here, surfaceCreated() is called after this
        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size size = minimizePreviewSize(parameters, width, height);
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);

        if (mHolder.getSurface() == null){
            Log.d(TAG, "Preview surface doesn't exist");
            return;
        }

        //stop before modifying
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            e.printStackTrace();
        }

        // ~~ Modifications: make any resize, rotate or reformatting changes here ~~
        mCamera.setDisplayOrientation(90);

        // restart preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    private Camera.Size minimizePreviewSize(Camera.Parameters parameters, int width, int height) {
        Camera.Size smallestPossible = null;
        for (Camera.Size cur : parameters.getSupportedPreviewSizes()) {
            if (cur.width <= width && cur.height <= height) {
                //if first time through loop
                if (smallestPossible == null) smallestPossible = cur;
                else {
                    //find smaller area
                    if ((cur.height * cur.width) < (smallestPossible.height * smallestPossible.width)) smallestPossible = cur;
                }
            }
        }
        return smallestPossible;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) { //normally ~10 fps
        //https://github.com/phishman3579/android-heart-rate-monitor/blob/master/src/com/jwetherell/heart_rate_monitor/HeartRateMonitor.java
        previewCount++;

        //https://stackoverflow.com/questions/20298699/onpreviewframe-data-image-to-imageview
        Camera.Parameters parameters = camera.getParameters();

        List<int[]> fpsSupported = parameters.getSupportedPreviewFpsRange();
        parameters.setPreviewFpsRange(fpsSupported.get(0)[0], fpsSupported.get(0)[1]);
        parameters.setPreviewSize(640, 480);

        Camera.Size paramSize = parameters.getPreviewSize();
        int w = paramSize.width;
        int h = paramSize.height;

        YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), w, h, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, w, h), 50, out);

        byte[] bytes = out.toByteArray();
        currentBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); //bitmap is updated accd to prev count

        //rotate 90 degrees, update current bitmap
        Matrix matrix = new Matrix(); //android.graphics, not opengl
        matrix.postRotate(90);
        matrix.postScale(0.25f, 0.25f);
        currentBitmap = Bitmap.createBitmap(currentBitmap, 0, 0, currentBitmap.getWidth(), currentBitmap.getHeight(), matrix, true);

        PPGActivity.onNewFrame(); //FIXME: notified via static reference...
    }

    /**
     * For debugging, logs preview count every second onPreviewFrame() is called.
     */
    @Deprecated
    private void bitmapRenderingFrameCounter() {
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "frame " + previewCount);
            }
        }, 0, 1000);
    }

}
