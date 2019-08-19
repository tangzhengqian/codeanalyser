#!/bin/bash

[ ! -f service.env ] && cp service.env.template service.env

source functions.sh

function usage {
    echo "usage:"
    echo "  ./run_code_analysis.sh server"
    echo "  ./run_code_analysis.sh quit"
}

if [ $1 ] ; then
    if [ "$1" == "quit" ]; then
        echo "stopping keywords analysis server ..."
        killProcess "code-analyser.jar"
    elif [ "$1" == "server" ]; then
        echo "starting keywords analysis server ..."
        killProcess "code-analyser.jar"
        sleep 5
        startup "canaly"
    else
        echo "unknown argument '$1'"
        usage
    fi
else
    usage
fi

echo "done"
