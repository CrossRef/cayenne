(ns cayenne.api.v1.types)

(def depositable #{"application/vnd.crossref.mini+xml"
                   "application/vnd.crossref.common+xml"
                   "application/vnd.crossref.object+json"})

(def html-or-json ["application/json" "text/html"])

(def json ["application/json"])

(def deposit "application/vnd.crossref.deposit+json")

(def prefix "application/vnd.crossref.prefix+json")

(def work-transform
  ["application/rdf+xml"
   "text/turtle"
   "text/n-triples"
   "text/n3"
   "application/vnd.citationstyles.csl+json"
   "application/citeproc+json"
   "text/x-bibliography"
   "text/bibliography"
   "application/x-research-info-systems"
   "application/x-bibtex"
   "application/vnd.crossref.unixref+xml"
   "application/vnd.crossref.unixsd+xml"])

