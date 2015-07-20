@echo off
set CLASSPATH=.;dist\*
java -Dodinms.recvops=recvops.properties -Dodinms.sendops=sendops.properties -Dodinms.wzpath=wz\ -Dodinms.login.config=login.properties -Djavax.net.ssl.keyStore=filename.keystore -Djavax.net.ssl.keyStorePassword=passwd -Djavax.net.ssl.trustStore=filename.keystore -Djavax.net.ssl.trustStorePassword=passwd odinms.net.login.LoginServer
pause