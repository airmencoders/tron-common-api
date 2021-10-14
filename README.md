
# TRON Common API

By far the easiest way to get up an running with a local instance of Common API for testing alongside another application you are developing is to follow the instructions in the repo https://code.il2.dso.mil/tron/products/tron-common-api/tron-common-api-local for how to use docker-compose to get all services up and running.

If you choose to get your hands dirty and want to run the Common API locally for _development_ on it, then note that Common API runs by default in the `development` profile (e.g `mvn spring-boot:run`) which means:
  + you can access it at http://localhost:8088/api
  + H2 (in-mem) database is used
  + spring security is disabled

You can run `development` with security enabled or you can run `production` with security disabled.  To control security manually 
set the env var `SECURITY_ENABLED` as in `SECURITY_ENABLED=true mvn spring-boot:run -Pdevelopment` which will force the development 
profile and force security to be enabled. Or you can just set `security.enabled=true` in application-development.properties


If you want to run it locally in `production` profile, then issue `mvn spring-boot:run -Pproduction`.  This means that:
  + you an access it at http://localhost:8080/api (note change in port number)
  + looks for and uses a postgres db (see postgres section below)
  + spring security is enabled

If you want to run properties specific to your local setup, create an `application-local.properties` file in the resources directory and override specific properties.

## Swagger Docs
Navigate to the root of the API - `/api` and a redirect will go to the Swagger UI docs.

Example:
`http://localhost:8088/api/` or `https://tron-common-api.staging.dso.mil/api` etc.

## H2 Test DB

The H2 database is the in-memory database used in `development` and in unit tests.

### H2 Console

The H2 console can only be accessed when Spring Security is disabled (which is default in devlopment profile).

http://localhost:8088/api/h2-console/

### H2 Connection String

jdbc:h2:mem:testdb

### H2 creds

username: `sa` with no password

## Postgres DB Usage

When the `production` profile is used (either with `-Pproduction` with Maven) or setting ENV VAR `spring_profiles_active` to `production` (as is done in the pipeline), 
then Common API will look to connect to a postgres db using the following ENV VARS:

`${PGHOST}` => defines the database hostname

`${PGPORT}` => port used by the database (normally is 5432)

`${PG_DATABASE}` => the database name

`${PG_USER}` => admin username to access the database

`${APP_DB_ADMIN_PASSWORD}` => admin password for the database


## Forcing use of Postgres DB via Maven CLI w/ env variables & profile
mvn spring-boot:run -Dspring-boot.run.arguments="--PGHOST=host --PGPORT=port --PG_DATABASE=database_name --APP_DB_ADMIN__PASSWORD=database_password --PG_USER=database_user" -Pproduction

## Seeded Data

See the seeder utility repo at: https://code.il2.dso.mil/tron/products/tron-common-api/tron-common-api-seeder

### Liquibase Changeset Generation

Some database migrations are simple (e.g. adding a single column), and you may choose to write the liquibase changesets manually. For more complex changes, allowing liquibase to generate it for you based on a diff between the current db can be a big time saver:

1. Checkout the current master and run it using the production profile to get a snapshot of the current database in your local postgresql:
`mvn spring-boot:run -Pproduction`
2. Checkout branch with your changes
3. Run the following command to tell liquibase to generate a diff between your current postgres database and the hibernate generated H2 database *(replace the parameters as appropriate to match your environment)*:
```
mvn -Dliquibase.url=jdbc:postgresql://localhost:5432/<db_name> -Dliquibase.username=<db_username> -Dliquibase.password=<db_password> liquibase:diff
```
4. The generated diff changelog file will have .XXX in the name, change this as appropriate to the next available version number
5. Make any appropriate changes or customizations to the generated file
6. You no longer have to add the new changelog file to the `db.changelog-master.xml`, it will be included automatically

### docker
`docker run -p 8080:8080 registry.il2.dso.mil/tron/products/tron-common-api/tron-common-api:{version}`

