package io.sentry.samples.spring.boot.graphql;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class AssigneeController {

  @BatchMapping(typeName = "Task", field = "assignee")
  public Mono<Map<ProjectController.Task, ProjectController.Assignee>> assignee(
      final @NotNull Set<ProjectController.Task> tasks) {
    return Mono.fromCallable(
        () -> {
          final @NotNull Map<ProjectController.Task, ProjectController.Assignee> map =
              new HashMap<>();
          for (final @NotNull ProjectController.Task task : tasks) {
            if ("Acrash".equalsIgnoreCase(task.assigneeId)) {
              throw new RuntimeException("Causing an error while loading assignee");
            }
            if (task.assigneeId != null) {
              map.put(
                  task, new ProjectController.Assignee(task.assigneeId, "Name" + task.assigneeId));
            }
          }

          return map;
        });
  }
}
