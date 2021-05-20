package io.sentry

import com.nhaarman.mockitokotlin2.mock
import io.sentry.Stack.StackItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StackTest {

    private class Fixture {
        val options = SentryOptions()
        val client = mock<ISentryClient>()
        val scope = Scope(options)

        val rootItem = StackItem(options, client, scope)

        fun getSut() = Stack(options.logger, rootItem)
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
        val stack = fixture.getSut()
        val clone = Stack(stack)

        assertEquals(stack.size(), clone.size())

        val stackRootItem = stack.peek()
        val cloneRootItem = clone.peek()
        assertNotEquals(stackRootItem, cloneRootItem)
        assertNotEquals(stackRootItem.scope, cloneRootItem.scope)
        assertEquals(stackRootItem.client, cloneRootItem.client)
    }
}
