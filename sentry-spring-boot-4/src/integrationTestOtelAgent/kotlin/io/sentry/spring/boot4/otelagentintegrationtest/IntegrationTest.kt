package io.sentry.spring.boot4.otelagentintegrationtest

import org.junit.Before

/**
 * Drives [AgentApp] over HTTP and asserts what reaches its server.
 *
 * Subclasses share the app and the server. Envelopes that arrive late can outlive the server's
 * reset, so tests match on values only they produce.
 */
abstract class IntegrationTest {
  val server = AgentApp.server
  val restTemplate = AgentApp.restTemplate

  @Before
  fun resetServer() {
    server.reset()
  }
}

/** The bodies the app's endpoints take and return. */
class Person(var firstName: String? = null, var lastName: String? = null)

class Todo(var id: Long? = null, var title: String? = null, var completed: Boolean = false)
