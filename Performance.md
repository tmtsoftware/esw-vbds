## Performance Test Results on 10 gigabit AWS Servers

The VBDS was tested with one Publisher, two servers and two subscribers: One subscriber on host A, one subscriber on host B (publisher on host A).
The tests were performed on two AWS hosts with a 10 gigabit ethernet connection and CPU: Intel(R) Xeon(R) Platinum 8175M CPU @ 2.50GHz.
Note: These tests only measure the throughput: The data is not processed in these tests:

| Image Size                   | Images/Sec (hz) |
| -----------------------------|-----------------|          
| 48   x   48 x 2              | 270.929         |
| 128  x  128 x 2              | 306.185         |
| 256  x  256 x 2              | 233.699         |
| 512  x  512 x 2              | 149.231         |
| 1024 x 1024 x 2              | 60.252          |
| 2048 x 2048 x 2              | 18.916          |
| 4096 x 4096 x 2              | 5.314           |

Here are the results of the same test, but with 4 byte FITS data (BITPIX=-32) and 16k header:

| Image Size                   | Images/Sec (hz) |
| -----------------------------|-----------------|          
| 48   x   48 x 4              | 211.461         |
| 128  x  128 x 4              | 195.198         |
| 256  x  256 x 4              | 153.163         |
| 512  x  512 x 4              | 83.174         |
| 1024 x 1024 x 4              | 29.861          |
| 2048 x 2048 x 4              | 8.901          |
| 4096 x 4096 x 4              | 2.434           |

# Performance Test Results using an Embedded Instance of JS9

These are the results of testing the image display performance using the web app in [web/vbds-scala-js](web/vbds-scala-js)
running locally on a Linux laptop with an Intel Core i7-6820HQ CPU @ 2.70GHz:

## JS9 FITS Display

| Image Size                   | Images/Sec (hz) |
| -----------------------------|---------------- |          
| 128  x  128 x 4              | 35              |
| 512  x  512 x 4              | 12              |
| 1024 x 1024 x 4              | 7               |
| 2048 x 2048 x 4              | 2               |

## JS9 JPEG Display

| Image Size               | Images/Sec (hz) |
| -------------------------|---------------- |          
| 128  x  128              | 39              |
| 512  x  512              | 27              |
| 1024 x 1024              | 16              |
| 2048 x 2048              | 5.5             |


## HTML Canvas JPEG Display

| Image Size               | Images/Sec (hz) |
| -------------------------|---------------- |          
| 128  x  128              | 145              |
| 512  x  512              | 130              |
| 1024 x 1024              | 57               |
| 2048 x 2048              | 22               |

## Latency

Here are some latency measurements from running VBDS on two AWS servers (a two node akka cluster), with one subscriber client and one publisher client (each on a different host). This
measures the time (in __seconds__) to publish a FITS file to server1, distribute it to server2 and write it to the subscriber on server2 (without writing any files to disk or displaying any images): 


| Image Size    | Size in Bytes | Min    | Max    | Average |
| ----------------|----------|--------|------- |--------|          
| 128 x 128 x 4   | 80640    | 0.0040 | 0.0680 | 0.0059 |
| 512 x 512 x 4   | 1065600  | 0.0110 | 0.0820 | 0.0143 |
| 1024 x 1024 x 4 | 4210560  | 0.0270 | 0.0940 | 0.0355 |
| 2048 x 2048 x 4 | 16793280 | 0.0970 | 0.2230 | 0.1237 |

 *(All times in seconds)*.

There was no notable difference in performance between Java-8 and Java-11.


### Performance issues: Fast publisher, Fast and Slow Subscribers

Akka streams are used in the tests on both client and server, with websockets in the middle.
Obviously, if one of the subscribers is too slow, it will either have to buffer the received images, or skip some of them.
Since the image data is sent in "chunks", just dropping a single websocket message would result in a corrupted image.
The code would have to be smart enough to drop everything up until the next image. 
For this reason a client websocket acknowledgement message is currently required. This makes it possible to have back-pressure
from the web-client all the way back to the publisher.

In the current test-client implementation, the received data is saved to temp files, the file names are streamed to the
receiver and each received file is deleted when done.

In the current implementation a slow client will cause the publisher to publish at a slower rate.
If this turns out to be a problem, it might be necessary to allow the subscribers to specify a slower rate, 
change the publisher to drop images when needed, or make the server smart enough to only drop images for slower subscribers.
