# VoltDB gets 66K TPS on the &quot;Telecom Application Transaction Processing&quot; benchmark

## Introduction

The &quot;Telecom Application Transaction Processing&quot; benchmark was devised to model telco system behaviour for benchmark purposes. The [Documentation](https://bitbucket.org/voltdbseteam/tatp_2019/src/master/docs/TATP_Description.pdf) describes it as follows:

_The __Telecommunication__ Application __Transaction__ Processing__(TATP)__benchmark __is__ designed __to__ measure __the__ performance __of__ a __relational__ DBMS/OS/Hardware __combination__ in __a__ typical __telco__ application._

_The __benchmark__ generates __a__ flooding __load__ on __a__ database __server.__ This __means__ that __the__ load __is__ generated __up__ to __the__ maximum __throughput__ point __that__ the __server__ can __sustain.__ The __load__ is __generated__ by __issuing__ pre-defined __transactions__ run __against__ a __specified__ target __database.__ The __target__ database __schema__ is __made__ to __resemble__ a __typical__ Home __Location__ Register__(HLR)__database __in__ a __mobile__ phone __network.__ The __HLR__ is __a__ database __that__ mobile __network__ operators __use__ to __store__ information __about__ the __subscribers__ and __the__ services__thereof._

## While not well known, this benchmark is interesting for three reasons:

1. It thinks in terms of business operations, instead of the &#39;Gets&#39;, &#39;Puts&#39; and &#39;Scans&#39; you see in NoSQL focused examples.
2. It&#39;s a good example of the &quot;Alternative Unique Key&quot; problem.
3. It&#39;s  reasonable model of a [Home Location Registry](https://en.wikipedia.org/wiki/Network_switching_subsystem#Home_location_register_(HLR)), which is an important real world system that almost everyone uses every day.

## The &quot;Alternative Unique Key&quot; problem

Over the last decade we&#39;ve seen a shift away from every bigger servers to private clouds filled with commodity hardware. As part of this shift database providers have responded by making new products that are [horizontally sharded](https://en.wikipedia.org/wiki/Shard_(database_architecture)) - instead of _all_ servers trying to &#39;know&#39; about _all_ the data an algorithm is used to send it to 2 or more of them, who effectively &#39;own&#39; it. So while a cluster might have 30 nodes, only 2 or 3 of them might actually have access to a given record, depending on how many copies you&#39;d decided to keep.

Obviously this approach depends on being able to figure out where a record belongs, which in turn implies that you know the [Primary Key](https://en.wikipedia.org/wiki/Primary_key) of your record. &quot;[Key value](https://en.wikipedia.org/wiki/Key-value_database)&quot; stores are a great example of this approach in action - finding a record if you know the key is really fast.

In cases where you are using a conventional [Foreign Key](https://en.wikipedia.org/wiki/Foreign_key) value - such as &#39;Zipcode&#39; or &#39;State&#39; - you can still get data by asking all 30 nodes, as these [low cardinality](https://en.wikipedia.org/wiki/Cardinality_(SQL_statements)) foreign keys imply many possible matches and asking all 30 nodes is thus a reasonable thing to do. In real life &#39;zipcode&#39; or &#39;state&#39; queries that return 1000&#39;s of rows aren&#39;t that common in OLTP systems, and when they are you can usually mitigate the issue by prepending them to the Primary Key, which limits where your rows can end up. VoltDB also has &quot;Materialized Views&quot; which can efficiently keep running totals for &quot;Count(\*)&quot; queries of this nature.

But what happens if have to work with an identifier which is an alternative [Unique Key](https://en.wikipedia.org/wiki/Unique_key)?, Like the primary key it uniquely identifies a record, but it comes from a different &#39;namespace&#39; to the one you use 80% of the time, and can change every couple of minutes? You don&#39;t know where to look, and asking all 30 nodes in your cluster isn&#39;t very efficient. It turns out this scenario is very common in the telecoms industry, where pretty much everything you speak to will give you an identifier it owns and manages at the start of every interaction, and will insist on using it for the rest of the interaction. This means you&#39;ll get lots of messages referring to an id that is unique at that moment in time, but is of no use to you when it comes to finding the underlying record you are talking about.

What makes the TATP benchmark interesting is that fully 20% of the transactions are like this.

## There is no &quot;Correct&quot; answer to the &quot;Alternative Unique Key&quot; Problem

From an implementation perspective this is a non-trivial problem. It&#39;s not a Volt-specific problem. All horizontally sharded systems will have to make some awkward choices. The default list generally looks like this:

### 1. Use a single, large server running a legacy RDBMS so you can do the search and write in one move.

This simply isn&#39;t going to work if you are obliged by the market to deploy on smaller, commodity hardware. In addition, most NoSQL/NewSQL products are around 10x faster than a legacy RDBMS. So a workload that would require 3 generic servers for NoSQL/NewSQL won&#39;t need  single server with 3x the power for a legacy RDBMS, it&#39;ll need to be 30x the power. Unsurprisingly, we don&#39;t bother testing this in our implementation.

### 2. Maintain a separate index table/KV store and write to both at the same time.

In the example below the Primary Key lives on Node 1. We create an index entry which looks like another record to the database.  For our purposes the index record lives on Node 2.

![KV Store](https://github.com/srmadscience/voltdb-tatpbenchmark/blob/master/docs/KVStore.png "KV Store")
 

This will work and scale well, but has two problems:

- Both reads and writes are now a two step process. There are a whole series of edge case scenarios where (for example) reads can&#39;t find a row that exists because they looked at the secondary index 500 microseconds too early. Depending on your application this may not matter. For the &quot;Telecom Application Transaction Processing&quot; Use Case we could probably get away with it, as the FK involves roaming users and it&#39;s hard for phone users  to change their roaming status multiple times per second.

- The real problem is the complexity around error handling. What happens if one write works, but another fails? How do I &quot;uncorrupt&quot; the data? What do I do with incoming requests in the meantime? What's interesting about TATP is that in all the cases where we need to do this we start with a read, and then do a write or another read. There is, for this subset of possible use cases, zero complexity to error handling. 

- We explore two variations on this theme. 

### 3. Get your NewSQL/NoSQL store to do a global search, then do a write.

![Multi Query](https://github.com/srmadscience/voltdb-tatpbenchmark/blob/master/docs/multiquery.png "Multi Query")

This assumes that there is such a thing as a global read in your environment. In VoltDB we handle this behind the scenes, but a single node will still be in charge of getting the other nodes to issue the query at the exact same moment in time. The &quot; **FKMODE\_MULTI\_QUERY\_FIRST**&quot; option in our implementation does such a read to turn the foreign key into a subscriber id, which is then used for the actual transaction. Under normal circumstances both events take well under a millisecond to complete, which means the time window when things can go wrong is very small.  The downside from a VoltDB perspective is that while what we call &quot;Multi Partition Reads&quot; are fast, they aren&#39;t nearly as fast as single partition transactions.

### 4. Do a &#39;read&#39; to ask all possible locations if they recognize your unique key, one at a time.

![Ask All](https://github.com/srmadscience/voltdb-tatpbenchmark/blob/master/docs/askall.png "Ask All")

You can do your transaction once you know the subscriber\_id, so as a prerequisite you get the subscriber id by asking everyone if they recognize your Unique Key. This seems slightly crazy, but is actually viable, provided you&#39;re not trying to do this for every transaction. Obviously it creates problems for scalability, but before panicking we should see what kind of numbers we get and see if this is actually an issue. In the TATP benchmark 20% of transactions fall into this category. In our implementation this is the &quot; **FKMODE\_QUERY\_ALL\_PARTITIONS\_FIRST&quot;** _option_ **.**

### 5. Ask all possible locations to do the update if they have an entry that matches the Unique Key.

![Do All](https://github.com/srmadscience/voltdb-tatpbenchmark/blob/master/docs/doall.png "Do All")


This is a variation on step &#39;4&#39;, except we just send &#39;writes&#39; to attempt to do the update in every possible location.  In our implementation this is the &quot; **FKMODE\_TASK\_ALL\_PARTITIONS**&quot; option. On the face of it this seems to be a classic case of &#39;write amplification&#39;, but from a Volt perspective &#39;reads&#39; and &#39;writes&#39; both cost roughly the same to do. It also has the advantage that being one step we can&#39;t be flummoxed by the unique key being updated, provided it isn&#39;t instantly assigned to someone else. We know from the Use Case this won&#39;t happen.

We go into detail about how our implementation works below, but a key takeaway is that not only is this a hard problem to solve, but the different possible solutions are all &#39;optimal&#39; depending on your Use Case.

## The TATP Schema

The Schema looks like this:

![Schema](https://github.com/srmadscience/voltdb-tatpbenchmark/blob/master/docs/tatp_schema.png "Schema")

The documentation defines it as the following tables:

### Subscriber Table

1. s_id is a unique number between 1 and N where N is the number of subscribers (the population size).Typically, the population sizes start at N=100,000 subscribers, and then N is multiplied by factors of 2, 5 and 10 and so forth, for each order of magnitude. During the population, s\_id is selected randomly from the set of allowed values.

2. sub_nbr is a 15 digit string. It is generated from s\_id by transforming s\_id to string and padding it with leading zeros. For example: s\_id 123 sub\_nbr &quot;000000000000123&quot;

3. bit_X fields are randomly generated values (either 0 or 1).

4. hex_X fields are randomly generated numbers between 0 and 15.

5. byte2_X fields are randomly generated numbers between 0 and 255.

6. sc_location and vlr_location are randomly generated numbers between 1 and (232 – 1).

### Access_Info Table

1. s_id references s_id in the Subscriber table.

2. ai_type is a number between 1 and 4. It is randomly chosen, but there can only be one record of each ai_type per each subscriber. In other words, if there are four Access_Info records for a certain subscriber they have values 1, 2, 3 and 4.

3. data1 and data2 are randomly generated numbers between 0 and 255.

4. data3 is a 3-character string that is filled with random characters created with upper case A-Z letters.

5. data4 is a 5-character string that is filled with random characters created with upper case A-Z letters.

There are between 1 and 4 Access_Info records per Subscriber record, so that there are 25 % subscribers with one record, 25% with two records and so on.

### Special_Facility Table

1. s_id references _id in the Subscriber table.

2. sf_type is a number between 1 and 4. It is randomly chosen, but there can only be one record of each sf_type per each subscriber. So if there are four Special_Facility records for a certain subscriber, they have values 1, 2, 3 and 4.

3. is_active is either 0 or 1. is_active is chosen to be 1 in 85% of the cases and 0 in 15% of the cases.

4. error_cntrl and data_a are randomly generated numbers between 0 and 255.

5. data_b is a 5-character string that is filled with random characters created with upper case A-Z letters.

There are between 1 and 4 Special_Facility records per row in the Subscriber table, so that there are 25% subscribers with one record, 25% with two records and so on.

### Call_Forwarding Table

1. s_id and sf_type reference the corresponding fields in the Special_Facility table.

2. start_time is of type integer. It can have value 0, 8 or 16 representing midnight, 8 o&#39;clock or 16 o&#39;clock.

3. end_time is of type integer. Its value is start_time + N, where N is a randomly generated value between 1 and 8.

4. numberx is a randomly generated 15 digit string.

There are between zero and 3 Call_Forwarding records per Special_Facility row, so that there are 25 % Special_Facility records without a Call_Forwarding record, 25% with one record and so on. Because start_time is part of the primary key, every record must have different start_time.

### Initial Data

The database is always freshly populated before each benchmark run. This ensures that runs are reproducible, and that each run starts with correct data distributions. The Subscriber table acts as the main table of the benchmark. After generating a subscriber row, its child records in the other tables are generated and inserted. The number of rows in the Subscriber table is used to scale the population size of the other tables. For example, a TATP with population size of 1,000,000 gives the following table cardinalities for the benchmark:

* Subscriber = 1,000,000 rows

* Access_Info ≈ 2,500,000 rows

* Special_Facility ≈ 2,500,000 rows

* Call_Forwarding ≈ 3,750,000 rows

### Transaction mixes

The basic TATP benchmark runs a mixture of seven (7) transactions issued by ten (10) independent clients. All the clients run the same transaction mixture with the same transaction probabilities as defined below.

#### Read Transactions (80%)

* GET_SUBSCRIBER_DATA 35 %

* GET_NEW_DESTINATION 10 %

* GET_ACCESS_DATA 35 %

#### Write Transactions (20%)

* UPDATE_SUBSCRIBER_DATA 2 %

* UPDATE_LOCATION 14 %

* INSERT_CALL_FORWARDING 2 %

* DELETE_CALL_FORWARDING 2 %

## VoltDB Implementation

From a VoltDB viewpoint the challenge here is how to handle transactions that access via sub_nbr instead of the partitioned key s_id.

The following transactions access via the partitioned key and are trivial to implement in VoltDB:

* GetSubscriberData

* GetNewDestination

* GetAccessData

* UpdateSubscriberData

These transactions represent 20% of the logical transactions, but use the synthetic Unique Key mandated by the benchmark:

* UpdateLocation

* InsertCallForwarding

* DeleteCallForwarding

From a VoltDB viewpoint our design choices were discussed above. We implemented the following:

- &quot;FKMODE_MULTI_QUERY_FIRST&quot; does a global read consistent query to find the row, and then follows up with a single partition update.

- &quot;FKMODE_QUERY_ALL_PARTITIONS_FIRST&quot; asks each partition independently to find the row, and then follows up with a single partition update.

- &quot;FKMODE_TASK_ALL_PARTITIONS&quot; cuts out the middleman and asks each partition independently to try and update the row. Given that there is only one row and while the Unique ID is volatile it doesn&#39;t get reused this will work for this specific Use Case.

- &quot;FKMODE_CACHED_ANSWER&quot; does a lookup and then a separate update once it knows the location of the row.

- &quot;FKMODE_COMPOUND_PROCS&quot; uses a compound procedure to do a lookup and update in one network trip.


## What our benchmark does

Our code stats by creating the test data if needed. It then runs our version of the benchmark for each implementation for 5 minutes. It starts at 2,000 TPS and then re-runs with 4,000, 6,000 , etc until the server can no longer get \&gt; 90% of requested transactions done or we hit some other reasons for stopping. We then look at the previous file and use the TPS it did as the result for that configuration. It does this for each of the three methods we discuss above. We produce an output log file which has detailed statistics for 1 of the 10 threads.

## VoltDB results in AWS

### Configuration

For testing purposes we ran all 3 options above on the following configuration:

* AWS

* [ ](https://aws.amazon.com/ec2/instance-types/z1d/)[z1d.2xlarge](https://aws.amazon.com/ec2/instance-types/z1d/) - Intel Xeon, runs up to &#39;up to 4.0 GHz&#39;, 4 physical cores per server. Around US$0.25/hr.

* 3 nodes

* k=1 (1 spare copy)

* Snapshotting and command logging enabled.

Note that:

* All our transactions are ACID transactions

* &#39;k=1&#39; ensures that all transactions take place on 2 nodes.

* &#39;z1d.2xlarge&#39; has 8 vCPUs, or 4 real ones.

### Results

The best result was obtained was when we asked all the partitions to do a read and then do an update once we&#39;ve found out where it is (FKMODE\_QUERY\_ALL\_PARTITIONS\_FIRST).  We were able to sustain 66,635 TPS while aiming for 68K. At that level the hardware wasn&#39;t maxed out, but the 99th percentile latency for our tricky &quot;Alternative Unique Key&quot; was still around 1ms. As we increased the workload it jumped to over 10ms, which is the point at which it will not be acceptable to customers.

The next best alternative for the &quot;Alternative Unique Key&quot; stuff is to ask all the partitions to try and do it. We were able to get 27,518 TPS by the time latency reached 10ms. This does, however, have the advantage of not having to worry about updates.

The worst outcome was for using a multi partition query. The additional overhead comes from making sure that all nodes issue the query at the exact same moment in time.

Note that at 66,635 TPS for basic read and write operations latency is still 1ms or under at this point.

In the graph below the X axis is the load we requested, the thick blue line is how ahy TPS we&#39;re doing (right axis). The performance of individual operations is on the left hand scale in milliseconds. Note that if for some reason you didn&#39;t care about latency you could get something more than 100K TPS.


![Results](https://github.com/srmadscience/voltdb-tatpbenchmark/blob/master/results/stats_z1d.2xlarge.3nodes.k1.png "Results")



Bear in mind this is on a generic AWS configuration without making any heroic attempts to maximize performance. Our goal is to create something that people evaluating VoltDB can do for themselves.This is not - and does not claim to be - the &#39;most&#39; VoltDB can get on this benchmark or on AWS.

## How to run this yourself

1. Download and install VoltDB

1. All the code is in github ([https://github.com/srmadscience/voltdb-tatpbenchmark](https://github.com/srmadscience/voltdb-tatpbenchmark))  and is a maven project. So getting it is a case of:

```
git clone https://github.com/srmadscience/voltdb-tatpbenchmark

cd voltdb-tatpbenchmark
```

1. The script runbenchmark does a build, and then installs the schema and starts the benchmark:

```
sh scripts/runbenchmark.sh
```

&#39;Runbenchmark.sh&#39; can be run with three parameters:

- Hosts - comma delimited list of volt servers. Defaults to the hostname.
- Testname - a word describing the purpose of the test. Defaults to the hostname.
- Subscribers - How many subscribers should be in your Home Location Registry. 1,000,000 subs takes about 600MB of RAM. We ran our tests with 20,000,000.

So an example would be:

```
sh scripts/runbenchmark.sh vdb1,vdb2,vdb3 mytest 20000000

```

