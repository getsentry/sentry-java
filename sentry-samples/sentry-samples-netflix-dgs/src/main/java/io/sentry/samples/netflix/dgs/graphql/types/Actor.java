package io.sentry.samples.netflix.dgs.graphql.types;

public class Actor {
  private Integer id;

  private String name;

  public Actor() {}

  public Actor(Integer id, String name) {
    this.id = id;
    this.name = name;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "Show{" + "id='" + id + "'," + "name='" + name + "'" + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Actor that = (Actor) o;
    return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(id, name);
  }

  public static Actor.Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private Integer id;

    private String name;

    private Integer releaseYear;

    public Actor build() {
      Actor result = new Actor();
      result.id = this.id;
      result.name = this.name;
      return result;
    }

    public Actor.Builder id(Integer id) {
      this.id = id;
      return this;
    }

    public Actor.Builder name(String name) {
      this.name = name;
      return this;
    }
  }
}
