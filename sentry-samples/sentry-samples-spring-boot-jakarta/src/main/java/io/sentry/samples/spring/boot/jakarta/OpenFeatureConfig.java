package io.sentry.samples.spring.boot.jakarta;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import io.sentry.openfeature.SentryOpenFeatureHook;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFeatureConfig {

  @Bean
  public OpenFeatureAPI openFeatureAPI() {
    OpenFeatureAPI api = OpenFeatureAPI.getInstance();

    // Use simple in-memory provider for demo purposes
    // In production, you would use a real provider like LaunchDarkly, Flagsmith, etc.
    FeatureProvider provider = new InMemoryProvider(createFeatureFlags());
    api.setProvider(provider);

    // Register Sentry hook to track feature flag evaluations
    api.addHooks(new SentryOpenFeatureHook());

    return api;
  }

  private Map<String, Object> createFeatureFlags() {
    Map<String, Object> flags = new HashMap<>();
    
    // Boolean flags
    flags.put("new-checkout-flow", true);
    flags.put("experimental-feature", false);
    flags.put("enable-caching", true);
    flags.put("dark-mode", false);
    flags.put("beta-features", true);
    
    // String flags
    flags.put("theme-color", "blue");
    flags.put("api-version", "v2");
    flags.put("default-language", "en");
    flags.put("welcome-message", "Welcome to Sentry!");
    
    // Integer flags
    flags.put("max-retries", 3);
    flags.put("page-size", 20);
    flags.put("timeout-seconds", 30);
    flags.put("max-connections", 100);
    
    // Double flags
    flags.put("discount-percentage", 0.15);
    flags.put("tax-rate", 0.08);
    flags.put("conversion-rate", 0.025);
    
    // Object/Structure flags (stored as Map)
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("enabled", true);
    configMap.put("threshold", 100);
    configMap.put("mode", "production");
    flags.put("advanced-config", configMap);
    
    Map<String, Object> uiConfig = new HashMap<>();
    uiConfig.put("layout", "grid");
    uiConfig.put("itemsPerPage", 12);
    flags.put("ui-settings", uiConfig);
    
    return flags;
  }

  /**
   * Simple in-memory provider for demo purposes.
   * In production, use a real provider like LaunchDarkly, Flagsmith, etc.
   */
  private static class InMemoryProvider implements FeatureProvider {
    private final Map<String, Object> flags;

    public InMemoryProvider(Map<String, Object> flags) {
      this.flags = flags;
    }

    @Override
    public Metadata getMetadata() {
      return new Metadata() {
        @Override
        public String getName() {
          return "InMemoryProvider";
        }
      };
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) {
      // No initialization needed for in-memory provider
    }

    @Override
    public void shutdown() {
      // No cleanup needed for in-memory provider
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
        String key, Boolean defaultValue, EvaluationContext ctx) {
      Object value = flags.get(key);
      if (value == null) {
        return ProviderEvaluation.<Boolean>builder()
            .value(defaultValue)
            .reason(Reason.DEFAULT.name())
            .build();
      }
      if (value instanceof Boolean) {
        return ProviderEvaluation.<Boolean>builder()
            .value((Boolean) value)
            .reason(Reason.STATIC.name())
            .build();
      }
      // Type mismatch - return default
      return ProviderEvaluation.<Boolean>builder()
          .value(defaultValue)
          .reason(Reason.DEFAULT.name())
          .build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(
        String key, String defaultValue, EvaluationContext ctx) {
      Object value = flags.get(key);
      if (value == null) {
        return ProviderEvaluation.<String>builder()
            .value(defaultValue)
            .reason(Reason.DEFAULT.name())
            .build();
      }
      if (value instanceof String) {
        return ProviderEvaluation.<String>builder()
            .value((String) value)
            .reason(Reason.STATIC.name())
            .build();
      }
      // Type mismatch - return default
      return ProviderEvaluation.<String>builder()
          .value(defaultValue)
          .reason(Reason.DEFAULT.name())
          .build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
        String key, Integer defaultValue, EvaluationContext ctx) {
      Object value = flags.get(key);
      if (value == null) {
        return ProviderEvaluation.<Integer>builder()
            .value(defaultValue)
            .reason(Reason.DEFAULT.name())
            .build();
      }
      if (value instanceof Integer) {
        return ProviderEvaluation.<Integer>builder()
            .value((Integer) value)
            .reason(Reason.STATIC.name())
            .build();
      }
      // Type mismatch - return default
      return ProviderEvaluation.<Integer>builder()
          .value(defaultValue)
          .reason(Reason.DEFAULT.name())
          .build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
        String key, Double defaultValue, EvaluationContext ctx) {
      Object value = flags.get(key);
      if (value == null) {
        return ProviderEvaluation.<Double>builder()
            .value(defaultValue)
            .reason(Reason.DEFAULT.name())
            .build();
      }
      if (value instanceof Double) {
        return ProviderEvaluation.<Double>builder()
            .value((Double) value)
            .reason(Reason.STATIC.name())
            .build();
      }
      // Type mismatch - return default
      return ProviderEvaluation.<Double>builder()
          .value(defaultValue)
          .reason(Reason.DEFAULT.name())
          .build();
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
        String key, Value defaultValue, EvaluationContext ctx) {
      Object value = flags.get(key);
      if (value == null) {
        return ProviderEvaluation.<Value>builder()
            .value(defaultValue)
            .reason(Reason.DEFAULT.name())
            .build();
      }
      if (value instanceof Map) {
        // Convert Map to Value object
        @SuppressWarnings("unchecked")
        Map<String, Object> mapValue = (Map<String, Object>) value;
        Value valueObj = Value.objectToValue(mapValue);
        return ProviderEvaluation.<Value>builder()
            .value(valueObj)
            .reason(Reason.STATIC.name())
            .build();
      }
      // Type mismatch - return default
      return ProviderEvaluation.<Value>builder()
          .value(defaultValue)
          .reason(Reason.DEFAULT.name())
          .build();
    }
  }
}

