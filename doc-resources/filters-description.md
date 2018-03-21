

## Filters

Filters allow you to narrow queries. All filter results are lists.

### Dot filters

A filter with a dot in its name is special. The dot signifies that the filter will be applied to some other record type that is related to primary resource record type. For example, with work queries, one can filter on works that have an award, where the same award has a particular award number and award-gving funding agency:

##
```
/works?filter=award.number:CBET-0756451,award.funder:10.13039/100000001
```
##

Here we filter on works that have an award by the National Science Foundation that also has the award number `CBET-0756451`.

##

This endpoint supports the following filters:

##
