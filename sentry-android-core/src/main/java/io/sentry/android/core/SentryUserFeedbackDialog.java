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

  public SentryUserFeedbackDialog(final @NotNull Context context) {
    super(context);
  }

  public SentryUserFeedbackDialog(
      final @NotNull Context context,
      final boolean cancelable,
      @Nullable final OnCancelListener cancelListener) {
    super(context, cancelable, cancelListener);
    isCancelable = cancelable;
  }

  public SentryUserFeedbackDialog(final @NotNull Context context, final int themeResId) {
    super(context, themeResId);
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
        Sentry.getCurrentScopes().getOptions().getFeedbackOptions();
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
}
