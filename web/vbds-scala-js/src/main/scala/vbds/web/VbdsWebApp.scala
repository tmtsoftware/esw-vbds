package vbds.web

import org.scalajs.dom
import org.scalajs.dom.{URL => _, _}
import org.scalajs.dom.raw.{Blob, WebSocket}

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}
import VbdsWebApp._
import org.scalajs.dom.html.Image
import upickle.default._

/**
 * A web app that lets you subscribe to vbds images and displays them in a JS9 window on the page.
 */
object VbdsWebApp {

  // Object returns when listing streams
  private case class StreamInfo(name: String, contentType: String)

  //noinspection ScalaUnusedSymbol
  private object StreamInfo {
    // JSON support
    implicit def rw: ReadWriter[StreamInfo] = macroRW
  }

  // vbds server routes
  private val adminRoute  = "/vbds/admin/streams"
  private val accessRoute = "/vbds/access/streams"

  val closeProps = js.Dynamic.literal("clear" -> false).asInstanceOf[BlobPropertyBag]

  // XXX: Set to true to test displaying with HTML canvas instead of JS9
  val canvasWidth  = 1920
  val canvasHeight = 1080
  val URL          = js.Dynamic.global.window.URL.asInstanceOf[org.scalajs.dom.URL.type]
}

class VbdsWebApp {

  // Can't save temp files from the browser, so have to keep the image parts in memory...
  private var currentImageData: List[Uint8Array] = Nil

  // Set to true while image is being displayed
  private var busyDisplay = false

  // WebSocket for current subscription
  private var currentWebSocket: Option[WebSocket] = None

  // Content type of current image stream
  private var imageType: String = "image/fits"

//  private def isFits = imageType == "image/fits"


  // Used to set some properties on the first call to JS9.Load
  private var initialized = false

  private val (hostField, portField) = {
    import scalatags.JsDom.all._
    (
      input(`type` := "text", value := "127.0.0.1").render,
      input(`type` := "text", value := "7777").render
    )
  }

  private val updateStreamsListButton = {
    import scalatags.JsDom.all._
    button(`type` := "submit", onclick := updateStreamsList _)("Update").render
  }

  // Acknowledge the message to prevent overrun
  // Note: Testing showed that if we don't call sendAck() here and images are streamed at full speed, that JS9.Load will
  // be called again before the image is loaded, causing errors.
  private def onloadHandler(event: Event): Unit = {
    busyDisplay = false
    currentWebSocket.foreach(sendAck)
  }

  // --- Canvas to display image ---
  private val cvs = {
    import scalatags.JsDom.all._
    canvas(widthA := canvasWidth, heightA := canvasHeight).render
  }

  private val ctx = cvs.getContext("2d").asInstanceOf[CanvasRenderingContext2D]

  private val img = document.createElement("img").asInstanceOf[Image]

  private val useCanvas: Boolean = {
    val scripts = document.getElementsByTagName("script")
    val script = scripts(scripts.length - 1)
    script.getAttribute("data-use-canvas") == "true"
  }

  img.onload = (e: dom.Event) => {
    ctx.drawImage(img, 0, 0, img.naturalHeight, img.naturalWidth)
    URL.revokeObjectURL(img.src)
    println("XXX Loaded canvas image")
    onloadHandler(e)
  }



  // URI to get a list of streams
  private def listStreamsUri = {
    s"http://${hostField.value}:${portField.value}$adminRoute"
  }

  // URI to subscribe to a stream
  private def subscribeUri(stream: StreamInfo) = {
    s"ws://${hostField.value}:${portField.value}$accessRoute/${stream.name}"
  }

  // Combobox listing the available streams
  private val streamsItem = {
    import scalatags.JsDom.all._
    select(onchange := subscribeToStream _)(
      option(value := "", selected := true)("")
    ).render
  }

  // Gets the currently selected stream name
  private def getSelectedStream: Option[StreamInfo] =
    streamsItem.value match {
      case "" => None
      case x  => Some(read[StreamInfo](x))
    }

  // Use the content type of the stream to tell the display what kind of image this is.
  private def getImageProps: BlobPropertyBag = {
    js.Dynamic
      .literal(
        "type" -> imageType,
      )
      .asInstanceOf[BlobPropertyBag]
  }

  // Update the streams combobox options
  private def updateStreamOptions(items: List[StreamInfo]): Unit = {
    import scalatags.JsDom.all._
    for (i <- (1 until streamsItem.length).reverse) {
      streamsItem.remove(i)
    }
    items.foreach { str =>
      streamsItem.add(option(value := write(str))(str.name).render)
    }
  }

