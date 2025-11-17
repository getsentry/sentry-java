package io.sentry.samples.spring.boot.jakarta;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/feature-flag/")
public class  FeatureFlagController {
  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFlagController.class);
  private final OpenFeatureAPI openFeatureAPI;

  public FeatureFlagController(OpenFeatureAPI openFeatureAPI) {
    this.openFeatureAPI = openFeatureAPI;
  }

  @GetMapping("check/{flagKey}")
  public FeatureFlagResponse checkFlag(@PathVariable String flagKey) {
    Client client = openFeatureAPI.getClient();

    // Evaluate boolean feature flag
    // This will trigger the SentryOpenFeatureHook which tracks the evaluation
    boolean flagValue = client.getBooleanValue(flagKey, false, new ImmutableContext("example-context-key"));

    LOGGER.info("Feature flag '{}' evaluated to: {}", flagKey, flagValue);

    return new FeatureFlagResponse(flagKey, flagValue);
  }

  @GetMapping("error/{flagKey}")
  public String errorWithFeatureFlag(@PathVariable String flagKey) {
    Client client = openFeatureAPI.getClient();

    // Evaluate feature flag before throwing error
    // The feature flag will be included in the Sentry event
    boolean flagValue = client.getBooleanValue(flagKey, false, new ImmutableContext("example-context-key"));

    LOGGER.info("Feature flag '{}' evaluated to: {} before error", flagKey, flagValue);

    throw new RuntimeException("Error occurred with feature flag: " + flagKey + " = " + flagValue);
  }

  public static class FeatureFlagResponse {
    private final String flagKey;
    private final boolean value;

    public FeatureFlagResponse(String flagKey, boolean value) {
      this.flagKey = flagKey;
      this.value = value;
    }

    public String getFlagKey() {
      return flagKey;
    }

    public boolean isValue() {
      return value;
    }
  }
}

