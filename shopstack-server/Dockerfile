FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /shopstack-server

COPY pom.xml .

RUN mvn dependency:go-offline

COPY src /shopstack-server/src

RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /shopstack-server

COPY --from=build /shopstack-server/target/*.jar shopstack-server.jar

EXPOSE 8081

RUN apt-get update && apt-get install -y curl

CMD ["java", "-jar", "shopstack-server.jar"]
