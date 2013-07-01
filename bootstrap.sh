#!/usr/bin/env bash

apt-get update
apt-get install -y openjdk-7-jdk
apt-get install -y tomcat7
apt-get install -y wget

wget -O /usr/local/bin/lein "https://raw.github.com/technomancy/leiningen/stable/bin/lein"
chmod a+x /usr/local/bin/lein

# cd /vagrant

cp solr/solr-web.xml /etc/tomcat7/Catalina/localhost
cp -r solr /opt
mkdir /var/lib/tomcat7/lib
mv /opt/solr/lib/* /var/lib/tomcat7/lib
mv /opt/solr/setenv.sh /usr/share/tomcat7/bin
rmdir /opt/solr/lib
mkdir /opt/solr/data
mkdir /opt/solr/data/crmds1
mkdir /opt/solr/data/crmds2
chown -R tomcat7:tomcat7 /opt/solr
service tomcat7 restart

# lein deps
# nohup lein repl :headless &
