FROM adoptopenjdk/openjdk11:alpine

#RUN addgroup -S app && adduser -S app -G app
#USER app:app

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-jar","/app.jar"]