FROM maven:3.8.1-openjdk-17 AS build

WORKDIR /shopstack-payment-service

COPY pom.xml .

RUN mvn dependency:go-offline

COPY src /shopstack-payment-service/src

RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /shopstack-payment-service

COPY --from=build /shopstack-payment-service/target/*.jar shopstack-payment-service.jar

EXPOSE 8082

RUN apt-get update && apt-get install -y curl

CMD ["java", "-jar", "shopstack-payment-service.jar"]
