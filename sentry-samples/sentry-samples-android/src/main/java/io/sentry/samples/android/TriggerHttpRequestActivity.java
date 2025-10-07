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
import java.io.IOException;
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
        postButton.setOnClickListener(v -> performPostRequest());
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

    private void performPostRequest() {
        String url = getUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject();
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
    }

    private String getCurrentTime() {
        return dateFormat.format(new Date());
    }
}
