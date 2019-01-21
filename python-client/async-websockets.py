import asyncio
import websockets
from photutils.datasets import make_4gaussians_image
from photutils import centroid_com, centroid_1dg, centroid_2dg
import matplotlib.pyplot as plt
from astropy.io import fits

# XXX TODO: Get from command line
host = "192.168.178.77"
port = 7777
route = "/vbds/access/streams"
stream = "XXX"

uri = f"ws://{host}:{port}{route}/{stream}"
print(f"URI = {uri}")

# called when an image is received
def receivedImage(image):
    print(f"Image size is {len(image)}")
    # data = make_4gaussians_image()[43:79, 76:104]    # extract single object
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

    # fig, ax = plt.subplots(1, 1)
    # # See https://bids.github.io/colormap/ for colormaps
    # ax.imshow(data, origin='lower', interpolation='nearest', cmap='viridis')
    # marker = '+'
    # ms, mew = 30, 2.
    # plt.plot(x1, y1, color='#1f77b4', marker=marker, ms=ms, mew=mew)
    # # plt.plot(x2, y2, color='#17becf', marker=marker, ms=ms, mew=mew)
    # # plt.plot(x3, y3, color='#d62728', marker=marker, ms=ms, mew=mew)
    # plt.show()

# Reads image data from the VBDS web socket in chunks, puts them together and calls receivedImage()
async def receive(uri):
    async with websockets.connect(uri) as websocket:
        chunks = []
        while True:
            bs = await websocket.recv()
            # print(f"Received message of type {type(bs)}, len: {len(bs)}")
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
