#!/bin/sh

TST=$1

CT=2000

FNAME=../results/stats_${TST}.csv

echo -n CPU_Requested,CPU_Got, > $FNAME

for MODE in ALL_PARTITIONS_FIRST MULTI_QUERY_FIRST ALL_PARTITIONS_TASKED
do
	for FD in GET_SUBSCRIBER_DATA_WALL_MILLIS GET_NEW_DESTINATION_WALL_MILLIS GET_ACCESS_DATA_WALL_MILLIS UPDATE_SUBSCRIBER_DATA_WALL_MILLIS UPDATE_LOCATION_2_WALL_MILLIS INSERT_CALL_FORWARDING_2_WALL_MILLIS DELETE_CALL_FORWARDING_2_WALL_MILLIS
	do


		echo -n ${MODE}_${FD}, >> $FNAME
	done
done

echo "" >> $FNAME

while
	[ "$CT" -lt "126000" ]
do
	echo $CT


	LEAD_MODE=ALL_PARTITIONS_FIRST

	TFILE=../results/${TST}*_${CT}_*${LEAD_MODE}*.dat

	if
		[ -r $TFILE ]
	then

		# Get TPS by adding each threads numbers
		TPSLIST=`cat $TFILE | tail -20 | grep -v Extra | awk -FS '{ print $3 }' | sed '1,$s/=//'`
		TPS=0
		for t in $TPSLIST
		do
			TPS=`expr $TPS + $t`
		done

		echo -n $CT,$TPS, >>  $FNAME	

		# Get other numbers for ALL_PARTITIONS_FIRST
		for FD in GET_SUBSCRIBER_DATA_WALL_MILLIS GET_NEW_DESTINATION_WALL_MILLIS GET_ACCESS_DATA_WALL_MILLIS UPDATE_SUBSCRIBER_DATA_WALL_MILLIS UPDATE_LOCATION_2_WALL_MILLIS INSERT_CALL_FORWARDING_2_WALL_MILLIS DELETE_CALL_FORWARDING_2_WALL_MILLIS
		do

			VAL=`grep $FD $TFILE | awk '{ print $13 }' | sed '1,$s/,//'`
			echo -n $VAL, >>  $FNAME

		done

		# See if other matching files exist


		for OF in MULTI_QUERY_FIRST ALL_PARTITIONS_TASKED
		do

			for FD in GET_SUBSCRIBER_DATA_WALL_MILLIS GET_NEW_DESTINATION_WALL_MILLIS GET_ACCESS_DATA_WALL_MILLIS UPDATE_SUBSCRIBER_DATA_WALL_MILLIS UPDATE_LOCATION_2_WALL_MILLIS INSERT_CALL_FORWARDING_2_WALL_MILLIS DELETE_CALL_FORWARDING_2_WALL_MILLIS
			do

				OTHERFILE=`echo $TFILE | sed '1,$s/ALL_PARTITIONS_FIRST/'$OF'/'`
				VAL=""

				if
					[ -r "$OTHERFILE" ]
				then
					VAL=`grep $FD $TFILE | awk '{ print $13 }' | sed '1,$s/,//'`
				else
					VAL=""	
				fi
	

				echo -n $VAL, >> $FNAME
						
	
			done


		done
	
		echo "" >> $FNAME
	fi


	CT=`expr $CT + 2000`
done
