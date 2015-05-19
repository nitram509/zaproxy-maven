
# ZAPROXY

## Ant -> Maven migration

This code is based on subversion rev-6124 from https://code.google.com/p/zaproxy/

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