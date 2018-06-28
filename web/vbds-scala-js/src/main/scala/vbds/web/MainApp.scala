package vbds.web

import org.scalajs.dom
import org.scalajs.dom.{Event, MessageEvent}
import org.scalajs.dom.raw.WebSocket

object MainApp {

  import upickle.default._

  case class StreamInfo(name: String)

  object StreamInfo {
    implicit def rw: ReadWriter[StreamInfo] = macroRW
  }

  // XXX TODO FIXME: Add feature to enter or discover host and port of vbds-server!
  val host = "192.168.178.77"
  val port = 7777

  val adminRoute                = "/vbds/admin/streams"
  val accessRoute               = "/vbds/access/streams"
  val transferRoute             = "/vbds/transfer/streams"

  private val listStreamsUri = s"http://$host:$port$adminRoute"

  private def subscribeUri(stream: String) = s"ws://$host:$port$accessRoute/$stream"

  // Combobox listing the available streams
  private val streamsItem = {
    import scalatags.JsDom.all._
    select(onchange := MainApp.this.subscribeToStream _)(
      option(value := "", selected := true)("")
    ).render
  }

  /**
    * Gets the currently selected stream name
    */
  private def getSelectedStream: Option[String] =
    streamsItem.value match {
      case "" => None
      case x => Some(x)
    }

  /**
    * Update the streams combobox options
    */
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


  // Called when a stream is selected: Subscribe to the websocket for the stream
  private def subscribeToStream(): Unit = {
    // XXX TODO FIXME: Unsubscribe to previous stream!

    getSelectedStream.foreach { stream =>
      println(s"Subscribe to stream: $stream")
      val ws = new WebSocket(subscribeUri(stream))
      ws.onopen = { event: Event ⇒
        println(s"Opened websocket for stream $stream")
      }
      ws.onerror = { event: Event ⇒
        println(s"Error for stream $stream websocket: $event")
      }
      ws.onmessage = { event: MessageEvent ⇒
        println(s"Received message on websocket for stream $stream")
      }
      ws.onclose = { event: Event ⇒
        println(s"Websocket closed for stream $stream")
      }
    }
  }


  def main(args: Array[String]): Unit = {
    import scalatags.JsDom.all._
    println("Starting 'vbds-scala-js'...")

    //    val streamField = input(`type` := "text").render

    updateStreamsList()

    //    val subscribeButton = button(`type` := "submit", onclick := subscribeToStream _)("Subscribe").render

    val layout = div(
      p("VBDS Test"),
      p("Stream name: ", streamsItem)
    ).render

    dom.document.body.appendChild(layout.render)
  }

}
