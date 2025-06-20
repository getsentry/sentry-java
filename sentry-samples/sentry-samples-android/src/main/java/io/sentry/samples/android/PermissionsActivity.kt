package io.sentry.samples.android

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import io.sentry.Sentry
import io.sentry.samples.android.databinding.ActivityPermissionsBinding

class PermissionsActivity : AppCompatActivity() {
  private lateinit var binding: ActivityPermissionsBinding
  private val requestPermissionLauncher =
    registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
      if (isGranted) {
        Toast.makeText(this, "The permission was granted", Toast.LENGTH_LONG).show()
      } else {
        Toast.makeText(this, "The permission was denied", Toast.LENGTH_LONG).show()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityPermissionsBinding.inflate(layoutInflater)

    binding.cameraPermission.setOnClickListener {
      requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    binding.writePermission.setOnClickListener {
      requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    binding.permissionsCrash.setOnClickListener {
      throw RuntimeException("Permissions Activity Exception")
    }

    setContentView(binding.root)
    Sentry.reportFullyDisplayed()
  }
}
