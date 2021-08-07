# VBDS Web App

This directory contains a VBDS web app that displays a menu of VBDS servers and image streams
and lets you select an image stream to display.

For jpeg images, an HTML canvas is used. For FITS, the [JS9](https://github.com/ericmandel/js9) application is embedded in the page.
It is assumed that the JS9 source is installed locally on the build host.
A shell script is provided to copy the necessary files from the JS9 source to the web app's public directory.

After checking out [JS9](https://github.com/ericmandel/js9)
and this project from Github, first edit and then run this script to "minify" and copy the JS9 dependencies:

    cd webApp
    ./update-js9.sh         # Edit this script to set the location of the JS9 sources

## Prerequisites Required for Running App

The web app uses the [CSW Location Service](https://tmtsoftware.github.io/csw/services/location.html)
to locate the VBDS servers. To start the
[CSW services](https://tmtsoftware.github.io/csw/apps/cswservices.html), run:

    csw-services start

The latest versions of [Node.js](https://nodejs.org/en/download/package-manager/)
and [npm](https://www.npmjs.com/) must be installed.

## Run the App in Local Environment

Run following commands in the terminal.

   ```bash
   npm install
   npm start
   ```

This should automatically open [localhost:8080](http://localhost:8080) in a browser.

## Build the App for Production

Run following commands in the terminal.

```bash
npm install
npm run build
```

## Testing the Display

One way to test the performance of the image viewer is to use vbds-client to publish all of the image files in a directory.
For example, by running each of these commands in different terminal windows:

    vbds-server --name server1
    vbds-client --name server1 --create XXX --content-type "image/fits"
    vbds-client --name server1 --repeat --publish XXX --delay "100 millis" --content-type 'image/fits' --data /my/image/directory

    vbds-client --name server1 --create YYY --content-type "image/jpeg"
    vbds-client --repeat --name server1 --publish YYY --content-type 'image/jpeg' --data /my/jpeg-image/directory

Where `/my/image/directory` is a directory containing a sequence of image files, in this case in FITS format.
The `--delay` option gives the amount of time between images. You can specify a value like "1s" or "1 second" or "500 millis".
If no --delay option is given, the images will be published as fast as the clients can handle them.
The `--repeat` option causes the publisher to keep publishing the same files (all the ones in the directory, or matching the `--suffix` option).
The `--stats` options causes the publisher to print statistics showing the throughput.

In the web app, refresh the page to update the list of servers and streams and select the stream `XXX`.
While testing, it is best to display the web console in the browse to see any error messages.

For reference, here are the vbds-client command line options:

```
vbds-client 0.0.1
Usage: vbds-client [options]

  -n, --name <name>        The name of the vbds-server server(default: vbds)
  --create <stream name>   Creates a new VBDS stream with the given name
  --content-type <content-type>
                           Specifies the content type of the files in the stream
  --delete <stream name>   Deletes the VBDS stream with the given name
  -l, --list               List the available streams
  --stats                  Print timing statistics when publishing files
  --repeat                 Keep publishing the same files forever, until killed (for testing)
  --save-files             If true, save the files received by the subscription to the current directory with names like <streamName>-<count>
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


## References

* ESW-TS Library - [Link](https://tmtsoftware/esw-ts/)
* ESW-TS Library Documentation - [Link](https://tmtsoftware.github.io/esw-ts/)
