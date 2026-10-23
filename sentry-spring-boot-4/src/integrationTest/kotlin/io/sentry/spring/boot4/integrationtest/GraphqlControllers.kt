package io.sentry.spring.boot4.integrationtest

import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.dataloader.DataLoader
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono

class Project(val slug: String)

enum class ProjectStatus {
  ACTIVE,
  COMMUNITY,
}

class Task(val id: String, val name: String, val assigneeId: String?, val creatorId: String?)

class Assignee(val id: String, val name: String)

class Creator(val id: String, val name: String)

/** Resolvers that throw when given a "crash" argument, so tests can check error reporting. */
@Controller
class ProjectController {
  @QueryMapping
  fun greeting(@Argument name: String): String {
    if (name == "crash") {
      throw RuntimeException("causing an error for $name")
    }
    return "Hello $name!"
  }

  @QueryMapping fun project(@Argument slug: String): Project = Project(slug)

  @SchemaMapping(typeName = "Project", field = "status")
  fun status(project: Project): ProjectStatus = ProjectStatus.COMMUNITY

  @MutationMapping
  fun addProject(@Argument slug: String): String {
    if (slug == "addprojectcrash") {
      throw RuntimeException("causing a project add error for $slug")
    }
    return UUID.randomUUID().toString()
  }

  @QueryMapping
  fun tasks(@Argument projectSlug: String): List<Task> =
    listOf(
      Task("T1", "Create a new API", "A3", "C3"),
      Task("T2", "Update dependencies", "A1", "C1"),
      Task("T3", "Document API", "A1", "C1"),
      Task("T4", "Merge community PRs", "A2", "C2"),
      Task("T5", "Plan more work", null, null),
    )
}

/** Loads a task's assignee with a batch mapping and its creator with a data loader. */
@Controller
class TaskRelationsController(batchLoaderRegistry: BatchLoaderRegistry) {
  init {
    batchLoaderRegistry
      .forTypePair(String::class.java, Creator::class.java)
      .registerMappedBatchLoader { ids, _ ->
        Mono.just(ids.associateWith { Creator(it, "Name$it") })
      }
  }

  @BatchMapping(typeName = "Task", field = "assignee")
  fun assignee(tasks: List<Task>): Map<Task, Assignee> =
    tasks
      .filter { it.assigneeId != null }
      .associateWith { Assignee(it.assigneeId!!, "Name${it.assigneeId}") }

  @SchemaMapping(typeName = "Task")
  fun creator(task: Task, creators: DataLoader<String, Creator>): CompletableFuture<Creator>? =
    task.creatorId?.let { creators.load(it) }
}
