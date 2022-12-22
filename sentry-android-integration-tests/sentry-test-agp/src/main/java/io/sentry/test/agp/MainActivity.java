package io.sentry.test.agp;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    helloWorld();
  }

  public void helloWorld() {
    System.out.println("¯\\_(ツ)_/¯");
  }

}

