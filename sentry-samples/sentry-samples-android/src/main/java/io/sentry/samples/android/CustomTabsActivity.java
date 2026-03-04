package io.sentry.samples.android;

import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

public class CustomTabsActivity extends AppCompatActivity {

  private static final String DEMO_URL = "https://www.sentry.io/";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

    CustomTabColorSchemeParams params =
        new CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .build();
    builder.setDefaultColorSchemeParams(params);

    builder.setShowTitle(true);
    builder.setShareState(CustomTabsIntent.SHARE_STATE_ON);
    builder.setInstantAppsEnabled(true);

    CustomTabsIntent customTabsIntent = builder.build();
    customTabsIntent.launchUrl(this, Uri.parse(DEMO_URL));

    finish();
  }
}
