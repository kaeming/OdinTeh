#!/bin/sh
export CLASSPATH=".:dist/odinms.jar:mina-core.jar:slf4j-api.jar:slf4j-jdk14.jar:mysql-connector-java-bin.jar"
java -Dnet.sf.odinms.recvops=recvops.properties \
-Dnet.sf.odinms.sendops=sendops.properties \
-Dnet.sf.odinms.wzpath=wz \
-Dnet.sf.odinms.channel.config=channel.properties \
-Djavax.net.ssl.keyStore=filename.keystore \
-Djavax.net.ssl.keyStorePassword=passwd \
-Djavax.net.ssl.trustStore=filename.keystore \
-Djavax.net.ssl.trustStorePassword=passwd \
net.sf.odinms.net.channel.ChannelServer