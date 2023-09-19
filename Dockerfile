FROM eclipse-temurin:17-jre-alpine
COPY build/libs/routing-*-all.jar routing.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "routing.jar"]
