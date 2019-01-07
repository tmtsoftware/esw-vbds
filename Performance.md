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

# Performance Test Results using an Embedded Instance of JS9

These are the results of testing the image display performance using the web app in [web/vbds-scala-js](web/vbds-scala-js)
running locally on a Linux laptop with an Intel Core i7-6820HQ CPU @ 2.70GHz:

## JS9 FITS Display

| Image Size                   | Images/Sec (hz) |
| -----------------------------|---------------- |          
| 128  x  128 x 2              | 35              |
| 512  x  512 x 2              | 12              |
| 1024 x 1024 x 2              | 7               |
| 2048 x 2048 x 2              | 2               |

## JS9 JPEG Display

| Image Size                   | Images/Sec (hz) |
| -----------------------------|---------------- |          
| 128  x  128 x 2              | 39              |
| 512  x  512 x 2              | 27              |
| 1024 x 1024 x 2              | 16              |
| 2048 x 2048 x 2              | 5.5             |


## HTML Canvas JPEG Display

| Image Size                   | Images/Sec (hz) |
| -----------------------------|---------------- |          
| 128  x  128 x 2              | 145              |
| 512  x  512 x 2              | 130              |
| 1024 x 1024 x 2              | 57               |
| 2048 x 2048 x 2              | 22               |

## Latency

Here are some latency measurements from running VBDS with one server, one subscriber and one publisher, all on the local host (A 2016 Lenovo Thinkpad P70): (Measures the time to publish a file to the subscriber without writing it to disk or displaying it): 

##### Latency in seconds for FITS files on localhost (16 bit short data):

| Image Size  | Min    | Max    | Average |
| ------------|--------|--------|------- |          
| 128 x 128   | 0.0010 | 0.0520 | 0.0027 |
| 512 x 512   | 0.0030 | 0.0510 | 0.0055 |
| 1024 x 1024 | 0.0100 | 0.0650 | 0.0158 |
| 2048 x 2048 | 0.0310 | 0.0880 | 0.0394 |

##### Latency test with subscriber and a second server running under vmware (CentOS) (With publisher and one server on the local host - all times in seconds): 

| Image Size  | Min    | Max    | Average |
| ------------|--------|--------|------- |          
| 128 x 128 |0.0060 | 0.0570 | 0.0117 |
| 512 x 512 | 0.0190 | 0.0750 | 0.0305|
| 1024 x 1024 | 0.0570 | 0.1040 | 0.0717 |
| 2048 x 2048 | 0.1760 | 0.2560 | 0.1995 |


##### Same latency test with second actual server (a 2008 Mac Pro, via 1000 base-t ethernet)

| Image Size  | Min    | Max    | Average |
| ------------|--------|--------|------- |          
| 128 x 128 | 0.0070 | 0.0420 | 0.0093 |
| 512 x 512 | 0.0210 | 0.0990 | 0.0268 |
| 1024 x 1024 | 0.0600 | 0.2070 | 0.0744 |
| 2048 x 2048 | 0.2330 | 0.5360 | 0.2736 |

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
