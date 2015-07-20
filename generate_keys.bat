@echo off
set CLASSPATH=.;dist\*
java -Dodinms.wzpath=wz\ odinms.tools.ext.wz.KeyGenerator
pause