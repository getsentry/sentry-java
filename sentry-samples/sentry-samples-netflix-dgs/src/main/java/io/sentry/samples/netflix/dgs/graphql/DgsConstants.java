package io.sentry.samples.netflix.dgs.graphql;

public class DgsConstants {
  public static final String QUERY_TYPE = "Query";
  public static final String MUTATION_TYPE = "Mutation";
  public static final String SUBSCRIPTION_TYPE = "Subscription";

  public static class QUERY {
    public static final String TYPE_NAME = "Query";

    public static final String Shows = "shows";

    public static final String NewShows = "newShows";
  }

  public static class MUTATION {
    public static final String TYPE_NAME = "Mutation";

    public static final String AddShow = "addShow";
  }

  public static class SUBSCRIPTION {
    public static final String TYPE_NAME = "Subscription";

    public static final String NotifyNewShow = "notifyNewShow";
  }

  public static class SHOW {
    public static final String TYPE_NAME = "Show";

    public static final String Id = "id";

    public static final String Title = "title";

    public static final String ReleaseYear = "releaseYear";
  }

  public static class ACTOR {
    public static final String TYPE_NAME = "Actor";

    public static final String Id = "id";

    public static final String Name = "name";
  }
}
