package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.SentryOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for Sentry integration. */
@ConfigurationProperties("sentry")
@Open
public class SentryProperties extends SentryOptions {

  /** Weather to use Git commit id as a release. */
  private boolean useGitCommitIdAsRelease = true;

  public boolean isUseGitCommitIdAsRelease() {
    return useGitCommitIdAsRelease;
  }

  public void setUseGitCommitIdAsRelease(boolean useGitCommitIdAsRelease) {
    this.useGitCommitIdAsRelease = useGitCommitIdAsRelease;
  }
}
