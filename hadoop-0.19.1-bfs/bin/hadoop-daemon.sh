#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# Runs a Hadoop command as a daemon.
#
# Environment Variables
#
#   HADOOP_CONF_DIR  Alternate conf dir. Default is ${HADOOP_HOME}/conf.
#   HADOOP_LOG_DIR   Where log files are stored.  PWD by default.
#   HADOOP_MASTER    host:path where hadoop code should be rsync'd from
#   HADOOP_PID_DIR   The pid files are stored. /tmp by default.
#   HADOOP_IDENT_STRING   A string representing this instance of hadoop. $USER by default
#   HADOOP_NICENESS The scheduling priority for daemons. Defaults to 0.
##

usage="Usage: hadoop-daemon.sh [--config <conf-dir>] [--hosts hostlistfile] (start|stop) <hadoop-command> <args...>"

# if no args specified, show usage
if [ $# -le 1 ]; then
  echo $usage
  exit 1
fi

bin=`dirname "$0"`
bin=`cd "$bin"; pwd`

. "$bin"/hadoop-config.sh

# get arguments
startStop=$1
shift
command=$1
shift

hadoop_rotate_log ()
{
    log=$1;
    num=5;
    if [ -n "$2" ]; then
	num=$2
    fi
    if [ -f "$log" ]; then # rotate logs
	while [ $num -gt 1 ]; do
	    prev=`expr $num - 1`
	    [ -f "$log.$prev" ] && mv "$log.$prev" "$log.$num"
	    num=$prev
	done
	mv "$log" "$log.$num";
    fi
}

if [ -f "${HADOOP_CONF_DIR}/hadoop-env.sh" ]; then
  . "${HADOOP_CONF_DIR}/hadoop-env.sh"
fi

# get log directory
if [ "$HADOOP_LOG_DIR" = "" ]; then
  export HADOOP_LOG_DIR="$HADOOP_HOME/logs"
fi
mkdir -p "$HADOOP_LOG_DIR"

if [ "$HADOOP_PID_DIR" = "" ]; then
  HADOOP_PID_DIR=/tmp
fi

if [ "$HADOOP_IDENT_STRING" = "" ]; then
  export HADOOP_IDENT_STRING="$USER"
fi

# some variables
export HADOOP_LOGFILE=hadoop-$HADOOP_IDENT_STRING-$command-$HOSTNAME.log
export HADOOP_ROOT_LOGGER="INFO,DRFA"
log=$HADOOP_LOG_DIR/hadoop-$HADOOP_IDENT_STRING-$command-$HOSTNAME.out
pid=$HADOOP_PID_DIR/hadoop-$HADOOP_IDENT_STRING-$command.pid

# Set default scheduling priority
if [ "$HADOOP_NICENESS" = "" ]; then
    export HADOOP_NICENESS=0
fi

case $startStop in

  (start)

    mkdir -p "$HADOOP_PID_DIR"

    if [ -f $pid ]; then
      if kill -0 `cat $pid` > /dev/null 2>&1; then
        echo $command running as process `cat $pid`.  Stop it first.
        exit 1
      fi
    fi

    if [ "$HADOOP_MASTER" != "" ]; then
      echo rsync from $HADOOP_MASTER
      rsync -a -e ssh --delete --exclude=.svn --exclude='logs/*' --exclude='contrib/hod/logs/*' $HADOOP_MASTER/ "$HADOOP_HOME"
    fi

    hadoop_rotate_log $log
    echo starting $command, logging to $log
    cd "$HADOOP_HOME"
    if [[ ("$command" = "bfsmaster") || ("$command" = "bfsdatanode") ]]; then
      # CLASSPATH initially contains $HADOOP_CONF_DIR
      CLASSPATH="${HADOOP_CONF_DIR}"
      CLASSPATH=${CLASSPATH}:$JAVA_HOME/lib/tools.jar

      # for developers, add Hadoop classes to CLASSPATH
      if [ -d "$HADOOP_HOME/build/classes" ]; then
        CLASSPATH=${CLASSPATH}:$HADOOP_HOME/build/classes
      fi
      if [ -d "$HADOOP_HOME/build/webapps" ]; then
        CLASSPATH=${CLASSPATH}:$HADOOP_HOME/build
      fi
      if [ -d "$HADOOP_HOME/build/test/classes" ]; then
        CLASSPATH=${CLASSPATH}:$HADOOP_HOME/build/test/classes
      fi

      # so that filenames w/ spaces are handled correctly in loops below
      IFS=

      # for releases, add core hadoop jar & webapps to CLASSPATH
      if [ -d "$HADOOP_HOME/webapps" ]; then
        CLASSPATH=${CLASSPATH}:$HADOOP_HOME
      fi
      for f in $HADOOP_HOME/hadoop-*-core.jar; do
        CLASSPATH=${CLASSPATH}:$f;
      done

      # add libs to CLASSPATH
      for f in $HADOOP_HOME/lib/*.jar; do
        CLASSPATH=${CLASSPATH}:$f;
      done

      for f in $HADOOP_HOME/lib/jetty-ext/*.jar; do
        CLASSPATH=${CLASSPATH}:$f;
      done

      # add user-specified CLASSPATH last
      if [ "$HADOOP_CLASSPATH" != "" ]; then
        CLASSPATH=${CLASSPATH}:${HADOOP_CLASSPATH}
      fi

      # XXX: hacky
      export MASTERFILE=$HADOOP_CONF_DIR/masters
      export SLAVEFILE=$HADOOP_CONF_DIR/slaves

      # Use 1GB for the maximum Java heap size
      BFS_JAVA_OPTS="-Xmx1000m"

      if [ "$command" = "bfsdatanode" ]; then
        BFS_DATA_DIR=/tmp/bfs_data
        nohup $JAVA $BFS_JAVA_OPTS -cp "$CLASSPATH" bfs.DataNode $BFS_DATA_DIR > "$log" 2>&1 < /dev/null &
      else
        export JOL_DIR=/root/jol
        export STASIS_DIR=/root/stasis
        export JAVA_DIR=/usr/lib/jvm/java-6-sun
        export LD_LIBRARY_PATH=/root/stasis/build/src/stasis
        nohup $JAVA -cp "$CLASSPATH" $BFS_JAVA_OPTS -Djava.library.path=/root/jol/ant-build/stasis/jni bfs.Master > "$log" 2>&1 < /dev/null &
      fi
    else
      nohup nice -n $HADOOP_NICENESS "$HADOOP_HOME"/bin/hadoop --config $HADOOP_CONF_DIR $command "$@" > "$log" 2>&1 < /dev/null &
    fi
    echo $! > $pid
    sleep 1; head "$log"
    ;;
          
  (stop)

    if [ -f $pid ]; then
      if kill -0 `cat $pid` > /dev/null 2>&1; then
        echo stopping $command
        kill `cat $pid`
      else
        echo no $command to stop
      fi
    else
      echo no $command to stop
    fi
    ;;

  (*)
    echo $usage
    exit 1
    ;;

esac


