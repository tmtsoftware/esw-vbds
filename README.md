# VIZ Bulk Data System (VBDS)

This project contains am early prototype of the VIZ Bulk Data System (VBDS).

***Note: This is work in progress...***

The main application is the `vbds-server`:

```
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

A `vdbs-client` application is also provided, although it is not necessary for testing, since any HTTP client will also work.
It demonstrates how to access the vdbs-server from Scala or Java code.

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

## Testing

You can test `vdbs-server` by first starting an instance as a seed node. For example:

    vdbs-server --http-port 7777 --akka-port 8888 -s 127.0.0.1:8888

and then starting one or more other instances that can join the cluster. For example, on the same host with a different port:

    vdbs-server --http-port 7778 -s 127.0.0.1:8888

### Test Client Using curl and wscat

* Create a stream named `XXX`:

    curl --request POST http://127.0.0.1:7777/vbds/admin/streams/XXX

* Subscribe to the stream using wscat (Install with `npm install -g wscat`)

    wscat -c ws://127.0.0.1:7777/vbds/access/streams/XXX

* Subscribe to the same stream from the other server:

    wscat -c ws://127.0.0.1:7778/vbds/access/streams/XXX

* Publish some data (Replace MyFile with the file name):

    curl --request POST -F data=@MyFile http://127.0.0.1:7777/vbds/transfer/streams/XXX

The data should be displayed in the stdout of the wscat application.
