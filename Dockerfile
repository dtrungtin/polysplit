FROM maven:3-jdk-8-slim AS build
COPY . ./
RUN mvn -f ./pom.xml clean package

FROM openjdk:8
COPY --from=build ./target/polygon-splitter-1.0-SNAPSHOT.jar ./polygon-splitter.jar
WORKDIR ./
CMD ["java", "-jar","polygon-splitter.jar"]