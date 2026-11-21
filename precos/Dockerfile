FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN chmod +x mvnw \
 && ./mvnw -B -DskipTests package \
 && cp target/precos-*.jar /tmp/precos.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /tmp/precos.jar /app/precos.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/precos.jar"]
