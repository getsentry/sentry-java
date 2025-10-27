package io.sentry.samples.android;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import io.sentry.HttpStatusCodeRange;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.okhttp.SentryOkHttpEventListener;
import io.sentry.okhttp.SentryOkHttpInterceptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;

public class TriggerHttpRequestActivity extends AppCompatActivity {

    private EditText urlInput;
    private TextView requestDisplay;
    private TextView responseDisplay;
    private ProgressBar loadingIndicator;
    private Button getButton;
    private Button postButton;
    private Button formButton;
    private Button binaryButton;
    private Button stringButton;
    private Button oneShotButton;
    private Button clearButton;

    private OkHttpClient okHttpClient;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trigger_http_request);

        dateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

        initializeViews();
        setupOkHttpClient();
        setupClickListeners();
    }

    private void initializeViews() {
        urlInput = findViewById(R.id.url_input);
        requestDisplay = findViewById(R.id.request_display);
        responseDisplay = findViewById(R.id.response_display);
        loadingIndicator = findViewById(R.id.loading_indicator);
        getButton = findViewById(R.id.trigger_get_request);
        postButton = findViewById(R.id.trigger_post_request);
        formButton = findViewById(R.id.trigger_form_request);
        binaryButton = findViewById(R.id.trigger_binary_request);
        stringButton = findViewById(R.id.trigger_string_request);
        oneShotButton = findViewById(R.id.trigger_oneshot_request);
        clearButton = findViewById(R.id.clear_display);

        requestDisplay.setMovementMethod(new ScrollingMovementMethod());
        responseDisplay.setMovementMethod(new ScrollingMovementMethod());
    }

    private void setupOkHttpClient() {
        // OkHttpClient with Sentry integration for monitoring HTTP requests
        okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            // performance monitoring
//            .eventListener(new SentryOkHttpEventListener())
            // breadcrumbs and failed request capture
            .addInterceptor(new SentryOkHttpInterceptor())
            .build();
    }

    private void setupClickListeners() {
        getButton.setOnClickListener(v -> performGetRequest());
        postButton.setOnClickListener(v -> performJsonRequest());
        formButton.setOnClickListener(v -> performFormUrlencodedRequest());
        binaryButton.setOnClickListener(v -> performOctetStreamRequest());
        stringButton.setOnClickListener(v -> performTextPlainRequest());
        oneShotButton.setOnClickListener(v -> performOneShotJsonRequest());
        clearButton.setOnClickListener(v -> clearDisplays());
    }

    private void performGetRequest() {
        String url = getUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        Request request = new Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "Sentry-Sample-Android")
            .addHeader("Accept", "application/json")
            .build();

        displayRequest("GET", request);
        executeRequest(request);
    }

    private void performJsonRequest() {
        String url = getUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("request_type", "POST_JSON");
            json.put("button_clicked", "POST JSON");
            json.put("message", "Hello from Sentry Android Sample");
            json.put("timestamp", System.currentTimeMillis());
            json.put("device", android.os.Build.MODEL);

            RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("User-Agent", "Sentry-Sample-Android")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("X-Request-Type", "POST_JSON")
                .build();

            displayRequest("POST", request, json.toString(2));
            executeRequest(request);
        } catch (Exception e) {
            Sentry.captureException(e);
            Toast.makeText(this, "Error creating request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void executeRequest(Request request) {
        showLoading(true);

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Sentry.captureException(e);
                runOnUiThread(() -> {
                    showLoading(false);
                    displayResponse(
                        "ERROR",
                        null,
                        "Request failed: " + e.getMessage(),
                        0
                    );
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final long startTime = System.currentTimeMillis();
                final int statusCode = response.code();
                final String statusMessage = response.message();
                ResponseBody responseBody = response.body();
                String body = "";

                try {
                    if (responseBody != null) {
                        body = responseBody.string();
                    }
                } catch (IOException e) {
                    body = "Error reading response body: " + e.getMessage();
                    Sentry.captureException(e);
                }

                final long responseTime = System.currentTimeMillis() - startTime;
                final String finalBody = body;

                runOnUiThread(() -> {
                    showLoading(false);
                    displayResponse(statusMessage, statusCode, finalBody, responseTime);
                });

                response.close();
            }
        });
    }

    private void displayRequest(String method, Request request) {
        displayRequest(method, request, null);
    }

    private void displayRequest(String method, Request request, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(getCurrentTime()).append("]\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("METHOD: ").append(method).append("\n");
        sb.append("URL: ").append(request.url()).append("\n\n");
        sb.append("HEADERS:\n");

        for (int i = 0; i < request.headers().size(); i++) {
            sb.append("  ").append(request.headers().name(i)).append(": ")
              .append(request.headers().value(i)).append("\n");
        }

        if (body != null && !body.isEmpty()) {
            sb.append("\nBODY:\n").append(body).append("\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━");

        requestDisplay.setText(sb.toString());
    }

    private void displayResponse(String status, Integer code, String body, long responseTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(getCurrentTime()).append("]\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");

        if (code != null) {
            sb.append("STATUS: ").append(code).append(" ").append(status).append("\n");
            sb.append("RESPONSE TIME: ").append(responseTime).append("ms\n\n");
        } else {
            sb.append("STATUS: ").append(status).append("\n\n");
        }

        if (body != null && !body.isEmpty()) {
            try {
                if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                    JSONObject json = new JSONObject(body);
                    sb.append("BODY (JSON):\n").append(json.toString(2));
                } else {
                    sb.append("BODY:\n").append(body);
                }
            } catch (Exception e) {
                sb.append("BODY:\n").append(body);
            }
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━");

        responseDisplay.setText(sb.toString());
    }

    private void clearDisplays() {
        requestDisplay.setText("No request yet...");
        responseDisplay.setText("No response yet...");
    }

    private String getUrl() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            return "https://api.github.com/users/getsentry";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        getButton.setEnabled(!show);
        postButton.setEnabled(!show);
        formButton.setEnabled(!show);
        binaryButton.setEnabled(!show);
        stringButton.setEnabled(!show);
        oneShotButton.setEnabled(!show);
    }

    private void performFormUrlencodedRequest() {
        String url = getUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create URL-encoded form data
            String formData = "request_type=POST_FORM_URLENCODED&" +
                             "button_clicked=POST%20Form&" +
                             "username=sentry_android_user&" +
                             "email=test@example.com&" +
                             "message=Hello%20from%20Android%20Sample%20Form%20Request&" +
                             "timestamp=" + System.currentTimeMillis() + "&" +
                             "device=" + android.os.Build.MODEL.replace(" ", "%20");

            RequestBody body = RequestBody.create(
                formData,
                MediaType.get("application/x-www-form-urlencoded")
            );

            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("User-Agent", "Sentry-Sample-Android")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("X-Request-Type", "POST_FORM_URLENCODED")
                .build();

            displayRequest("POST", request, formData);
            executeRequest(request);
        } catch (Exception e) {
            Sentry.captureException(e);
            Toast.makeText(this, "Error creating form request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performOctetStreamRequest() {
        String url = getUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Add request type to URL as query parameter for binary requests
            String separator = url.contains("?") ? "&" : "?";
            String urlWithType = url + separator + "request_type=POST_BINARY&button=POST_Binary";

            // Create binary data (simulate a small file upload)
            byte[] binaryData = new byte[1024]; // 1KB of binary data
            for (int i = 0; i < binaryData.length; i++) {
                binaryData[i] = (byte) (i % 256);
            }

            RequestBody body = RequestBody.create(
                binaryData,
                MediaType.get("application/octet-stream")
            );

            Request request = new Request.Builder()
                .url(urlWithType)
                .post(body)
                .addHeader("User-Agent", "Sentry-Sample-Android")
                .addHeader("Content-Type", "application/octet-stream")
                .addHeader("Content-Length", String.valueOf(binaryData.length))
                .addHeader("X-Request-Type", "POST_BINARY")
                .build();

            String displayBody = "[Binary data: " + binaryData.length + " bytes]\n" +
                               "Request type in URL: POST_BINARY\n" +
                               "Sample bytes: " + Arrays.toString(Arrays.copyOf(binaryData, Math.min(10, binaryData.length)));

            displayRequest("POST", request, displayBody);
            executeRequest(request);
        } catch (Exception e) {
            Sentry.captureException(e);
            Toast.makeText(this, "Error creating binary request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performTextPlainRequest() {
        String url = getUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create plain text string data with request type identifier
            String textData = "REQUEST_TYPE: POST_STRING\n" +
                            "BUTTON_CLICKED: POST String\n" +
                            "Hello from Sentry Android Sample!\n" +
                            "This is a plain text request body.\n" +
                            "Timestamp: " + new Date().toString() + "\n" +
                            "Device: " + android.os.Build.MODEL + "\n" +
                            "SDK Version: " + android.os.Build.VERSION.SDK_INT + "\n" +
                            "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";

            RequestBody body = RequestBody.create(
                textData,
                MediaType.get("text/plain; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("User-Agent", "Sentry-Sample-Android")
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("X-Request-Type", "POST_STRING")
                .build();

            displayRequest("POST", request, textData);
            executeRequest(request);
        } catch (Exception e) {
            Sentry.captureException(e);
            Toast.makeText(this, "Error creating string request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performOneShotJsonRequest() {
        String url = getUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Add request type to URL as query parameter for one-shot requests  
            String separator = url.contains("?") ? "&" : "?";
            String urlWithType = url + separator + "request_type=POST_ONE_SHOT&button=POST_OneShotBody";

            // Create JSON data for one-shot request body
            JSONObject json = new JSONObject();
            json.put("request_type", "POST_ONE_SHOT");
            json.put("button_clicked", "POST One-Shot");
            json.put("message", "This is a ONE-SHOT REQUEST BODY - can only be read once!");
            json.put("timestamp", System.currentTimeMillis());
            json.put("device", android.os.Build.MODEL);
            json.put("warning", "Reading this body multiple times will cause IOException");

            String jsonString = json.toString();
            byte[] bodyBytes = jsonString.getBytes("UTF-8");

            // Create a TRUE one-shot request body that will fail if read multiple times
            RequestBody oneShotBody = new RequestBody() {
                private InputStream inputStream = new ByteArrayInputStream(bodyBytes);
                private boolean hasBeenRead = false;

                @Override
                public MediaType contentType() {
                    return MediaType.get("application/json; charset=utf-8");
                }

                @Override
                public long contentLength() {
                    return bodyBytes.length;
                }

                @Override
                public void writeTo(okio.BufferedSink sink) throws IOException {
                    if (hasBeenRead) {
                        throw new IOException("One-shot body has already been read! This would happen in real scenarios with FileInputStream or other non-repeatable streams.");
                    }
                    
                    hasBeenRead = true;
                    
                    try {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            sink.write(buffer, 0, bytesRead);
                        }
                    } finally {
                        inputStream.close();
                    }
                }
            };

            Request request = new Request.Builder()
                .url(urlWithType)
                .post(oneShotBody)
                .addHeader("User-Agent", "Sentry-Sample-Android")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("X-Request-Type", "POST_ONE_SHOT")
                .addHeader("X-Body-Type", "ONE_SHOT_STREAM")
                .build();

            String displayBody = "[ONE-SHOT REQUEST BODY]\n" +
                               "Type: InputStream-based RequestBody\n" +
                               "Size: " + bodyBytes.length + " bytes\n" +
                               "Content: " + json.toString(2) + "\n" +
                               "\nWARNING: This body can only be read once!\n" +
                               "If interceptors try to read it multiple times, it will fail.";

            displayRequest("POST", request, displayBody);
            executeRequest(request);
        } catch (Exception e) {
            Sentry.captureException(e);
            Toast.makeText(this, "Error creating one-shot request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getCurrentTime() {
        return dateFormat.format(new Date());
    }
}
