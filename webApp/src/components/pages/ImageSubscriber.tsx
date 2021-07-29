import React, {useState} from 'react'
import {useAppContext} from "../../AppContext";
import {StreamSelector} from "../StreamSelector";

export const ImageSubscriber = (): JSX.Element => {
  const [canvasWidth, setCanvasWidth] = useState<number>(1920)
  const [canvasHeight, setCanvasHeight] = useState<number>(1080)

  return <div>
    <StreamSelector/>
    <canvas width={canvasWidth} height={canvasHeight}/>
  </div>
}
