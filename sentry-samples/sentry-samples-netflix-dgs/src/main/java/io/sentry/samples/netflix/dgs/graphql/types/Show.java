package io.sentry.samples.netflix.dgs.graphql.types;

public class Show {
  private Integer id;

  private String title;

  private Integer releaseYear;

  private Integer actorId;

  public Show() {}

  public Show(Integer id, String title, Integer releaseYear, Integer actorId) {
    this.id = id;
    this.title = title;
    this.releaseYear = releaseYear;
    this.actorId = actorId;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Integer getReleaseYear() {
    return releaseYear;
  }

  public void setReleaseYear(Integer releaseYear) {
    this.releaseYear = releaseYear;
  }

  public Integer getActorId() {
    return actorId;
  }

  public void setActorId(Integer actorId) {
    this.actorId = actorId;
  }

  @Override
  public String toString() {
    return "Show{"
        + "id='"
        + id
        + "',"
        + "title='"
        + title
        + "',"
        + "releaseYear='"
        + releaseYear
        + "',"
        + "actorId='"
        + actorId
        + "'"
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Show that = (Show) o;
    return java.util.Objects.equals(id, that.id)
        && java.util.Objects.equals(title, that.title)
        && java.util.Objects.equals(releaseYear, that.releaseYear)
        && java.util.Objects.equals(actorId, that.actorId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(id, title, releaseYear, actorId);
  }

  public static io.sentry.samples.netflix.dgs.graphql.types.Show.Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private Integer id;

    private String title;

    private Integer releaseYear;

    private Integer actorId;

    public Show build() {
      io.sentry.samples.netflix.dgs.graphql.types.Show result =
          new io.sentry.samples.netflix.dgs.graphql.types.Show();
      result.id = this.id;
      result.title = this.title;
      result.releaseYear = this.releaseYear;
      result.actorId = this.actorId;
      return result;
    }

    public io.sentry.samples.netflix.dgs.graphql.types.Show.Builder id(Integer id) {
      this.id = id;
      return this;
    }

    public io.sentry.samples.netflix.dgs.graphql.types.Show.Builder title(String title) {
      this.title = title;
      return this;
    }

    public io.sentry.samples.netflix.dgs.graphql.types.Show.Builder releaseYear(
        Integer releaseYear) {
      this.releaseYear = releaseYear;
      return this;
    }

    public io.sentry.samples.netflix.dgs.graphql.types.Show.Builder actorId(Integer actorId) {
      this.actorId = actorId;
      return this;
    }
  }
}
