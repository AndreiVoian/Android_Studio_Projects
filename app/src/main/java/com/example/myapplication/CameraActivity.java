package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class CameraActivity extends AppCompatActivity {

    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private SeekBar exposureSeekBar;
    private int exposureValue = 50; // Initial value for exposure
    private String backCameraId = null;
    private String frontCameraId = null;
    private boolean isFrontCamera = false; // Flag to track which camera is currently active
    private Button switchCameraButton;
    private boolean isFlashlightOn = false;
    private Button flashButton;
    private CameraManager cameraManager;
    private SeekBar zoomSeekBar;
    private int backCameraZoomLevel = 0;
    private int frontCameraZoomLevel = 0;
    private int newZoomLevel;

    private void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CameraActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCameraZoom(int sliderValue, boolean isFrontCamera) {
        if (this.isFrontCamera) {
            frontCameraZoomLevel = sliderValue;
        } else {
            backCameraZoomLevel = sliderValue;
        }
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            float maxZoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM));
            Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            // Assuming the sliderValue is from 0 to 100, and sliderValue of 100 should correspond to maxZoom.
            float zoomRatio = sliderValue / 100f * (maxZoom - 1) + 1; // +1 because min zoom is 1x, not 0x.

            // Here we ensure that we don't exceed the maximum zoom ratio provided by the camera.
            zoomRatio = Math.min(zoomRatio, maxZoom);

            int cropW = (int) (m.width() / zoomRatio);
            int cropH = (int) (m.height() / zoomRatio);
            int cropX = (m.width() - cropW) / 2;
            int cropY = (m.height() - cropH) / 2;

            Rect zoom = new Rect(cropX, cropY, cropX + cropW, cropY + cropH);
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        initializeCameraProperties();

        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(surfaceTextureListener);

        zoomSeekBar = findViewById(R.id.zoom_seekbar);
        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (cameraDevice != null && fromUser) {
                    if (isFrontCamera) {
                        frontCameraZoomLevel = progress;
                    } else {
                        backCameraZoomLevel = progress;
                    }
                    updateCameraZoom(progress, isFrontCamera);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional implementation
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Optional implementation
            }
        });

        updateZoomSeekBarMax();

        exposureSeekBar = findViewById(R.id.exposure_seekbar);
        exposureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                exposureValue = progress;
                updateCameraPreviewSession();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional implementation
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Optional implementation
            }
        });


        switchCameraButton = findViewById(R.id.switch_camera_button);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });

        flashButton = findViewById(R.id.button_flash);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        flashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("CameraActivity", "Flash button clicked");
                toggleFlashLight();
            }
        });

    }

    private void updateZoomSeekBarMax() {
        int maxZoomValue;
        if (isFrontCamera && frontCameraProperties != null) {
            maxZoomValue = (int) (frontCameraProperties.maxDigitalZoom * 10); // Assuming 10 is the scaling factor.
        } else if (!isFrontCamera && backCameraProperties != null) {
            maxZoomValue = (int) (backCameraProperties.maxDigitalZoom * 10); // Assuming 10 is the scaling factor.
        } else {
            maxZoomValue = 100; // Default value if camera properties are not available
        }
        zoomSeekBar.setMax(maxZoomValue - 1); // Subtracting one to ensure we don't go over the limit.
    }



    private void toggleFlashLight() {
        if (isFrontCamera) {
            showToast("Flashlight not available on front camera.");
            return;
        }

        isFlashlightOn = !isFlashlightOn;
        updateCameraPreviewSession();
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(isFrontCamera ? frontCameraId : backCameraId);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Optional implementation
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Optional implementation
        }
    };

    class CameraProperties {
        float maxDigitalZoom;
        // Add other properties here as needed

        // Constructor
        CameraProperties(float maxDigitalZoom) {
            this.maxDigitalZoom = maxDigitalZoom;
        }
    }

    private CameraProperties backCameraProperties;
    private CameraProperties frontCameraProperties;


    private void initializeCameraProperties() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                float maxZoomRatio = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        backCameraId = cameraId;
                        backCameraProperties = new CameraProperties(maxZoomRatio);
                    } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        frontCameraId = cameraId;
                        frontCameraProperties = new CameraProperties(maxZoomRatio);
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    private void switchCamera() {
        closeCurrentCameraSession();
        isFrontCamera = !isFrontCamera;

        updateFlashlightAndUI();
        updateZoomSeekBarMax();

        // Update the slider to reflect the current zoom level of the active camera
        int currentZoomLevel = isFrontCamera ? frontCameraZoomLevel : backCameraZoomLevel;
        zoomSeekBar.setProgress(currentZoomLevel);

        String cameraIdToOpen = isFrontCamera ? frontCameraId : backCameraId;
        openCamera(cameraIdToOpen);
    }


    private void updateFlashlightAndUI() {
        if (isFrontCamera) {
            // Front camera usually doesn't have a flashlight
            isFlashlightOn = false;
            flashButton.setVisibility(View.GONE);
        } else {
            // Back camera may support a flashlight
            flashButton.setVisibility(View.VISIBLE);
        }
    }

    private void closeCurrentCameraSession() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }


    private void openCamera(String cameraId) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraId == null) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
            return;
        }

        try {
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                        if (map == null) {
                            throw new RuntimeException("Cannot get available preview/video sizes");
                        }

                        // Get display rotation and sensor orientation to determine if we are in portrait mode
                        int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                        boolean isPortrait = (displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180);

                        // Swap width and height if we are in portrait mode
                        Size optimalSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                                isPortrait ? textureView.getHeight() : textureView.getWidth(),
                                isPortrait ? textureView.getWidth() : textureView.getHeight());

                        // Set the aspect ratio for AutoFitTextureView
                        if (textureView instanceof AutoFitTextureView) {
                            ((AutoFitTextureView) textureView).setAspectRatio(
                                    isPortrait ? optimalSize.getHeight() : optimalSize.getWidth(),
                                    isPortrait ? optimalSize.getWidth() : optimalSize.getHeight());
                        } else {
                            throw new RuntimeException("Expected textureView to be instance of AutoFitTextureView");
                        }

                        SurfaceTexture texture = textureView.getSurfaceTexture();
                        assert texture != null;
                        texture.setDefaultBufferSize(optimalSize.getWidth(), optimalSize.getHeight());
                        Surface surface = new Surface(texture);

                        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        captureRequestBuilder.addTarget(surface);

                        cameraDevice.createCaptureSession(Collections.singletonList(surface),
                                new CameraCaptureSession.StateCallback() {

                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                        if (cameraDevice == null) return;
                                        CameraActivity.this.cameraCaptureSession = cameraCaptureSession;
                                        updateCameraPreviewSession();
                                        // Determine the current zoom level based on the active camera
                                        int currentZoomLevel = isFrontCamera ? frontCameraZoomLevel : backCameraZoomLevel;
                                        // Apply the current zoom level
                                        updateCameraZoom(currentZoomLevel, isFrontCamera);
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                        showToast("Failed to configure camera.");
                                    }
                                }, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight) {
        // Sort the choices in ascending order of area
        Arrays.sort(choices, new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                        (long) rhs.getWidth() * rhs.getHeight());
            }
        });

        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            // Check if the size is suitable for the aspect ratio and resolution
            if (option.getHeight() == option.getWidth() * textureViewHeight / textureViewWidth &&
                    option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                bigEnough.add(option);
            }
        }

        if (!bigEnough.isEmpty()) {
            // Choose the smallest size that's big enough
            return bigEnough.get(0);
        } else {
            // No size is big enough; choose the largest available size
            return choices[choices.length - 1];
        }
    }


    private void updateCameraPreviewSession() {
        if (null == cameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(captureRequestBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, new Handler(thread.getLooper()));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());

            // Set AE mode for both cameras
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            // Check and set exposure compensation
            Range<Integer> exposureCompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            if (exposureCompensationRange != null && exposureCompensationRange.contains(exposureValue - 50)) {
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureValue - 50);
            } else {
                Log.d("CameraActivity", "Exposure compensation out of range or not supported");
            }

            // Handle flash mode for the back camera
            if (!isFrontCamera) {
                if (isFlashlightOn) {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                } else {
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (null != cameraCaptureSession) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera(isFrontCamera ? frontCameraId : backCameraId);
            } else {
                // Permission denied. Handle appropriately.
            }
        }
    }
}
