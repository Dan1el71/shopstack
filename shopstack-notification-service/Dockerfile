FROM maven:3.8.1-openjdk-17 AS build

WORKDIR /shopstack-notification-service

COPY pom.xml .

RUN mvn dependency:go-offline

COPY src /shopstack-notification-service/src

RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /shopstack-notification-service

COPY --from=build /shopstack-notification-service/target/*.jar shopstack-notification-service.jar

EXPOSE 8083

RUN apt-get update && apt-get install -y curl

CMD ["java", "-jar", "shopstack-notification-service.jar"]
