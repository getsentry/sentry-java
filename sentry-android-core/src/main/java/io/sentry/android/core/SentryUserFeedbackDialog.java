package io.sentry.android.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import io.sentry.Sentry;
import io.sentry.SentryFeedbackOptions;
import io.sentry.protocol.Feedback;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryUserFeedbackDialog extends AlertDialog {

  private boolean isCancelable = false;

  public SentryUserFeedbackDialog(final @NotNull Context context) {
    super(context);
    isCancelable = false;
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
    isCancelable = false;
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

    if (feedbackOptions.isUseSentryUser()) {
      final @Nullable User user = Sentry.getCurrentScopes().getScope().getUser();
      if (user != null) {
        edtName.setText(user.getName());
        edtEmail.setText(user.getEmail());
      }
    }

    lblMessage.setText(feedbackOptions.getMessageLabel());
    edtMessage.setHint(feedbackOptions.getMessagePlaceholder());
    lblTitle.setText(feedbackOptions.getFormTitle());

    btnSend.setBackgroundTintList(
        ColorStateList.valueOf(Color.parseColor(feedbackOptions.getSubmitBackgroundHex())));
    btnSend.setTextColor(Color.parseColor(feedbackOptions.getSubmitForegroundHex()));
    btnSend.setText(feedbackOptions.getSubmitButtonLabel());
    btnSend.setOnClickListener(
        v -> {
          final @NotNull Feedback feedback = new Feedback(edtMessage.getText().toString());
          feedback.setName(edtName.getText().toString());
          feedback.setContactEmail(edtEmail.getText().toString());

          SentryId id = Sentry.captureFeedback(feedback);
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

    final @Nullable Runnable onFormClose = feedbackOptions.getOnFormClose();
    if (onFormClose != null) {
      setOnDismissListener(dialog -> onFormClose.run());
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    final @NotNull SentryFeedbackOptions feedbackOptions =
        Sentry.getCurrentScopes().getOptions().getFeedbackOptions();
    final @Nullable Runnable onFormOpen = feedbackOptions.getOnFormOpen();
    if (onFormOpen != null) {
      onFormOpen.run();
    }
  }
}