  // Update the menu with the list of streams
  private def updateStreamsList(): Unit = {
    val xhr = new dom.XMLHttpRequest()
    xhr.open("GET", listStreamsUri)
    xhr.onload = { _: dom.Event =>
      if (xhr.status == 200) {
        val streams = read[List[StreamInfo]](xhr.responseText)
        updateStreamOptions(streams)
      }
    }
    xhr.send()
  }

  // Set as inital properties on first call to JS9.Load to
  private def loadProps() =
    js.Dynamic
      .literal(
        "onload"   -> onloadHandler _,
        "filename" -> s"vbds"
      )
      .asInstanceOf[BlobPropertyBag]

//  private val regionProps = js.Dynamic
//    .literal(
//      "format" -> "text"
//    )
//    .asInstanceOf[BlobPropertyBag]

  // Combine the image parts and send to the display
  private def displayImage(blob: Blob): Unit = {
    if (!busyDisplay) {
      busyDisplay = true
      if (useCanvas) {
        img.src = URL.createObjectURL(blob)
      } else try {
        val settings = JS9.GetParam("all")
//        val regions  = JS9.GetRegions("all", regionProps)
//        println(s"XXX regions = $regions")
        JS9.CloseImage(closeProps)
        // Use the first time to set the onload handler, so we know when the image has been displayed.
        if (!initialized) {
          JS9.Load(blob, loadProps())
          initialized = true
        } else {
//          val mutableSettings = settings.asInstanceOf[js.Dynamic]
//          mutableSettings.regions = regions
//          JS9.Load(blob, mutableSettings.asInstanceOf[BlobPropertyBag])
          JS9.Load(blob, settings)
        }
      } catch {
        case ex: Exception =>
          println(s"Display image failed: $ex")
      }
    }
  }

  // Acknowledge the message to prevent overrun (Allow some buffering, move to start of function?)
  private def sendAck(ws: WebSocket): Unit = {
    println("XXX send ACK")
    ws.send("ACK")
  }

  // Returns true if the array buffer contains the marker for the end of a file stream ("\n")
//  private def isEndOfFileMarker(arrayBuffer: Uint8Array): Boolean = {
//    val s = new String(arrayBuffer.toArray.take(1).map(_.asInstanceOf[Char]))
//    s == "\n"
//  }

  // Called when a stream is selected: Subscribe to the websocket for the stream
  private def subscribeToStream(event: dom.Event): Unit = {
    // Close previous web socket, which unsubscribes to the previous stream
    currentWebSocket.foreach(_.close())

    getSelectedStream.foreach { stream =>
      println(s"Subscribe to stream: $stream")
      imageType = stream.contentType
      val ws = new WebSocket(subscribeUri(stream))
      currentWebSocket = Some(ws)
      ws.binaryType = "arraybuffer"
      ws.onopen = { _: Event ⇒
        println(s"Opened websocket for stream $stream")
      }
      ws.onerror = { event: Event ⇒
        println(s"Error for stream $stream websocket: $event")
      }
      ws.onmessage = { event: MessageEvent ⇒
        val arrayBuffer = new Uint8Array(event.data.asInstanceOf[ArrayBuffer])
        println(s"XXX received message with ${arrayBuffer.byteLength} bytes")
        // End marker is a message with one byte ("\n")
        if (arrayBuffer.byteLength == 1) {
          val buffers = currentImageData.reverse
          currentImageData = Nil
          // JS9 has code to "flatten if necessary", so we can just pass in all the file parts together
          val blob = new Blob(js.Array(buffers: _*), getImageProps)
          displayImage(blob)
        } else {
          currentImageData = new Uint8Array(arrayBuffer) :: currentImageData
          sendAck(ws)
        }
      }
      ws.onclose = { _: Event ⇒
        println(s"Websocket closed for stream $stream")
      }
    }
  }

  def init(): Unit = {
    import scalatags.JsDom.all._
    println("Starting 'vbds-scala-js'...")

    // XXX TODO: Use css for layout...
    val layout = div(
      p("VBDS Test"),
      p("VBDS Server"),
      p("Host: ", hostField),
      p("Port: ", portField),
      p("Stream name: ", streamsItem, " ", updateStreamsListButton),
      if (useCanvas) p("Using Canvas", div(cvs)) else p("Using JS9")
    ).render

    dom.document.body.appendChild(layout.render)
  }
}
