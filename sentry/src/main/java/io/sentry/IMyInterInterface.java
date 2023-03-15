package io.sentry;

public interface IMyInterInterface {
  default void DoTheThing() {
    System.out.println("Look at me!");
  }
}
