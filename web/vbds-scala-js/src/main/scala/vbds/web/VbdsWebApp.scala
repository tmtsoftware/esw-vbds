package vbds.web

import org.scalajs.dom
import org.scalajs.dom.{BlobPropertyBag, Event, MessageEvent}
import org.scalajs.dom.raw.{Blob, WebSocket}

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}
import VbdsWebApp._
import upickle.default._

import scala.collection.immutable.Queue

/**
 * A web app that lets you subscribe to vbds images and displays them in a JS9 window on the page.
 */
object VbdsWebApp {

  // Object returns when listing streams
  private case class StreamInfo(name: String, contentType: String)

  private object StreamInfo {
    // JSON support
    implicit def rw: ReadWriter[StreamInfo] = macroRW
  }

  // vbds server routes
  private val adminRoute  = "/vbds/admin/streams"
  private val accessRoute = "/vbds/access/streams"
  //  private val transferRoute             = "/vbds/transfer/streams"

  val closeProps = js.Dynamic.literal("clear" -> false).asInstanceOf[BlobPropertyBag]
}

class VbdsWebApp {

  // Can't save temp files from the browser, so have to keep the image parts in memory...
  private var currentImageData: List[Uint8Array] = Nil

  // Set to true while image is being displayed (XXX TODO FIXME)
  private var busyDisplay = false

  // WebSocket for current subscription
  private var currentWebSocket: Option[WebSocket] = None

  // Running cout for received files
  private var fileCount = 0

  // Holds images waiting to be displayed
  private var queue = Queue[Blob]()

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
    // XXX TODO: FIXME: deprecated warning: forward usage?
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
    fileCount = fileCount + 1
    val imageType = getSelectedStream.map(_.contentType).getOrElse("image/fits")
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

  // Acknowledge the message to prevent overrun (Allow some buffering, move to start of function?)
  // XXX FIXME: May not be needed, can send ACK message on receiving WS message?
  private def onloadHandler(event: Event): Unit = {
    println("XXX onload handler")
    busyDisplay = false
    currentWebSocket.foreach(sendAck)
  }

  // XXX Might be that onload is not always called...
  def loadProps() =
    js.Dynamic
      .literal(
        "onload"   -> onloadHandler _,
        "filename" -> s"vbds$fileCount"
      )
      .asInstanceOf[BlobPropertyBag]

  // Combine the image parts and send to the display
  private def displayImage(): Unit = {
    if (busyDisplay) {
      println("\n------------- Ignoring image: Display is busy ----------\n")
    } else if (queue.nonEmpty) {
      busyDisplay = true
      println(s"XXX Queue size = ${queue.size}")
      try {
        println("\nXXX display image\n")
        val settings = JS9.GetParam("all")
        JS9.CloseImage(closeProps)
          val (blob, q) = queue.dequeue
          queue = q
          // Use the first time to set the onload handler, so we know when the image has been displayed.
        if (fileCount == 1)
          JS9.Load(blob, loadProps)
        else
          JS9.Load(blob, settings)
      } catch {
        case ex: Exception =>
          println(s"Display image failed: $ex")
      } finally {
        println("XXX Display image done")
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
        // End marker is a message with one byte ("\n")
        if (arrayBuffer.byteLength == 1) {
          println(s"Received EOF marker")
          val buffers = currentImageData.reverse
          currentImageData = Nil
          // JS9 has code to "flatten if necessary", so we can just pass in all the file parts together
          val blob = new Blob(js.Array(buffers: _*), getImageProps)
          queue = queue :+ blob
          displayImage()
        } else {
          currentImageData = new Uint8Array(arrayBuffer) :: currentImageData
          sendAck(ws)
          println(s"Received ${arrayBuffer.byteLength} bytes")
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
      p("Stream name: ", streamsItem, " ", updateStreamsListButton)
    ).render

    dom.document.body.appendChild(layout.render)
  }

}
