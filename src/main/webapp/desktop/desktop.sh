#!/bin/bash

DEFAULT_URL=http://localhost:8080

if [ "$1" == "-?" ]; then
   echo "Usage:"
   echo "   desktop.sh [<URL>] [-?]"
   echo ""
   echo "   Starts Pado Desktop via JNLP. A valid URL that has pado-web installed"
   echo "   is required."
   echo ""
   echo "      <URL>  Pado-web URL in the form of http(s)://<host>:<port>." 
   echo "             If URL is not specified it defaults to $DEFAULT_URL."
   echo ""
   echo "   Default: ./desktop.sh $DEFAULT_URL"
   echo ""
   exit
fi

export JAVAWS_TRACE_NATIVE=1
export JAVAWS_VM_ARGS="-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,address=8989,server=y,suspend=n"
if [ "$1" == "" ]; then
   URL=$DEFAULT_URL
else
   URL=$1
fi
URL=$URL/pado-web/desktop/pado-desktop.jnlp

javaws $URL
#javaws -clearcache -viewer -wait $URL
