# Netflix DGS Sample

## How to test

For testing [GraphQL Playground](https://github.com/graphql/graphql-playground) can be used.

Config for GraphQL Playground may look like this:

```
extensions:
      endpoints:
        default: 
          url: 'http://localhost:8080/graphql'
          subscription:
             url: 'ws://localhost:8080/subscriptions'
```

## Queries

The following queries can be used for testing.

### Shows
```
{
    shows {
        id
        title
        releaseYear
    }
}
```

### New shows
```
{
    newShows {
        id
        title
        releaseYear
        iDoNotExist
    }
}
```

### Mutation
```
mutation AddShowMutation($title: String!) {
    addShow(title: $title)
}
```
variables:
```
{
    "title": "A new show"
}
```

### Subscription
```
subscription SubscriptionNotifyNewShow($releaseYear: Int!) {
  notifyNewShow(releaseYear: $releaseYear) {
    id
    title
    releaseYear
  }
}

```
variables:
```
{
  "releaseYear": -1
}
```

### Data loader
```
query QueryShows {
    shows {
        id
        title
        releaseYear
        actorId
        actor {
            id
            name
        }
    }
}
```
