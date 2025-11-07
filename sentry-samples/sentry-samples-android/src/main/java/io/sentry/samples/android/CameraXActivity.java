package io.sentry.samples.android;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import io.sentry.Sentry;
import io.sentry.samples.android.databinding.ActivityCameraxBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CameraXActivity extends AppCompatActivity {
  private static final String TAG = "CameraXActivity";
  private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

  private ActivityCameraxBinding binding;
  private PreviewView previewView;
  private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
  private ImageCapture imageCapture;
  private Camera camera;
  private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityCameraxBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    previewView = binding.previewView;

    if (allPermissionsGranted()) {
      startCamera();
    } else {
      ActivityCompat.requestPermissions(
          this, new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    binding.captureButton.setOnClickListener(view -> takePhoto());
    binding.switchCameraButton.setOnClickListener(view -> switchCamera());
    binding.backButton.setOnClickListener(view -> finish());
  }

  private void startCamera() {
    cameraProviderFuture = ProcessCameraProvider.getInstance(this);
    cameraProviderFuture.addListener(
        () -> {
          try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            bindPreview(cameraProvider);
          } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error starting camera", e);
            Sentry.captureException(e);
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void bindPreview(ProcessCameraProvider cameraProvider) {
    Preview preview = new Preview.Builder().build();
    imageCapture =
        new ImageCapture.Builder()
            .setTargetRotation(previewView.getDisplay().getRotation())
            .build();

    preview.setSurfaceProvider(previewView.getSurfaceProvider());

    cameraProvider.unbindAll();
    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
  }

  private void takePhoto() {
    if (imageCapture == null) return;

    String timeStamp =
        new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    String fileName = "CameraX_" + timeStamp + ".jpg";

    ContentValues contentValues = new ContentValues();
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Images");

    ImageCapture.OutputFileOptions outputFileOptions =
        new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build();

    imageCapture.takePicture(
        outputFileOptions,
        ContextCompat.getMainExecutor(this),
        new ImageCapture.OnImageSavedCallback() {
          @Override
          public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
            String msg = "Photo saved successfully: " + outputFileResults.getSavedUri();
            Toast.makeText(CameraXActivity.this, "Photo saved!", Toast.LENGTH_SHORT).show();
            Log.d(TAG, msg);
          }

          @Override
          public void onError(@NonNull ImageCaptureException exception) {
            Log.e(TAG, "Photo capture failed", exception);
            Toast.makeText(CameraXActivity.this, "Photo capture failed", Toast.LENGTH_SHORT).show();
            Sentry.captureException(exception);
          }
        });
  }

  private void switchCamera() {
    cameraSelector =
        (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            ? CameraSelector.DEFAULT_FRONT_CAMERA
            : CameraSelector.DEFAULT_BACK_CAMERA;

    try {
      if (cameraProviderFuture != null) {
        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
        bindPreview(cameraProvider);
      }
    } catch (ExecutionException | InterruptedException e) {
      Log.e(TAG, "Error switching camera", e);
      Sentry.captureException(e);
    }
  }

  private boolean allPermissionsGranted() {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED;
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
      if (allPermissionsGranted()) {
        startCamera();
      } else {
        Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
        finish();
      }
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    binding = null;
  }
}
