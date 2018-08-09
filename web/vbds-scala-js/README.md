VBDS Scala.js Client
====================

This directory contains a VBDS web client based on Scala.js and JS9.
It is assumed that the JS9 source is installed locally on the build host.

After checking out JS9 and this project from Github, first edit and then run this command to "minify" and copy the JS9 dependencies:

    cd web/vbds-scala-js
    ./update-js9.sh         # Edit this script to set the location of the JS9 sources

To build and test the web app, run:

    sbt fastOptJS

and point your browser at:

    http://localhost:12345/web/vbds-scala-js/target/scala-2.12/classes/index-dev.html

for the development version, or:
    
    http://localhost:12345/web/vbds-scala-js/target/scala-2.12/classes/index.html

for the optimized Javascript.

Note that the web app needs to be run via an http server to avoid cross-site scripting errors when accessing the VBDS server.
The sbt workbench plugin starts an http server on localhost:12345 for testing.

## Testing the Display

One way to test the performance of the image viewer is to use vbds-client to publish all of the image files in a directory.
For example, by running each of these commands in different terminal windows:

    vbds-server --http-host localhost --http-port 7777 --akka-host localhost --akka-port 8888 -s localhost:8888
    
    vbds-client --host localhost -p 7777 --create XXX --contentType "image/fits"
    
    vbds-client --host localhost -p 7777 --publish XXX --data /my/image/directory --delay "500 millis"

Where `/my/image/directory` is a dirtectory containing a sequence of image files, in this case in FITS format.
The `--delay` option gives the amount of time between images. You can specify a value like "1s" or "1 second" or "500 millis".

Then, in the web app, press the `Update` button to update the list of streams and select the stream `XXX`.
While testing, it is best to display the web console in the browse to see any error messages.

## Test Data

You can find sequences of FITS files for testing on the web. For example, see http://das.sdss.org/imaging/7150/40/corr/.


