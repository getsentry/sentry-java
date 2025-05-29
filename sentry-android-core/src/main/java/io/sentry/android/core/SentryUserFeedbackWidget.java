package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.Button;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SentryUserFeedbackWidget extends Button {

  private @Nullable OnClickListener delegate;

  public SentryUserFeedbackWidget(Context context) {
    super(context);
    init(context, null, 0, 0);
  }

  public SentryUserFeedbackWidget(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs, 0, 0);
  }

  public SentryUserFeedbackWidget(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context, attrs, defStyleAttr, 0);
  }

  public SentryUserFeedbackWidget(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init(context, attrs, defStyleAttr, defStyleRes);
  }

  @SuppressLint("SetTextI18n")
  @SuppressWarnings("deprecation")
  private void init(
      final @NotNull Context context,
      final @Nullable AttributeSet attrs,
      final int defStyleAttr,
      final int defStyleRes) {
    try (final @NotNull TypedArray typedArray =
            context.obtainStyledAttributes(
                attrs, R.styleable.SentryUserFeedbackWidget, defStyleAttr, defStyleRes)) {
      final float dimensionScale = context.getResources().getDisplayMetrics().density;
      final float drawablePadding =
          typedArray.getDimension(R.styleable.SentryUserFeedbackWidget_android_drawablePadding, -1);
      final int drawableStart =
          typedArray.getResourceId(R.styleable.SentryUserFeedbackWidget_android_drawableStart, -1);
      final boolean textAllCaps =
          typedArray.getBoolean(R.styleable.SentryUserFeedbackWidget_android_textAllCaps, false);
      final int background =
          typedArray.getResourceId(R.styleable.SentryUserFeedbackWidget_android_background, -1);
      final float padding =
          typedArray.getDimension(R.styleable.SentryUserFeedbackWidget_android_padding, -1);
      final int textColor =
          typedArray.getColor(R.styleable.SentryUserFeedbackWidget_android_textColor, -1);
      final @Nullable String text =
          typedArray.getString(R.styleable.SentryUserFeedbackWidget_android_text);

      // If the drawable padding is not set, set it to 4dp
      if (drawablePadding == -1) {
        setCompoundDrawablePadding((int) (4 * dimensionScale));
      }

      // If the drawable start is not set, set it to the default drawable
      if (drawableStart == -1) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.baseline_campaign_24, 0, 0, 0);
      }

      // Set the text all caps
      setAllCaps(textAllCaps);

      // If the background is not set, set it to the default background
      if (background == -1) {
        setBackgroundResource(R.drawable.oval_button_ripple_background);
      }

      // If the padding is not set, set it to 12dp
      if (padding == -1) {
        int defaultPadding = (int) (12 * dimensionScale);
        setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding);
      }

      // If the text color is not set, set it to the default text color
      if (textColor == -1) {
        // We need the TypedValue to resolve the color from the theme
        final @NotNull TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorForeground, typedValue, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          setTextColor(context.getResources().getColor(typedValue.resourceId, context.getTheme()));
        } else {
          setTextColor(context.getResources().getColor(typedValue.resourceId));
        }
      }

      // If the text is not set, set it to "Report a Bug"
      if (text == null) {
        setText("Report a Bug");
      }
    }

    // Set the default ClickListener to open the SentryUserFeedbackDialog
    setOnClickListener(delegate);
  }

  @Override
  public void setOnClickListener(final @Nullable OnClickListener listener) {
    delegate = listener;
    super.setOnClickListener(
        v -> {
          new SentryUserFeedbackDialog(getContext()).show();
          if (delegate != null) {
            delegate.onClick(v);
          }
        });
  }
}
