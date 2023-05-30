package io.sentry.internal.debugmeta;

import java.util.Properties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IDebugMetaLoader {
  @Nullable
  Properties loadDebugMeta();
}
