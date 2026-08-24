package com.racks.parentalcontrol.child.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.racks.parentalcontrol.child.remote.FirebaseClient;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;

public class SnapshotManager {
    private static final String TAG = "RaviKumar-SnapshotManager";
    private final Context context;
    private Handler backgroundHandler;
    private MediaProjection mediaProjection;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private final FirebaseClient firebaseClient;
    private static SnapshotManager instance;
    private int cameraSensorOrientation = 0;
    private CameraCharacteristics selectedCameraCharacteristics;
    private boolean isFrontCamera = false;

    public SnapshotManager(Context context) {
        this.context = context.getApplicationContext();
        this.firebaseClient = new FirebaseClient();
    }
    public static synchronized SnapshotManager getInstance(Context context) {
        if (instance == null) {
            instance = new SnapshotManager(context);
        }
        return instance;
    }

    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    public void takeScreenshot(Runnable onReady) {
        startBackgroundThread();

        if (mediaProjection == null) {
            Log.e("RaviKumar-SnapshotManager", "MediaProjection is not initialized.");
            return;
        }

        Log.d("RaviKumar-SnapshotManager", "MediaProjection is not null, preparing to take screenshot");

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                Log.d("RaviKumar-SnapshotManager", "MediaProjection stopped.");
                stopBackgroundThread();
            }
        }, backgroundHandler);

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        Log.d("RaviKumar-SnapshotManager", "Screen width=" + width + ", height=" + height + ", density=" + density);

        ImageReader imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1);

        VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
                "screenshot",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, backgroundHandler
        );

        Log.d("RaviKumar-SnapshotManager", "Virtual display created");
        new Handler(backgroundHandler.getLooper()).postDelayed(() ->
            imageReader.setOnImageAvailableListener(reader -> {
            Log.d("RaviKumar-SnapshotManager", "Image available from virtual display");
            try (reader; Image image = reader.acquireLatestImage()) {
                if (image == null) return;

                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();

                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;

                Bitmap bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                );
                bitmap.copyPixelsFromBuffer(buffer);

                Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                cropped.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] data = baos.toByteArray();

                Log.d("RaviKumar-SnapshotManager", "Uploading screenshot to Firebase");
                firebaseClient.uploadCaptureToFirebase(data, "screen");

                bitmap.recycle();
                cropped.recycle();

            } catch (Exception e) {
                Log.e("RaviKumar-SnapshotManager", "Screenshot failed", e);
            } finally {
                virtualDisplay.release();
                mediaProjection.stop();
                if (onReady != null) onReady.run();
            }
        }, backgroundHandler),1000);


    }
    public void takeSnapshot(boolean useFrontCamera) {
        startBackgroundThread();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == (useFrontCamera ?
                        CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
                    cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    isFrontCamera = (facing == CameraCharacteristics.LENS_FACING_FRONT);
                    selectedCameraCharacteristics = characteristics;
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Camera permission not granted");
                        return;
                    }

                    manager.openCamera(cameraId, stateCallback, backgroundHandler);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            new Handler(Looper.getMainLooper()).postDelayed(() -> captureImage(), 2000);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            Log.e(TAG, "Camera error: " + error);
        }
    };
    private void captureImage() {
        Size imageSize = new Size(1920, 1440);
        if (selectedCameraCharacteristics != null) {
            Size[] jpegSizes = selectedCameraCharacteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.JPEG);
            if (jpegSizes != null && jpegSizes.length > 0) {
                imageSize = jpegSizes[0];
            }
        }

        imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(), ImageFormat.JPEG, 1);
        imageReader.setOnImageAvailableListener(reader -> {
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    firebaseClient.uploadCaptureToFirebase(bytes, "camera");
                }
            } catch (Exception e) {
                Log.e(TAG, "Image capture failed", e);
            } finally {
                stopCamera();
            }
        }, backgroundHandler);

        try {
            cameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                captureBuilder.addTarget(imageReader.getSurface());

                                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                                captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
                                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                                int deviceRotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                                        .getDefaultDisplay().getRotation();
                                int surfaceRotationDegrees = ORIENTATIONS.get(deviceRotation, 0);
                                int jpegOrientation = (cameraSensorOrientation - surfaceRotationDegrees + 360) % 360;
                                if (isFrontCamera) jpegOrientation = (360 - jpegOrientation) % 360;
                                jpegOrientation = (jpegOrientation + 90) % 360;

                                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);
                                captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);

                                session.capture(captureBuilder.build(), null, backgroundHandler);

                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Capture failed", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Camera session config failed");
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Capture setup failed", e);
        }
    }
    // Orientation helper
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }



    private void stopCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("snapshotBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread stop error", e);
            }
        }
    }
}
