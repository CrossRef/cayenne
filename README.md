# cayenne

The cayenne base codebase. Implements useful metadata transforms, ID handling, a resource API, OAI metadata
download and ingest / indexing.

## Usage

Install leiningen, then run lein repl and try a few commands:

    $ lein repl
	> (action/get-oai-records (conf/get-param [:oai :crossref-journals]) "2012-01-01" "2012-01-02" action/dump-plain-docs)

## Production

Run as a production service with some profiles:

    $ lein run :api :index

- :api - Run the resource HTTP API.
- :index - Run an OAI download and index once daily.
