
# Field Queries
Field queries allow for queries that match only particular fields of metadata. For example, this query matches records that contain the tokens `richard` or `feynman` (or both)
in any author field:

##

```
https://api.crossref.org/works?query.author=richard+feynman
```

##

Field queries can be combined with the general `query` paramter and each other. Each query parameter
is ANDed with the others:

##
```
https://api.crossref.org/works?query.title=room+at+the+bottom&query.author=richard+feynman
```

##

This endpoint supports the following field queries:

##
