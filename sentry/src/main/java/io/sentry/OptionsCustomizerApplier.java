package io.sentry;

import java.lang.reflect.Constructor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OptionsCustomizerApplier<T extends SentryOptions> {

  private final @NotNull Class<? extends Sentry.OptionsConfiguration<T>> customizerClass;
  private final @NotNull ClassLoader classLoader;

  public OptionsCustomizerApplier(
      final @NotNull Class<? extends Sentry.OptionsConfiguration<T>> customizerClass) {
    this(customizerClass, OptionsCustomizerApplier.class.getClassLoader());
  }

  public OptionsCustomizerApplier(
      final @NotNull Class<? extends Sentry.OptionsConfiguration<T>> customizerClass,
      final @Nullable ClassLoader classLoader) {
    this.customizerClass = customizerClass;
    // bootstrap classloader is represented as null, so using system classloader instead
    if (classLoader == null) {
      this.classLoader = ClassLoader.getSystemClassLoader();
    } else {
      this.classLoader = classLoader;
    }
  }

  @SuppressWarnings("unchecked")
  public void apply(@NotNull T options) {
    final @Nullable String optionsCustomizerClassFullName = options.getOptionsCustomizer();
    if (optionsCustomizerClassFullName != null) {
      try {
        final @NotNull Class<?> clazz = classLoader.loadClass(optionsCustomizerClassFullName);
        final @NotNull Class<?>[] interfaces = clazz.getInterfaces();
        boolean foundOptionsConfigInterface = false;
        for (Class<?> anInterface : interfaces) {
          if (customizerClass.isAssignableFrom(anInterface)) {
            foundOptionsConfigInterface = true;
          }
        }

        if (foundOptionsConfigInterface) {
          Constructor<?> constructor = clazz.getDeclaredConstructor();
          if (constructor != null) {
            final @NotNull Object customizerObject = constructor.newInstance();
            final @NotNull Sentry.OptionsConfiguration<T> untypedCustomizer =
                (Sentry.OptionsConfiguration<T>) customizerObject;
            options
                .getLogger()
                .log(
                    SentryLevel.DEBUG,
                    "Calling %s as it was defined as 'optionsCustomizer'.",
                    optionsCustomizerClassFullName);
            untypedCustomizer.configure(options);
          } else {
            options
                .getLogger()
                .log(
                    SentryLevel.ERROR,
                    "Class %s specified as 'optionsCustomizer' does not have a zero argument constructor.",
                    optionsCustomizerClassFullName);
          }
        } else {
          options
              .getLogger()
              .log(
                  SentryLevel.ERROR,
                  "Class %s specified as 'optionsCustomizer' does not implement SentryOptionsCustomizerBase interface.",
                  optionsCustomizerClassFullName);
        }
      } catch (ClassNotFoundException e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                e,
                "Unable to find %s specified as 'optionsCustomizer'.",
                optionsCustomizerClassFullName);
      } catch (Throwable t) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                t,
                "Error in 'optionsCustomizer', using %s.",
                optionsCustomizerClassFullName);
      }
    }
  }
}
