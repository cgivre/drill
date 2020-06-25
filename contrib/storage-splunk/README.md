# Drill Connector for Splunk
This plugin enables Drill to query Splunk. 

## Configuration
To connect Drill to Splunk, create a new storage plugin with the following configuration:

To successfully connect, Splunk uses port `8089` for interfaces.  This port must be open for Drill to query Splunk. 

```json
{
   "type":"splunk",
   "username": "admin",
   "password": "changeme",
   "hostname": "localhost",
   "port": 8089,
   "earliestTime": "-14d",
   "latestTime": "now",
   "enabled": false
}
```

## Understanding Splunk's Data Model
Splunk's primary use case is analyzing event logs with a timestamp. As such, data is indexed by the timestamp, with the most recent data being indexed first.  By default, Splunk
 will sort the data in reverse chronological order.  Large Splunk installations will put older data into buckets of hot, warm and cold storage with the "cold" storage on the
  slowest and cheapest disks.
  
With this understood, it is **very** important to put time boundaries on your Splunk queries. The Drill plugin allows you to set default values in the configuration such that every
 query you run will be bounded by these boundaries.  Alternatively, you can set the time boundaries at query time.  In either case, you will achieve the best performance when
  you are asking Splunk for the smallest amount of data possible.
  
## Understanding Drill's Data Model with Splunk
Drill treats Splunk indexes as tables. Splunk's access model does not restrict to the catalog, but does restrict access to the actual data. It is therefore possible that you can
 see the names of indexes to which you do not have access.
  
```
apache drill> SHOW TABLES IN splunk;
+--------------+----------------+
| TABLE_SCHEMA |   TABLE_NAME   |
+--------------+----------------+
| splunk       | summary        |
| splunk       | splunklogger   |
| splunk       | _thefishbucket |
| splunk       | _audit         |
| splunk       | _internal      |
| splunk       | _introspection |
| splunk       | main           |
| splunk       | history        |
| splunk       | _telemetry     |
+--------------+----------------+
9 rows selected (0.304 seconds)
```
To query Splunk from Drill, use the following format: 
```sql
SELECT <fields>
FROM splunk.<index>
```
  
 ## Bounding Your Queries
  When you learn to query Splunk via their interface, the first thing you learn is to bound your queries so that they are looking at the shortest time span possible. When using
   Drill to query Splunk, it is advisable to do the same thing, and Drill offers two ways to accomplish this: via the configuration and at query time.
   
  ### Bounding your Queries at Query Time
  The easiest way to bound your query is to do so at querytime via special filters in the `WHERE` clause. There are two special fields, `earliestTime` and `latestTime` which can
   be set to bound the query. If they are not set, the query will be bounded to the defaults set in the configuration.
   
   You can use any of the time formats specified in the Splunk documentation here:   
  https://docs.splunk.com/Documentation/Splunk/8.0.3/SearchReference/SearchTimeModifiers
  
  ### Data Types
  Splunk does not have sophisticated data types and unfortunately does not provide metadata from its query results.  With the exception of the fields below, Drill will interpret
   all fields as `VARCHAR` and hence you will have to convert them to the appropriate data type at query time.
  
  #### Timestamp Fields
  * `_indextime`
  * `_time` 
  
  #### Numeric Fields
  * `date_hour` 
  * `date_mday`
  * `date_minute`
  * `date_second` 
  * `date_year`
  * `linecount`
  
### Selecting Fields
When you execute a query in Drill for Splunk, the fields you select are pushed down to Splunk. Therefore, it will always be more efficient to explicitly specify fields to push
 down to Splunk rather than using `SELECT *` queries.
 
 ### Special Fields
 There are several fields which can be included in a Drill query 
 
 * `spl`:  If you just want to send an SPL query to Splunk, this will do that. 
 * `earliestTime`: Overrides the `earliestTime` setting in the configuration. 
 * `latestTime`: Overrides the `latestTime` setting in the configuration. 
  
### Sorting Results
Due to the nature of Splunk indexes, data will always be returned in reverse chronological order. Thus, sorting is not necessary if that is the desired order.

## Sending Arbitrary SPL to Splunk
There is a special table called `spl` which you can use to send arbitrary queries to Splunk. If you use this table, you must include a query in the `spl` filter as shown below:
```sql
SELECT *
FROM splunk.spl
WHERE spl='<your SPL query'
```