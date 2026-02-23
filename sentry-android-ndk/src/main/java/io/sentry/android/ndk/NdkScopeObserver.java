package io.sentry.android.ndk;

import io.sentry.Attachment;
import io.sentry.Breadcrumb;
import io.sentry.DateUtils;
import io.sentry.IScope;
import io.sentry.ScopeObserverAdapter;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanContext;
import io.sentry.ndk.INativeScope;
import io.sentry.ndk.NativeScope;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NdkScopeObserver extends ScopeObserverAdapter {

  private final @NotNull SentryOptions options;
  private final @NotNull INativeScope nativeScope;
  private final @Nullable INativeScopeAttachments nativeScopeAttachments;

  public NdkScopeObserver(final @NotNull SentryOptions options) {
    this(options, new NativeScope());
  }

  NdkScopeObserver(final @NotNull SentryOptions options, final @NotNull INativeScope nativeScope) {
    this.options = Objects.requireNonNull(options, "The SentryOptions object is required.");
    this.nativeScope = Objects.requireNonNull(nativeScope, "The NativeScope object is required.");
    this.nativeScopeAttachments =
        nativeScope instanceof INativeScopeAttachments
            ? (INativeScopeAttachments) nativeScope
            : null;
  }

  @Override
  public void setUser(final @Nullable User user) {
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                if (user == null) {
                  // remove user if its null
                  nativeScope.removeUser();
                } else {
                  nativeScope.setUser(
                      user.getId(), user.getEmail(), user.getIpAddress(), user.getUsername());
                }
              });
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setUser has an error.");
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb crumb) {
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                String level = null;
                if (crumb.getLevel() != null) {
                  level = crumb.getLevel().name().toLowerCase(Locale.ROOT);
                }
                final String timestamp = DateUtils.getTimestamp(crumb.getTimestamp());

                String data = null;
                try {
                  final Map<String, Object> dataRef = crumb.getData();
                  if (!dataRef.isEmpty()) {
                    data = options.getSerializer().serialize(dataRef);
                  }
                } catch (Throwable e) {
                  options
                      .getLogger()
                      .log(SentryLevel.ERROR, e, "Breadcrumb data is not serializable.");
                }

                nativeScope.addBreadcrumb(
                    level,
                    crumb.getMessage(),
                    crumb.getCategory(),
                    crumb.getType(),
                    timestamp,
                    data);
              });
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync addBreadcrumb has an error.");
    }
  }

  @Override
  public void setTag(final @NotNull String key, final @NotNull String value) {
    try {
      options.getExecutorService().submit(() -> nativeScope.setTag(key, value));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setTag(%s) has an error.", key);
    }
  }

  @Override
  public void removeTag(final @NotNull String key) {
    try {
      options.getExecutorService().submit(() -> nativeScope.removeTag(key));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync removeTag(%s) has an error.", key);
    }
  }

  @Override
  public void setExtra(final @NotNull String key, final @NotNull String value) {
    try {
      options.getExecutorService().submit(() -> nativeScope.setExtra(key, value));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setExtra(%s) has an error.", key);
    }
  }

  @Override
  public void removeExtra(final @NotNull String key) {
    try {
      options.getExecutorService().submit(() -> nativeScope.removeExtra(key));
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, e, "Scope sync removeExtra(%s) has an error.", key);
    }
  }

  @Override
  public void setTrace(@Nullable SpanContext spanContext, @NotNull IScope scope) {
    if (spanContext == null) {
      return;
    }

    try {
      options
          .getExecutorService()
          .submit(
              () ->
                  nativeScope.setTrace(
                      spanContext.getTraceId().toString(), spanContext.getSpanId().toString()));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setTrace failed.");
    }
  }

  @Override
  public void addAttachment(final @NotNull Attachment attachment) {
    if (nativeScopeAttachments == null) {
      return;
    }
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                final @Nullable String pathname = attachment.getPathname();
                if (pathname != null) {
                  nativeScopeAttachments.attachFile(pathname);
                  return;
                }

                final byte @Nullable [] bytes = attachment.getBytes();
                if (bytes != null) {
                  nativeScopeAttachments.attachBytes(bytes, attachment.getFilename());
                  return;
                }

                final @Nullable Callable<byte[]> byteProvider = attachment.getByteProvider();
                if (byteProvider != null) {
                  try {
                    final byte @Nullable [] providedBytes = byteProvider.call();
                    if (providedBytes != null) {
                      nativeScopeAttachments.attachBytes(providedBytes, attachment.getFilename());
                    }
                  } catch (Throwable e) {
                    options
                        .getLogger()
                        .log(
                            SentryLevel.ERROR,
                            e,
                            "Failed to resolve bytes from attachment provider for: %s",
                            attachment.getFilename());
                  }
                }
              });
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync addAttachment has an error.");
    }
  }

  @Override
  public void setAttachments(final @NotNull List<Attachment> attachments) {
    if (nativeScopeAttachments == null) {
      return;
    }
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                nativeScopeAttachments.clearAttachments();
                for (final Attachment attachment : attachments) {
                  addAttachment(attachment);
                }
              });
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Scope sync setAttachments has an error.");
    }
  }
}
