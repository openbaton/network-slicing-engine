#!/bin/bash

source ./gradle.properties

_version=${version}

_project_base="/opt/openbaton/network-slicing-engine"
_process_name="network-slicing-engine"
_screen_name="openbaton"
_config_file="/etc/openbaton/network-slicing-engine.properties"

function checkBinary {
  if command -v $1 >/dev/null 2>&1; then
     return 0
   else
     echo >&2 "FAILED."
     return 1
   fi
}

_ex='sh -c'
if [ "$_user" != 'root' ]; then
    if checkBinary sudo; then
        _ex='sudo -E sh -c'
    elif checkBinary su; then
        _ex='su -c'
    fi
fi


function check_already_running {
    pgrep -f ${_process_name}-${_version}.jar
    if [ "$?" -eq "0" ]; then
        echo "${_process_name} is already running.."
        exit;
    fi
}

function start_checks {
    check_already_running
    if [ ! -d build/  ]
        then
            compile
    fi
}

function init {
    if [ ! -f $_config_file ]; then
        if [ $EUID != 0 ]; then
            echo "creating the directory and copying the file"
            sudo -E sh -c "mkdir /etc/openbaton; cp ${_project_base}/src/main/resources/application.properties ${_config_file}"
            #echo "copying the file, insert the administrator password" | sudo -kS cp ${_nubomedia_paas_base}/src/main/resources/paas.properties ${_nubomedia_config_file}
        else
            echo "creating the directory"
            mkdir /etc/openbaton
            echo "copying the file"
            cp ${_project_base}/src/main/resources/application.properties ${_config_file}
        fi
    else
        echo "Properties file already exist"
    fi
}

function start {
    start_checks
    screen_exists=$(screen -ls | grep ${_screen_name} | wc -l);
    if [ "${screen_exists}" -eq 0 ]; then
        screen -c screenrc -d -m -S ${_screen_name} -t ${_process_name} java -jar "$_project_base/build/libs/$_process_name-$_version.jar" --spring.config.location=file:${_config_file}
    else
        screen -S $_screen_name -p 0 -X screen -t $_process_name java -jar "$_project_base/build/libs/$_process_name-$_version.jar" --spring.config.location=file:${_config_file}
    fi
}

function start_fg {
    start_checks
    java -jar "build/libs/${_process_name}-$_version.jar" --spring.config.location=file:${_config_file}
}


#function stop {
#    if screen -list | grep ${_screen_name}; then
#	    screen -S ${_screen_name} -p 0 -X stuff "exit$(printf \\r)"
#    fi
#}

function restart {
    kill
    sleep 2
    start
}


function kill {
    pkill -f ${_process_name}-${_version}.jar
}


function compile {
    ./gradlew build -x test 
}

function tests {
    ./gradlew test
}

function clean {
    ./gradlew clean
}

function end {
    exit
}
function usage {
    echo -e "network-slicing-engine\n"
    echo -e "Usage:\n\t ./network-slicing-engine.sh [compile|init|start|start_fg|test|kill|clean]"
}

##
#   MAIN
##

if [ $# -eq 0 ]
   then
        usage
        exit 1
fi

declare -a cmds=($@)
for (( i = 0; i <  ${#cmds[*]}; ++ i ))
do
    case ${cmds[$i]} in
        "clean" )
            clean ;;
        "sc" )
            clean
            compile
            start ;;
        "start" )
            start ;;
        "start_fg" )
            start_fg ;;
        #"stop" )
        #    stop ;;
        "restart" )
            restart ;;
        "compile" )
            compile ;;
        "init" )
            init ;;
        "kill" )
            kill ;;
        "test" )
            tests ;;
        * )
            usage
            end ;;
    esac
    if [[ $? -ne 0 ]]; 
    then
	    exit 1
    fi
done

