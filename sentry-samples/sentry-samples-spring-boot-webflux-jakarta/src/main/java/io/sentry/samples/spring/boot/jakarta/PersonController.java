package io.sentry.samples.spring.boot.jakarta;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/person/")
public class PersonController {
  private final PersonService personService;
  private final AuthorRepository authorRepository;
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonController.class);

  public PersonController(PersonService personService, AuthorRepository authorRepository) {
    this.personService = personService;
    this.authorRepository = authorRepository;
  }

  @GetMapping("{id}")
  Flux<Author> person(@PathVariable Long id) {
    return Flux.deferContextual(
        ctx -> {
//          System.out.println("Reactor Context at method start: " + ctx);
          return Flux.range(0, (int) (id + 1))
              .map(
                  currentId -> {
                    System.out.println("creating author " + currentId);
                    Author author = new Author();
                    author.setId(UUID.randomUUID());
                    author.setName("user_" + currentId);
                    return author;
                  })
              .flatMap(authorRepository::save);
        });
  }

  @PostMapping
  Mono<Person> create(@RequestBody Person person) {
    return personService.create(person);
  }

  @GetMapping("/authors")
  Mono<List<Author>> getAuthors() {
    return authorRepository.findAll()
      .map((author -> {
        author.setName(author.getName() + "_updated");
        return author;
      }))
      .collectList()
      .map((authors -> {
        System.out.println("authors: " + authors.size());
        return authors;
      }));
  }
}
