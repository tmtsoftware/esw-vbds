package vbds.web

import org.scalajs.dom

object MainApp {

  import upickle.default._
  case class StreamInfo(name: String)
  object StreamInfo {
      implicit def rw: ReadWriter[StreamInfo] = macroRW
  }

  private val listStreamsUri = "http://192.168.178.77:7777/vbds/admin/streams"

  // Combobox listing the available streams
  private val streamsItem = {
    import scalatags.JsDom.all._
    select(onchange := subscribeToStream _)(
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


  // Temp: print streams
  private def listStreams(): Unit = {
    // XXX temp

    val xhr = new dom.XMLHttpRequest()
    xhr.open("GET", listStreamsUri)
    xhr.onload = { e: dom.Event =>
      if (xhr.status == 200) {
        println(s"XXX Streams available: ${xhr.responseText}")
        val streams = read[List[StreamInfo]](xhr.responseText)
        println(s"XXX List of streams: $streams")
        updateStreamOptions(streams)
      }
    }
    xhr.send()
  }


  // Called when button is pressed
  private def subscribeToStream(): Unit = {
    val s = streamsItem.value
    println(s"Subscribe to stream: $s")

    //      JS9Wrapper.Load("file:///shared/data/tmt/frames/input/out-12363.jpg")
  }


  def main(args: Array[String]): Unit = {
    import scalatags.JsDom.all._
    println("Starting 'vbds-scala-js'...")

//    val streamField = input(`type` := "text").render

    listStreams()

//    val subscribeButton = button(`type` := "submit", onclick := subscribeToStream _)("Subscribe").render

    val layout = div(
      p("VBDS Test"),
      p("Stream name: ", streamsItem)
    ).render

    dom.document.body.appendChild(layout.render)
  }

}
