#!/bin/sh
NSE_SERVICE_KEY=$(grep nse.key /etc/openbaton/openbaton-nse.properties|cut -d'=' -f 2)

if [ -z "$NSE_SERVICE_KEY" ];then
    until curl -sSf http://nfvo:8080;do sleep 10;done

    USER=admin
    PASS=openbaton
    NFVO_IP=nfvo
    NFVO_PORT=8080
    PID=$(openbaton -pid none -u "$USER" -p "$PASS" -ip "$NFVO_IP" --nfvo-port "$NFVO_PORT" project list|grep default|awk '{print $2}')
    SERVICE_KEY=$(openbaton -pid "$PID" -u "$USER" -p "$PASS" -ip "$NFVO_IP" --nfvo-port "$NFVO_PORT" service create '{"name":"nse", "roles":["*"]}')

    export NSE_SERVICE_KEY="$SERVICE_KEY"
    sed -i "s/nse.key =/nse.key=$SERVICE_KEY/g" /etc/openbaton/openbaton-nse.properties
fi

exec java -jar /nse.jar --spring.config.location=file:/etc/openbaton/openbaton-nse.properties
