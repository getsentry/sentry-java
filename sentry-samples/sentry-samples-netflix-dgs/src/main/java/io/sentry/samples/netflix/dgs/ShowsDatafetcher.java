package io.sentry.samples.netflix.dgs;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import io.sentry.samples.netflix.dgs.graphql.DgsConstants;
import io.sentry.samples.netflix.dgs.graphql.types.Show;
import java.util.List;
import java.util.Random;

@DgsComponent
public class ShowsDatafetcher {

  @DgsQuery(field = DgsConstants.QUERY.Shows)
  public List<Show> shows() throws InterruptedException {
    Thread.sleep(new Random().nextInt(500));

    //        return Arrays.asList(
    //          Show.newBuilder().id(1).title("Stranger Things").releaseYear(2015).build(),
    //          Show.newBuilder().id(2).title("Breaking Bad").releaseYear(2013).build()
    //        );
    throw new RuntimeException("foo");
  }
}
