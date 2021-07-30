// import React, {useRef} from 'react'
//
// type ImageDisplayProps = {
//   blob: Blob,
//   ack: () => void,
//   ctx: CanvasRenderingContext2D
// }
//
// export const ImageDisplay = ({blob, ack, ctx}: ImageDisplayProps): JSX.Element => {
//
//   const urlCreator = window.URL || window.webkitURL;
//
//   function displayImage(blob: Blob) {
//     console.log(`XXX displayImage: ctx = ${ctx}, blob = `, blob)
//     const x = urlCreator.createObjectURL(blob)
//     console.log(`XXX x = ${x}`)
//     const img = new Image()
//     img.onload = () => {
//       console.log("XXX Loaded canvas image")
//       console.log(`XXX ctx.drawImage ${img.naturalHeight}, ${img.naturalWidth}`)
//       ctx.drawImage(img, 0, 0, img.naturalHeight, img.naturalWidth)
//       URL.revokeObjectURL(img.src)
//       ack()
//     }
//     img.src = x
//   }
//
//   displayImage(blob)
//   return <></>
// }
