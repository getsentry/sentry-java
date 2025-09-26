package io.sentry.samples.spring7.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Person {
  private final String firstName;
  private final String lastName;

  @JsonCreator
  public Person(
      @JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName) {
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
