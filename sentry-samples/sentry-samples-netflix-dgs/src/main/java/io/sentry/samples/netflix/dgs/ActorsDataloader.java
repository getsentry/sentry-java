package io.sentry.samples.netflix.dgs;

import com.netflix.graphql.dgs.DgsDataLoader;
import io.sentry.samples.netflix.dgs.graphql.types.Actor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.dataloader.MappedBatchLoader;
import org.jetbrains.annotations.NotNull;

@DgsDataLoader(name = "actors")
public class ActorsDataloader implements MappedBatchLoader<Integer, Actor> {

  @Override
  public CompletionStage<Map<Integer, Actor>> load(Set<Integer> keys) {
    return CompletableFuture.supplyAsync(
        () -> {
          final @NotNull Map<Integer, Actor> map = new HashMap<>();
          for (Integer key : keys) {
            if (key != null && key == -1) {
              throw new RuntimeException("Causing an error while loading actor");
            }
            map.put(key, new Actor(key, "Name" + key));
          }
          return map;
        });
  }
}
