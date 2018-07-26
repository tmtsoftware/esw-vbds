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

Note that the web app needs to be run via an http server to avoid cross-site scripting errors when accessing the VBDS server.
The sbt workbench plugin starts an http server on localhost:12345 for testing.

