package io.sentry.samples.spring.boot4.otlp.graphql;

import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
public class ProjectController {

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
    tasks.add(new Task("T1", "Create a new API", "A3", "C3"));
    tasks.add(new Task("T2", "Update dependencies", "A1", "C1"));
    tasks.add(new Task("T3", "Document API", "A1", "C1"));
    tasks.add(new Task("T4", "Merge community PRs", "A2", "C2"));
    tasks.add(new Task("T5", "Plan more work", null, null));
    if ("crash".equalsIgnoreCase(projectSlug)) {
      tasks.add(new Task("T6", "Fix crash", "Acrash", "Ccrash"));
    }
    return tasks;
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
    final String creatorId = "creatorcrash".equalsIgnoreCase(projectSlug) ? "Ccrash" : "C1";
    final @NotNull AtomicInteger counter = new AtomicInteger(1000);
    return Flux.interval(Duration.ofSeconds(1))
        .map(
            num -> {
              int i = counter.incrementAndGet();
              if ("produceerror".equalsIgnoreCase(projectSlug) && i % 2 == 0) {
                throw new RuntimeException("causing produce error for subscription");
              }
              return new Task("T" + i, "A new task arrived ", assigneeId, creatorId);
            });
  }

  public static class Task {
    public String id;
    public String name;
    public String assigneeId;
    public String creatorId;

    public Task(
        final String id, final String name, final String assigneeId, final String creatorId) {
      this.id = id;
      this.name = name;
      this.assigneeId = assigneeId;
      this.creatorId = creatorId;
    }

    @Override
    public String toString() {
      return "Task{id=" + id + "}";
    }
  }

  public static class Assignee {
    public String id;
    public String name;

    public Assignee(final String id, final String name) {
      this.id = id;
      this.name = name;
    }
  }

  public static class Creator {
    public String id;
    public String name;

    public Creator(final String id, final String name) {
      this.id = id;
      this.name = name;
    }
  }

  public static class Project {
    public String slug;
  }

  public enum ProjectStatus {
    ACTIVE,
    COMMUNITY,
    INCUBATING,
    ATTIC,
    EOL;
  }
}
