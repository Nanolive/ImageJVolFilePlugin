package ch.nanolive.ij

import java.io.File

import ch.nanolive.acquisition.models.Frame
import ch.nanolive.acquisition.services.io.AcquisitionZip
import com.sun.media.jai.codec.{ImageCodec, ImageDecoder}
import ij.VirtualStack
import ij.process.{FloatProcessor, ImageProcessor}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Paul on 20/06/2017.
  */
class VolFileFluoStack(acquisitionZip: AcquisitionZip, channel: String, framesWithChannel: List[Frame]) extends VirtualStack {

  // Constructor
  private val _frame = framesWithChannel.head
  private val _file:File = Await.ready(
    acquisitionZip.getFile(_frame.pathInZip(_frame.fluoImage(channel).get)),
    5 second
  ).value.get.get
  private val _decoder = ImageCodec.createImageDecoder("tiff", _file, null)
  private val _firstSlice = _decoder.decodeAsRaster(0)
  private val fileCache: mutable.WeakHashMap[Int, ImageDecoder] = new mutable.WeakHashMap[Int, ImageDecoder]
  fileCache += ((0, _decoder))

  // image stack data
  setBitDepth(32)
  val slicesPerFrame: Int = 1
  val width: Int = _firstSlice.getWidth
  val height: Int = _firstSlice.getHeight
  override def getSize: Int = framesWithChannel.size * slicesPerFrame
  val minMax: (Double, Double) = {
    val pixelData = _decoder.decodeAsRaster(0).getPixels(0, 0, width, height, null.asInstanceOf[Array[Float]])

    val max = pixelData.max

    if (max < 3.0)  // RI info. Best guess
      (pixelData.min, max)
    else if (max <= 255) // 8-bit
      (0, 255)
    else if (max <= 65535) // 16 bits
      (0, 65535)
    else
      (0, max)  // dunno??
  }

  override def getProcessor(n: Int): ImageProcessor = {

    val frameNo = (n-1) / slicesPerFrame
    val sliceNo = (n-1) % slicesPerFrame

    val decoder = fileCache.getOrElse(
      frameNo,
      {
        val frame = framesWithChannel(frameNo)

        val file:File = Await.ready(
          acquisitionZip.getFile(frame.pathInZip(frame.fluoImage(channel).get)),
          1 second
        ).value.get.get
        ImageCodec.createImageDecoder("tiff", file, null)
      }
    )
    val pixelData = decoder.decodeAsRaster(sliceNo).getPixels(0, 0, width, height, null.asInstanceOf[Array[Float]])
    val processor = new FloatProcessor(width, height, pixelData)
    processor.setMinAndMax(minMax._1, minMax._2)
    processor.flipVertical()
    processor
  }
}
