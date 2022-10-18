package io.sentry;

import java.io.FilePermission;
import java.security.Permission;

/**
 * Writing this in Kotlin produced stackoverflows because Kotlins CharSequence.contains inside
 * checkPermission requests a FilePermissions for StringsKt.class, which again calls
 * checkPermission.
 */
@SuppressWarnings("removal")
public final class DenyReadFileSecurityManager extends java.lang.SecurityManager {

  private final String pathname;

  public DenyReadFileSecurityManager(String pathname) {
    this.pathname = pathname;
  }

  @Override
  public void checkPermission(Permission permission) {
    if (permission instanceof FilePermission
        && permission.getName() != null
        && permission.getName().contains(pathname)) {
      throw new SecurityException(String.format("Reading file %s is not allowed", pathname));
    }
  }
}
