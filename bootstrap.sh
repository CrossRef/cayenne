#!/usr/bin/env bash

apt-get update
apt-get install -y openjdk-7-jdk
apt-get install -y mongodb
apt-get install -y wget

wget -O /usr/local/bin/lein "https://raw.github.com/technomancy/leiningen/stable/bin/lein" 
chmod a+x /usr/local/bin/lein

cd /vagrant
lein deps
nohup lein repl :headless &