Note to log into the IL2 GitLab container registry first:

`docker login registry.il2.dso.mil -u gitlab_ci_token -u <token>` where `<token>` is your GitLab access token with registry accesses enabled.

## Authorization
Application to Common API authorization is based off the `x-forwarded-client-cert` header to identify the requesting application's identify. This header will be provided by ISTIO in production. For development purposes, the application can be ran with the `development` profile to circumvent authorization so that the header does not need to be provided in requests. The `security.enabled` (or env var SECURITY_ENABLED=true) field in the properties is used to control whether or not Spring Security will enforce authorization.

Example header: `"x-forwarded-client-cert": "By=spiffe://cluster.local/ns/tron-common-api/sa/default;Hash=855b1556a45637abf05c63407437f6f305b4627c4361fb965a78e5731999c0c7;Subject=\"\";URI=spiffe://cluster.local/ns/guardianangel/sa/default"`

The identity is obtained by parsing down the URI field of the header to obtain the namespace name. For example, given a x-forwarded-client-cert header with the URI field: `URI=spiffe://cluster.local/ns/guardianangel/sa/default`, the identity obtained is `guardianangel`.

### Authorization in local development
To use authorization in local development run the jwt-cli-utility (https://code.il2.dso.mil/tron/products/dod-open-source/utilities/jwt-cli-utility)

Before running the API with the JWT UTILITY, set an admin email to `ckumabe.ctr@revacomm.com` in the admin.jwt file.

Run `node proxy.js 9000 8080` if you're running tron-common-api on the "production" profile or `node proxy.js 9000 8088` if running in "development".

### Authorization in local development #2
To use an alternative method run the tron-common-api-proxy(https://code.il2.dso.mil/tron/products/tron-common-api/tron-common-api-proxy)

Steps after cloning repository:
* Create a new directory `app` and another directory `jwts` inside of app
* Move `admin.jwt` into the new `jwts` directory and set an admin email to `ckumabe.ctr@revacomm.com`
* In `app.js`change the path `'/app/jwts/'` to `'./app/jwts/'`
* Set the following environment variables  (http://localhost:8080 if running production profile)
  REAL_PROXY_URL=http://localhost:8088;WEB_PORT=9001;LISTEN_ON_PORT=9000;DEFAULT_JWT_FILE=admin.jwt;DEFAULT_NAMESPACE=istio-system;ENABLE_PROXY=true
* Start the proxy with `node app.js`

### Current Privileges
Current privileges include `READ`/`WRITE` (access to endpoints like /persons and /organization), and `DASHBOARD_USER`/`DASHBOARD_ADMIN` (access to endpoints specifically for dashboard app).

### App Source configuration for local development
To populate your App Sources when running in the "development" profile, you'll need to create an `appSourceConfig.local.json` file. To create this file, copy the appSourceConfig.example.json file and fill in with your local App Source configuration.


## MinIO
To run MinIO locally with the development profile you can run it in a docker container with the following steps:

1. Run `docker run --name common-api-minio -p 9002:9002 -p 9003:9003 -e "MINIO_ROOT_USER=admin" -e "MINIO_ROOT_PASSWORD=adminpass" quay.io/minio/minio server /data --console-address ":9003" --address :9002`

This declares port 9002 to be the port to minIO bucket and port 9003 to the web gui.  


2. Login to http://localhost:9003 with the credentials used to start up the container

3. Go to Buckets and then click create bucket in the top corner

4. Set bucket name as testbucket

5. Set `minio.enabled=true` in application-development.properties

#### Alternative
You can also run it locally using the following and follow the same steps 2-5 above
```
wget https://dl.min.io/server/minio/release/darwin-amd64/minio
chmod +x minio
MINIO_ROOT_USER=admin MINIO_ROOT_PASSWORD=adminpass ./minio server /tmp/data --address ":9002" --console-address ":9003"
```



 



