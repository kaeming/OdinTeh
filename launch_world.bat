@echo off
set CLASSPATH=.;dist\*
java -Dodinms.recvops=recvops.properties -Dodinms.sendops=sendops.properties -Dodinms.wzpath=wz\ -Djavax.net.ssl.keyStore=filename.keystore -Djavax.net.ssl.keyStorePassword=passwd -Djavax.net.ssl.trustStore=filename.keystore -Djavax.net.ssl.trustStorePassword=passwd odinms.net.world.WorldServer
pause
