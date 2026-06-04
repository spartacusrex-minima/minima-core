# minima-core
Minima full node application 

This is an implementation of a Minima node running on the Minima network

It does not include the MDS system or Maxima - JUST Minima.. Clean.

Run from the command line with 

```
java -jar minima.jar
```

You can find all the cli params with

```
java -jar minima.jar -help
```

The main ones being ( these are the default values )

```
java -jar minima.jar -port 9001 -data ~/.minima
```

You will need to add a valid Minima peer to get started.. none are added by default

You can do this by running a command in Minima once it starts

```
peers action:addpeers peerslist:spartacusrex.com:9001
```

Or you can specify that from the cli.. 

```
java -jar minima.jar -port 9001 -data ~/.minima -p2pnodes spartacusrex.com:9001
```

You can also provide a file with a list of nodes.. (preferred option)

```
java -jar minima.jar -port 9001 -data ~/.minima -p2pnodes https://spartacusrex.com/minimapeers.txt
```

Once Minima has started use help to get a full list of functions available to you.. 

```
help
```

And to breakdown how to use a function

```
help command:send
```

### Build

You can build the project with

```
./gardlew clean build
```

Or use the helper script

```
./buildjars.sh
```

Which will build the jars and copy them to the jar folder


