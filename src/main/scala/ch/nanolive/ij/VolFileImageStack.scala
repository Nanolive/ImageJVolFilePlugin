package ch.nanolive.ij

import java.io.{File, FileInputStream}
import javax.imageio.{ImageIO, ImageReader}

import ch.nanolive.acquisition.models.{Acquisition, Frame}
import ch.nanolive.acquisition.services.io.AcquisitionZip
import com.sun.media.jai.codec.{ImageCodec, ImageDecoder}
import ij.VirtualStack
import ij.process.{FloatProcessor, ImageProcessor}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Paul on 20/06/2017.
  */
class VolFileImageStack(acquisitionZip: AcquisitionZip, framesWithVolumes: List[Frame]) extends VirtualStack {

  setBitDepth(32)

  val slicesPerFrame = 96

  override def getSize: Int = framesWithVolumes.size * slicesPerFrame

  override def getProcessor(n: Int): ImageProcessor = {

    val frameNo = (n-1) / slicesPerFrame
    val sliceNo = (n-1) % slicesPerFrame
    val frame = framesWithVolumes(frameNo)

    val file:File = Await.ready(
      acquisitionZip.getFile(frame.pathInZip(frame.volume.get)),
      5 second
    ).value.get.get
    val decoder: ImageDecoder = ImageCodec.createImageDecoder("tiff", new FileInputStream(file), null)

    val pixelData = decoder.decodeAsRaster(sliceNo).getPixels(0, 0, 512, 512, null.asInstanceOf[Array[Float]])
    new FloatProcessor(512, 512, pixelData)
  }

}
