#!/bin/bash

. mine.config

if [ -f $LOG_NAME ]; then
    rm $LOG_NAME
fi

if [ -f $REPORT_NAME ]; then
    rm $REPORT_NAME
fi

# get a list of all dirs we need to process (all the experiments)
EXPDIRLIST=$(find . -maxdepth 1 -type d -name "$EXP_DIRPRE" -prune | sort )

echo -e "Will process:\n$EXPDIRLIST" | tee -a $LOG_NAME 

# for each iteration append to report file:
# 1) validate that app start times are > than first event - report if that is not the case
#    print the time differences anyway
# 2) grep -R from all nodes information about
#    - how many interests to satisfy / total interests
#    - time taken to satisfy successful interest
#    - total time to get result
#####  Format:   
# experiment#, iteration#, # of nodes, # of interests sent, time for success interest(ms) , total time to succeed (ms)
# example: 1, 1, 2, 1, 1, 512, 512
NUMNODES=-1
NUMINTERESTS=-1
SUCCESSTIME=-1
TOTALTIME=-1

echo -e "Time\tExperiment#\t# of nodes:\tIteration#\t# of Interests Sent\tTime for success interest (ms)\tTotal time to succeed (ms)" | tee -a $REPORT_NAME

# go through all iterations for each experiment
for x in $EXPDIRLIST; do
    ITERDIRLIST=$(find $x -maxdepth 1 -mindepth 1 -type d  | sort --version-sort)
    echo -e "Iterations:\n$ITERDIRLIST" | tee -a $LOG_NAME

    NUMINTERESTSARRAY=()
    SUCCESSTIMEARRAY=()
    TOTALTIMEARRAY=()
    for y in $ITERDIRLIST; do
	echo -e "Getting data from iteration $y"
        # TODO: check timestamps to make sure data is valid
        # Get the data we need
	NUMINTERESTS=$(egrep -re "<message>Number of Interests to fulfill request: .*</message>" $y | sed -n "s/.*: \(.*\)<.*/\1/p")
	echo -e "Number of interests sent: $NUMINTERESTS" | tee -a $LOG_NAME
	SUCCESSTIME=$(egrep -re "<message>Time to fulfill request for successful Interest: .* milliseconds.</message>" $y | sed -n "s/.*: \(.*\) milliseconds.*/\1/p")
	echo -e "Time taken for successful interest: $SUCCESSTIME ms" | tee -a $LOG_NAME
	TOTALTIME=$(egrep -re "<message>Time to fulfill request from first Interest: .* milliseconds.</message>" $y | sed -n "s/.*: \(.*\) milliseconds.*/\1/p")
	echo -e "Time to fulfill request from first Interest: $TOTALTIME ms" | tee -a $LOG_NAME
	TIME=$(echo $x | sed -n "s/.*-\(.*\)/\1/p")
	TIME=$(date -d @$TIME)
	EXPNAME=$(echo $x | sed -n "s/.*experiment\(.*\)-.*/\1/p")
	ITERNUM=$(echo $y | sed -n "s/.*\/\(.*\)/\1/p")
	NUMNODES=$(find $y -type d -name "node-*" | wc -l)
	echo -e "$TIME,$EXPNAME,$NUMNODES: $ITERNUM,$NUMINTERESTS,$SUCCESSTIME,$TOTALTIME" | tee -a $REPORT_NAME

	NUMINTERESTSARRAY+=($NUMINTERESTS)
	SUCCESSTIMEARRAY+=($SUCCESSTIME)
	TOTALTIMEARRAY+=($TOTALTIME)
    done
    
    export EXPNAME
    # Calculate averages for each experiment, min, max
    # echo -e "NUMINTERESTARRAY:${NUMINTERESTSARRAY[@]}"
    echo "${NUMINTERESTSARRAY[@]}" | awk ' BEGIN {FS=" "} { count=NF;max=min=$1;sum=0; for (i=1; i<=NF; i++) { if ($i>max) max=$i; if ($i<min) min=$i; sum+=$i} }  END { print ENVIRON["EXPNAME"]":NUMINTEREST SUMMARY(interests): Minimum=" min ", Maximum="max ", Average="sum/count } ' | tee -a $REPORT_NAME
    # echo -e "SUCCESSTIMEARRAY:${SUCCESSTIMEARRAY[@]}"
    echo "${SUCCESSTIMEARRAY[@]}" | awk ' BEGIN {FS=" "} { count=NF;max=min=$1;sum=0; for (i=1; i<=NF; i++) { if ($i>max) max=$i; if ($i<min) min=$i; sum+=$i} }  END { print ENVIRON["EXPNAME"]":SUCCESSTIME SUMMARY(ms): Minimum=" min ", Maximum="max ", Average="sum/count } ' | tee -a $REPORT_NAME
    # echo -e "TOTALTIMEARRAY:${TOTALTIMEARRAY[@]}"
    echo "${TOTALTIMEARRAY[@]}" | awk ' BEGIN {FS=" "} { count=NF;max=min=$1;sum=0; for (i=1; i<=NF; i++) { if ($i>max) max=$i; if ($i<min) min=$i; sum+=$i} }  END { print ENVIRON["EXPNAME"]":TOTALTIME SUMMARY(ms): Minimum=" min ", Maximum="max ", Average="sum/count } ' | tee -a $REPORT_NAME
done
