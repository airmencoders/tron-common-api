
# TRON Common API

## Swagger Docs
http://localhost:8080/api/swagger-ui/index.html?configUrl=/api/v3/api-docs/swagger-config

## H2 Test DB

### Console
http://localhost:8080/api/h2-console/

### Connection String
jdbc:h2:mem:testdb

## Maven CLI w/ env variables & profile
mvn spring-boot:run -Dspring-boot.run.arguments="--DB_HOST_URL=host:port --DB_NAME=database_name --DB_PASSWORD=database_password --DB_USERNAME=database_user" -Pproduction

## Seeded Data

Seeded data is provided for the purpose of local integration development.

Seeded data is located at `src/main/resources/db/seed/{version}`, and names of the `.csv` files correlate to the table or entity they reperesent.

By default seeded data is not added to the database, but can be trigged through the environemnt variable `CONTEXTS` to `test`. Below are three examples.

### docker
`docker run -p 8080:8080 --env CONTEXTS=test registry.il2.dso.mil/tron/products/tron-common-api/tron-common-api:{version}`

### docker-compose
Note: No external port is needed, as your api should communicate directly with the Common API.
```
version: '3.3'

services:

  common-api:
    image: registry.il2.dsop.io/tron/products/tron-common-api/tron-common-api:{version}
    environment:
      CONTEXTS: test
```

### jar file
`export CONTEXTS=test && java -jar target/commonapi-{version}.jar`