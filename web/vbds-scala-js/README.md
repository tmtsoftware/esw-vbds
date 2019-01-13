VBDS Scala.js Client
====================

This directory contains a VBDS web client based on Scala.js and JS9.
It is assumed that the JS9 source is installed locally on the build host.

After checking out JS9 and this project from Github, first edit and then run this command to "minify" and copy the JS9 dependencies:

    cd web/vbds-scala-js
    ./update-js9.sh         # Edit this script to set the location of the JS9 sources

To build and test the web app, run:

        sbt
        > fastOptJS

and point your browser at:

    http://localhost:12345/web/vbds-scala-js/target/scala-2.12/classes/index-dev.html

for the development version, or:
    
    sbt
    > fullOptJS

and then go to:
    
    http://localhost:12345/web/vbds-scala-js/target/scala-2.12/classes/index.html

for the optimized Javascript.

Displaying the received images in a simple HTML canvas is much faster than displaying them in JS9, where
there are a lot of additional calculations being made for WCS, grids, toolbars, etc. To use the canvas version,
the URLs would be:

    http://localhost:12345/web/vbds-scala-js/target/scala-2.12/classes/index-with-canvas-dev.html

for the development version, or:
    
    http://localhost:12345/web/vbds-scala-js/target/scala-2.12/classes/index-with-canvas.html

Note that the web app needs to be run via an http server to avoid cross-site scripting errors when accessing the VBDS server.
The sbt workbench plugin starts an http server on localhost:12345 for testing.

## Testing the Display

One way to test the performance of the image viewer is to use vbds-client to publish all of the image files in a directory.
For example, by running each of these commands in different terminal windows:

    vbds-server --http-host localhost --http-port 7777 --akka-host localhost --akka-port 8888 -s localhost:8888
    
    vbds-client --host localhost -p 7777 --create XXX --contentType "image/fits"
    
    vbds-client --host localhost -p 7777 --publish XXX --data /my/image/directory --delay "500 millis" --stats --repeat

Where `/my/image/directory` is a dirtectory containing a sequence of image files, in this case in FITS format.
The `--delay` option gives the amount of time between images. You can specify a value like "1s" or "1 second" or "500 millis".
If no --delay option is given, the images will be published as fast as the clients can handle them.
The `--repeat` option causes the publisher to keep publishing the same files (all the ones in the directory, or matching the `--suffix` option).
The `--stats` options causes the publisher to print statistics showing the throughput.

In the web app, press the `Update` button to update the list of streams and select the stream `XXX`.
While testing, it is best to display the web console in the browse to see any error messages.

For reference, here are the vbds-client command line options:

```
vbds-client 0.0.1
Usage: vbds-client [options]

  -n, --name <name>        The name of the vbds-server server(default: vbds)
  --host <host name>       The VBDS HTTP server host name (default: 127.0.0.1)
  -p, --port <number>      The VBDS HTTP server port number (default: 80)
  --create <stream name>   Creates a new VBDS stream with the given name
  --contentType <content-type>
                           Specifies the content type of the files in the stream
  --delete <stream name>   Deletes the VBDS stream with the given name
  -l, --list               List the available streams
  --stats                  Print timing statistics when publishing files
  --repeat                 Keep publishing the same files forever, until killed (for testing)
  --stats-interval <value>
                           If --stats option was given, controls how often statistics are printed (default: 1 = every time)
  --subscribe <stream name>
                           Subscribes to the given VBDS stream (see --action option)
  --dir <path>             Directory to hold received data files (default: current directory)
  -a, --action <shell-command>
                           A shell command to execute when a new file is received (args: file-name)
  --publish <stream-name>  Publish to the given stream (see --data option)
  --delay <duration>       Delay between publishing files in a directory (see --data)
  --data <file-name>       Specifies the file (or directory full of files) to publish
  --suffix <suffix>        Optional suffix for files to publish if the file given by --data is a directory
  --chunk-size <num-bytes>
                           Optional chunk size (to tune file transfer performance)
  --help                   
  --version                
```

