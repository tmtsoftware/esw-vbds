package vbds.web

import org.scalajs.dom
import org.scalajs.dom.{BlobPropertyBag, Event, MessageEvent}
import org.scalajs.dom.raw.{Blob, WebSocket}

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}
import VbdsWebApp._

import upickle.default._


object VbdsWebApp {

  // Object returns when listing streams
  case class StreamInfo(name: String)

  object StreamInfo {
    // JSON support
    implicit def rw: ReadWriter[StreamInfo] = macroRW
  }

  // XXX TODO FIXME: Add feature to enter or discover host and port of vbds-server!
  private val host = "192.168.178.77"
  val port = 7777

  // vbds server routes
  private val adminRoute                = "/vbds/admin/streams"
  private val accessRoute               = "/vbds/access/streams"
  //  private val transferRoute             = "/vbds/transfer/streams"

  // URI to get a list of streams
  private val listStreamsUri = s"http://$host:$port$adminRoute"

  // URI to subscribe to a stream
  private def subscribeUri(stream: String) = s"ws://$host:$port$accessRoute/$stream"

}

class VbdsWebApp {

  // Can't save temp files from the browser, so have to keep the image parts in memory...
  private var currentImageData: List[Uint8Array] = Nil

  // Combobox listing the available streams
  private val streamsItem = {
    import scalatags.JsDom.all._
    // XXX TODO: FIXME: deprecated warning: forward usage?
    select(onchange := subscribeToStream _)(
      option(value := "", selected := true)("")
    ).render
  }

  // Gets the currently selected stream name
  private def getSelectedStream: Option[String] =
    streamsItem.value match {
      case "" => None
      case x => Some(x)
    }

  // Update the streams combobox options
  private def updateStreamOptions(items: List[StreamInfo]): Unit = {
    import scalatags.JsDom.all._
    for (i <- (1 until streamsItem.length).reverse) {
      streamsItem.remove(i)
    }
    items.foreach { str =>
      streamsItem.add(option(value := str.name)(str.name).render)
    }
  }


  // Update the menu with the list of streams
  private def updateStreamsList(): Unit = {
    val xhr = new dom.XMLHttpRequest()
    xhr.open("GET", listStreamsUri)
    xhr.onload = { _: dom.Event =>
      if (xhr.status == 200) {
        println(s"XXX Streams available: ${xhr.responseText}")
        val streams = read[List[StreamInfo]](xhr.responseText)
        println(s"XXX List of streams: $streams")
        updateStreamOptions(streams)
      }
    }
    xhr.send()
  }

  // Combine the image parts and send to the display
  private def displayImage(): Unit = {
    val buffers = currentImageData.reverse
    val length = buffers.map(_.byteLength).sum
    val offsets = buffers.map(_.byteLength).scanLeft(0)(_+_).reverse.tail.reverse
    val imageData = new Uint8Array(length)
    buffers.zip(offsets).foreach { p =>
      imageData.set(p._1, p._2)
    }
    currentImageData = Nil
    val properties = js.Dynamic.literal("type" -> "image/fits").asInstanceOf[BlobPropertyBag]
    val blob = new Blob(js.Array(imageData), properties)
    JS9Wrapper.Load(blob)
  }

  // Called when a stream is selected: Subscribe to the websocket for the stream
  private def subscribeToStream(event: dom.Event): Unit = {
    // XXX TODO FIXME: Unsubscribe to previous stream!

    getSelectedStream.foreach { stream =>
      println(s"Subscribe to stream: $stream")
      val ws = new WebSocket(subscribeUri(stream))
      ws.binaryType = "arraybuffer"

      ws.onopen = { _: Event ⇒
        println(s"Opened websocket for stream $stream")
      }
      ws.onerror = { event: Event ⇒
        println(s"Error for stream $stream websocket: $event")
      }
      ws.onmessage = { event: MessageEvent ⇒
//        println(s"Received message on websocket for stream: $stream: ${event.data}")
        val arrayBuffer = event.data.asInstanceOf[ArrayBuffer]
        if (arrayBuffer.byteLength == 1) {
          displayImage()
        } else {
          currentImageData =  new Uint8Array(arrayBuffer, 0, arrayBuffer.byteLength) :: currentImageData
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

    //    val streamField = input(`type` := "text").render
    //    val subscribeButton = button(`type` := "submit", onclick := subscribeToStream _)("Subscribe").render

    updateStreamsList()

    val layout = div(
      p("VBDS Test"),
      p("Stream name: ", streamsItem)
    ).render

    dom.document.body.appendChild(layout.render)
  }

}
