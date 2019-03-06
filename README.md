# cayenne

Cayenne serves the Crossref REST API. Implements useful metadata transforms, ID handling, a resource API, OAI metadata
download and ingest / indexing.

## Quick Start

Cayenne runs in Docker, both in developmentt and production. In development the environment is mounted inside the container so any file changes are immediately reflected in the container.

Make sure you have Docker and Docker Compose installed. Check by running:

    $ docker-compose --version

### Preparing CSL Resources

Before running Cayenne, building an uberjar or building a production docker docker image, CSL resources
must be pulled into the local repository via git submodules. Manifest files must then be
created for CSL style files and locale files.

Update git submodules to bring in CSL style and locale files:

    $ git submodule update --init

Refresh `resources/styles.edn` and `resources/locales.edn`:

    $ docker-compose  -f docker-compose.yml run api lein csl

## Running in a REPL

To start a repl:

    $ docker-compose -f docker-compose.yml run --service-ports api lein repl
    
Then call `(begin)` and try a few commands, for example, parse some XML downloaded from
OAI-PMH:

    > (begin)
	> (action/get-oai-records (conf/get-param [:oai :crossref-journals]) "2012-01-01" "2012-01-02" action/dump-plain-docs)
	
To start a test version of the API type:

      > (user/start)

Then, to load the corpus of documents complete with coverage checks (the same function that's used in the integration tests):

    >  (user/index-feed)
    
You can ctrl-c to stop coverage checks if you wish.

Then visit e.g. <http://localhost:3000/v1/works>

## Testing

Tests fall into a few categories.

 - Unit tests run in isolation, and work purely by running Cayenne source code. They usually centre around single functions.
 - Component tests exercise a given chunk of service, for example, a particular API endpoint, but make no dependency on Elastic.
 - Integration tests involve a dependency to test how Cayenne works with it. Currently this is only Elastic Search.
 - Manual tests are for experimental or development work. They may depend on external resources, such as the previous version of the API, and may not be reliable.

### Running tests

All tests are run using Docker Compose. In the case of integration tests, the Elastic Search instance is provided as part of the Docker Compose setup, and the test fixtures are responsible for clearing all data between tests. In theory Docker isn't required for unit and component tests, but it's better that the tests run on the target platform. 
 
To run each category:
 
     docker-compose  -f docker-compose.yml  run api lein test :unit
     docker-compose  -f docker-compose.yml  run api lein test :component
     docker-compose  -f docker-compose.yml  run api lein test :integration
     docker-compose  -f docker-compose.yml  run api lein test :manual

Or in a repl:

    (clojure.test/test-vars [#'the-ns/the-test])

### Regression testing

Regression testing ensures that things that used to work, keep working. They fall across a number of categories.

#### Parser Regression tests

The XML parser has a suite of regression XML files that are run for finding regressions. You will find full details of the below, including file paths and types in the `cayenne.formats.parser-regression-test` namespace. 

The tests themselves are classed as unit tests because whilst the tests load data from disk, the code exercised is purely stateless. There is also a test to checks that there are no orphaned XML files without corresponding EDN output files. This ensures that we don't get into a situation where there are ambiguous files. 

When a new feature is introduced, or a new bug found, a new XML file should be added. There's a function which parses the input and creates an EDN file of the output. You should run this, **check the output to see if it's actually correct**, and then then the new files into source control.



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
