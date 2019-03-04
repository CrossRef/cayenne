FROM clojure

COPY . /usr/src/app
WORKDIR /usr/src/app

RUN lein deps
