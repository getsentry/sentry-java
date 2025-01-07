# Sentry Sample Spring Boot 3.0+

Sample application showing how to use Sentry with [Spring boot](http://spring.io/projects/spring-boot) from version `3.0` onwards integrated with the [OpenTelemetry Spring Boot Starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/) without an agent.

## How to run? 

To see events triggered in this sample application in your Sentry dashboard, go to `src/main/resources/application.properties` and replace the test DSN with your own DSN.

Then, execute a command from the module directory:

```
../../gradlew bootRun
```

Make an HTTP request that will trigger events:

```
curl -XPOST --user user:password http://localhost:8080/person/ -H "Content-Type:application/json" -d '{"firstName":"John","lastName":"Smith"}'
```

## GraphQL

The following queries can be used to test the GraphQL integration.

### Greeting
```
{
    greeting(name: "crash")
}
```

### Greeting with variables

```
query GreetingQuery($name: String) {
    greeting(name: $name)
}
```
variables:
```
{
    "name": "crash"
}
```

### Project

```
query ProjectQuery($slug: ID!) {
    project(slug: $slug) {
        slug
        name
        repositoryUrl
        status
    }
}
```
variables:
```
{
    "slug": "statuscrash"
}
```

### Mutation

```
mutation AddProjectMutation($slug: ID!) {
    addProject(slug: $slug)
}
```
variables:
```
{
    "slug": "nocrash",
    "name": "nocrash"
}
```

### Subscription

```
subscription SubscriptionNotifyNewTask($slug: ID!) {
    notifyNewTask(projectSlug: $slug) {
        id
        name
        assigneeId
        assignee {
            id
            name
        }
    }
}
```
variables:
```
{
    "slug": "crash"
}
```

### Data loader

```
query TasksAndAssigneesQuery($slug: ID!) {
    tasks(projectSlug: $slug) {
        id
        name
        assigneeId
        assignee {
            id
            name
        }
    }
}
```
variables:
```
{
    "slug": "crash"
}
```
