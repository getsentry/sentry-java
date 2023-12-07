package android.graphics;

import android.util.Log;
import com.bytedance.shadowhook.ShadowHook;
import java.util.List;
import java.util.Map;

public class RenderNodeHelper {
  static {
    System.loadLibrary("sentry-android-replay");
    System.loadLibrary("sentry-android-replay-headers");
  }

  public static native List<Map<String, Object>> nGetDisplayList(long renderNode);

  public static native List<Map<String, Object>> nGetDisplayList2(long renderNode);

  public static List<Map<String, Object>> fetchDisplayList(long renderNode) {
    return nGetDisplayList2(renderNode);
  }
}
