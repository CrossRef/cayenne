# cayenne

The cayenne base codebase. Implements useful metadata transforms, ID handling, a resource API, OAI metadata
download and ingest / indexing.

## Quick Start

Make sure you have these dependencies installed within your development environment:

- Java 7
- Leiningen

Plus, if you wish to build the docker container for deployment:

- Docker

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

    $ lein run :api :index :update-members :update-journals :update-funders

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

    $ lein daemon start cayenne :api :index :update-members

Accepts the same arguments as lein run. Also available are:

    $ lein daemon stop cayenne
    $ lein daemon check cayenne

In daemonized mode, feature logging will still go to `log/log.txt`, however,
standard out from the daemonized process's start up phase will be sent to
`cayenne.log` rather than standard out.

When running as a daemon it is sometimes useful to start an nrepl server
to later connect a repl:

    $ lein daemon start cayenne :api :nrepl

## Run within a Docker Container

Create a docker image:

    $ lein uberimage
