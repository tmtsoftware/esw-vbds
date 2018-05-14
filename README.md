# VIZ Bulk Data System (VBDS)

This project contains a prototype of the VIZ Bulk Data System (VBDS).
The VBDS allows applications to publish images or other data to named streams and other applications to subscribe to those streams.
It is implemented as a cluster of HTTP servers that share subscriber information and transfer data to subscribers via websockets.
Applications use an HTTP/REST API to send commands to one of the servers in the cluster. 
If a data file is published to one remote server and there are multiple subscribers attached to another server, the published files are each
only transfered once to the other server, and then from there via websocket to each subscriber.

The main application is an HTTP server called `vbds-server`:

```
vbds-server 0.0.1
Usage: vbds-server [options]

  -n, --name <name>        The name of this server(default: vbds)
  --http-host <hostname>   The HTTP server host name (default: 127.0.0.1)
  --http-port <number>     The HTTP server port number (default: 0)
  --akka-host <hostname>   The Akka system host name (default: 127.0.0.1)
  --akka-port <number>     The Akka system port number (default: 0)
  -s, --seeds <host>:<port>,<host>:<port>,...
                           Optional list of cluster seeds in the form host:port,host:port,...
  --help                   
  --version                
```

A `vdbs-client` application is provided, for convenience and for use in tests, but any HTTP client will also work, as long as
it can handle the websocket responses for subscribers.
 
The `vdbs-client` also demonstrates how you can access the vdbs-server from Scala or Java code using akka-http, streams and actors.

Note that the data transfered via websocket to a subscriber is *chunked*: Multiple websocket messages are sent and the terminating 
message for each file contains a single newline "\n" byte, which does not belong to the data file. 

```
vbds-client 0.0.1
Usage: vbds-client [options]

  -n, --name <name>        The name of the vbds-server server(default: vbds)
  -h, --host <host name>   The VBDS HTTP server host name (default: 127.0.0.1)
  -p, --port <number>      The VDBS HTTP server port number (default: 80)
  --create <stream name>   Creates a new VBDS stream with the given name
  --delete <stream name>   Deletes the VBDS stream with the given name
  -l, --list               List the available streams
  --subscribe <stream name>
                           Subscribes to the given VBDS stream (see --action option)
  --dir <path>             Directory to hold received data files (default: current directory)
  -a, --action <shell-command>
                           A shell command to execute when a new file is received (args: stream-name file-name)
  --publish <stream-name>  Publish to the given stream (see --data option)
  --delay <duration>       Delay between publishing files in a directory (see --data)
  --data <file-name>       Specifies the file (or directory full of files) to publish
  --chunk-size <num-bytes>
                           Optional chunk size (to tune file transfer performance)
  --help                   
  --version                
```

## VBDS HTTP/REST API

| Description                   | Verb      | URI                               | Response |
| ------------- | ------------- |    -------|---------------------------------- |--------- |          
| List all streams              | GET       | /vbds/admin/streams              | OK (200) – Stream names in JSON; empty document if no streams
| Create a stream               | POST      | /vbds/admin/streams/{streamName} | OK (200) – New stream name in JSON; Conflict (409) if stream exists
| Delete a stream               | DELETE    | /vbds/admin/streams/{streamName} | OK (200) – Deleted stream name in JSON; NotFound (404) if stream does not exist
| Publish an image to a stream  | POST      | /vbds/transfer/streams/{streamName}/image | Accepted (204) – (no content); Bad Request (400) – for non-existent stream
| Create a subscription         | POST      | /vbds/access/streams/{streamName} | SwitchingProtocols (101) – Creates a websocket connection for receiving the data
| Delete a subscription         | Delete    | /vbds/access/streams/{streamName} | Accepted (204) – (no content); NotFound (404) if stream does not exist

## Running the Server

You can test `vdbs-server` by first starting an instance as a seed node. For example:

    vdbs-server --http-port 7777 --akka-port 8888 -s 127.0.0.1:8888

and then starting one or more other instances (normally on other hosts) that can join the Akka cluster:

    vdbs-server --http-port 7778 -s 127.0.0.1:8888

In the first case we specified an Akka port (8888) for the cluster seed node. In the second case, a random port was used,
since no -akka-port option was specified. 


### Test Client Using [curl](https://curl.haxx.se/) and [wscat](https://github.com/websockets/wscat)

Create a stream named `WFS1-RAW`:

    curl --request POST http://127.0.0.1:7777/vbds/admin/streams/WFS1-RAW

Subscribe to the stream using wscat (Install with `npm install -g wscat`)

    wscat -c ws://127.0.0.1:7777/vbds/access/streams/WFS1-RAW

Subscribe to the same stream from the other server:

    wscat -c ws://127.0.0.1:7778/vbds/access/streams/WFS1-RAW

Publish some data (Replace MyFile with the file name):

    curl --request POST -F data=@MyFile http://127.0.0.1:7777/vbds/transfer/streams/WFS1-RAW

The data should be displayed in the stdout of the wscat application.
