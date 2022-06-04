FROM openjdk:17-buster AS builder
MAINTAINER Jack Ratner <jackratner@gmail.com>

RUN apt-get install -y curl \
  && curl -sL https://deb.nodesource.com/setup_16.x | bash - \
  && apt-get install -y nodejs \
  && apt-get install -y leiningen

WORKDIR /

COPY package*.json ./
RUN npm install

COPY project.clj ./
COPY shadow-cljs.edn ./
COPY src/ ./src
COPY resources/ ./resources
COPY protoc/ ./protoc

RUN npx shadow-cljs release :app
RUN lein uberjar

FROM openjdk:17-alpine

COPY --from=builder /target/uberjar/when-is-my-ride-0.0.0-standalone.jar when-is-my-ride.jar
EXPOSE 3000

CMD java -jar when-is-my-ride.jar
