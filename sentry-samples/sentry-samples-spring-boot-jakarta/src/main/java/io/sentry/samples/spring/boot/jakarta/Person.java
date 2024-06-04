package io.sentry.samples.spring.boot.jakarta;

import java.util.Objects;

public class Person {
  private final String firstName;
  private final String lastName;

  public Person(String firstName, String lastName) {
    this.firstName = firstName;
    this.lastName = lastName;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  @Override
  public String toString() {
    return "Person{" + "firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Person person)) return false;
    return Objects.equals(getFirstName(), person.getFirstName())
        && Objects.equals(getLastName(), person.getLastName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getFirstName(), getLastName());
  }
}
