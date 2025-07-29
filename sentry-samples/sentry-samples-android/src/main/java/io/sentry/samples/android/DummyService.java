package io.sentry.samples.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class DummyService extends Service {

  private static final String TAG = "DummyService";
  private static final String CHANNEL_ID = "dummy_service_channel";

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "DummyService created");
    createNotificationChannel();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "DummyService started");

    Notification notification = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      notification = new Notification.Builder(this, CHANNEL_ID)
          .setContentTitle("Dummy Service Running")
          .setContentText("Used for background broadcast testing.")
          .setSmallIcon(android.R.drawable.ic_menu_info_details)
          .build();
    }

    if (notification != null) {
      startForeground(1, notification);
    }

    // You can stop immediately or keep running
    // stopSelf();

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "DummyService destroyed");
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
          CHANNEL_ID,
          "Dummy Service Channel",
          NotificationManager.IMPORTANCE_LOW
      );
      NotificationManager manager = getSystemService(NotificationManager.class);
      manager.createNotificationChannel(channel);
    }
  }
}
