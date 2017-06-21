package ch.nanolive.ij

import java.io.{File, FileInputStream}
import javax.imageio.{ImageIO, ImageReader}

import ch.nanolive.acquisition.models.{Acquisition, Frame}
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
class VolFileImageStack(acquisitionZip: AcquisitionZip, framesWithVolumes: List[Frame]) extends VirtualStack {

  setBitDepth(32)
  val slicesPerFrame = 96
  override def getSize: Int = framesWithVolumes.size * slicesPerFrame

  val fileCache: mutable.WeakHashMap[Int, ImageDecoder] = new mutable.WeakHashMap[Int, ImageDecoder]
  var minMax: Option[(Double, Double)] = None

  def getMinMax(pixelData: Array[Float]): (Double, Double) = {
    if (minMax.isEmpty)
    {
      val max = pixelData.max
      if (max < 3.0)
        minMax = Some((pixelData.min, max))
      else if (max <= 255)
        minMax = Some((0, 255))
      else if (max <= 65535)
        minMax = Some((0, 65535))
      else
        minMax = Some((0, max))
    }

    minMax.get
  }

  override def getProcessor(n: Int): ImageProcessor = {

    val frameNo = (n-1) / slicesPerFrame
    val sliceNo = (n-1) % slicesPerFrame

    val decoder = fileCache.getOrElse(
      frameNo,
      {
        val frame = framesWithVolumes(frameNo)

        val file:File = Await.ready(
          acquisitionZip.getFile(frame.pathInZip(frame.volume.get)),
          5 second
        ).value.get.get
        ImageCodec.createImageDecoder("tiff", file, null)
      }
    )
    val pixelData = decoder.decodeAsRaster(sliceNo).getPixels(0, 0, 512, 512, null.asInstanceOf[Array[Float]])
    val processor = new FloatProcessor(512, 512, pixelData)
    val minMax = getMinMax(pixelData)
    processor.setMinAndMax(minMax._1, minMax._2)
    processor
  }

}
