@echo off
set CLASSPATH=.;dist\*
java -Xms128m -Xmx256m -Dodinms.recvops=recvops.properties -Dodinms.sendops=sendops.properties -Dodinms.wzpath=wz\ -Dodinms.channel.config=channel.properties -Djavax.net.ssl.keyStore=filename.keystore -Djavax.net.ssl.keyStorePassword=passwd -Djavax.net.ssl.trustStore=filename.keystore -Djavax.net.ssl.trustStorePassword=passwd odinms.net.channel.ChannelServer -Dcom.sun.management.jmxremote.port=13373 -Dcom.sun.management.jmxremote.password.file=jmxremote.password -Dcom.sun.management.jmxremote.access.file=jmxremote.access
