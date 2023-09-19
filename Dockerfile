FROM eclipse-temurin:17-jre-alpine
COPY build/libs/routing-*-all.jar routing-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "routing-service.jar"]
