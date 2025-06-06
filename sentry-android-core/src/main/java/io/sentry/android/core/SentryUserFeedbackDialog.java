package io.sentry.android.core;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import io.sentry.IScopes;
import io.sentry.Sentry;
import io.sentry.SentryFeedbackOptions;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryUserFeedbackDialog extends AlertDialog {

  private boolean isCancelable = false;
  private @Nullable SentryId currentReplayId;
  private @Nullable OnDismissListener delegate;

  private final @Nullable OptionsConfiguration configuration;

  SentryUserFeedbackDialog(
      final @NotNull Context context,
      final int themeResId,
      final @Nullable OptionsConfiguration configuration) {
    super(context, themeResId);
    this.configuration = configuration;
  }

  @Override
  public void setCancelable(boolean cancelable) {
    super.setCancelable(cancelable);
    isCancelable = cancelable;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sentry_dialog_user_feedback);
    setCancelable(isCancelable);

    final @NotNull SentryFeedbackOptions feedbackOptions =
        new SentryFeedbackOptions(Sentry.getCurrentScopes().getOptions().getFeedbackOptions());
    if (configuration != null) {
      configuration.configure(getContext(), feedbackOptions);
    }
    final @NotNull TextView lblTitle = findViewById(R.id.sentry_dialog_user_feedback_title);
    final @NotNull ImageView imgLogo = findViewById(R.id.sentry_dialog_user_feedback_logo);
    final @NotNull TextView lblName = findViewById(R.id.sentry_dialog_user_feedback_txt_name);
    final @NotNull EditText edtName = findViewById(R.id.sentry_dialog_user_feedback_edt_name);
    final @NotNull TextView lblEmail = findViewById(R.id.sentry_dialog_user_feedback_txt_email);
    final @NotNull EditText edtEmail = findViewById(R.id.sentry_dialog_user_feedback_edt_email);
    final @NotNull TextView lblMessage =
        findViewById(R.id.sentry_dialog_user_feedback_txt_description);
    final @NotNull EditText edtMessage =
        findViewById(R.id.sentry_dialog_user_feedback_edt_description);
    final @NotNull Button btnSend = findViewById(R.id.sentry_dialog_user_feedback_btn_send);
    final @NotNull Button btnCancel = findViewById(R.id.sentry_dialog_user_feedback_btn_cancel);

    if (feedbackOptions.isShowBranding()) {
      imgLogo.setVisibility(View.VISIBLE);
    } else {
      imgLogo.setVisibility(View.GONE);
    }

    // If name is required, ignore showName flag
    if (!feedbackOptions.isShowName() && !feedbackOptions.isNameRequired()) {
      lblName.setVisibility(View.GONE);
      edtName.setVisibility(View.GONE);
    } else {
      lblName.setVisibility(View.VISIBLE);
      edtName.setVisibility(View.VISIBLE);
      lblName.setText(feedbackOptions.getNameLabel());
      edtName.setHint(feedbackOptions.getNamePlaceholder());
      if (feedbackOptions.isNameRequired()) {
        lblName.append(feedbackOptions.getIsRequiredLabel());
      }
    }

    // If email is required, ignore showEmail flag
    if (!feedbackOptions.isShowEmail() && !feedbackOptions.isEmailRequired()) {
      lblEmail.setVisibility(View.GONE);
      edtEmail.setVisibility(View.GONE);
    } else {
      lblEmail.setVisibility(View.VISIBLE);
      edtEmail.setVisibility(View.VISIBLE);
      lblEmail.setText(feedbackOptions.getEmailLabel());
      edtEmail.setHint(feedbackOptions.getEmailPlaceholder());
      if (feedbackOptions.isEmailRequired()) {
        lblEmail.append(feedbackOptions.getIsRequiredLabel());
      }
    }

    // If Sentry user is set, and useSentryUser is true, populate the name and email
    if (feedbackOptions.isUseSentryUser()) {
      final @Nullable User user = Sentry.getCurrentScopes().getScope().getUser();
      if (user != null) {
        edtName.setText(user.getName());
        edtEmail.setText(user.getEmail());
      }
    }

    lblMessage.setText(feedbackOptions.getMessageLabel());
    lblMessage.append(feedbackOptions.getIsRequiredLabel());
    edtMessage.setHint(feedbackOptions.getMessagePlaceholder());
    lblTitle.setText(feedbackOptions.getFormTitle());

    btnSend.setText(feedbackOptions.getSubmitButtonLabel());
    btnSend.setOnClickListener(
        v -> {
          // Gather fields and trim them
          final @NotNull String name = edtName.getText().toString().trim();
          final @NotNull String email = edtEmail.getText().toString().trim();
          final @NotNull String message = edtMessage.getText().toString().trim();

          // If a required field is missing, shows the error label
          if (name.isEmpty() && feedbackOptions.isNameRequired()) {
            edtName.setError(lblName.getText());
            return;
          }

          if (email.isEmpty() && feedbackOptions.isEmailRequired()) {
            edtEmail.setError(lblEmail.getText());
            return;
          }

          if (message.isEmpty()) {
            edtMessage.setError(lblMessage.getText());
            return;
          }

          // Create the feedback object
          final @NotNull Feedback feedback = new Feedback(message);
          feedback.setName(name);
          feedback.setContactEmail(email);
          if (currentReplayId != null) {
            feedback.setReplayId(currentReplayId);
          }

          // Capture the feedback. If the ID is empty, it means that the feedback was not sent
          final @NotNull SentryId id = Sentry.captureFeedback(feedback);
          if (!id.equals(SentryId.EMPTY_ID)) {
            Toast.makeText(
                    getContext(), feedbackOptions.getSuccessMessageText(), Toast.LENGTH_SHORT)
                .show();
            final @Nullable SentryFeedbackOptions.SentryFeedbackCallback onSubmitSuccess =
                feedbackOptions.getOnSubmitSuccess();
            if (onSubmitSuccess != null) {
              onSubmitSuccess.call(feedback);
            }
          } else {
            final @Nullable SentryFeedbackOptions.SentryFeedbackCallback onSubmitError =
                feedbackOptions.getOnSubmitError();
            if (onSubmitError != null) {
              onSubmitError.call(feedback);
            }
          }
          cancel();
        });

    btnCancel.setText(feedbackOptions.getCancelButtonLabel());
    btnCancel.setOnClickListener(v -> cancel());
    setOnDismissListener(delegate);
  }

  @Override
  public void setOnDismissListener(final @Nullable OnDismissListener listener) {
    delegate = listener;
    // If the user set a custom onDismissListener, we ensure it doesn't override the onFormClose
    final @NotNull SentryOptions options = Sentry.getCurrentScopes().getOptions();
    final @Nullable Runnable onFormClose = options.getFeedbackOptions().getOnFormClose();
    if (onFormClose != null) {
      super.setOnDismissListener(
          dialog -> {
            onFormClose.run();
            currentReplayId = null;
            if (delegate != null) {
              delegate.onDismiss(dialog);
            }
          });
    } else {
      super.setOnDismissListener(delegate);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    final @NotNull SentryOptions options = Sentry.getCurrentScopes().getOptions();
    final @NotNull SentryFeedbackOptions feedbackOptions = options.getFeedbackOptions();
    final @Nullable Runnable onFormOpen = feedbackOptions.getOnFormOpen();
    if (onFormOpen != null) {
      onFormOpen.run();
    }
    options.getReplayController().captureReplay(false);
    currentReplayId = options.getReplayController().getReplayId();
  }

  @Override
  public void show() {
    // If Sentry is disabled, don't show the dialog, but log a warning
    final @NotNull IScopes scopes = Sentry.getCurrentScopes();
    final @NotNull SentryOptions options = scopes.getOptions();
    if (!scopes.isEnabled() || !options.isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Sentry is disabled. Feedback dialog won't be shown.");
      return;
    }
    // Otherwise, show the dialog
    super.show();
  }

  public static class Builder {

    @Nullable OptionsConfiguration configuration;
    final @NotNull Context context;
    final int themeResId;

    /**
     * Creates a builder for a {@link SentryUserFeedbackDialog} that uses the default alert dialog
     * theme.
     *
     * <p>The default alert dialog theme is defined by {@link android.R.attr#alertDialogTheme}
     * within the parent {@code context}'s theme.
     *
     * @param context the parent context
     */
    public Builder(final @NotNull Context context) {
      this(context, 0);
    }

    /**
     * Creates a builder for a {@link SentryUserFeedbackDialog} that uses an explicit theme
     * resource.
     *
     * <p>The specified theme resource ({@code themeResId}) is applied on top of the parent {@code
     * context}'s theme. It may be specified as a style resource containing a fully-populated theme,
     * such as {@link android.R.style#Theme_Material_Dialog}, to replace all attributes in the
     * parent {@code context}'s theme including primary and accent colors.
     *
     * <p>To preserve attributes such as primary and accent colors, the {@code themeResId} may
     * instead be specified as an overlay theme such as {@link
     * android.R.style#ThemeOverlay_Material_Dialog}. This will override only the window attributes
     * necessary to style the alert window as a dialog.
     *
     * <p>Alternatively, the {@code themeResId} may be specified as {@code 0} to use the parent
     * {@code context}'s resolved value for {@link android.R.attr#alertDialogTheme}.
     *
     * @param context the parent context
     * @param themeResId the resource ID of the theme against which to inflate this dialog, or
     *     {@code 0} to use the parent {@code context}'s default alert dialog theme
     */
    public Builder(Context context, int themeResId) {
      this(context, themeResId, null);
    }
    /**
     * Creates a builder for a {@link SentryUserFeedbackDialog} that uses the default alert dialog
     * theme. The {@code configuration} can be used to configure the feedback options for this
     * specific dialog.
     *
     * <p>The default alert dialog theme is defined by {@link android.R.attr#alertDialogTheme}
     * within the parent {@code context}'s theme.
     *
     * @param context the parent context
     * @param configuration the configuration for the feedback options, can be {@code null} to use
     *     the global feedback options.
     */
    public Builder(
        final @NotNull Context context, final @Nullable OptionsConfiguration configuration) {
      this(context, 0, configuration);
    }

    /**
     * Creates a builder for a {@link SentryUserFeedbackDialog} that uses an explicit theme
     * resource. The {@code configuration} can be used to configure the feedback options for this
     * specific dialog.
     *
     * <p>The specified theme resource ({@code themeResId}) is applied on top of the parent {@code
     * context}'s theme. It may be specified as a style resource containing a fully-populated theme,
     * such as {@link android.R.style#Theme_Material_Dialog}, to replace all attributes in the
     * parent {@code context}'s theme including primary and accent colors.
     *
     * <p>To preserve attributes such as primary and accent colors, the {@code themeResId} may
     * instead be specified as an overlay theme such as {@link
     * android.R.style#ThemeOverlay_Material_Dialog}. This will override only the window attributes
     * necessary to style the alert window as a dialog.
     *
     * <p>Alternatively, the {@code themeResId} may be specified as {@code 0} to use the parent
     * {@code context}'s resolved value for {@link android.R.attr#alertDialogTheme}.
     *
     * @param context the parent context
     * @param themeResId the resource ID of the theme against which to inflate this dialog, or
     *     {@code 0} to use the parent {@code context}'s default alert dialog theme
     * @param configuration the configuration for the feedback options, can be {@code null} to use
     *     the global feedback options.
     */
    public Builder(
        final @NotNull Context context,
        final int themeResId,
        final @Nullable OptionsConfiguration configuration) {
      this.context = context;
      this.themeResId = themeResId;
      this.configuration = configuration;
    }

    /**
     * Builds a new {@link SentryUserFeedbackDialog} with the specified context, theme, and
     * configuration.
     *
     * @return a new instance of {@link SentryUserFeedbackDialog}
     */
    public SentryUserFeedbackDialog create() {
      return new SentryUserFeedbackDialog(context, themeResId, configuration);
    }
  }

  /** Configuration callback for feedback options. */
  public interface OptionsConfiguration {

    /**
     * configure the feedback options
     *
     * @param context the context of the feedback dialog
     * @param options the feedback options
     */
    void configure(final @NotNull Context context, final @NotNull SentryFeedbackOptions options);
  }
}
