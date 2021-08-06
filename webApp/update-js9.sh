#!/bin/sh

# For development: Run this after making a change in the JS9 sources.
# It generates the minified js files and copies them to the web client resources dir.
# Note: Assumes the node.js minified command is available:
# Use: "npm install -g minifier" to install.

# EDIT: Set this to the directory containing the JS9 release
js9dir=../../../js9

if test ! -d $js9dir; then
    echo "$js9dir does not exist. Please edit to set correct path to JS9 sources."
    exit 1
fi

targetDir=public/js9
rm -rf $targetDir
test -d $targetDir || mkdir $targetDir
(cd $js9dir; make)
(cd $js9dir; tar cf - *.js *.css *.wasm images/ font/) | (cd $targetDir; tar xf -)
