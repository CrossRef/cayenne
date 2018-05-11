# cayenne

The cayenne base codebase. Implements useful metadata transforms, ID handling, a resource API, OAI metadata
download and ingest / indexing.

## Quick Start

Make sure you have these dependencies installed within your development environment:

- Java 7
- Leiningen
- Docker

The tests require Docker to spin up service dependencies on development machine (ElasticSearch). Download Docker for Mac from https://www.docker.com/docker-mac and confirm that it's installed with `docker-compose --version`. 

### Preparing CSL Resources

Before running cayenne, building an uberjar or building a docker docker image, CSL resources
must be pulled into the local repository via git submodules. Manifest files must then be
created for CSL style files and locale files.

Update git submodules to bring in CSL style and locale files:

    $ git submodule update --init

Refresh `resources/styles.edn` and `resources/locales.edn`:

    $ lein csl

### Running in a REPL

Call `lein repl`, `(begin)` then try a few commands, for example, parse some XML downloaded from
OAI-PMH:

    $ lein repl
    > (begin)
	> (action/get-oai-records (conf/get-param [:oai :crossref-journals]) "2012-01-01" "2012-01-02" action/dump-plain-docs)

## Run in Foreground

Run as a production service with some profiles:

    $ lein with-profiles prod run :api :index :update-members :update-journals :update-funders

- :api - Run the resource HTTP API.
- :index - Run an OAI download and index once daily.
- :update-members - Collect member records and update with metadata coverage stats (once a day).
- :update-journals - Collect journal records and update with metadata coverage stats (once a day).
- :update-funders - Load new funder registry RDF when available. Checks for new RDF once an hour.
- :graph - Enables a connection to the graph database backend, datomic.
- :graph-api - Must be specified along with :api and :graph. Enables the graph API. Requires datomic leiningen profile.
- :feed-api - Must be specified along with :api. Enables the feed API for real-time metadata ingest.
- :process-feed-files - Run async processing of incoming feed files. Should be enabled with :feed-api.

## Run as a Daemon

Run as a daemonized production service with lein-daemon:

    $ lein with-profiles prod daemon start cayenne :api :index :update-members

Accepts the same arguments as lein run. Also available are:

    $ lein with-profiles prod daemon stop cayenne
    $ lein with-profiles prod daemon check cayenne

In daemonized mode, feature logging will still go to `log/log.txt`, however,
standard out from the daemonized process's start up phase will be sent to
`cayenne.log` rather than standard out.

When running as a daemon it is sometimes useful to start an nrepl server
to later connect a repl:

    $ lein with-profiles prod daemon start cayenne :api :nrepl

## Run within a Docker Container

Create a docker image:

    $ lein uberimage

## Running tests

Running with `lein test` should take care of creating any required infrastructure, typically ElasticSearch. 

The ElasticSearch instance will be created using docker image `docker.elastic.co/elasticsearch/elasticsearch:6.2.3`.

The default corpus loaded into ElasticSearch is located in `dev-resources/feeds/corpus`, you can switch to a different corpus using: 

``` 
CAYENNE_API_TEST_CORPUS=/large-corpus lein test cayenne.corpus-test
```

The example above switches to a larger corpus located in `dev-resources/feeds/large-corpus` for the specific test run. Keep in mind that many of tests rely on a specific corpus being loaded into ElasticSearch.

## Reference Visibility

References are displayed in API output. The visiblity level of those references
can be set as `open`, `limited` or `closed`, where each setting will display
more references than the last. This setting can be configured directly by
the internal config variable `[:service :api :references]` or via the ENV VAR
`REFERENCES`.
