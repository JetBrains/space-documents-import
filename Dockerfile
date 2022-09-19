FROM openjdk:18.0.2-slim

MAINTAINER Semyon Atamas <semyon.atamas@jetbrains.com>

COPY . /home/space
WORKDIR /home/space

RUN ["./gradlew", "--no-daemon", "shadowJar"]

FROM openjdk:18.0.2-slim

COPY --from=0 /home/space/build/libs/space.jar /home/space/space.jar

ENTRYPOINT ["java", "-jar", "/home/space/space.jar"]