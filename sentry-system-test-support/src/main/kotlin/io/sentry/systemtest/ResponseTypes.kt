package io.sentry.systemtest

data class Todo(val id: Long, val title: String, val isCompleted: Boolean)

data class Person(val firstName: String, val lastName: String) {

    override fun toString(): String {
        return "Person{firstName='$firstName', lastName='$lastName'}"
    }
}
