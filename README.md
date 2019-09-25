# <span dir="ltr">VoltDB gets 66K TPS on the “Telecom Application Transaction Processing” benchmark</span>

## <span dir="ltr">Introduction</span>

<span dir="ltr">The “Telecom Application Transaction Processing”
benchmark was devised to model telco system behaviour for benchmark
purposes. The</span> <span dir="ltr">[<span class="underline">
Documentation</span>](https://bitbucket.org/voltdbseteam/tatp_2019/src/master/docs/TATP_Description.pdf)
describes it as follows:</span>

<span dir="ltr"></span>

<span dir="ltr">*The* *Telecommunication* *Application* *Transaction*
*Processing* *(TATP)* *benchmark* *is* *designed* *to* *measure* *the*
*performance* *of* *a* *relational* *DBMS/OS/Hardware* *combination*
*in* *a* *typical* *telco* *application.*</span>

<span dir="ltr"></span>

<span dir="ltr">*The* *benchmark* *generates* *a* *flooding* *load* *on*
*a* *database* *server.* *This* *means* *that* *the* *load* *is*
*generated* *up* *to* *the* *maximum* *throughput* *point* *that* *the*
*server* *can* *sustain.* *The* *load* *is* *generated* *by* *issuing*
*pre-defined* *transactions* *run* *against* *a* *specified* *target*
*database.* *The* *target* *database* *schema* *is* *made* *to*
*resemble* *a* *typical* *Home* *Location* *Register* *(HLR)* *database*
*in* *a* *mobile* *phone* *network.* *The* *HLR* *is* *a* *database*
*that* *mobile* *network* *operators* *use* *to* *store* *information*
*about* *the* *subscribers* *and* *the* *services* *thereof.*</span>

<span dir="ltr"></span>

<span dir="ltr">While not well known, this benchmark is interesting for
three reasons:</span>

<span dir="ltr"></span>

1.  > <span dir="ltr">It thinks in terms of business operations, instead
    > of the ‘Gets’, ‘Puts’ and ‘Scans’ you see in NoSQL focused
    > examples.</span>

2.  > <span dir="ltr">It’s a good example of the “Alternative Unique
    > Key” problem.</span>

3.  > <span dir="ltr">It’s reasonable model of a
    > [<span class="underline">Home Location
    > Registry</span>](https://en.wikipedia.org/wiki/Network_switching_subsystem#Home_location_register_\(HLR\)),
    > which is an important real world system that almost everyone uses
    > every day.</span>

<span dir="ltr"></span>

## <span dir="ltr">The “Alternative Unique Key” problem</span>

<span dir="ltr">Over the last decade we’ve seen a shift away from every
bigger servers to private clouds filled with commodity hardware. As part
of this shift database providers have responded by making new products
that are [<span class="underline">horizontally
sharded</span>](https://en.wikipedia.org/wiki/Shard_\(database_architecture\))
- instead of *all* servers trying to ‘know’ about *all* the data an
algorithm is used to send it to 2 or more of them, who effectively ‘own’
it.</span> <span dir="ltr">So while a cluster might have 30 nodes, only
2 or 3 of them might actually have access to</span> <span dir="ltr">a
given record, depending on how many copies you’d decided to keep.</span>

<span dir="ltr"></span>

<span dir="ltr">Obviously this approach depends on being able to figure
out where a record belongs, which in turn implies that you know the
[<span class="underline">Primary
Key</span>](https://en.wikipedia.org/wiki/Primary_key) of your record.
“[<span class="underline">Key
value</span>](https://en.wikipedia.org/wiki/Key-value_database)” stores
are a great example of this approach in action - finding a record if you
know the key is really fast.</span>

<span dir="ltr"></span>

<span dir="ltr">In cases where you are using a conventional
[<span class="underline">Foreign
Key</span>](https://en.wikipedia.org/wiki/Foreign_key) value - such as
‘Zipcode’ or ‘State’ - you can still get data by asking all 30 nodes,
as these [<span class="underline">low
cardinality</span>](https://en.wikipedia.org/wiki/Cardinality_\(SQL_statements\))
foreign keys imply many possible matches and asking all 30 nodes is thus
a reasonable thing to do. In real life ‘zipcode’ or ‘state’ queries that
return 1000’s of rows aren’t that common in OLTP systems, and when they
are you can usually mitigate the issue by prepending them to the Primary
Key, which limits where your rows can end up. VoltDB also has
“Materialized Views” which can efficiently keep running totals for
“Count(\*)” queries of this nature.</span>

<span dir="ltr"></span>

<span dir="ltr">But what happens if have to work with an identifier
which is an alternative [<span class="underline">Unique
Key</span>](https://en.wikipedia.org/wiki/Unique_key)?, Like the primary
key it uniquely identifies a record, but it comes from a different
‘namespace’ to the one you use 80% of the time, and can change every
couple of minutes? You don’t know where to look, and asking all 30 nodes
in your cluster isn’t very efficient. It turns out this scenario is very
common in the telecoms industry, where pretty much everything you speak
to will give you an identifier it owns and manages at the start of every
interaction, and will insist on using it for the rest of the
interaction. This means you’ll get lots of messages referring to an id
that is unique at that moment in time, but is of no use to you when it
comes to finding the underlying record you are talking about.</span>

<span dir="ltr"></span>

<span dir="ltr">What makes the TATP benchmark interesting is that fully
20% of the transactions are like this.</span>

## <span dir="ltr">There is no “Correct” answer to the “Alternative Unique Key” Problem</span>

<span dir="ltr">From an implementation perspective this is a non-trivial
problem. It’s not a VoltDB-specific problem. All horizontally sharded
systems will have to make some awkward choices. The default list
generally looks like this:</span>

<span dir="ltr"></span>

### <span dir="ltr">Use a single, large server running a legacy RDBMS so you can do the search and write in one move.</span>

<span dir="ltr"></span>

> <span dir="ltr">This simply isn’t going to work if you are obliged by
> the market to deploy on smaller, commodity hardware. In addition, most
> NoSQL/NewSQL products are around 10x faster than a legacy RDBMS. So a
> workload that would require 3 generic servers for NoSQL/NewSQL won’t
> need single server with 3x the power for a legacy RDBMS, it’ll need to
> be 30x the power. Unsurprisingly, we don’t bother testing this in our
> implementation.</span>

### <span dir="ltr">Maintain a separate index table/KV store and write to both at the same time.</span>

> <span dir="ltr"></span>
> 
> <span dir="ltr">In the example below</span> <span dir="ltr">the
> Primary Key lives on Node 1. We create an index entry which looks like
> another record to the database. For our purposes the index record
> lives on Node 2.</span>
> 
> <span dir="ltr"></span>
> 
> ![](media/image5.png)<span dir="ltr"></span>
> 
> <span dir="ltr">This will work and scale well, but has two serious
> problems:</span>
> 
> <span dir="ltr"></span>

  - > <span dir="ltr">Both reads and writes are now a two step process.
    > There are a whole series of edge case scenarios where (for
    > example) reads can’t find a row that exists because they looked at
    > the secondary index 500 microseconds too early. Depending on your
    > application this may not matter. For the “Telecom Application
    > Transaction Processing” Use Case we could probably get away with
    > it, as the FK involves roaming users and it’s hard for phone users
    > to change their roaming status multiple times per second.</span>

> <span dir="ltr"></span>

  - > <span dir="ltr">The real problem is the complexity around error
    > handling. What happens if one write works, but another fails? How
    > do I “uncorrupt” the data? What do I do with incoming requests in
    > the meantime? For these reasons we don’t bother testing this in
    > our implementation, although we might add this in a future
    > iteration.</span>

> <span dir="ltr"></span>

### <span dir="ltr">Get your NewSQL/NoSQL store to do a global search, then do a write.</span> 

> <span dir="ltr"></span>
> 
> ![](media/image1.png)<span dir="ltr"></span>
> 
> <span dir="ltr"></span>
> 
> <span dir="ltr">This assumes that there is such a thing as a global
> read in your environment. In VoltDB we handle this behind the scenes,
> but a single node will still be in charge of getting the other nodes
> to issue the query at the exact same moment in time. The
> “**FKMODE\_MULTI\_QUERY\_FIRST**” option in our implementation does
> such a read to turn the foreign key into a subscriber id, which is
> then used for the actual transaction. Under normal circumstances both
> events take well under a millisecond to complete, which means the time
> window when things can go wrong is very small. The downside from a
> VoltDB perspective is that while what we call “Multi Partition Reads”
> are fast, they aren’t nearly as fast as single partition
> transactions.</span>

### <span dir="ltr">Do a ‘read’ to ask all possible locations if they recognize your unique key, one at a time.</span> 

> ![](media/image2.png)<span dir="ltr"></span>
> 
> <span dir="ltr"></span>
> 
> <span dir="ltr">You can do</span> <span dir="ltr">your transaction
> once you know the subscriber\_id, so as a prerequisite you get the
> subscriber id by asking everyone if they recognize your Unique Key.
> This seems slightly crazy, but is actually viable, provided you’re not
> trying to do this for every transaction. Obviously it creates problems
> for scalability, but before panicking we should see what kind of
> numbers we get and see if this is actually an issue. In the TATP
> benchmark 20% of transactions fall into this category. In our
> implementation this is the
> “***FKMODE\_QUERY\_ALL\_PARTITIONS\_FIRST”** option**.***</span>
> 
> <span dir="ltr"></span>

### <span dir="ltr">Ask all possible locations to do the update if they have an entry that matches the Unique Key.</span> 

![](media/image3.png)<span dir="ltr"></span>

> <span dir="ltr">This is a variation on step ‘4’, except we just send
> ‘writes’ to attempt to do the update in every possible location. In
> our implementation this is the “**FKMODE\_TASK\_ALL\_PARTITIONS**”
> option. On the face of it this seems to be a classic case of ‘write
> amplification’, but from a Volt perspective ‘reads’ and ‘writes’ both
> cost roughly the same to do. It also has the advantage that being one
> step we can’t be flummoxed by the unique key being updated, provided
> it isn’t instantly assigned to someone else. We know from the Use Case
> this won’t happen.</span>

<span dir="ltr"></span>

<span dir="ltr">We go into detail about how our implementation works
below, but a key takeaway is that not only is this a hard problem to
solve, but the different possible solutions are all ‘optimal’ depending
on your Use Case.</span>

<span dir="ltr"></span>

**<span dir="ltr">The TATP Schema</span>**

<span dir="ltr">The Schema looks like this:</span>

<span dir="ltr"></span>

![](media/image6.png)<span dir="ltr"></span>

<span dir="ltr">The documentation defines it as the following
tables:</span>

### **<span dir="ltr">Subscriber Table</span>**

> <span dir="ltr">1. s\_id is a unique number between 1 and N where N is
> the number of subscribers (the population size).Typically, the
> population sizes start at N=100,000 subscribers, and then N is
> multiplied by factors of 2, 5 and 10 and so forth, for each order of
> magnitude. During the population, s\_id is selected randomly from the
> set of allowed values.</span>
> 
> <span dir="ltr"></span>
> 
> <span dir="ltr">2. sub\_nbr is a 15 digit string. It is generated from
> s\_id by transforming s\_id to string and padding it with leading
> zeros. For example: s\_id 123 sub\_nbr “000000000000123”</span>
> 
> <span dir="ltr">3. bit\_X fields are randomly generated values (either
> 0 or 1).</span>
> 
> <span dir="ltr">4. hex\_X fields are randomly generated numbers
> between 0 and 15.</span>
> 
> <span dir="ltr">5. byte2\_X fields are randomly generated numbers
> between 0 and 255.</span>
> 
> <span dir="ltr">6. sc\_location and vlr\_location are randomly
> generated numbers between 1 and (232 – 1).</span>

### **<span dir="ltr">Access\_Info Table</span>**

> <span dir="ltr">1. s\_id references s\_id in the Subscriber
> table.</span>
> 
> <span dir="ltr">2. ai\_type is a number between 1 and 4. It is
> randomly chosen, but there can only be one record of each ai\_type per
> each subscriber. In other words, if there are four Access\_Info
> records for a certain subscriber they have values 1, 2, 3 and
> 4.</span>
> 
> <span dir="ltr">3. data1 and data2 are randomly generated numbers
> between 0 and 255.</span>
> 
> <span dir="ltr">4. data3 is a 3-character string that is filled with
> random characters created with upper case A-Z letters.</span>
> 
> <span dir="ltr">5. data4 is a 5-character string that is filled with
> random characters created with upper case A-Z letters.</span>

<span dir="ltr">There are between 1 and 4 Access\_Info records per
Subscriber record, so that there are 25 % subscribers with one record,
25% with two records and so on.</span>

### **<span dir="ltr">Special\_Facility Table</span>**

> <span dir="ltr">1. s\_id references s\_id in the Subscriber
> table.</span>
> 
> <span dir="ltr">2. sf\_type is a number between 1 and 4. It is
> randomly chosen, but there can only be one record of each sf\_type per
> each subscriber. So if there are four Special\_Facility records for a
> certain subscriber, they have values 1, 2, 3 and 4.</span>
> 
> <span dir="ltr">3. is\_active is either 0 or 1. is\_active is chosen
> to be 1 in 85% of the cases and 0 in 15% of the cases.</span>
> 
> <span dir="ltr">4. error\_cntrl and data\_a are randomly generated
> numbers between 0 and 255.</span>
> 
> <span dir="ltr">5. data\_b is a 5-character string that is filled with
> random characters created with upper case A-Z letters.</span>

<span dir="ltr">There are between 1 and 4 Special\_Facility records per
row in the Subscriber table, so that there are 25% subscribers with one
record, 25% with two records and so on.</span>

### **<span dir="ltr">Call\_Forwarding Table</span>**

> <span dir="ltr">1. s\_id and sf\_type reference the corresponding
> fields in the Special\_Facility table.</span>
> 
> <span dir="ltr">2. start\_time is of type integer. It can have value
> 0, 8 or 16 representing midnight, 8 o’clock or 16 o’clock.</span>
> 
> <span dir="ltr">3. end\_time is of type integer. Its value is
> start\_time + N, where N is a randomly generated value between 1 and
> 8.</span>
> 
> <span dir="ltr">4. numberx is a randomly generated 15 digit
> string.</span>

<span dir="ltr">There are between zero and 3 Call\_Forwarding records
per Special\_Facility row, so that there are 25 % Special\_Facility
records without a Call\_Forwarding record, 25% with one record and so
on. Because start\_time is part of the primary key, every record must
have different start\_time.</span>

## **<span dir="ltr">Initial Data</span>**

<span dir="ltr">The database is always freshly populated before each
benchmark run. This ensures that runs are reproducible, and that each
run starts with correct data distributions. The Subscriber table acts as
the main table of the benchmark. After generating a subscriber row, its
child records in the other tables are generated and inserted. The number
of rows in the Subscriber table is used to scale the population size of
the other tables. For example, a TATP with population size of 1,000,000
gives the following table cardinalities for the benchmark:</span>

<span dir="ltr"></span>

> <span dir="ltr">• Subscriber = 1,000,000 rows</span>
> 
> <span dir="ltr">• Access\_Info ≈ 2,500,000 rows</span>
> 
> <span dir="ltr">• Special\_Facility ≈ 2,500,000 rows</span>
> 
> <span dir="ltr">• Call\_Forwarding ≈ 3,750,000 rows</span>

## **<span dir="ltr">Transaction mixes</span>**

<span dir="ltr">The basic TATP benchmark runs a mixture of seven (7)
transactions issued by ten (10) independent clients. All the clients run
the same transaction mixture with the same transaction probabilities as
defined below.</span>

### **<span dir="ltr">Read Transactions (80%)</span>**

> <span dir="ltr">• GET\_SUBSCRIBER\_DATA 35 %</span>
> 
> <span dir="ltr">• GET\_NEW\_DESTINATION 10 %</span>
> 
> <span dir="ltr">• GET\_ACCESS\_DATA 35 %</span>

### **<span dir="ltr">Write Transactions (20%)</span>**

> <span dir="ltr">• UPDATE\_SUBSCRIBER\_DATA 2 %</span>
> 
> <span dir="ltr">• UPDATE\_LOCATION 14 %</span>
> 
> <span dir="ltr">• INSERT\_CALL\_FORWARDING 2 %</span>
> 
> <span dir="ltr">• DELETE\_CALL\_FORWARDING 2 %</span>

## **<span dir="ltr">VoltDB Implementation</span>**

<span dir="ltr">From a VoltDB viewpoint the challenge here is how to
handle transactions that access via sub\_nbr instead of the partitioned
key s\_id.</span>

<span dir="ltr">The following transactions access via the partitioned
key and are trivial to implement in VoltDB:</span>

<span dir="ltr"></span>

> <span dir="ltr">• GetSubscriberData</span>
> 
> <span dir="ltr">• GetNewDestination</span>
> 
> <span dir="ltr">• GetAccessData</span>
> 
> <span dir="ltr">• UpdateSubscriberData</span>
> 
> <span dir="ltr"></span>

<span dir="ltr">These transactions represent 20% of the logical
transactions, but use the synthetic Unique Key mandated by the
benchmark:</span>

<span dir="ltr"></span>

> <span dir="ltr">• UpdateLocation</span>
> 
> <span dir="ltr">• InsertCallForwarding</span>
> 
> <span dir="ltr">• DeleteCallForwarding</span>
> 
> <span dir="ltr"></span>

<span dir="ltr">From a VoltDB viewpoint our design choices were
discussed above. We implemented the following:</span>

<span dir="ltr"></span>

  - > <span dir="ltr">“FKMODE\_MULTI\_QUERY\_FIRST” does a global read
    > consistent query to find the row, and then follows up with a
    > single partition update.</span>

> <span dir="ltr"></span>

  - > <span dir="ltr">“FKMODE\_QUERY\_ALL\_PARTITIONS\_FIRST” asks each
    > partition independently to find the row, and then follows up with
    > a single partition update.</span>

> <span dir="ltr"></span>

  - > <span dir="ltr">“FKMODE\_TASK\_ALL\_PARTITIONS” cuts out the
    > middleman and asks each partition independently to try and update
    > the row. Given that there is only one row and while the Unique ID
    > is volatile it doesn’t get reused this will work for this specific
    > Use Case.</span>

<span dir="ltr"></span>

**<span dir="ltr">What our benchmark does</span>**

<span dir="ltr">Our code stats by creating the test data if needed. It
then runs our version of the benchmark for each implementation for 5
minutes. It starts at 2,000 TPS and then re-runs with 4,000, 6,000 , etc
until the server can no longer get \> 90% of requested transactions done
or we hit some other reasons for stopping. We then look at the previous
file and use the TPS it did as the result for that configuration. It
does this for each of the three methods we discuss above. We produce an
output log file which has detailed statistics for 1 of the 10
threads.</span>

## **<span dir="ltr">VoltDB results in AWS</span>**

### **<span dir="ltr">Configuration</span>**

<span dir="ltr">For testing purposes we ran all 3 options above on the
following configuration:</span>

> <span dir="ltr">• AWS</span>
> 
> <span dir="ltr">•</span> <span dir="ltr">[<span class="underline">
> z1d.2xlarge</span>](https://aws.amazon.com/ec2/instance-types/z1d/) -
> Intel Xeon, runs up to ‘up to 4.0 GHz’, 4 physical cores per server.
> Around US$0.25/hr.</span>
> 
> <span dir="ltr">• 3 nodes</span>
> 
> <span dir="ltr">• k=1 (1 spare copy)</span>
> 
> <span dir="ltr">• Snapshotting and command logging enabled.</span>

<span dir="ltr">Note that:</span>

> <span dir="ltr">• All our transactions are ACID transactions</span>
> 
> <span dir="ltr">• ‘k=1’ ensures that all transactions take place on 2
> nodes.</span>
> 
> <span dir="ltr">• ‘z1d.2xlarge’ has 8 vCPUs, or 4 real ones.</span>

## <span dir="ltr">Results</span>

<span dir="ltr">The best result was obtained was when we asked all the
partitions to do a read and then do an update once we’ve found out where
it is (FKMODE\_QUERY\_ALL\_PARTITIONS\_FIRST). We were able to sustain
66,635 TPS while aiming for 68K. At that level the hardware wasn’t maxed
out, but the 99th percentile latency for our tricky “Alternative Unique
Key” was still around 1ms. As we increased the workload it jumped to
over 10ms, which is the point at which it will not be acceptable to
customers.</span>

<span dir="ltr"></span>

<span dir="ltr">The next best alternative for the “Alternative Unique
Key” stuff is to ask all the partitions to try and do it. We were able
to get 27,518 TPS by the time latency reached 10ms. This does, however,
have the advantage of not having to worry about updates.</span>

<span dir="ltr"></span>

<span dir="ltr">The worst outcome was for using a multi partition query.
The additional overhead comes from making sure that all nodes issue the
query at the exact same moment in time.</span>

<span dir="ltr"></span>

<span dir="ltr">Note that at 66,635 TPS for basic read and write
operations latency is still 1ms or under at this point.</span>

<span dir="ltr"></span>

<span dir="ltr">In the graph below the X axis is the load we requested,
the thick blue line is how ahy TPS we’re doing (right axis). The
performance of individual operations is on the left hand scale in
milliseconds. Note that if for some reason you didn’t care about latency
you could get something more than 100K TPS.</span>

<span dir="ltr"></span>

<span dir="ltr"></span>

![](media/image4.png)<span dir="ltr"></span>

<span dir="ltr"></span>

<span dir="ltr">Bear in mind this is on a generic AWS configuration
without making any heroic attempts to maximize performance. Our goal is
to create something that people evaluating VoltDB can do for
themselves.This is not - and does not claim to be - the ‘most’ VoltDB
can get on this benchmark or on AWS.</span>

## <span dir="ltr">How to run this yourself</span>

<span dir="ltr"></span>

1.  > <span dir="ltr">Download and install VoltDB</span>

<span dir="ltr"></span>

2.  > <span dir="ltr">All the code is in github
    > ([<span class="underline">https://github.com/srmadscience/voltdb-tatpbenchmark</span>](https://github.com/srmadscience/voltdb-tatpbenchmark))
    > and is a maven project. So getting it is a case of:</span>

> <span dir="ltr"></span>
> 
> <span dir="ltr">git clone
> [<span class="underline">https://github.com/srmadscience/voltdb-tatpbenchmark</span>](https://github.com/srmadscience/voltdb-tatpbenchmark)</span>
> 
> <span dir="ltr">cd
> [<span class="underline">voltdb-tatpbenchmark</span>](https://github.com/srmadscience/voltdb-tatpbenchmark)</span>
> 
> <span dir="ltr"></span>

3.  > <span dir="ltr">The script runbenchmark does a build, and then
    > installs the schema and starts the benchmark:</span>

<span dir="ltr"></span>

> <span dir="ltr">sh scripts/runbenchmark.sh</span>
> 
> <span dir="ltr"></span>
> 
> <span dir="ltr">‘Runbenchmark.sh’ can be run with three
> parameters:</span>
> 
> <span dir="ltr"></span>

  - > <span dir="ltr">Hosts - comma delimited list of volt servers.
    > Defaults to the hostname.</span>

  - > <span dir="ltr">Testname - a word describing the purpose of the
    > test. Defaults to the hostname.</span>

  - > <span dir="ltr">Subscribers - How many subscribers should be in
    > your Home Location Registry. 1,000,000 subs takes about 600MB of
    > RAM. We ran our tests with 20,000,000.</span>

> <span dir="ltr"></span>
> 
> <span dir="ltr">So an example would be:</span>
> 
> <span dir="ltr"></span>
> 
> <span dir="ltr">sh scripts/runbenchmark.sh vdb1,vdb2,vdb3 mytest
> 20000000</span>
> 
> <span dir="ltr"></span>
> 
> <span dir="ltr"></span>
> 
> <span dir="ltr"></span>

<span dir="ltr"></span>

<span dir="ltr"></span>

<span dir="ltr"></span>

<span dir="ltr">DO NOT PUBLISH THIS BIT:</span>

<span dir="ltr"></span>

<span dir="ltr">Notes on sequence diagrams:</span>

<span dir="ltr"></span>

<span dir="ltr">Done with
[<span class="underline">https://sequencediagram.org/\#</span>](https://sequencediagram.org/#)</span>

<span dir="ltr"></span>

<span dir="ltr">Can be saved as SVG, but google docs doesn’t like
it.</span>

<span dir="ltr"></span>

<span dir="ltr">The text that created the diagrams is in comments next
to them.</span>
