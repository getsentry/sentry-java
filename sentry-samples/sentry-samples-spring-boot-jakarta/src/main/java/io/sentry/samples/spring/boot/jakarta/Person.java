package io.sentry.samples.spring.boot.jakarta;

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
}
