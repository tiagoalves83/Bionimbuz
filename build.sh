#!/bin/bash

export LANGUAGE="en"
export LANG="C"
export LC_MESSAGES="C"

ZOOKEEPER_VERSION="3.4.8"
ZOOKEEPER_HOME=/opt/zookeeper
SSH_PORT=22

usage()
{
  echo "USAGE:"
  echo "    $0 SSH_USER SSH_PASSWORD [SSH_PORT=22]"
  echo "    $0 ubuntu 123456 22"
  exit
}

if [[ "$1" -eq "" ]]; then
  usage
fi
if [[ "$2" -eq "" ]]; then
  usage
fi
if [[ "$3" -neq "" ]]; then
  SSH_PORT=$3
fi

SSH_USER=$1
SSH_PASS=$2

sudo add-apt-repository -y ppa:webupd8team/java
sudo apt-get -y update
sudo apt-get -y install oracle-java8-installer maven2 gcc dstat bowtie bedtools unzip mysql-server-5.6 unzip

wget -c "http://ftp.unicamp.br/pub/apache/zookeeper/stable/zookeeper-$ZOOKEEPER_VERSION.tar.gz"
tar xzvf zookeeper-$ZOOKEEPER_VERSION.tar.gz

sudo mv zookeeper-$ZOOKEEPER_VERSION $ZOOKEEPER_HOME
sudo cp $ZOOKEEPER_HOME/conf/zoo_sample.cfg $ZOOKEEPER_HOME/conf/zoo.cfg
sudo sed 's/\/tmp\/zookeeper/\/opt\/zookeeper/g' $ZOOKEEPER_HOME/conf/zoo.cfg

mkdir -p ~/.bionimbuz
> credentials.yml
echo "user: "'"'$SSH_USER'"' >> ~/.bionimbuz/credentials.yml
echo "password: "'"'$SSH_PASS'"' >> ~/.bionimbuz/credentials.yml
echo "ssh-port: "'"'$SSH_POST'"' >> ~/.bionimbuz/credentials.yml 

