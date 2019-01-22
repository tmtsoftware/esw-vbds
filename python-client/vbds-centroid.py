import asyncio
import websockets
from photutils import centroid_com, centroid_1dg, centroid_2dg
from astropy.io import fits
import argparse

# Example usage: python3 async-websockets.py --host 192.168.178.77 -p 7777 -s XXX
ap = argparse.ArgumentParser()
ap.add_argument("--host", required=True, help="VBDS server host")
ap.add_argument("-p", "--port", required=True, type=int, help="VBDS server port")
ap.add_argument("-s", "--subscribe", required=True, help="VBDS stream name to subscribe to")
args = vars(ap.parse_args())

host = args["host"]
port = args["port"]
stream = args["subscribe"]
route = "/vbds/access/streams"

uri = f"ws://{host}:{port}{route}/{stream}"
print(f"URI = {uri}")

# called when an image is received
def receivedImage(image):
    print(f"Image size is {len(image)}")
    hdulist = fits.HDUList(file=image)
    data = hdulist[0].data
    do_centroid(data)

def do_centroid(data):
    x1, y1 = centroid_com(data)
    print((x1, y1))
    # x2, y2 = centroid_1dg(data)
    # print((x2, y2))
    # x3, y3 = centroid_2dg(data)
    # print((x3, y3))

# Reads image data from the VBDS web socket in chunks, puts them together and calls receivedImage()
async def receive(uri):
    async with websockets.connect(uri) as websocket:
        chunks = []
        while True:
            bs = await websocket.recv()
            if (len(bs) == 1):
                await websocket.send("ACK")
                image = b"".join(chunks)
                chunks = []
                receivedImage(image)
            else:
                chunks.append(bs)

# Receive loop
asyncio.get_event_loop().run_until_complete(
    receive(uri))
