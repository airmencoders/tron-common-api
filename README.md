
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
