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

A `vbds-client` application is provided, for convenience and for use in tests, but any HTTP client will also work, as long as
it can handle the websocket responses for subscribers.
 
The `vbds-client` also demonstrates how you can access the vbds-server from Scala or Java code using akka-http, streams and actors.

Note that the data transfered via websocket to a subscriber is *chunked*: Multiple websocket messages are sent and the terminating 
message for each file contains a single newline "\n" byte, which does not belong to the data file. 

```
vbds-client 0.0.1
Usage: vbds-client [options]

  -n, --name <name>        The name of the vbds-server server(default: vbds)
  --host <host name>       The VBDS HTTP server host name (default: 127.0.0.1)
  -p, --port <number>      The VBDS HTTP server port number (default: 80)
  --create <stream name>   Creates a new VBDS stream with the given name
  --delete <stream name>   Deletes the VBDS stream with the given name
  -l, --list               List the available streams
  --subscribe <stream name>
                           Subscribes to the given VBDS stream (see --action option)
  --dir <path>             Directory to hold received data files (default: current directory)
  -a, --action <shell-command>
                           A shell command to execute when a new file is received (args: file-name)
  --publish <stream-name>  Publish to the given stream (see --data option)
  --delay <duration>       Delay between publishing files in a directory (see --data)
  --data <file-name>       Specifies the file (or directory full of files) to publish
  --chunk-size <num-bytes>
                           Optional chunk size (to tune file transfer performance)
  --help                   
  --version                
```

