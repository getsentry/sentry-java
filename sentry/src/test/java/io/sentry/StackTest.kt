package io.sentry

import io.sentry.Stack.StackItem
import io.sentry.test.createSentryClientMock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.mockito.kotlin.mock

class StackTest {
  private class Fixture {
    val options = SentryOptions()
    val client = createSentryClientMock()
    val scope = Scope(options)

    lateinit var rootItem: StackItem

    fun getSut(rootItem: StackItem = StackItem(options, client, scope)): Stack {
      this.rootItem = rootItem
      return Stack(options.logger, rootItem)
    }

    fun createStackItem(scope: IScope = Scope(options)) =
      StackItem(this.options, this.client, scope)
  }

  private val fixture = Fixture()

  @Test
  fun `stack after creation has single item`() {
    val stack = fixture.getSut()
    assertEquals(1, stack.size())
  }

  @Test
  fun `pop() on single item stack does not remove item`() {
    val stack = fixture.getSut()
    stack.pop()
    assertEquals(1, stack.size())
  }

  @Test
  fun `push() adds item to stack`() {
    val stack = fixture.getSut()
    stack.push(mock())
    assertEquals(2, stack.size())
  }

  @Test
  fun `peek() returns last added item`() {
    val stack = fixture.getSut()
    val item = mock<StackItem>()
    stack.push(item)
    assertEquals(item, stack.peek())
  }

  @Test
  fun `pop() removes last added item`() {
    val stack = fixture.getSut()
    val item = mock<StackItem>()
    stack.push(item)

    stack.pop()
    assertEquals(fixture.rootItem, stack.peek())
  }

  @Test
  fun `cloning stack clones stack items`() {
    val stack =
      fixture.getSut(
        fixture.createStackItem(Scope(fixture.options).apply { this.setTag("rootTag", "value") })
      )
    stack.push(
      fixture.createStackItem(Scope(fixture.options).apply { this.setTag("childTag", "value") })
    )
    val clone = Stack(stack)

    assertEquals(stack.size(), clone.size())
    // assert first stack item
    assertStackItems(stack.peek(), clone.peek())
    stack.pop()
    clone.pop()
    // assert root item
    assertStackItems(stack.peek(), clone.peek())
  }

  private fun assertStackItems(item1: StackItem, item2: StackItem) {
    assertNotEquals(item1, item2)
    assertNotEquals(item1.scope, item2.scope)
    // assert that scope content is the same
    assertEquals(item1.scope.tags, item2.scope.tags)
    assertEquals(item1.client, item2.client)
  }
}
