. ./env_setup

java -cp $CLASSPATH:thoth.jar -Dorg.ccnx.ccn.LogDir=. thoth.DTNode -R $1 $2
