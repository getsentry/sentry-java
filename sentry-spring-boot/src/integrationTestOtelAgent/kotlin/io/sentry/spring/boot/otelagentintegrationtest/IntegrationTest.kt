package io.sentry.spring.boot.otelagentintegrationtest

import org.junit.Before

/**
 * Drives an [AgentApp] over HTTP and asserts what reaches its server, by default the one most tests
 * share.
 *
 * Envelopes that arrive late can outlive the server's reset, so tests match on values only they
 * produce.
 */
abstract class IntegrationTest {
  protected open val app: AgentApp
    get() = AgentApp.shared

  val server
    get() = app.server

  val restTemplate
    get() = app.restTemplate

  @Before
  fun resetServer() {
    server.reset()
  }
}

/** The bodies the app's endpoints take and return. */
class Person(var firstName: String? = null, var lastName: String? = null)

class Todo(var id: Long? = null, var title: String? = null, var completed: Boolean = false)
