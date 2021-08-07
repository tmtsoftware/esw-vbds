import React, {useEffect} from 'react'

type ImageConsumerProps = {
  blob: Blob | undefined,
  sendAck: () => void
}

// Used to display FITS images using JS9 (see index.html).
// Since JS9 is not a react component, it is not managed here. We only tell it to load images.
export const FitsImageConsumer = ({blob, sendAck}: ImageConsumerProps): JSX.Element => {
  useEffect(() => {
    if (blob) displayImage(blob)
  }, [blob])


  function onloadHandler(_: Event) {
    sendAck()
  }

  const settings = {
    onload: onloadHandler,
    filename: "vbds"
  }

  function displayImage(blob: Blob) {
    // @ts-ignore
    const savedSettings = JS9.GetParam("all")
    // @ts-ignore
    JS9.CloseImage({clear: false})
    // @ts-ignore
    JS9.Load(blob, {...settings, ...savedSettings})
  }

  return (
    // JS9 divs are created in index.html, since js9 is not a react component and
    // doesn't like being reinitialized along with this component.
    <></>
  )
}
