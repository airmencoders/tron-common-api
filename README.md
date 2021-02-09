
# TRON Common API

## Swagger Docs
Navigate to the root of the API - `/api` and a redirect will go to the Swagger UI docs.

Example:
`http://localhost:8080/api/` or `https://tron-common-api.staging.dso.mi/api`

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

By default, seeded data is not added to the database, but can be triggered through the environment variable `CONTEXTS` to `test`. Below are three examples.

### Liquibase Changeset Generation

Some database migrations are simple (e.g. adding a single column), and you may choose to write the liquibase changesets manually. For more complex changes, allowing liquibase to generate it for you based on a diff between the current db can be a big time saver:

1. Checkout the current master and run it using the production profile to get a snapshot of the current database in your local postgresql:
`mvn spring-boot:run -Pproduction`
2. Checkout branch with your changes
3. In the `liquibase-*.properties` files, update the `diffChangeLogFile` property to increment the version number to 1 greater than the current changeset (the newly generated changeset will take this file name, so you don't want to overwrite any existing ones)
4. Run the following command to tell liquibase to generate a diff between your current postgres database and the hibernate generated H2 databse *(replace the parameters as appropriate to match your environment)*:
```
mvnw -Dliquibase.url=jdbc:postgresql://localhost:5432/<dbname> -Dliquibase.username=<username> -Dliquibase.password=<password> liquibase:diff
```
5. Make any appropriate changes or customizations to the generated file
6. Don't forget to add the new changelog file to the `db.changelog-master.xml`


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


## Authorization
Application to Common API authorization is based off the `x-forwarded-client-cert` header to identify the requesting application's identify. This header will be provided by ISTIO in production. For development purposes, the application can be ran with the `development` profile to circumvent authorization so that the header does not need to be provided in requests. The `security.enabled` field in the properties is used to control whether or not Spring Security will enforce authorization.

Example header: `"x-forwarded-client-cert": "By=spiffe://cluster.local/ns/tron-common-api/sa/default;Hash=855b1556a45637abf05c63407437f6f305b4627c4361fb965a78e5731999c0c7;Subject=\"\";URI=spiffe://cluster.local/ns/guardianangel/sa/default"`

The identity is obtained by parsing down the URI field of the header to obtain the namespace name. For example, given a x-forwarded-client-cert header with the URI field: `URI=spiffe://cluster.local/ns/guardianangel/sa/default`, the identity obtained is `guardianangel`.

### Current Privileges
Current privileges include `READ`/`WRITE` (access to endpoints like /persons and /organization), and `DASHBOARD_USER`/`DASHBOARD_ADMIN` (access to endpoints specifically for dashboard app).
