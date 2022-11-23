#!/bin/sh

JAVA_HOME=/usr/bin
BASEPATH=/app/xtorm/test/COM_SimpleImportSam_20221102
CLASSPATH=$BASEPATH/lib/ojdbc8.jar
CLASSPATH=$CLASSPATH:$BASEPATH/lib/logback-core-1.3.4.jar
CLASSPATH=$CLASSPATH:$BASEPATH/lib/logback-classic-1.3.4.jar
CLASSPATH=$CLASSPATH:$BASEPATH/lib/slf4j-api-2.0.1.jar
CLASSPATH=$CLASSPATH:$BASEPATH/bin

$JAVA_HOME/javac -encoding utf-8 -cp $CLASSPATH -d $BASEPATH/bin `find $BASEPATH/src -type f -name *.java`
