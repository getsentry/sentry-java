package io.sentry.samples.android.minsdk;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.samples.android.minsdk.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());

    setContentView(binding.getRoot());
  }
}
