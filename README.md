
# ZAPROXY

## Ant -> Maven migration

This code is based on subversion rev-6124 from https://code.google.com/p/zaproxy/

## ToDo List

* ~~create Maven directory structure~~ Done.
* ~~make it compile in Maven~~ Done.
* ~~make tests run in Maven~~ Done. (except 2 out of 308 tests are failing, but this seems to be a real issue, needs further investigation)
* ~~make ZAP run from inside an IDE~~ Done.
* make Maven build javadocs 
* migrate the build/build.xml
   * Windows.exe
   * Mac_OS_X.dmg
   * Linux.tar.gz
   * Core.tar.gz
   * Sources.zip (new)
* migrate the build/build-api.xml
   * nodejs
   * php
   * python
   * java
* migrate the build/build-debian.xml
* migrate Docker build
* migrate wave
* migrate wavesep

### Requirements

* Java 1.7+
* Maven 3.2+

### Compile

```
$> mvn compile
```

### Run unit tests

```
$> mvn tests
```

## Work with IntelliJ IDEA

1. import as Maven based project
2. run or debug ZAP
   1. main class is "org.zaproxy.zap.ZAP"
   2. working directory is "src/main/scripts" (which have to be full qualified in your run config)
3. Thats it! :-)