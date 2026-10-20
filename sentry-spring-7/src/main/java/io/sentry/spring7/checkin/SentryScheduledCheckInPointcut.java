package io.sentry.spring7.checkin;

import io.sentry.FilterString;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

/**
 * Matches {@code @Scheduled} methods without {@link SentryCheckIn}, except excluded ones. Excludes
 * match the task name ({@code ClassName.methodName}) exactly or as a regex.
 */
@ApiStatus.Internal
public final class SentryScheduledCheckInPointcut extends StaticMethodMatcherPointcut {
  private final @NotNull List<FilterString> excludedScheduledTasks = new ArrayList<>();

  public SentryScheduledCheckInPointcut(final @Nullable List<String> excludedScheduledTasks) {
    if (excludedScheduledTasks != null) {
      for (final @NotNull String task : excludedScheduledTasks) {
        if (!task.trim().isEmpty()) {
          this.excludedScheduledTasks.add(new FilterString(task.trim()));
        }
      }
    }
  }

  @Override
  public boolean matches(final @NotNull Method method, final @NotNull Class<?> targetClass) {
    final @NotNull Class<?> userClass = ClassUtils.getUserClass(targetClass);
    // CGLIB can't intercept these
    if (Modifier.isFinal(userClass.getModifiers()) || Modifier.isFinal(method.getModifiers())) {
      return false;
    }
    final @NotNull Method specificMethod = AopUtils.getMostSpecificMethod(method, userClass);
    if (!SentryCheckInAdvice.isScheduled(specificMethod)
        || AnnotationUtils.findAnnotation(specificMethod, SentryCheckIn.class) != null) {
      return false;
    }
    // Spring calls reactive and suspend methods once and subscribes to the result on each run
    if (specificMethod.getParameterCount() > 0
        || ReactiveAdapterRegistry.getSharedInstance().getAdapter(specificMethod.getReturnType())
            != null) {
      return false;
    }
    final @NotNull String taskName =
        SentryCheckInAdvice.scheduledTaskName(userClass, specificMethod);
    for (final @NotNull FilterString excluded : excludedScheduledTasks) {
      if (excluded.getFilterString().equals(taskName) || excluded.matches(taskName)) {
        return false;
      }
    }
    return true;
  }
}
