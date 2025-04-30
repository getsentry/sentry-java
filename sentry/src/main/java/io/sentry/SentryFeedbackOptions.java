package io.sentry;

import org.jetbrains.annotations.NotNull;

public class SentryFeedbackOptions {

  // User and Form
  /** Requires the name field on the feedback form to be filled in. */
  private boolean isNameRequired = false;
  /** Displays the name field on the feedback form. Ignored if isNameRequired is true. */
  private boolean showName = true;
  /** Requires the email field on the feedback form to be filled in. */
  private boolean isEmailRequired = false;
  /** Displays the email field on the feedback form. Ignored if isEmailRequired is true. */
  private boolean showEmail = true;
  /** Sets the email and name fields to the corresponding Sentry SDK user fields that were called with SentrySDK.setUser. */
  private boolean useSentryUser = true;
  /** Displays the Sentry logo inside of the form */
  private boolean showBranding = true;

  // Text Customization
  /** The title of the feedback form. */
  private @NotNull String formTitle = "Report a Bug";
  /** The label of the submit button. */
  private @NotNull String submitButtonLabel = "Send Bug Report";
  /** The label of the cancel button. */
  private @NotNull String cancelButtonLabel = "Cancel";
  /** The label of the confirm button. */
  private @NotNull String confirmButtonLabel = "Confirm";
  /** The label next to the name input field. */
  private @NotNull String nameLabel = "Name";
  /** The placeholder in the name input field. */
  private @NotNull String namePlaceholder = "Your Name";
  /** The label next to the email input field. */
  private @NotNull String emailLabel = "Email";
  /** The placeholder in the email input field. */
  private @NotNull String emailPlaceholder = "your.email@example.org";
  /** The text to attach to the title label for a required field. */
  private @NotNull String isRequiredLabel = "(Required)";
  /** The label of the feedback description input field. */
  private @NotNull String messageLabel = "Description";
  /** The placeholder in the feedback description input field. */
  private @NotNull String messagePlaceholder = "What's the bug? What did you expect?";
  /** The message displayed after a successful feedback submission. */
  private @NotNull String successMessageText = "Thank you for your report!";

  public boolean isNameRequired() {
    return isNameRequired;
  }
  public void setNameRequired(boolean isNameRequired) {
    this.isNameRequired = isNameRequired;
  }
  public boolean isShowName() {
    return showName;
  }
  public void setShowName(boolean showName) {
    this.showName = showName;
  }
  public boolean isEmailRequired() {
    return isEmailRequired;
  }
  public void setEmailRequired(boolean isEmailRequired) {
    this.isEmailRequired = isEmailRequired;
  }
  public boolean isShowEmail() {
    return showEmail;
  }
  public void setShowEmail(boolean showEmail) {
    this.showEmail = showEmail;
  }

  public boolean isUseSentryUser() {
    return useSentryUser;
  }
  public void setUseSentryUser(boolean useSentryUser) {
    this.useSentryUser = useSentryUser;
  }
  public boolean isShowBranding() {
    return showBranding;
  }
  public void setShowBranding(boolean showBranding) {
    this.showBranding = showBranding;
  }

  CONTINUE
  public @NotNull String getFormTitle() {
    return formTitle;
  }
  public void setFormTitle(@NotNull String formTitle) {
    this.formTitle = formTitle;
  }
  public @NotNull String getSubmitButtonLabel() {
    return submitButtonLabel;
  }
  public void setSubmitButtonLabel(@NotNull String submitButtonLabel) {
    this.submitButtonLabel = submitButtonLabel;
  }
  public @NotNull String getCancelButtonLabel() {
    return cancelButtonLabel;
  }
  public void setCancelButtonLabel(@NotNull String cancelButtonLabel) {
    this.cancelButtonLabel = cancelButtonLabel;
  }
  public @NotNull String getConfirmButtonLabel() {
    return confirmButtonLabel;
  }
  public void setConfirmButtonLabel(@NotNull String confirmButtonLabel) {
    this.confirmButtonLabel = confirmButtonLabel;
  }
  public @NotNull String getNameLabel() {
    return nameLabel;
  }
  public void setNameLabel(@NotNull String nameLabel) {
    this.nameLabel = nameLabel;
  }
  public @NotNull String getNamePlaceholder() {
    return namePlaceholder;
  }
  public void setNamePlaceholder(@NotNull String namePlaceholder) {
    this.namePlaceholder = namePlaceholder;
  }
  public @NotNull String getEmailLabel() {
    return emailLabel;
  }
  public void setEmailLabel(@NotNull String emailLabel) {
    this.emailLabel = emailLabel;
  }
  public @NotNull String getEmailPlaceholder() {
    return emailPlaceholder;
  }
  public void setEmailPlaceholder(@NotNull String emailPlaceholder) {
    this.emailPlaceholder = emailPlaceholder;
  }
  public @NotNull String getIsRequiredLabel() {
    return isRequiredLabel;
  }
  public void setIsRequiredLabel(@NotNull String isRequiredLabel) {
    this.isRequiredLabel = isRequiredLabel;
  }

  public @NotNull String getMessageLabel() {
    return messageLabel;
  }
  public void setMessageLabel(@NotNull String messageLabel) {
    this.messageLabel = messageLabel;
  }
  public @NotNull String getMessagePlaceholder() {
    return messagePlaceholder;
  }
  public void setMessagePlaceholder(@NotNull String messagePlaceholder) {
    this.messagePlaceholder = messagePlaceholder;
  }
  public @NotNull String getSuccessMessageText() {
    return successMessageText;
  }
  public void setSuccessMessageText(@NotNull String successMessageText) {
    this.successMessageText = successMessageText;
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
        + ", confirmButtonLabel='"
        + confirmButtonLabel
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
}
