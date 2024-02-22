FROM openjdk:17

WORKDIR /app

ADD target/gigachat_ml_service/lib    /app/lib
ADD target/gigachat_ml_service        /app

ENTRYPOINT ["java", "-cp", "*:lib/*", "gigachat.GigaChatService"]

