package vbds.web

import org.scalajs.dom.html.Image
import org.scalajs.dom.{Blob, BlobPropertyBag}

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobal
object JS9 extends js.Object {

  /**
   * Load an image into JS9.
   *
   * Load a FITS file or a PNG representation file into JS9. You also can pass an in-memory buffer containing a FITS file, or a string containing a base64-encoded FITS file.
   *
   * @param input url, fitsy object, in-memory FITS, or FITS blob
   * @param options optional object or json string containing image parameters
   */
  def Load(input: Blob, options: BlobPropertyBag): Unit = js.native

  def Load(input: Blob): Unit = js.native

  /**
   * Re-read the image data and re-display.
   *
   * @param input  object, javascript array, typed array, FITS blob, or string
   * @param options optional options or function to call when refresh is complete
   */
  def RefreshImage(input: Blob, options: BlobPropertyBag): Unit = js.native

  /**
   * Clear the image from the display and mark resources for release.
   *
   * Each loaded image claims a non-trivial amount of memory from a finite amount of browser heap space. For example,
   * the default 32-bit version of Google Chrome has a memory limit of approximately 500Mb. If you are finished viewing an image,
   * closing it tells the browser that the image's memory can be freed. In principle, this is can help reduce overall
   * memory usage as successive images are loaded and discarded. Note, however, that closing an image only provides a hint
   * to the browser, since this sort of garbage collection is not directly accessible to JavaScript programming.
   *
   * Some day, all browsers will support full 64-bit addressing and this problem will go away ...
   *
   */
  def CloseImage(options: BlobPropertyBag = js.native): Unit = js.native

  /**
   * Closes all images in the specified display.
   * You can supply a display name as the first argument or the display object: (Default display is "JS9")
   */
  def CloseDisplay(name: String): Unit = js.native

  /**
   * Gets an image param value
   * @param param param name or "all" for all image params
   */
  def GetParam(param: String): BlobPropertyBag = js.native

  /**
   * Sets a param value.
   * @param param param name or "all" for all image params
   * @param value a value previously returned from GetParam
   */
  def SetParam(param: String, value: BlobPropertyBag): Unit = js.native

  /**
    * Get information about one or more regions
    * @param region "selected" or "all" for all image regions
    * @param options supply {format: “text”} to get regions as a text string
    */
  def GetRegions(region: String, options: BlobPropertyBag): BlobPropertyBag = js.native


  // return the image object for the specified image name or the display id
  def GetImage(): Image = js.native

  /**
   * Set the image inherit mode.
   *
   * The JS9.SetImageInherit() routine specifies whether a new image grabs the image params (e.g., colormap, scale, zoom, etc.) from the currently displayed image. If false, these params are taken from the default JS9.imageOpts object.
   *
   * @param mode true (inherit params from currently displayed image) or false
   */
  def SetImageInherit(mode: Boolean): Unit = js.native

}

//@js.native
//@JSGlobalScope
//object DOMGlobalScope extends js.Object {
//  def vbdsCleanupImage(): Unit = js.native
//}