A prototype web app based on [Scala.js](https://www.scala-js.org/) and [JS9](https://js9.si.edu/) is available under 
[web/vbds-scala-js](web/vbds-scala-js)

_Note that in the current implementation, the vdbs-client does not exit after short commands, such as publishing or creating a stream. This will be fixed later_

## VBDS REST API

| Description                   | Verb      | URI                               | Response |
| ----------------------------- |-----------|---------------------------------- |--------- |          
| List all streams              | GET       | /vbds/admin/streams              | OK (200) – Stream names in JSON; empty document if no streams
| Create a stream               | POST      | /vbds/admin/streams/{streamName} | OK (200) – New stream name in JSON; Conflict (409) if stream exists
| Delete a stream               | DELETE    | /vbds/admin/streams/{streamName} | OK (200) – Deleted stream name in JSON; NotFound (404) if stream does not exist
| Publish an image to a stream  | POST      | /vbds/transfer/streams/{streamName}/image | Accepted (204) – (no content); Bad Request (400) – for non-existent stream
| Create a subscription         | POST      | /vbds/access/streams/{streamName} | SwitchingProtocols (101) – Creates a websocket connection for receiving the data

To delete a subscription, just close the websocket for it. Subscriptions are automatically deleted if a client disconnects.

## Running the Server

You can test `vbds-server` by first starting an instance as a seed node (hostA). For example:

    vbds-server --http-host hostA --http-port 7777 --akka-host hostA --akka-port 8888 -s hostA:8888

and then starting one or more other instances on other hosts that can join the Akka cluster:

    vbds-server --http-host hostB --http-port 7777 --akka-host hostB -s hostA:8888

In the first case we specified an Akka port (8888) for the cluster seed node. In the second case, a random port was used,
since no -akka-port option was specified. We need to know the akka host and port for the seed node, 
and of course the HTTP host and port, so we can send requests to it.


### Test Client Using [curl](https://curl.haxx.se/) and [wscat](https://github.com/websockets/wscat)

Create a stream named `WFS1-RAW`:

    curl --request POST http://hostA:7777/vbds/admin/streams/WFS1-RAW

Subscribe to the stream using wscat (Just for testing, with non-binary files: Install with `npm install -g wscat`)

    wscat -c ws://hostA:7777/vbds/access/streams/WFS1-RAW

Subscribe to the same stream from the other server:

    wscat -c ws://hostB:7777/vbds/access/streams/WFS1-RAW

Publish some data (Replace MyFile with the file name):

    curl --request POST -F data=@MyFile http://hostA:7777/vbds/transfer/streams/WFS1-RAW

The data should be displayed in the stdout of the wscat application. 
Note that instead of a file, a directory can be specified, in which case all the files in the directory are sorted and sent (for testing).


## Multi-jvm and Multi-Node Tests

The multi-jvm directory contains a test that uses multiple servers, publishers and subscribers.

To test locally on different JVMs, run: 

    sbt multi-jvm:test

To test with remote hosts, set the `multiNodeHosts` environment variable to a comma separated list of hosts and 
then run `sbt multiNodeTest`. For example: 

    export multiNodeHosts="username@hostA,username@hostB"
    sbt multiNodeTest

This copies the necessary jars to the `~/multi-node-test` dir on each host and runs a part of the test there.
There are other settings that might be of interest. 
See [Multi Node Testing](https://doc.akka.io/docs/akka/current/multi-node-testing.html).

## Testing on AWS

Assuming you have two (or more) CentOS hosts on [AWS](https://aws.amazon.com/) and you want to run the multi-node tests, 
there are some issues to consider: 

* The local system administrator (who has the AWS account) needs to configure the nodes to open the required ports 
  (or all ports, if random ports are used).
  Otherwise communication between the hosts will be blocked, exxcept for ssh connections.

* If you want to run the multi-node test (`sbt multiNodeTest`), you should check out the vbds source code on one of the AWS hosts 
  and run the sbt command from there using the internal IP addresses, which are different than the public IP addresses 
  that would be needed from the outside.
  
* The same goes for testing manually with the vbds-server command line app: Use the internal IP addresses, otherwise there will be
  problems with the cluster.


## Performance Test Results

The VBDS was tested with one Publisher, and two subscribers: One subscriber on host A, one subscriber on host B (publisher on host A).
The tests were performed on two AWS hosts with a 10 gigabit ethernet connection and CPU: Intel(R) Xeon(R) Platinum 8175M CPU @ 2.50GHz:

1 gb files:

    3.7437 secs per file, 0.2671 hz, 267.1154 mb/sec

640 mb files:

    2.3990 secs per file, 0.4168 hz, 266.7834 mb/sec

75mb files:

    0.2994 secs per file, 3.3395 hz, 250.4625 mb/sec

256 x 256 x 2 = 131 kb files

    0.0037 secs per file, 268.2403 hz, 35.1588 mb/sec

48 x 48 x 2 = 4608 byte files:

    0.0034 secs per file, 290.4697 hz, 1.3385 mb/sec

For comparison, here are the results on another pair of AWS machines with a slower network connection (1 gigabit ethernet connection and CPU: Intel(R) Xeon(R) CPU E5-2686 v4 @ 2.30GHz):

100 mb files:

    1.012 secs per file, 0.988 hz, 98.790 mb/sec

1 mb files:

    0.028 secs per file, 35.266 hz, 35.266 mb/sec

256 x 256 x 2 = 131 kb files

    0.0063 secs per file, 157.6551 hz, 20.6642 mb/sec
    
48 x 48 x 2 = 4608 byte files:

    0.0048 secs per file, 209.9643 hz, 0.9675 mb/sec


### Performance issues: Fast publisher, Fast and Slow Subscribers

Akka streams are used in the tests on both client and server, with websockets in the middle.
Obviously, if one of the subscribers is too slow, it will either have to buffer the received images, or skip some of them.
Since the image data is sent in "chunks", just dropping a single websocket message would result in a corrupted image.
The code would have to be smart enough to drop everything up until the next image.

In the current test-client implementation, the received data is saved to temp files, the file names are streamed to the
receiver and each received file is deleted when done.

Depending on how the client is implemented, a slow client could potentially cause the server to publisher at a slower rate.
If this turns out to be a problem, it might be necessary to allow the subscribers to specify a slower rate.

## Implementation Notes

This is the basic flow of a published data file:

* When a VBDS client publishes a data file, the client POSTS the data to the VBDS HTTP server.

* The VBDS server receives the data in chunks and broadcasts it (using Akka Streams) to the local subscriber's websockets 
  and also POSTS the data to any other VBDS servers that have subscribers for that stream 
  (The websocket writes and HTTP POSTS are both in the same broadcast graph).

* The other VBDS servers that receive the data also broadcast it to their subscribers.

* On the client side, the clients receive multiple websocket messages for each data file, 
  ending with a message containing a single newline, which is not part of the data.
  The clients need to collect the data until the file is complete and then can display it, do calculations, etc.

The clients have to decide what to do if another file arrives before the last one has been processed (i.e.: Buffer it or drop it).

