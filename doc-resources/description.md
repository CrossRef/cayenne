## Preamble

The Crossref REST API is one of [a variety of tools and APIs](https://www.crossref.org/services/metadata-delivery/) that allow anybody to search and reuse our members' metadata in sophisticated ways.


## Meta

### Frequency of indexing

Records typically appear in the REST API within 20 minutes of their having been successfully deposited with Crossref.

Summary information (e.g. counts, etc.) are processed in batch every 24 hours.

### Learning about performance or availability problems

Note that we generally post notice any ongoing performance problems with our services on our twitter feeds at [CrossrefOrg](https://twitter.com/CrossrefOrg) and [CrossrefSupport](https://twitter.com/@CrossrefSupport). We also report them on our [support site](https://support.crossref.org/hc/en-us). You might want to check these to see if we are already aware of a problem before you report it.

### Reporting performance or availability problems

Report performance/availability at our [support site](https://support.crossref.org/hc/en-us).

### Reporting bugs, requesting features

Please report bugs with the API or the documentation on our [issue tracker](https://github.com/Crossref/rest-api-doc/issues).

### Documentation License

<a rel="license" href="http://creativecommons.org/licenses/by/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by/4.0/88x31.png" /></a><br />This work is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a>.

### Metadata License

Crossref asserts no claims of ownership to individual items of bibliographic metadata and associated Digital Object Identifiers (DOIs) acquired through the use of the Crossref Free Services. Individual items of bibliographic metadata and associated DOIs may be cached and incorporated into the user's content and systems.

### Privacy

We also have a [privacy policy](https://www.crossref.org/privacy/).

### Libraries

You might be able to avoid reading all this documentation if you instead use one of the several excellent libraries that have been written for the Crossref REST API. For example:

- [habanero](https://github.com/sckott/habanero) (Python)
- [serrano](https://github.com/sckott/serrano) (Ruby)
- [rcrossref](https://github.com/ropensci/rcrossref) (R)
- [crossrefapi](https://github.com/fabiobatalha/crossrefapi) (Python)

If you know of another library you would like to see listed here, please let us know about it via the [issue tracker](https://github.com/Crossref/rest-api-doc/issues).

### Etiquette

We want to provide a public, open, and free API for all. And we don't want to unnecessarily burden developers (or ourselves) with cumbersome API tokens or registration processes in order to use the public REST API. For that to work, we ask that you be polite and try not to do anything that will take the public REST API down or otherwise make it unusable for others. Specifically, we encourage the following polite behaviour:

- Cache data so you don't request the same data over and over again.
- Actively monitor API response times. If they start to go up, back-off for a while. For example, add pauses between requests and/or reduce the number of parallel requests.
- Specify a `User-Agent` header that properly identifies your script or tool and that provides a means of contacting you via email using "mailto:". For example:
`GroovyBib/1.1 (https://example.org/GroovyBib/; mailto:GroovyBib@example.org) BasedOnFunkyLib/1.4`.

This way we can contact you if we see a problem.

- report problems and/or ask questions on our [issue tracker](https://github.com/Crossref/rest-api-doc/issues).

Alas, not all people are polite. And for this reason we reserve the right to impose rate limits and/or to block clients that are disrupting the public service.

### Good manners = more reliable service.

But we prefer carrots to sticks. As of September 18th 2017 any API queries that **use HTTPS and have appropriate contact information** will be directed to a special pool of API machines that are reserved for polite users.

Why are are we doing this? Well- we don't want to force users to have to register with us. But this means that if some user of the public server writes a buggy script or ignores timeouts and errors- they can really bring the API service to its knees. What's more, it is very hard for us to identify these problem users because they tend to work off multiple parallel machines and use generic User-Agent headers. They are effectively anonymous. We're starting to have to spend a lot of time dealing with these problems and the degraded performance of the public API is affecting all the polite users as well.

So... we are keeping the public service as is. It will probably continue to fluctuate widely in performance. But now, if a client connects to the API using HTTPS and provides contact information either in their User-Agent header or as a parameter on their queries, then we will send them to a separate pool of machines. We expect to be able to better control the performance of these machines because, if a script starts causing problems, we can contact the people responsible for the script to ask them to fix it. Or, in extremis, we can block it.

How does it work? Simple. You can do one of two things to get directed to the "polite pool":

1) Include a "mailto" parameter in your query. For example:

`https://api.crossref.org/works?filter=has-full-text:true&mailto=GroovyBib@example.org`

2) Include a "mailto:" in your User-Agent header. For example:

`GroovyBib/1.1 (https://example.org/GroovyBib/; mailto:GroovyBib@example.org) BasedOnFunkyLib/1.4`.

Note that this only works if you query the API using HTTPS. You really should be doing that anyway (wags finger).

##### Frequently anticipated questions

**Q:** Will you spam me with marketing [bumf](https://en.oxforddictionaries.com/definition/bumf) once you have our contact info?

**A:** No. We will only use it to contact you about problems with your scripts.


**Q:** Is this a secret plot to kill public access to your API?

**A:** No. It is an attempt to keep the public API reliable.


**Q:** What if I provide fake or incorrect contact info?

**A:** That is not very polite. If there is a problem and you don't respond, we'll block you.


**Q:** Does the contact info have to be a real name?

**A:** No. As long as somebody actually receives and pays attention to email at the address, it can be pseudo-anonymous, or whatever.



#### Rate limits

From time to time Crossref needs to impose rate limits to ensure that the free API is usable by all. Any rate limits that are in effect will be advertised in the `X-Rate-Limit-Limit` and `X-Rate-Limit-Interval` HTTP headers.

#### Blocking

This is always our last resort, and you can generally avoid it if you include contact information in the `User-Agent` header or `mailto` parameter as described above.

But seriously... this is a bummer. We really want you to use the API. If you are polite about it, you shouldn't have any problems.

### Use for production services

What if you want to use our API for a production service that cannot depend on the performance uncertainties of the free and open public API? What if you don't want to be affected by impolite people who do not follow the [API Etiquette](#api-etiquette) guidelines? Well, if you’re interested in using these tools or APIs for production services, we [have a service-level offering](https://www.crossref.org/services/metadata-delivery/plus-service/) called "Plus". This service provides you with with access to all supported APIs and metadata, but with extra service and support guarantees.

#### Authorization token for Plus service

When you sign up for the Plus service, you will be issued an API token that you should put in the `Authorization` header of all your rest API requests. This token will ensure that said requests get directed to a pool of machines that are reserved for "Plus" SLA users. For example, with [curl](https://curl.haxx.se/):

```
curl -X GET \
  https://api.crossref.org/works \
  -H 'Authorization: Bearer yJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vY3Jvc3NyZWYub3JnLyIsImF1ZXYZImVuaGFuY2VkY21zIiwianRpIjoiN0M5ODlFNTItMTFEQS00QkY3LUJCRUUtODFCMUM3QzE0OTZEIn0.NYe3-O066sce9R1fjMzNEvP88VqSEaYdBY622FDiG8Uq' \
  -H 'User-Agent: GroovyBib/1.1 (https://example.org/GroovyBib/; mailto:GroovyBib@example.org) BasedOnFunkyLib/1.4'
  ```

Note that you can still be "polite" and identify yourself as well. And, of course, replace the fake token above with the real token.

## API overview

The API is generally RESTFUL and returns results in JSON. JSON formats returned by the API are documented [here](https://github.com/Crossref/rest-api-doc/blob/master/api_format.md).

The API supports HTTP and HTTPS. Examples here are provided using HTTPS.

You should always url-encode DOIs and parameter values when using the API. DOIs are notorious for including characters that break URLs (e.g. semicolons, hashes, slashes, ampersands, question marks, etc.).

Note that, for the sake of clarity, the examples in this document do *not* url-encode DOIs or parameter values.

The API will only work for Crossref DOIs. You can test the registration agency for a DOI using the following route:

`https://api.crossref.org/works/{doi}/agency`

Testing the following Crossref DOI:

`10.1037/0003-066X.59.1.29`

Using the URL:

`https://api.crossref.org/works/10.1037/0003-066X.59.1.29/agency`

Will return the following result:

    {
      status: "ok",
      message-type: "work-agency",
      message-version: "1.0.0",
      message: {
        DOI: "10.1037/0003-066x.59.1.29",
        agency: {
          id: "crossref",
          label: "Crossref"
        }
      }
    }

If you use any of the API calls listed below with a non-Crossref DOI, you will get a `404` HTTP status response. Typical agency IDs include `crossref`, `datacite`, `medra` and also `public` for test DOIs.

## Result types

All results are returned in JSON. There are three general types of results:

- Singletons
- Headers-only
- Lists

The mime-type for API results is `application/vnd.crossref-api-message+json`

### Singletons

Singletons are single results. Retrieving metadata for a specific identifier (e.g. DOI, ISSN, funder_identifier) typically returns in a singleton result.

### Headers only

You can use HTTP HEAD requests to quickly determine "existence" of a singleton. The advantage of this technique is that it is very fast because it does not return any metadata- it only retruns headers and an HTTP status code (200=exists, 404=does not exist).

To determine if member ID `98` exists:

`curl --head "http://api.crossref.org/members/98"`

To determine if a journal with ISSN `1549-7712` exists:

`curl --head "http://api.crossref.org/journals/1549-7712"`

### Lists
Lists results can contain multiple entries. Searching or filtering typically returns a list result. A list has two parts:

- Summary, which include the following information:

    - status (e.g. "ok", error)
    - message-type (e.g. "work-list" )
    - message-version (e.g. 1.0.0 )

- Items, which will will contain the items matching the query or filter.

Note that the "message-type" returned will differ from the mime-type:

- funder (singleton)
- prefix (singleton)
- member (singleton)
- work (singleton)
- work-list (list)
- funder-list (list)
- prefix-list (list)
- member-list (list)

Normally, an API list result will return both the summary and the items. If you want to just retrieve the summary, you can do so by specifying that the number of rows returned should be zero.

#### Sort order

If the API call includes a query, then the sort order will be by the relevance score. If no query is included, then the sort order will be by DOI update date.

See the documentation for each endpoint to find the list of elements that you can sort by.

### Selecting which elements to return

Crossref metadata records can be quite large. Sometimes you just want a few elements from the schema. You can "select" a subset of elements to return using the `select` parameter. This can make your API calls much more efficient. For example:

`http://api.crossref.org/works?sample=10&select=DOI,title`

See the documentation for each endpoint to find the list of elements that can be selected.
