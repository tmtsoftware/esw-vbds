#!/bin/sh

# For development: Run this after making a change in the JS9 sources.
# It generates the minified js files and copies them to the web client resources dir.
# Note: Assumes the node.js minified command is available:
# Use: "npm install -g minifier" to install.

# Set this to the directory containing the JS9 release
js9dir=../../js9

(cd $js9dir; make refresh)

cp $js9dir/favicon.ico \
    $js9dir/js9support.css \
    $js9dir/js9.css \
    $js9dir/images/js9-apple-touch-icon.png \
    $js9dir/js9prefs.js \
    $js9dir/js9support.min.js \
    $js9dir/js9.min.js \
    $js9dir/js9plugins.js \
    $js9dir/js9worker.js \
    $js9dir/astroem.js $js9dir/astroemw.js $js9dir/astroemw.wasm \
    web/vbds-scala-js/src/main/resources/js9


