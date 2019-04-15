FROM clojure

COPY . /usr/src/app
RUN mkdir /usr/src/app/data
WORKDIR /usr/src/app

RUN lein deps
