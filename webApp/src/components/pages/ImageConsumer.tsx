import React, {useEffect, useRef} from 'react'

type ImageConsumerProps = {
  blob: Blob | undefined,
  sendAck: () => void
}

// Used to display jpeg images in a canvas
export const ImageConsumer = ({blob, sendAck}: ImageConsumerProps): JSX.Element => {
  const img = new Image()

  // TODO
  const canvasWidth = 1920
  const canvasHeight = 1080

  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    if (blob) displayImage(blob)
  }, [blob])

  img.onload = () => {
    const ctx = canvasRef.current?.getContext("2d")
    if (ctx) {
      ctx.drawImage(img, 0, 0, img.naturalHeight, img.naturalWidth)
    }
    sendAck()
    URL.revokeObjectURL(img.src)
  }

  const urlCreator = window.URL || window.webkitURL;

  function displayImage(blob: Blob) {
    img.src = urlCreator.createObjectURL(blob)
  }

  return (
    <canvas
      ref={canvasRef}
      height={canvasHeight}
      width={canvasWidth}
      style={{border: '3px'}}/>
  )
}
