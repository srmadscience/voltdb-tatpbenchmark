#!/bin/sh -x 
. $HOME/.profile


BASEDIR=$(dirname "$0")
cd $BASEDIR/..

if
        [ ! -r target/voltdb-tatpbenchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar ]
then
    which mvn

    if 
	[ "$?" = "1" ]
    then
	echo "Maven needed. Try:"
	echo "sudo apt install maven"
	exit 1
    fi 

    mvn clean compile assembly:single
fi

TNAME=`uname -n`
HNAME=`cat $HOME/.vdbhostnames`
STARTTPS=2000
INCTPS=2000
SUBS=5000000
MINS=5
THREADS=10


if 
	[ "$1" != "" ]
then
	HNAME=$1
fi

if 
	[ "$2" != "" ]
then
	TNAME=$2
fi

if 
	[ "$3" != "" ]
then
	SUBS=$3
fi

if
	[ ! -d results ]
then
	mkdir results
fi

# We test five ways of solving the 'foreign key' problem
for i in  4 3 2 0 1 
do
        java  ${JVMOPTS} -Dlog4j.configurationFile=src/main/java/org/voltdb/tatp/client/log4j2-test.xml -jar target/voltdb-tatpbenchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar  \
                ${TNAME} ${HNAME} ${STARTTPS} ${INCTPS} ${SUBS} ${i} ${MINS} ${THREADS} |  \
                tee -a  results/${TNAME}_${HNAME}_${STARTTPS}_${INCTPS}_${SUBS}_${i}_${MINS}_${THREADS}.log

done

