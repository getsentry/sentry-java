package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// other approach is to use EventProcessor<T extends SentryBaseEvent>
// and having a processor which is the common fields and specific processors for Event and Transactions
// not sure if our event processor interface should be deal with this
// the downside using only the base class, if people want to process a field which is not part of SentryBaseEvent
// they need to cast it manually
public interface EventProcessor {
  @Nullable
  SentryBaseEvent process(@NotNull SentryBaseEvent event, @Nullable Object hint);
}
