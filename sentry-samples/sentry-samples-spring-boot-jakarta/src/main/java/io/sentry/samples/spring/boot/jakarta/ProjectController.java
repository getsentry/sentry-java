package io.sentry.samples.spring.boot.jakarta;

import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class ProjectController {

  //  public ProjectController(final BatchLoaderRegistry batchLoaderRegistry) {
  //    // using mapped BatchLoader to not have to deal with correct ordering of items
  //    batchLoaderRegistry.forTypePair(String.class,
  // Assignee.class).registerMappedBatchLoader((Set<String> keys, BatchLoaderEnvironment env) -> {
  //      return Mono.fromCallable(() -> {
  //        final @NotNull Map<String, Assignee> map = new HashMap<>();
  //        for (String key : keys) {
  //          if ("Acrash".equalsIgnoreCase(key)) {
  //            throw new RuntimeException("Causing an error while loading assignee");
  //          }
  //          map.put(key, new Assignee(key, "Name" + key));
  //        }
  //
  //        return map;
  //      });
  //    });
  //  }

  @QueryMapping
  public Project project(final @Argument String slug) throws Exception {
    if ("crash".equalsIgnoreCase(slug) || "projectcrash".equalsIgnoreCase(slug)) {
      throw new RuntimeException("causing a project error for " + slug);
    }
    if ("notfound".equalsIgnoreCase(slug)) {
      throw new IllegalStateException("not found");
    }
    if ("nofile".equals(slug)) {
      throw new NoSuchFileException("no such file");
    }
    Project project = new Project();
    project.slug = slug;
    return project;
  }

  @SchemaMapping(typeName = "Project", field = "status")
  public ProjectStatus projectStatus(final Project project) {
    if ("crash".equalsIgnoreCase(project.slug) || "statuscrash".equalsIgnoreCase(project.slug)) {
      throw new RuntimeException("causing a project status error for " + project.slug);
    }
    return ProjectStatus.COMMUNITY;
  }

  @MutationMapping
  public String addProject(@Argument String slug) {
    if ("crash".equalsIgnoreCase(slug) || "addprojectcrash".equalsIgnoreCase(slug)) {
      throw new RuntimeException("causing a project add error for " + slug);
    }
    return UUID.randomUUID().toString();
  }

  @QueryMapping
  public List<Task> tasks(final @Argument String projectSlug) {
    List<Task> tasks = new ArrayList<>();
    tasks.add(new Task("T1", "Create a new API", "A3"));
    tasks.add(new Task("T2", "Update dependencies", "A1"));
    tasks.add(new Task("T3", "Document API", "A1"));
    tasks.add(new Task("T4", "Merge community PRs", "A2"));
    tasks.add(new Task("T5", "Plan more work", null));
    if ("crash".equalsIgnoreCase(projectSlug)) {
      tasks.add(new Task("T6", "Fix crash", "Acrash"));
    }
    return tasks;
  }

  //  @SchemaMapping(typeName="Task")
  //  public @Nullable CompletableFuture<Assignee> assignee(final Task task, final
  // DataLoader<String, Assignee> dataLoader) {
  //    if (task.assigneeId == null) {
  //      return null;
  //    }
  //    return dataLoader.load(task.assigneeId);
  //  }

  @BatchMapping(typeName = "Task")
  public Mono<Map<String, Assignee>> assignee(final @NotNull Set<Task> tasks) {
    return Mono.fromCallable(
        () -> {
          final @NotNull Map<String, Assignee> map = new HashMap<>();
          for (final @NotNull Task task : tasks) {
            if ("Acrash".equalsIgnoreCase(task.assigneeId)) {
              throw new RuntimeException("Causing an error while loading assignee");
            }
            map.put(task.assigneeId, new Assignee(task.assigneeId, "Name" + task.assigneeId));
          }

          return map;
        });
  }

  @SubscriptionMapping
  public Flux<Task> notifyNewTask(@Argument String projectSlug) {
    if ("crash".equalsIgnoreCase(projectSlug)) {
      throw new RuntimeException("causing error for subscription");
    }
    if ("fluxerror".equalsIgnoreCase(projectSlug)) {
      return Flux.error(new RuntimeException("causing flux error for subscription"));
    }
    final String assigneeId = "assigneecrash".equalsIgnoreCase(projectSlug) ? "Acrash" : "A1";
    final @NotNull AtomicInteger counter = new AtomicInteger(1000);
    return Flux.interval(Duration.ofSeconds(1))
        .map(
            num -> {
              int i = counter.incrementAndGet();
              if ("produceerror".equalsIgnoreCase(projectSlug) && i % 2 == 0) {
                throw new RuntimeException("causing produce error for subscription");
              }
              return new Task("T" + i, "A new task arrived ", assigneeId);
            });
  }

  class Task {
    private String id;
    private String name;
    private String assigneeId;

    public Task(final String id, final String name, final String assigneeId) {
      this.id = id;
      this.name = name;
      this.assigneeId = assigneeId;
    }
  }

  class Assignee {
    private String id;
    private String name;

    public Assignee(final String id, final String name) {
      this.id = id;
      this.name = name;
    }
  }

  class Project {
    private String slug;
  }

  enum ProjectStatus {
    ACTIVE,
    COMMUNITY,
    INCUBATING,
    ATTIC,
    EOL;
  }
}
