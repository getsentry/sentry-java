package io.sentry.android.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import io.sentry.android.core.internal.util.ContentProviderSecurityChecker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A ContentProvider that does NOT store or provide any data for read or write operations.
 *
 * <p>This does not allow for overriding the abstract query, insert, update, and delete operations
 * of the {@link ContentProvider}. Additionally, those functions are secure.
 */
@ApiStatus.Internal
abstract class EmptySecureContentProvider extends ContentProvider {

  private final ContentProviderSecurityChecker securityChecker =
      new ContentProviderSecurityChecker();

  @Override
  public final @Nullable Cursor query(
      @NotNull Uri uri,
      @Nullable String[] strings,
      @Nullable String s,
      @Nullable String[] strings1,
      @Nullable String s1) {
    securityChecker.checkPrivilegeEscalation(this);
    return null;
  }

  @Override
  public final @Nullable Uri insert(@NotNull Uri uri, @Nullable ContentValues contentValues) {
    securityChecker.checkPrivilegeEscalation(this);
    return null;
  }

  @Override
  public final int delete(@NotNull Uri uri, @Nullable String s, @Nullable String[] strings) {
    securityChecker.checkPrivilegeEscalation(this);
    return 0;
  }

  @Override
  public final int update(
      @NotNull Uri uri,
      @Nullable ContentValues contentValues,
      @Nullable String s,
      @Nullable String[] strings) {
    securityChecker.checkPrivilegeEscalation(this);
    return 0;
  }
}
