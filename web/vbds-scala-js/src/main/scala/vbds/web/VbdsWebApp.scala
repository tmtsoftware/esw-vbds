package vbds.web

import org.scalajs.dom
import org.scalajs.dom.{BlobPropertyBag, Event, MessageEvent}
import org.scalajs.dom.raw.{Blob, WebSocket}

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}
import VbdsWebApp._
import upickle.default._

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

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

  val defaultLoadProps = js.Dynamic.literal("type" -> "image/fits").asInstanceOf[BlobPropertyBag]

  val closeProps = js.Dynamic.literal("clear" -> false).asInstanceOf[BlobPropertyBag]
}

class VbdsWebApp {

  // Can't save temp files from the browser, so have to keep the image parts in memory...
  private var currentImageData: List[Uint8Array] = Nil

  // Set to true while image is being displayed (XXX TODO FIXME)
  private var busyDisplay = false

  // WebSocket for current subscription
  private var currentWebSocket: Option[WebSocket] = None

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
    getSelectedStream match {
      case Some(streamInfo) =>
        if (streamInfo.contentType.nonEmpty)
          js.Dynamic.literal("type" -> streamInfo.contentType).asInstanceOf[BlobPropertyBag]
        else
          defaultLoadProps
      case None =>
        defaultLoadProps
    }
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
        println(s"XXX streams = $streams")
        updateStreamOptions(streams)
      }
    }
    xhr.send()
  }

  // Acknowledge the message to prevent overrun (Allow some buffering, move to start of function?)
  private def onloadHandler(p: Promise[Boolean])(): Unit = {
    println(s"XXX called onload handler")
    p.success(true)
  }

  // XXX Might be that onload is not always called...
  def loadProps(p: Promise[Boolean]) = js.Dynamic.literal("onload" -> onloadHandler(p) _).asInstanceOf[BlobPropertyBag]

  // Combine the image parts and send to the display
  private def displayImage(): Future[Boolean] = {
    if (busyDisplay) {
      Future.failed(new RuntimeException("Display is busy"))
    } else {
      busyDisplay = true
      val buffers = currentImageData.reverse
      currentImageData = Nil
      JS9.CloseImage(closeProps)

      // --- XXX TEMP XXX
      val s = new String(buffers.head.toArray.take(6).map(_.asInstanceOf[Char]))
      if (s != "SIMPLE") println(s"\nXXX INVALID FITS START: $s\n")
      // ---

      // JS9 has code to "flatten if necessary", so we can just pass in all the file parts together
      val blob = new Blob(js.Array(buffers: _*), getImageProps)
      println("XXX Loading image")
      val p = Promise[Boolean]
      JS9.Load(blob, loadProps(p))
      dom.window.setTimeout(() => Try(if (!p.isCompleted) p.failure(new RuntimeException("Image load failed"))), 1000)
      p.future
    }
  }

  // Acknowledge the message to prevent overrun (Allow some buffering, move to start of function?)
  private def sendAck(ws: WebSocket): Unit = {
    ws.send("ACK")
  }

  // Returns true if the array buffer contains the marker for the end of a file stream ("\n")
  private def isEndOfFileMarker(arrayBuffer: Uint8Array): Boolean = {
    val s = new String(arrayBuffer.toArray.take(1).map(_.asInstanceOf[Char]))
    s == "\n"
  }

  // Called when a stream is selected: Subscribe to the websocket for the stream
  private def subscribeToStream(event: dom.Event): Unit = {
    // Close previous web socket, which unsubscribes to the previous stream
    currentWebSocket.foreach(_.close())
    JS9.SetImageInherit("true")

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
        if (arrayBuffer.byteLength == 1 && isEndOfFileMarker(arrayBuffer)) {
          try {
            displayImage().onComplete {
              case Success(_)  =>
                println("XXX Image load succeeded!")
                sendAck(ws)
                busyDisplay = false
              case Failure(ex) =>
                println(s"Load image failed")
                sendAck(ws)
                busyDisplay = false
            }
          } catch {
            case ex: Exception => println("Image load failed!")
          }
        } else {
          println(s"XXX Received image chunk: size: ${arrayBuffer.byteLength}")
          currentImageData = new Uint8Array(arrayBuffer) :: currentImageData
          sendAck(ws)
        }
      }
      ws.onclose = { event: Event ⇒
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
