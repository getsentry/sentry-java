package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DataCollectionTest {
  @Test
  fun `public constructor creates explicit empty configuration`() {
    val dataCollection = DataCollection()

    assertThat(dataCollection.userInfo).isNull()
    assertThat(dataCollection.cookies).isNull()
    assertThat(dataCollection.queryParams).isNull()
    assertThat(dataCollection.httpBodies).isNull()
    assertThat(dataCollection.databaseQueryData).isNull()
    assertThat(dataCollection.queues).isNull()
    assertThat(dataCollection.httpHeaders.request).isNull()
    assertThat(dataCollection.httpHeaders.response).isNull()
    assertThat(dataCollection.graphql.document).isNull()
    assertThat(dataCollection.graphql.variables).isNull()
    assertThat(dataCollection.isExplicitlyConfigured()).isTrue()
  }

  @Test
  fun `SDK-owned configuration starts unconfigured`() {
    val dataCollection = DataCollection(false)

    assertThat(dataCollection.isExplicitlyConfigured()).isFalse()
  }

  @Test
  fun `nested override makes SDK-owned configuration explicit`() {
    val dataCollection = DataCollection(false)

    dataCollection.graphql.setVariables(false)

    assertThat(dataCollection.isExplicitlyConfigured()).isTrue()
  }

  @Test
  fun `explicit false is distinct from unset`() {
    val dataCollection = DataCollection(false)

    dataCollection.setUserInfo(false)

    assertThat(dataCollection.userInfo).isFalse()
    assertThat(dataCollection.isExplicitlyConfigured()).isTrue()
  }

  @Test
  fun `empty HTTP body set is distinct from unset`() {
    val dataCollection = DataCollection(false)

    dataCollection.setHttpBodies(emptySet())

    assertThat(dataCollection.httpBodies).isEmpty()
    assertThat(dataCollection.isExplicitlyConfigured()).isTrue()
  }

  @Test
  fun `HTTP body set is copied and immutable`() {
    val bodies = mutableSetOf(HttpBodyType.INCOMING_REQUEST)
    val dataCollection = DataCollection()

    dataCollection.setHttpBodies(bodies)
    bodies += HttpBodyType.OUTGOING_REQUEST

    assertThat(dataCollection.httpBodies).containsExactly(HttpBodyType.INCOMING_REQUEST)
    assertFailsWith<UnsupportedOperationException> {
      dataCollection.httpBodies!!.add(HttpBodyType.OUTGOING_REQUEST)
    }
  }

  @Test
  fun `database query data false is distinct from unset`() {
    val dataCollection = DataCollection(false)

    dataCollection.setDatabaseQueryData(false)

    assertThat(dataCollection.databaseQueryData).isFalse()
    assertThat(dataCollection.isExplicitlyConfigured()).isTrue()
  }

  @Test
  fun `queues false is distinct from unset`() {
    val dataCollection = DataCollection(false)

    dataCollection.setQueues(false)

    assertThat(dataCollection.queues).isFalse()
    assertThat(dataCollection.isExplicitlyConfigured()).isTrue()
  }

  @Test
  fun `nested HTTP header override marks configuration explicit`() {
    val dataCollection = DataCollection(false)
    val behavior = KeyValueCollectionBehavior.denyList("authorization")

    dataCollection.httpHeaders.setRequest(behavior)

    assertThat(dataCollection.httpHeaders.request).isSameInstanceAs(behavior)
    assertThat(dataCollection.isExplicitlyConfigured()).isTrue()
  }

  @Test
  fun `nested GraphQL false marks configuration explicit`() {
    val dataCollection = DataCollection(false)

    dataCollection.graphql.setVariables(false)

    assertThat(dataCollection.graphql.variables).isFalse()
    assertThat(dataCollection.isExplicitlyConfigured()).isTrue()
  }
}
