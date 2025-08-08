package io.sentry.samples.spring.boot4.graphql;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
class TaskCreatorController {

  public TaskCreatorController(final BatchLoaderRegistry batchLoaderRegistry) {
    // using mapped BatchLoader to not have to deal with correct ordering of items
    batchLoaderRegistry
        .forTypePair(String.class, ProjectController.Creator.class)
        .registerMappedBatchLoader(
            (Set<String> keys, BatchLoaderEnvironment env) -> {
              return Mono.fromCallable(
                  () -> {
                    final @NotNull Map<String, ProjectController.Creator> map = new HashMap<>();
                    for (String key : keys) {
                      if ("Ccrash".equalsIgnoreCase(key)) {
                        throw new RuntimeException("Causing an error while loading creator");
                      }
                      map.put(key, new ProjectController.Creator(key, "Name" + key));
                    }

                    return map;
                  });
            });
  }

  @SchemaMapping(typeName = "Task")
  public @Nullable CompletableFuture<ProjectController.Creator> creator(
      final ProjectController.Task task,
      final DataLoader<String, ProjectController.Creator> dataLoader) {
    if (task.creatorId == null) {
      return null;
    }
    return dataLoader.load(task.creatorId);
  }
}
