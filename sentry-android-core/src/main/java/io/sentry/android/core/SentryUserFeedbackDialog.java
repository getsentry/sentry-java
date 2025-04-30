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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SentryUserFeedbackDialog extends AlertDialog {

    private boolean isCancelable = false;

    protected SentryUserFeedbackDialog(@NotNull final Context context) {
        super(context);
        isCancelable = false;
    }

    protected SentryUserFeedbackDialog(@NotNull final Context context, final boolean cancelable, @Nullable final OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
        isCancelable = cancelable;
    }

    protected SentryUserFeedbackDialog(@NotNull final Context context, final int themeResId) {
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

        @NotNull final TextView lblTitle = findViewById(R.id.sentry_dialog_user_feedback_title);
        @NotNull final ImageView imgLogo = findViewById(R.id.sentry_dialog_user_feedback_logo);
        @NotNull final TextView lblName = findViewById(R.id.sentry_dialog_user_feedback_txt_name);
        @NotNull final EditText edtName = findViewById(R.id.sentry_dialog_user_feedback_edt_name);
        @NotNull final TextView lblEmail = findViewById(R.id.sentry_dialog_user_feedback_txt_email);
        @NotNull final EditText edtEmail = findViewById(R.id.sentry_dialog_user_feedback_edt_email);
        @NotNull final TextView lblMessage = findViewById(R.id.sentry_dialog_user_feedback_txt_description);
        @NotNull final EditText edtMessage = findViewById(R.id.sentry_dialog_user_feedback_edt_description);
        @NotNull final Button btnSend = findViewById(R.id.sentry_dialog_user_feedback_btn_send);
        @NotNull final Button btnCancel = findViewById(R.id.sentry_dialog_user_feedback_btn_cancel);

        if (showBranding) {
            imgLogo.setVisibility(View.VISIBLE);
        } else {
            imgLogo.setVisibility(View.GONE);
        }

        if (!showName && !isNameRequired) {
            lblName.setVisibility(View.GONE);
            edtName.setVisibility(View.GONE);
        } else {
            lblName.setVisibility(View.VISIBLE);
            edtName.setVisibility(View.VISIBLE);
            lblName.setText(nameLabel);
            edtName.setHint(namePlaceholder);
            if (isNameRequired) {
                lblName.append(isRequiredLabel);
            }
        }

        if (!showEmail && !isEmailRequired) {
            lblEmail.setVisibility(View.GONE);
            edtEmail.setVisibility(View.GONE);
        } else {
            lblEmail.setVisibility(View.VISIBLE);
            edtEmail.setVisibility(View.VISIBLE);
            lblEmail.setText(emailLabel);
            edtEmail.setHint(emailPlaceholder);
            if (isEmailRequired) {
                lblEmail.append(isRequiredLabel);
            }
        }

        if (useSentryUser) {
            edtName.setText();
            edtEmail.setText();
        }

        lblMessage.setText(messageLabel);
        edtMessage.setHint(messagePlaceholder);
        lblTitle.setText(formTitle);

        btnSend.setText(submitButtonLabel);
        btnSend.setOnClickListener(v -> {
            Toast.makeText(getContext(), successMessageText, Toast.LENGTH_SHORT).show();
            cancel();
        });

        btnCancel.setText(cancelButtonLabel);
        btnCancel.setOnClickListener(v -> cancel());
    }

    @Override
    protected void onStart() {
        super.onStart();
    }
}
