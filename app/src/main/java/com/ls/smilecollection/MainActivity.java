package com.ls.smilecollection;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.MatOfRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.core.Point;
import org.opencv.objdetect.Objdetect;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.PopupMenu;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {
    static {
        OpenCVLoader.initDebug();
    }

    ImageButton capture, flip, menu, gallery;
    private PreviewView previewView;
    private View topView;
    int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private static final String STATE_CAMERA_FACING = "cameraFacing";
    boolean isFlashOn = false;
    private CascadeClassifier faceCascade;
    private CascadeClassifier smileCascade;
    private CameraControl cameraControl;
    private ImageCapture imageCapture;
    private boolean isSmileDetectionEnabled = false;
    private ImageAnalysis imageAnalysis;
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST = 1001;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private long lastAutoCaptureTime = 0;
    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
        @Override
        public void onActivityResult(Boolean result) {
            if (result) {
                startCamera(cameraFacing);
            }
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        faceCascade = loadCascade(R.raw.haarcascade_frontalface_alt);
        smileCascade = loadCascade(R.raw.haarcascade_smile);
        previewView = findViewById(R.id.cameraPreview);
        capture = findViewById(R.id.capture);
        flip = findViewById(R.id.flip);
        menu = findViewById(R.id.menu);
        gallery = findViewById(R.id.gallery);
        topView = findViewById(R.id.topView);

        if (savedInstanceState != null) {
            cameraFacing = savedInstanceState.getInt(STATE_CAMERA_FACING, CameraSelector.LENS_FACING_BACK);
            startCamera(cameraFacing); // Restore the camera state
        } else {
            // Start camera normally
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                activityResultLauncher.launch(Manifest.permission.CAMERA);
            } else {
                startCamera(cameraFacing);
            }
        }

        if (OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV loaded successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "OpenCV not loaded", Toast.LENGTH_SHORT).show();
        }

        flip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                    cameraFacing = CameraSelector.LENS_FACING_FRONT;
                } else {
                    cameraFacing = CameraSelector.LENS_FACING_BACK;
                }
                startCamera(cameraFacing);
            }
        });

        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Call the showPopup() method to display the PopupMenu
                showPopup(view); 
            }
        });

        gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                Uri uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Smile/");
                intent.setDataAndType(uri, "image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivity(Intent.createChooser(intent, "Open folder with"));
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CAMERA_FACING, cameraFacing);
        super.onSaveInstanceState(outState);
    }

    private CascadeClassifier loadCascade(int rawResourceId) {
        CascadeClassifier cascade = new CascadeClassifier();
        InputStream inputStream = getResources().openRawResource(rawResourceId);
        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
        File cascadeFile = new File(cascadeDir, "temp.xml");

        try {
            FileOutputStream outputStream = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            if (cascade.load(cascadeFile.getAbsolutePath())) {
                if (rawResourceId == R.raw.haarcascade_frontalface_alt) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Face cascade loaded successfully", Toast.LENGTH_SHORT).show();
                    });
                }
                else if (rawResourceId == R.raw.haarcascade_smile) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Smile cascade loaded successfully", Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to load cascade", Toast.LENGTH_SHORT).show();
                });
            }

            cascadeFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return cascade;
    }

    public void startCamera(int cameraFacing) {
        int aspectRatio = aspectRatio(previewView.getWidth(),previewView.getHeight());
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Initialize ImageAnalysis globally
                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(rotation)
                        .build();

                Preview preview = new Preview.Builder().setTargetAspectRatio(aspectRatio).build();

                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraFacing).build();

                ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation());

                imageCapture = imageCaptureBuilder.build();

                cameraProvider.unbindAll();

                // Bind all necessary use cases including ImageAnalysis to the camera lifecycle
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

                cameraControl = camera.getCameraControl();

                Log.d("Camera", "ImageAnalysis bound to camera");

                // Set the Preview surface provider
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Set up Smile Detection using ImageAnalysis
                configureSmileDetection(cameraProvider);

                // Set capture OnClickListener
                capture.setOnClickListener(view -> {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        // Request permission
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
                    } else {
                        takePicture(imageCapture);
                    }
                });

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void configureSmileDetection(ProcessCameraProvider cameraProvider) {
        FaceDetectionOverlayView faceDetectionOverlay = findViewById(R.id.faceDetectionOverlay);

        // Set the analyzer for image analysis
        imageAnalysis.setAnalyzer(executorService, imageProxy -> {
            boolean smileDetected = false;
            Mat yuvMat = null;
            Mat bgrMat = null;
            Mat gray = null;
            Mat rgbaMat = null;
            try {
                // Get rotation of this image
                int rotation = imageProxy.getImageInfo().getRotationDegrees();

                // Extract YUV image data from ImageProxy
                ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
                ByteBuffer yBuffer = planes[0].getBuffer();
                ByteBuffer uBuffer = planes[1].getBuffer();
                ByteBuffer vBuffer = planes[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();

                byte[] nv21 = new byte[ySize + uSize + vSize];
                yBuffer.get(nv21, 0, ySize);
                uBuffer.get(nv21, ySize, uSize);
                vBuffer.get(nv21, ySize + uSize, vSize);

                // Create YUV Mat (NV21) with correct shape
                int width = imageProxy.getWidth();
                int height = imageProxy.getHeight();
                yuvMat = new Mat(height + height / 2, width, CvType.CV_8UC1);
                yuvMat.put(0, 0, nv21);

                // Convert NV21 -> BGR
                bgrMat = new Mat();
                Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_NV21);

                // Rotate according to rotationDegrees so faces are upright
                if (rotation == 90) {
                    org.opencv.core.Core.rotate(bgrMat, bgrMat, org.opencv.core.Core.ROTATE_90_CLOCKWISE);
                } else if (rotation == 180) {
                    org.opencv.core.Core.rotate(bgrMat, bgrMat, org.opencv.core.Core.ROTATE_180);
                } else if (rotation == 270) {
                    org.opencv.core.Core.rotate(bgrMat, bgrMat, org.opencv.core.Core.ROTATE_90_COUNTERCLOCKWISE);
                }

                // Convert to gray for detection
                gray = new Mat();
                Imgproc.cvtColor(bgrMat, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.equalizeHist(gray, gray);

                // Detect faces on gray image
                MatOfRect faces = new MatOfRect();
                faceCascade.detectMultiScale(
                        gray,
                        faces,
                        1.1,
                        5,
                        Objdetect.CASCADE_SCALE_IMAGE,
                        new org.opencv.core.Size(30, 30),
                        new org.opencv.core.Size()
                );

                // Draw detections on bgrMat
                for (org.opencv.core.Rect face : faces.toArray()) {
                    Imgproc.rectangle(bgrMat, new Point(face.x, face.y),
                            new Point(face.x + face.width, face.y + face.height),
                            new Scalar(255, 0, 0), 2);

                    // Detect smiles in face ROI (use gray ROI)
                    Mat roiGray = new Mat(gray, face);
                    MatOfRect smiles = new MatOfRect();
                    smileCascade.detectMultiScale(
                            roiGray,
                            smiles,
                            1.4,
                            15,
                            Objdetect.CASCADE_SCALE_IMAGE,
                            new org.opencv.core.Size(20, 20),
                            new org.opencv.core.Size()
                    );

                    if (smiles.toArray().length > 0) {
                        smileDetected = true;
                    }

                    for (org.opencv.core.Rect smile : smiles.toArray()) {
                        Point pt1 = new Point(face.x + smile.x, face.y + smile.y);
                        Point pt2 = new Point(pt1.x + smile.width, pt1.y + smile.height);
                        Imgproc.rectangle(bgrMat, pt1, pt2, new Scalar(0, 255, 0), 2);
                    }

                    roiGray.release();
                    smiles.release();
                }

                // Convert BGR -> RGBA for bitmap
                rgbaMat = new Mat();
                Imgproc.cvtColor(bgrMat, rgbaMat, Imgproc.COLOR_BGR2RGBA);

                Bitmap bitmap = Bitmap.createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(rgbaMat, bitmap);

                // Prepare final values for use inside the UI thread lambda
                final boolean smileDetectedFinal = smileDetected;
                final org.opencv.core.Rect[] facesArray = faces.toArray();
                final org.opencv.core.Rect[] facesArrayFinal = facesArray;
                final Bitmap bitmapFinal = bitmap;

                runOnUiThread(() -> {
                    // update overlay with faces
                    faceDetectionOverlay.updateOverlay(bitmapFinal);
                    faceDetectionOverlay.setFaces(facesArrayFinal);

                    // Only change topView when a smile is detected
                    if (smileDetectedFinal) {
                        topView.setBackgroundColor(getResources().getColor(R.color.smile_detected_color));
                    } else {
                        topView.setBackgroundColor(getResources().getColor(R.color.default_color));
                    }


                    // Auto-capture when smile detected and feature enabled
                    if (isSmileDetectionEnabled && smileDetectedFinal && imageCapture != null) {
                        long now = System.currentTimeMillis();
                        if (now - lastAutoCaptureTime > 3000) { // 3s cooldown
                            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
                            } else {
                                lastAutoCaptureTime = now;
                                takePicture(imageCapture);
                            }
                        }
                    }
                });

                faces.release();

            } catch (Exception e) {
                Log.e("SmileDetection", "Error during smile detection: " + e.getMessage());
            } finally {
                if (yuvMat != null) yuvMat.release();
                if (bgrMat != null) bgrMat.release();
                if (gray != null) gray.release();
                if (rgbaMat != null) rgbaMat.release();
                if (imageProxy != null) imageProxy.close();
            }
        });
    }

    public void takePicture(ImageCapture imageCapture) {
        File smileFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Smile/");
        if (!smileFolder.exists()) {
            smileFolder.mkdirs();
        }
        final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Smile/", "IMG_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, executorService, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // Display a message indicating the local path where the image is saved
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Image saved at: " + file.getPath(), Toast.LENGTH_SHORT).show();
                });
                startCamera(cameraFacing);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Failed to save: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                });
                startCamera(cameraFacing);
            }
        });
    }

    public void showPopup(View v) {
        PopupMenu popup = new PopupMenu(this, menu);
        popup.setOnMenuItemClickListener(this); 
        popup.inflate(R.menu.popup_menu); 
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        // Handle menu item clicks here
        switch (item.getItemId()) {
            case R.id.flash:
                flash(cameraControl); 
                return true;
            case R.id.sd:
                isSmileDetectionEnabled = !isSmileDetectionEnabled; 
                Toast.makeText(this, isSmileDetectionEnabled ? "Smile detection enabled!" : "Smile detection disabled!", Toast.LENGTH_SHORT).show();
                return true;
            default:
                return false;
        }
    }

    private void flash(CameraControl cameraControl) {
        if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
            Toast.makeText(this, "Flash not available for front camera", Toast.LENGTH_SHORT).show();
            return;
        }
        isFlashOn = !isFlashOn;
        if (cameraControl != null) {
            if (isFlashOn) {
                cameraControl.enableTorch(true);
                Toast.makeText(this, "Flash Light turned ON!", Toast.LENGTH_SHORT).show();
            } else {
                cameraControl.enableTorch(false);
                Toast.makeText(this, "Flash Light turned OFF!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed to take picture
                takePicture(imageCapture);
            } else {
                Toast.makeText(MainActivity.this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int aspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        double aspectTolerance = 0.1; // Tolerance value for aspect ratio comparison

        if (Math.abs(previewRatio - (4.0 / 3.0)) <= aspectTolerance) {
            return AspectRatio.RATIO_4_3;
        } else if (Math.abs(previewRatio - (16.0 / 9.0)) <= aspectTolerance) {
            return AspectRatio.RATIO_16_9;
        } else {
            return AspectRatio.RATIO_4_3;
        }
    }
}