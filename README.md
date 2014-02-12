# cayenne

The cayenne base codebase. Implements useful metadata transforms, ID handling, a resource API, OAI metadata
download and ingest / indexing.

## Usage

Install leiningen, then run lein repl and try a few commands:

    $ lein repl
	> (action/get-oai-records (conf/get-param [:oai :crossref-journals]) "2012-01-01" "2012-01-02" action/dump-plain-docs)

## Run in Foreground

Run as a production service with some profiles:

    $ lein run :api :index :update-members

- :api - Run the resource HTTP API.
- :index - Run an OAI download and index once daily.
- :update-members - Collect member records and update with metadata stats.

## Run as a Daemon

Run as a daemonized production service with lein-daemon:

    $ lein daemon start cayenne :api :index :update-members

Accepts the same arguments as lein run. Also available are:

    $ lein daemon stop cayenne
    $ lein daemon check cayenne

In daemonized mode, feature logging will still go to `log/log.txt`, however,
standard out from the daemonized process's start up phase will be sent to
`cayenne.log` rather than standard out.
