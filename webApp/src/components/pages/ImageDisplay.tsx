import React, {useRef} from 'react'

type ImageDisplayProps = {
  blob: Blob,
  ack: () => void
}

export const ImageDisplay = ({blob, ack}: ImageDisplayProps): JSX.Element => {

  // TODO
  const canvasWidth = 1920
  const canvasHeight = 1080
  const urlCreator = window.URL || window.webkitURL;

  const canvasRef = useRef<HTMLCanvasElement>(null)
  const canvas = <canvas
    ref={canvasRef}
    height={canvasHeight}
    width={canvasWidth}
    style={{border: '3px'}}/>

  const ctx = canvasRef.current?.getContext("2d")

  function displayImage(blob: Blob) {
    console.log(`XXX displayImage: ctx = ${ctx}, blob = `, blob)
    const x = urlCreator.createObjectURL(blob)
    console.log(`XXX x = ${x}`)
    const img = new Image()
    img.onload = () => {
      if (ctx) {
        console.log("XXX Loaded canvas image")
        console.log(`XXX ctx.drawImage ${img.naturalHeight}, ${img.naturalWidth}`)
        ctx.drawImage(img, 0, 0, img.naturalHeight, img.naturalWidth)
        URL.revokeObjectURL(img.src)
        ack()
      } else {
        console.log('XXX ctx is null')
      }
    }
    img.src = x
  }

  displayImage(blob)
  return canvas
}
