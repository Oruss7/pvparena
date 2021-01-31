#!/bin/sh

rm -rf /e/Games/Minecraft/Server/serveur_dev_1164/plugins/pvparena-*-SNAPSHOT.jar
cp target/pvparena-*-SNAPSHOT.jar /e/Games/Minecraft/Server/serveur_dev_1164/plugins/
echo "deployed !"
sleep 1
echo "launching server..."

cd /e/Games/Minecraft/Server/serveur_dev_1164/
./start.sh
sleep 2