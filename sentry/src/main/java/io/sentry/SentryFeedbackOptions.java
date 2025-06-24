package io.sentry;

import io.sentry.protocol.Feedback;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryFeedbackOptions {
  // User and Form
  /** Requires the name field on the feedback form to be filled in. Defaults to false. */
  private boolean isNameRequired = false;

  /**
   * Displays the name field on the feedback form. Ignored if isNameRequired is true. Defaults to
   * true.
   */
  private boolean showName = true;

  /** Requires the email field on the feedback form to be filled in. Defaults to false. */
  private boolean isEmailRequired = false;

  /**
   * Displays the email field on the feedback form. Ignored if isEmailRequired is true. Defaults to
   * true.
   */
  private boolean showEmail = true;

  /**
   * Sets the email and name fields to the corresponding Sentry SDK user fields that were called
   * with SentrySDK.setUser. Defaults to true.
   */
  private boolean useSentryUser = true;

  /** Displays the Sentry logo inside of the form. Defaults to true. */
  private boolean showBranding = true;

  // Text Customization
  /** The title of the feedback form. Defaults to "Report a Bug". */
  private @NotNull CharSequence formTitle = "Report a Bug";

  /** The label of the submit button. Defaults to "Send Bug Report". */
  private @NotNull CharSequence submitButtonLabel = "Send Bug Report";

  /** The label of the cancel button. Defaults to "Cancel". */
  private @NotNull CharSequence cancelButtonLabel = "Cancel";

  /** The label next to the name input field. Defaults to "Name". */
  private @NotNull CharSequence nameLabel = "Name";

  /** The placeholder in the name input field. Defaults to "Your Name". */
  private @NotNull CharSequence namePlaceholder = "Your Name";

  /** The label next to the email input field. Defaults to "Email". */
  private @NotNull CharSequence emailLabel = "Email";

  /** The placeholder in the email input field. Defaults to "your.email@example.org". */
  private @NotNull CharSequence emailPlaceholder = "your.email@example.org";

  /** The text to attach to the title label for a required field. Defaults to " (Required)". */
  private @NotNull CharSequence isRequiredLabel = " (Required)";

  /** The label of the feedback description input field. Defaults to "Description". */
  private @NotNull CharSequence messageLabel = "Description";

  /**
   * The placeholder in the feedback description input field. Defaults to "What's the bug? What did
   * you expect?".
   */
  private @NotNull CharSequence messagePlaceholder = "What's the bug? What did you expect?";

  /**
   * The message displayed after a successful feedback submission. Defaults to "Thank you for your
   * report!".
   */
  private @NotNull CharSequence successMessageText = "Thank you for your report!";

  // Callbacks
  /** Callback called when the feedback form is opened. */
  private @Nullable Runnable onFormOpen;

  /** Callback called when the feedback form is closed. */
  private @Nullable Runnable onFormClose;

  /** Callback called when feedback is successfully submitted via the prepared form. */
  private @Nullable SentryFeedbackOptions.SentryFeedbackCallback onSubmitSuccess;

  /** Callback called when there is an error submitting feedback via the prepared form. */
  private @Nullable SentryFeedbackCallback onSubmitError;

  public SentryFeedbackOptions() {}

  /** Creates a copy of the passed {@link SentryFeedbackOptions}. */
  public SentryFeedbackOptions(final @NotNull SentryFeedbackOptions other) {
    this.isNameRequired = other.isNameRequired;
    this.showName = other.showName;
    this.isEmailRequired = other.isEmailRequired;
    this.showEmail = other.showEmail;
    this.useSentryUser = other.useSentryUser;
    this.showBranding = other.showBranding;
    this.formTitle = other.formTitle;
    this.submitButtonLabel = other.submitButtonLabel;
    this.cancelButtonLabel = other.cancelButtonLabel;
    this.nameLabel = other.nameLabel;
    this.namePlaceholder = other.namePlaceholder;
    this.emailLabel = other.emailLabel;
    this.emailPlaceholder = other.emailPlaceholder;
    this.isRequiredLabel = other.isRequiredLabel;
    this.messageLabel = other.messageLabel;
    this.messagePlaceholder = other.messagePlaceholder;
    this.successMessageText = other.successMessageText;
    this.onFormOpen = other.onFormOpen;
    this.onFormClose = other.onFormClose;
    this.onSubmitSuccess = other.onSubmitSuccess;
    this.onSubmitError = other.onSubmitError;
  }

  /**
   * Requires the name field on the feedback form to be filled in. Defaults to false.
   *
   * @return true if the name field is required
   */
  public boolean isNameRequired() {
    return isNameRequired;
  }

  /**
   * Sets whether the name field on the feedback form is required. Defaults to false.
   *
   * @param isNameRequired true if the name field is required
   */
  public void setNameRequired(final boolean isNameRequired) {
    this.isNameRequired = isNameRequired;
  }

  /**
   * Displays the name field on the feedback form. Ignored if isNameRequired is true. Defaults to
   * true.
   *
   * @return true if the name field is shown
   */
  public boolean isShowName() {
    return showName;
  }

  /**
   * Sets whether the name field on the feedback form is shown. Ignored if isNameRequired is true.
   * Defaults to true.
   *
   * @param showName true if the name field is shown
   */
  public void setShowName(final boolean showName) {
    this.showName = showName;
  }

  /**
   * Requires the email field on the feedback form to be filled in. Defaults to false.
   *
   * @return true if the email field is required
   */
  public boolean isEmailRequired() {
    return isEmailRequired;
  }

  /**
   * Sets whether the email field on the feedback form is required. Defaults to false.
   *
   * @param isEmailRequired true if the email field is required
   */
  public void setEmailRequired(boolean isEmailRequired) {
    this.isEmailRequired = isEmailRequired;
  }

  /**
   * Displays the email field on the feedback form. Ignored if isEmailRequired is true. Defaults to
   * true.
   *
   * @return true if the email field is shown
   */
  public boolean isShowEmail() {
    return showEmail;
  }

  /**
   * Sets whether the email field on the feedback form is shown. Ignored if isEmailRequired is true.
   * Defaults to true.
   *
   * @param showEmail true if the email field is shown
   */
  public void setShowEmail(final boolean showEmail) {
    this.showEmail = showEmail;
  }

  /**
   * Sets the email and name fields to the corresponding Sentry SDK user fields that were called
   * with SentrySDK.setUser. Defaults to true.
   *
   * @return true if the email and name fields are set to the Sentry SDK user fields
   */
  public boolean isUseSentryUser() {
    return useSentryUser;
  }

  /**
   * Sets whether the email and name fields are set to the corresponding Sentry SDK user fields that
   * were called with SentrySDK.setUser. Defaults to true.
   *
   * @param useSentryUser true if the email and name fields are set to the Sentry SDK user fields
   */
  public void setUseSentryUser(final boolean useSentryUser) {
    this.useSentryUser = useSentryUser;
  }

  /**
   * Displays the Sentry logo inside of the form. Defaults to true.
   *
   * @return true if the Sentry logo is shown
   */
  public boolean isShowBranding() {
    return showBranding;
  }

  /**
   * Sets whether the Sentry logo is shown inside of the form. Defaults to true.
   *
   * @param showBranding true if the Sentry logo is shown
   */
  public void setShowBranding(final boolean showBranding) {
    this.showBranding = showBranding;
  }

  /**
   * The title of the feedback form. Defaults to "Report a Bug".
   *
   * @return the title of the feedback form
   */
  public @NotNull CharSequence getFormTitle() {
    return formTitle;
  }

  /**
   * Sets the title of the feedback form.
   *
   * @param formTitle the title of the feedback form
   */
  public void setFormTitle(final @NotNull CharSequence formTitle) {
    this.formTitle = formTitle;
  }

  /**
   * The label of the submit button. Defaults to "Send Bug Report".
   *
   * @return the label of the submit button
   */
  public @NotNull CharSequence getSubmitButtonLabel() {
    return submitButtonLabel;
  }

  /**
   * Sets the label of the submit button. Defaults to "Send Bug Report".
   *
   * @param submitButtonLabel the label of the submit button
   */
  public void setSubmitButtonLabel(final @NotNull CharSequence submitButtonLabel) {
    this.submitButtonLabel = submitButtonLabel;
  }

  /**
   * The label of the cancel button. Defaults to "Cancel".
   *
   * @return the label of the cancel button
   */
  public @NotNull CharSequence getCancelButtonLabel() {
    return cancelButtonLabel;
  }

  /**
   * Sets the label of the cancel button.
   *
   * @param cancelButtonLabel the label of the cancel button
   */
  public void setCancelButtonLabel(final @NotNull CharSequence cancelButtonLabel) {
    this.cancelButtonLabel = cancelButtonLabel;
  }

  /**
   * The label next to the name input field. Defaults to "Name".
   *
   * @return the label next to the name input field
   */
  public @NotNull CharSequence getNameLabel() {
    return nameLabel;
  }

  /**
   * Sets the label next to the name input field.
   *
   * @param nameLabel the label next to the name input field
   */
  public void setNameLabel(final @NotNull CharSequence nameLabel) {
    this.nameLabel = nameLabel;
  }

  /**
   * The placeholder in the name input field. Defaults to "Your Name".
   *
   * @return the placeholder in the name input field
   */
  public @NotNull CharSequence getNamePlaceholder() {
    return namePlaceholder;
  }

  /**
   * Sets the placeholder in the name input field.
   *
   * @param namePlaceholder the placeholder in the name input field
   */
  public void setNamePlaceholder(final @NotNull CharSequence namePlaceholder) {
    this.namePlaceholder = namePlaceholder;
  }

  /**
   * The label next to the email input field. Defaults to "Email".
   *
   * @return the label next to the email input field
   */
  public @NotNull CharSequence getEmailLabel() {
    return emailLabel;
  }

  /**
   * Sets the label next to the email input field.
   *
   * @param emailLabel the label next to the email input field
   */
  public void setEmailLabel(final @NotNull CharSequence emailLabel) {
    this.emailLabel = emailLabel;
  }

  /**
   * The placeholder in the email input field. Defaults to "your.email@example.org".
   *
   * @return the placeholder in the email input field
   */
  public @NotNull CharSequence getEmailPlaceholder() {
    return emailPlaceholder;
  }

  /**
   * Sets the placeholder in the email input field.
   *
   * @param emailPlaceholder the placeholder in the email input field
   */
  public void setEmailPlaceholder(final @NotNull CharSequence emailPlaceholder) {
    this.emailPlaceholder = emailPlaceholder;
  }

  /**
   * The text to attach to the title label for a required field. Defaults to " (Required)".
   *
   * @return the text to attach to the title label for a required field
   */
  public @NotNull CharSequence getIsRequiredLabel() {
    return isRequiredLabel;
  }

  /**
   * Sets the text to attach to the title label for a required field.
   *
   * @param isRequiredLabel the text to attach to the title label for a required field
   */
  public void setIsRequiredLabel(final @NotNull CharSequence isRequiredLabel) {
    this.isRequiredLabel = isRequiredLabel;
  }

  /**
   * The label of the feedback description input field. Defaults to "Description".
   *
   * @return the label of the feedback description input field
   */
  public @NotNull CharSequence getMessageLabel() {
    return messageLabel;
  }

  /**
   * Sets the label of the feedback description input field.
   *
   * @param messageLabel the label of the feedback description input field
   */
  public void setMessageLabel(final @NotNull CharSequence messageLabel) {
    this.messageLabel = messageLabel;
  }

  /**
   * The placeholder in the feedback description input field. Defaults to "What's the bug? What did
   * you expect?".
   *
   * @return the placeholder in the feedback description input field
   */
  public @NotNull CharSequence getMessagePlaceholder() {
    return messagePlaceholder;
  }

  /**
   * Sets the placeholder in the feedback description input field.
   *
   * @param messagePlaceholder the placeholder in the feedback description input field
   */
  public void setMessagePlaceholder(final @NotNull CharSequence messagePlaceholder) {
    this.messagePlaceholder = messagePlaceholder;
  }

  /**
   * The message displayed after a successful feedback submission. Defaults to "Thank you for your
   * report!".
   *
   * @return the message displayed after a successful feedback submission
   */
  public @NotNull CharSequence getSuccessMessageText() {
    return successMessageText;
  }

  /**
   * Sets the message displayed after a successful feedback submission.
   *
   * @param successMessageText the message displayed after a successful feedback submission
   */
  public void setSuccessMessageText(final @NotNull CharSequence successMessageText) {
    this.successMessageText = successMessageText;
  }

  // Callbacks
  /**
   * Callback called when the feedback form is opened.
   *
   * @return the callback to be called when the feedback form is opened
   */
  public @Nullable Runnable getOnFormOpen() {
    return onFormOpen;
  }

  /**
   * Sets the callback to be called when the feedback form is opened.
   *
   * @param onFormOpen the callback to be called when the feedback form is opened
   */
  public void setOnFormOpen(final @Nullable Runnable onFormOpen) {
    this.onFormOpen = onFormOpen;
  }

  /**
   * Callback called when the feedback form is closed.
   *
   * @return the callback to be called when the feedback form is closed
   */
  public @Nullable Runnable getOnFormClose() {
    return onFormClose;
  }

  /**
   * Sets the callback to be called when the feedback form is closed.
   *
   * @param onFormClose the callback to be called when the feedback form is closed
   */
  public void setOnFormClose(final @Nullable Runnable onFormClose) {
    this.onFormClose = onFormClose;
  }

  /**
   * Callback called when feedback is successfully submitted via the prepared form.
   *
   * @return the callback to be called when feedback is successfully submitted via the prepared form
   */
  public @Nullable SentryFeedbackOptions.SentryFeedbackCallback getOnSubmitSuccess() {
    return onSubmitSuccess;
  }

  /**
   * Sets the callback to be called when feedback is successfully submitted via the prepared form.
   *
   * @param onSubmitSuccess the callback to be called when feedback is successfully submitted via
   *     the prepared form
   */
  public void setOnSubmitSuccess(
      final @Nullable SentryFeedbackOptions.SentryFeedbackCallback onSubmitSuccess) {
    this.onSubmitSuccess = onSubmitSuccess;
  }

  /**
   * Callback called when there is an error submitting feedback via the prepared form.
   *
   * @return the callback to be called when there is an error submitting feedback via the prepared
   *     form
   */
  public @Nullable SentryFeedbackCallback getOnSubmitError() {
    return onSubmitError;
  }

  /**
   * Sets the callback to be called when there is an error submitting feedback via the prepared
   * form.
   *
   * @param onSubmitError the callback to be called when there is an error submitting feedback via
   *     the prepared form
   */
  public void setOnSubmitError(final @Nullable SentryFeedbackCallback onSubmitError) {
    this.onSubmitError = onSubmitError;
  }

  @Override
  public String toString() {
    return "SentryFeedbackOptions{"
        + "isNameRequired="
        + isNameRequired
        + ", showName="
        + showName
        + ", isEmailRequired="
        + isEmailRequired
        + ", showEmail="
        + showEmail
        + ", useSentryUser="
        + useSentryUser
        + ", showBranding="
        + showBranding
        + ", formTitle='"
        + formTitle
        + '\''
        + ", submitButtonLabel='"
        + submitButtonLabel
        + '\''
        + ", cancelButtonLabel='"
        + cancelButtonLabel
        + '\''
        + ", nameLabel='"
        + nameLabel
        + '\''
        + ", namePlaceholder='"
        + namePlaceholder
        + '\''
        + ", emailLabel='"
        + emailLabel
        + '\''
        + ", emailPlaceholder='"
        + emailPlaceholder
        + '\''
        + ", isRequiredLabel='"
        + isRequiredLabel
        + '\''
        + ", messageLabel='"
        + messageLabel
        + '\''
        + ", messagePlaceholder='"
        + messagePlaceholder
        + '\''
        + '}';
  }

  public interface SentryFeedbackCallback {
    void call(final @NotNull Feedback feedback);
  }
}
