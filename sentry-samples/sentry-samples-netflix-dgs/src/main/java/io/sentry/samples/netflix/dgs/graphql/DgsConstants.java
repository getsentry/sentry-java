package io.sentry.samples.netflix.dgs.graphql;

public class DgsConstants {
  public static final String QUERY_TYPE = "Query";

  public static class QUERY {
    public static final String TYPE_NAME = "Query";

    public static final String Shows = "shows";

    public static final String NewShows = "newShows";
  }

  public static class SHOW {
    public static final String TYPE_NAME = "Show";

    public static final String Id = "id";

    public static final String Title = "title";

    public static final String ReleaseYear = "releaseYear";
  }
}
