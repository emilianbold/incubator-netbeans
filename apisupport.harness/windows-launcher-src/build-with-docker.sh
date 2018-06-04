#!/bin/sh

mkdir -p o.n.bootstrap/launcher/ && cp -r `pwd`/../../o.n.bootstrap/launcher/windows/ o.n.bootstrap/launcher/windows

mkdir -p ide/launcher/windows && cp -r `pwd`/../../ide/launcher/windows ide/launcher/

docker build . -t netbeans-windows-launcher

docker run -d netbeans-windows-launcher
CONTAINER=$(docker ps -alq)
docker cp $CONTAINER:/windows/app64.exe .
docker cp $CONTAINER:/windows/app.exe .
docker stop $CONTAINER
