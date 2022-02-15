FROM openjdk:17-alpine
MAINTAINER Jack Ratner <jackratner@gmail.com>

WORKDIR /

COPY target/uberjar/when-is-my-ride-0.0.0-standalone.jar when-is-my-ride.jar
EXPOSE 3000

CMD java -jar when-is-my-ride.jar
