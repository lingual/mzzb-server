#!/usr/bin/env bash

pid=$(ps -x | grep 'target.name=mzzb-server' | grep -v grep | awk '{print $1}')
kill -9 ${pid}
echo 'Stop Mzzb Server'