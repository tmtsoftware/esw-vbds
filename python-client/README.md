# Example VBDS Python Client

This directory contains a python script that subscribes to a VBDS stream and then calculates the centroid coordinates 
of the incoming FITS files.

## Requirements

## Usage

Start the VBDS server:

    vbds-server --http-host 192.168.178.77 --http-port 7777 --akka-host 192.168.178.77 --akka-port 8888 -s 192.168.178.77:8888

Create a stream named MyStream:

    vbds-client --host 192.168.178.77 -p 7777 --create MyStream --content-type "image/fits"

Start the example script, which subscribes to the stream:

    python3 async-websockets.py --host 192.168.178.77 -p 7777 -s MyStream

Publish a FITS file:

    vbds-client --host 192.168.178.77 -p 7777 --stats --publish MyStream --data /path/to/file
