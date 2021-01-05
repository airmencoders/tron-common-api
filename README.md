
# TRON Common API

## Swagger Docs
http://localhost:8080/api/swagger-ui/index.html?configUrl=/api/v3/api-docs/swagger-config

## H2 Test DB

### Console
http://localhost:8080/api/h2-console/

### Connection String
jdbc:h2:mem:testdb

## Postgres DB Usage

When the `production` profile is used (either with `-Pproduction` with Maven) or setting ENV VAR `spring_profiles_active` to `production` (as is done in the pipeline), 
then Common API will look to connect to a postgres db using the following ENV VARS:

`${PGHOST}` => defines the database hostname

`${PGPORT}` => port used by the database (normally is 5432)

`${PG_DATABASE}` => the database name

`${PG_USER}` => admin username to access the database

`${APP_DB_ADMIN_PASSWORD}` => admin password for the database


## Maven CLI w/ env variables & profile
mvn spring-boot:run -Dspring-boot.run.arguments="--PGHOST=host --PGPORT=port --PG_DATABASE=database_name --APP_DB_ADMIN__PASSWORD=database_password --PG_USER=database_user" -Pproduction

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