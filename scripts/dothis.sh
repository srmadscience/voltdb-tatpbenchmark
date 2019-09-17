#!/bin/sh -x 

for i in  0 1 3 2 ; do java -jar tatp_2019_client.jar macbook_2.5_GHz_i7 localhost 10000 5000 10000000 $i 3 10; done
