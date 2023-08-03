package io.sentry.samples.netflix.dgs;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.DgsSubscription;
import graphql.schema.DataFetchingEnvironment;
import io.sentry.samples.netflix.dgs.graphql.DgsConstants;
import io.sentry.samples.netflix.dgs.graphql.types.Actor;
import io.sentry.samples.netflix.dgs.graphql.types.Show;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.dataloader.DataLoader;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

@DgsComponent
public class ShowsDatafetcher {

  @DgsQuery(field = DgsConstants.QUERY.Shows)
  public List<Show> shows() throws InterruptedException {
    Thread.sleep(new Random().nextInt(500));
    return Arrays.asList(
        Show.newBuilder().id(1).title("Stranger Things").releaseYear(2015).actorId(1).build(),
        Show.newBuilder().id(2).title("Breaking Bad").releaseYear(2013).actorId(-1).build());
  }

  @DgsQuery(field = DgsConstants.QUERY.NewShows)
  public List<Show> newShows() throws InterruptedException {
    Thread.sleep(new Random().nextInt(500));
    throw new RuntimeException("error when loading new shows");
  }

  @DgsMutation(field = DgsConstants.MUTATION.AddShow)
  public Integer addShow(String title) {
    throw new RuntimeException("error while adding a show");
  }

  @DgsSubscription(field = DgsConstants.SUBSCRIPTION.NotifyNewShow)
  public Publisher<Show> notifyNewShow(Integer releaseYear) {
    if (releaseYear == -1) {
      throw new RuntimeException("Causing error for subscription");
    } else if (releaseYear == -2) {
      return Flux.error(new RuntimeException("Causing error for subscription with flux"));
    }
    final @NotNull AtomicInteger counter = new AtomicInteger(2);
    return Flux.interval(Duration.ofSeconds(1))
        .map(
            t -> {
              int i = counter.incrementAndGet();
              if (releaseYear == -3 && i % 2 == 0) {
                throw new RuntimeException(
                    "Causing error for subscription while producing an element");
              }
              return new Show(i, "A new show has arrived " + i, releaseYear, 1);
            });
  }

  @DgsData(parentType = "Show", field = "actor")
  public CompletableFuture<Actor> actor(DataFetchingEnvironment dfe) {

    DataLoader<Integer, Actor> dataLoader = dfe.getDataLoader("actors");
    // does not work, thanks docs
    //    String id = dfe.getArgument("actorId");
    Show show = dfe.getSource();

    return dataLoader.load(show.getActorId());
  }
}
