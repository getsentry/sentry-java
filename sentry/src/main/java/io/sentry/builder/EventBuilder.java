package io.sentry.builder;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Geo;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class EventBuilder {

//  public static void main(String[] args) {
//
//    SentryEvent.builder()
//      .withMessage(m -> m.value("message"))
//      .withThrowable(new RuntimeException("test"))
//      .withUser(u -> u.withId("userId"))
//      .withLevel(SentryLevel.FATAL)
//      .captureEvent();
//
//  }

  private final SentryEvent event;

  public EventBuilder(SentryEvent event) {
    this.event = event;
  }

  public EventBuilder withThrowable(Throwable throwable) {
    event.setThrowable(throwable);
    return this;
  }

  public EventBuilder withUser(Function<UserBuilder, UserBuilder> function) {

    User user = event.getUser();

    if (user == null) {
      user = new User();
    }

    event.setUser(function.apply(new UserBuilder(user)).build());

    return this;
  }

  public EventBuilder withMessage(Function<MessageBuilder, MessageBuilder> consumer) {
    event.setMessage(consumer.apply(new MessageBuilder(new Message()))
      .build());
    return this;
  }

  public EventBuilder withFingerprint(String fingerprint) {
    if (event.getFingerprints() == null) {
      event.setFingerprints(new ArrayList<>());
    }

    event.getFingerprints().add(fingerprint);

    return this;
  }

  public EventBuilder withFingerprints(Collection<String> fingerprints) {

    if (event.getFingerprints() == null) {
      event.setFingerprints(new ArrayList<>(fingerprints));
      return this;
    }

    event.getFingerprints().addAll(fingerprints);

    return this;
  }

  public EventBuilder withExtra(String key, Object value) {
    event.setExtra(key, value);
    return this;
  }

  public EventBuilder withEnvironment(String environment) {
    event.setEnvironment(environment);
    return this;
  }

  public EventBuilder withRelease(String release) {
    event.setRelease(release);
    return this;
  }

  public EventBuilder withDist(String dist) {
    event.setDist(dist);
    return this;
  }

  public EventBuilder withPlatform(String platform) {
    event.setPlatform(platform);
    return this;
  }

  public EventBuilder withServerName(String serverName) {
    event.setServerName(serverName);
    return this;
  }

  public EventBuilder withLogger(String logger) {
    event.setLogger(logger);
    return this;
  }

  public EventBuilder withLevel(SentryLevel level) {
    event.setLevel(level);
    return this;
  }

  public EventBuilder withTransaction(String transaction) {
    event.setTransaction(transaction);
    return this;
  }

  public EventBuilder withTag(String key, String value) {

    if (event.getTags() == null) {
      event.setTags(new HashMap<>());
    }

    event.getTags().put(key, value);
    return this;
  }

  public EventBuilder withBreadcrumb(Breadcrumb breadcrumb) {
    if (event.getBreadcrumbs() == null) {
      event.setBreadcrumbs(new ArrayList<>());
    }

    event.getBreadcrumbs().add(breadcrumb);

    return this;
  }

  public EventBuilder withMessage(String message) {
    Message m = new Message();
    m.setMessage(message);

    event.setMessage(m);

    return this;
  }

  public SentryEvent build() {
    return event;
  }

  public SentryId captureEvent() {
    return Sentry.captureEvent(build());
  }

  public static class UserBuilder {

    private final User user;

    public UserBuilder(User user) {
      this.user = user;
    }

    public UserBuilder withId(String id) {
      user.setId(id);
      return this;
    }

    public UserBuilder withUsername(String username) {
      user.setUsername(username);
      return this;
    }

    public UserBuilder withIpAddress(String ipAddress) {
      user.setIpAddress(ipAddress);
      return this;
    }

    public UserBuilder withEmail(String email) {
      user.setEmail(email);
      return this;
    }

    public UserBuilder withData(Map<String, String> data) {
      user.setData(data);
      return this;
    }

    public UserBuilder withGeo(@NotNull Function<GeoBuilder, Geo> function) {
      user.setGeo(function.apply(new GeoBuilder()));
      return this;
    }

    public User build() {
      return user;
    }
  }

  public static class GeoBuilder {
    private final Geo geo;

    public GeoBuilder() {
      this.geo = new Geo();
    }

    public GeoBuilder withCountryCode(String countryCode) {
      geo.setCountryCode(countryCode);
      return this;
    }

    public GeoBuilder withRegion(String region) {
      geo.setRegion(region);
      return this;
    }

    public GeoBuilder withCity(String city) {
      geo.setCity(city);
      return this;
    }

    public Geo build() {
      return geo;
    }
  }

  public static class MessageBuilder {

    private final Message message;

    public MessageBuilder(Message message) {
      this.message = message;
    }

    public MessageBuilder value(String value) {
      message.setMessage(value);
      return this;
    }


    public Message build() {
      return message;
    }

  }

}
