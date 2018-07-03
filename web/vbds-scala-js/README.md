VBDS Scala.js Client
====================

This directory contains a VBDS web client based on Scala.js and JS9.

After checking out from Github, run this command to download JS9 and any other npm based dependencies:

    cd web/vbds-scala-js
    npm install

To build and test the web app, run:

    sbt fastOptJS::webpack

and point your browser at:

    http://localhost:12345/web/vbds-scala-js/target/scala-2.12/classes/index-dev.html

You can ignore the errors and warnings printed by the scalajs-bundler sbt plugin. (TODO)

Note that the web app needs to be run via an http server to avoid cross-site scripting errors when accessing the VBDS server.
The sbt workbench plugin starts an http server on localhost:12345 for testing.

