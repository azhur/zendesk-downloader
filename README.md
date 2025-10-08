# zendesk-downloader
Stateful ticket event stream download service:
 - provides a rest api to start/retrieve ticket download jobs for different zendesk customers (customer identified by their Zendesk subdomain)
 - handles zendesk api rate-limits by retrying requests after the time specified in the Retry-After response header
 - keeps track of the last downloaded ticket event timestamp per customer, so that subsequent requests only download new ticket events
 - decoupled components via traits, so that different implementations can be easily swapped + easy testing

## How to run the application
`sbt run` 
- runs the service on port 8080

### Example new customer ticket download job request
```bash
curl -v --location 'http://localhost:8080/api/v1/customers' \
--header 'Content-Type: application/json' \
--data '{
    "subdomain": "<your_zendesk_subdomain>",
    "oauth_token": "<your_oauth_token>",
    "start_timestamp": <epoch_seconds>
}'

# start_timestamp - is optional, if not provided the job will start from EPOCH timestamp
```

### Example existing customer ticket download job request
```bash
curl --location 'http://localhost:8080/api/v1/customers/<your_zendesk_subdomain>'
```

## Limitations
- No persistence, all state is in memory, if the service goes down all jobs are lost
- Doesn't scale horizontally, all jobs are processed in a single instance, a persistent storage would be needed to scale
- Zendesk api rate-limits rely on retry-after response header only. A more robust solution would be needed, to not overload zendesk api, so in addition to Retry-After header, it rate-limits on the client side as well. 
- Duplicates: assumption is that ticket sink handles duplicates and for console sink duplicates are allowed
- No authentication or authorization for the rest api service
- tests are not implemented yet :(. 
  - As all the components are decoupled and expressed via traits it's fairly easy to mock them and write unit tests.
  - sttp provides a stub backend which can be used to mock zendesk api responses so ZendeskApi can be easily tested as well.
  - When the persistent storage is added, integration tests can be added as well, ie via testcontainers.
