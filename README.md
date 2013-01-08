# cayenne
x
Cayenne aims to be a high performance, parallel parser of CrossRef OAI-PMH / unixref metadata.

Currently cayenne can parse all CrossRef metadata, just over 50 million work records 
(with 100 millions of citation entries) in just under 4 hours on a modest 6-core machine.

## Usage

Install leiningen, then run lein repl and try a few commands:

$ lein repl
> (use 'cayenne.core)
> (use 'cayenne.tasks)
> (process-dir (file "/some/dir/with/xml") :task (doi-record-writer "out.txt"))

This prints DOI records as JSON to out.txt. Alternatively, :task can be any function that
takes parsed DOI record data structures (Clojure maps) and processes them in some way.
