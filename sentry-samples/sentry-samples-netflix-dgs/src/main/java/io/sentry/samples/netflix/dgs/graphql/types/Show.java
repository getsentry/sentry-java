package io.sentry.samples.netflix.dgs.graphql.types;

public class Show {
  private Integer id;

  private String title;

  private Integer releaseYear;

  public Show() {}

  public Show(Integer id, String title, Integer releaseYear) {
    this.id = id;
    this.title = title;
    this.releaseYear = releaseYear;
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
        && java.util.Objects.equals(releaseYear, that.releaseYear);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(id, title, releaseYear);
  }

  public static io.sentry.samples.netflix.dgs.graphql.types.Show.Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private Integer id;

    private String title;

    private Integer releaseYear;

    public Show build() {
      io.sentry.samples.netflix.dgs.graphql.types.Show result =
          new io.sentry.samples.netflix.dgs.graphql.types.Show();
      result.id = this.id;
      result.title = this.title;
      result.releaseYear = this.releaseYear;
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
  }
}
