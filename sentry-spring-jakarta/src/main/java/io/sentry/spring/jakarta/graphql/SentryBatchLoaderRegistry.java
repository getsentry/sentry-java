package io.sentry.spring.jakarta.graphql;

import static io.sentry.graphql.SentryGraphqlInstrumentation.SENTRY_SCOPES_CONTEXT_KEY;

import graphql.GraphQLContext;
import io.sentry.Breadcrumb;
import io.sentry.IScopes;
import io.sentry.NoOpScopes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ApiStatus.Internal
public final class SentryBatchLoaderRegistry implements BatchLoaderRegistry {

  private final @NotNull BatchLoaderRegistry delegate;

  SentryBatchLoaderRegistry(final @NotNull BatchLoaderRegistry delegate) {
    this.delegate = delegate;
  }

  @Override
  public <K, V> RegistrationSpec<K, V> forTypePair(Class<K> keyType, Class<V> valueType) {
    return new SentryRegistrationSpec<K, V>(
        delegate.forTypePair(keyType, valueType), keyType, valueType);
  }

  @Override
  public <K, V> RegistrationSpec<K, V> forName(String name) {
    return new SentryRegistrationSpec<K, V>(delegate.forName(name), name);
  }

  @Override
  public void registerDataLoaders(DataLoaderRegistry registry, GraphQLContext context) {
    delegate.registerDataLoaders(registry, context);
  }

  public static final class SentryRegistrationSpec<K, V>
      implements BatchLoaderRegistry.RegistrationSpec<K, V> {

    private final @NotNull RegistrationSpec<K, V> delegate;
    private final @Nullable String name;
    private final @Nullable Class<K> keyType;
    private final @Nullable Class<V> valueType;

    public SentryRegistrationSpec(
        final @NotNull RegistrationSpec<K, V> delegate, Class<K> keyType, Class<V> valueType) {
      this.delegate = delegate;
      this.keyType = keyType;
      this.valueType = valueType;
      this.name = null;
    }

    public SentryRegistrationSpec(final @NotNull RegistrationSpec<K, V> delegate, String name) {
      this.delegate = delegate;
      this.name = name;
      this.keyType = null;
      this.valueType = null;
    }

    @Override
    public BatchLoaderRegistry.RegistrationSpec<K, V> withName(String name) {
      return delegate.withName(name);
    }

    @Override
    public BatchLoaderRegistry.RegistrationSpec<K, V> withOptions(
        Consumer<DataLoaderOptions> optionsConsumer) {
      return delegate.withOptions(optionsConsumer);
    }

    @Override
    public BatchLoaderRegistry.RegistrationSpec<K, V> withOptions(DataLoaderOptions options) {
      return delegate.withOptions(options);
    }

    @Override
    public void registerBatchLoader(BiFunction<List<K>, BatchLoaderEnvironment, Flux<V>> loader) {
      delegate.registerBatchLoader(
          (keys, batchLoaderEnvironment) -> {
            scopesFromContext(batchLoaderEnvironment)
                .addBreadcrumb(Breadcrumb.graphqlDataLoader(keys, keyType, valueType, name));
            return loader.apply(keys, batchLoaderEnvironment);
          });
    }

    @Override
    public void registerMappedBatchLoader(
        BiFunction<Set<K>, BatchLoaderEnvironment, Mono<Map<K, V>>> loader) {
      delegate.registerMappedBatchLoader(
          (keys, batchLoaderEnvironment) -> {
            scopesFromContext(batchLoaderEnvironment)
                .addBreadcrumb(Breadcrumb.graphqlDataLoader(keys, keyType, valueType, name));
            return loader.apply(keys, batchLoaderEnvironment);
          });
    }

    private @NotNull IScopes scopesFromContext(final @NotNull BatchLoaderEnvironment environment) {
      Object context = environment.getContext();
      if (context instanceof GraphQLContext) {
        GraphQLContext graphqlContext = (GraphQLContext) context;
        return graphqlContext.getOrDefault(SENTRY_SCOPES_CONTEXT_KEY, NoOpScopes.getInstance());
      }

      return NoOpScopes.getInstance();
    }
  }
}
