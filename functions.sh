#!/bin/bash
function startup {
    source service.env
    [ ! -d logs ] && mkdir logs
    if [ $1 == "testcenter" ]; then
        nohup java -jar tc.jar $1 >/dev/null 2>>logs/tc.log &
    elif [ $1 == "canaly" ]; then
        nohup java -jar code-analyser.jar $1 >/dev/null 2>>logs/canaly.log &
    else
        java -jar tc.jar $1
    fi
}

function killProcess {
    pid=$(ps -ef |grep "$1" |grep -v grep |awk "{print \$2}")
    all=(${pid//\s+/ })
    for id in ${all[@]}
    do
        if [ "$id" -gt 0 ] 2>/dev/null ;then
            echo "kill $id"
            if [ `uname -s` != Darwin ]; then
                pstree -p $id| awk -F"[()]" "{system(\"kill \"\$2)}"
            else
                pstree -g3 -l2 -U $id| awk "{system(\"kill \"\$2)}"
            fi
        else
            echo "ignore $id"
        fi
    done
}
