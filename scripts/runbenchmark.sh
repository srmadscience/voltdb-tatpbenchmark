#!/bin/sh -x 

if
	[ ! -r target/voltdb-tatpbenchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar ]
then
    mvn clean compile assembly:single
fi

HNAME=`uname -n`
SUBS=5000000
MINS=5
THREADS=10

for i in  0 1 2
do
	java -Dlog4j.configurationFile=src/main/java/org/voltdb/tatp/client/log4j2-test.xml -jar target/voltdb-tatpbenchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar $HNAME localhost 2000 2000 $SUBS $i $MINS $THREADS
done
