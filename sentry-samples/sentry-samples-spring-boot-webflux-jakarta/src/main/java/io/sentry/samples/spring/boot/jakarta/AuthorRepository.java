package io.sentry.samples.spring.boot.jakarta;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface AuthorRepository extends ReactiveCrudRepository<Author, UUID> {}
