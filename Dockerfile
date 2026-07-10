# syntax=docker/dockerfile:1
# One parameterized image for all subgraphs: docker build --build-arg MODULE=subgraph-titles .

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace
COPY pom.xml .
COPY common/ common/
COPY subgraph-titles/ subgraph-titles/
COPY subgraph-names/ subgraph-names/
COPY subgraph-ratings/ subgraph-ratings/
COPY subgraph-episodes/ subgraph-episodes/
COPY subgraph-crew/ subgraph-crew/
COPY subgraph-akas/ subgraph-akas/
COPY subgraph-principals/ subgraph-principals/
COPY subgraph-orchestrator/ subgraph-orchestrator/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre AS extract
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-*.jar app.jar
# Boot tools jarmode: exploded layout enables the CDS archive below
RUN java -Djarmode=tools -jar app.jar extract --destination extracted
# CDS training run: starts the context and exits at refresh (no Mongo needed),
# writing app.jsa to cut cold-start time on Cloud Run
RUN cd extracted && java -XX:ArchiveClassesAtExit=app.jsa \
    -Dspring.context.exit=onRefresh -jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=extract /app/extracted/ ./
ENV JAVA_TOOL_OPTIONS="-XX:SharedArchiveFile=app.jsa -XX:MaxRAMPercentage=70"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
