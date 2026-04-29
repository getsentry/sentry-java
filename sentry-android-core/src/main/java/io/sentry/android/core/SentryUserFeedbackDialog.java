package io.sentry.android.core;

import android.content.Context;
import io.sentry.SentryFeedbackOptions;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link SentryUserFeedbackForm} instead.
 */
@Deprecated
public final class SentryUserFeedbackDialog extends SentryUserFeedbackForm {

  SentryUserFeedbackDialog(
      final @NotNull Context context,
      final int themeResId,
      final @Nullable SentryId associatedEventId,
      final @Nullable SentryUserFeedbackForm.OptionsConfiguration configuration,
      final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {
    super(context, themeResId, associatedEventId, configuration, configurator);
  }

  /**
   * @deprecated Use {@link SentryUserFeedbackForm.Builder} instead.
   */
  @Deprecated
  public static class Builder extends SentryUserFeedbackForm.Builder {

    /**
     * Creates a builder for a {@link SentryUserFeedbackDialog} that uses the default alert dialog
     * theme.
     *
     * @param context the parent context
     * @deprecated Use {@link SentryUserFeedbackForm.Builder#Builder(Context)} instead.
     */
    @Deprecated
    public Builder(final @NotNull Context context) {
      super(context);
    }

    /**
     * Creates a builder for a {@link SentryUserFeedbackDialog} that uses an explicit theme
     * resource.
     *
     * @param context the parent context
     * @param themeResId the resource ID of the theme
     * @deprecated Use {@link SentryUserFeedbackForm.Builder#Builder(Context, int)} instead.
     */
    @Deprecated
    public Builder(Context context, int themeResId) {
      super(context, themeResId);
    }

    /**
     * Creates a builder for a {@link SentryUserFeedbackDialog} with a configuration.
     *
     * @param context the parent context
     * @param configuration the configuration for the feedback options
     * @deprecated Use {@link SentryUserFeedbackForm.Builder#Builder(Context,
     *     SentryUserFeedbackForm.OptionsConfiguration)} instead.
     */
    @Deprecated
    public Builder(
        final @NotNull Context context, final @Nullable OptionsConfiguration configuration) {
      super(context, configuration);
    }

    /**
     * Creates a builder for a {@link SentryUserFeedbackDialog} with a theme and configuration.
     *
     * @param context the parent context
     * @param themeResId the resource ID of the theme
     * @param configuration the configuration for the feedback options
     * @deprecated Use {@link SentryUserFeedbackForm.Builder#Builder(Context, int,
     *     SentryUserFeedbackForm.OptionsConfiguration)} instead.
     */
    @Deprecated
    public Builder(
        final @NotNull Context context,
        final int themeResId,
        final @Nullable OptionsConfiguration configuration) {
      super(context, themeResId, configuration);
    }

    @Deprecated
    @Override
    public Builder configurator(
        final @Nullable SentryFeedbackOptions.OptionsConfigurator configurator) {
      super.configurator(configurator);
      return this;
    }

    @Deprecated
    @Override
    public Builder associatedEventId(final @Nullable SentryId associatedEventId) {
      super.associatedEventId(associatedEventId);
      return this;
    }

    @Override
    public SentryUserFeedbackDialog create() {
      return new SentryUserFeedbackDialog(
          context, themeResId, associatedEventId, configuration, configurator);
    }
  }

  /**
   * @deprecated Use {@link SentryUserFeedbackForm.OptionsConfiguration} instead.
   */
  @Deprecated
  public interface OptionsConfiguration extends SentryUserFeedbackForm.OptionsConfiguration {}
}
