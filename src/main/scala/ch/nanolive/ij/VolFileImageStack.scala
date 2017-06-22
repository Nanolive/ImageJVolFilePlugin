package ch.nanolive.ij

import java.io.File

import ch.nanolive.acquisition.models.Frame
import ch.nanolive.acquisition.services.io.AcquisitionZip
import com.sun.media.jai.codec._
import com.sun.media.jai.codecimpl.TIFFImageDecoder
import ij.VirtualStack
import ij.process.{FloatProcessor, ImageProcessor}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by Paul on 20/06/2017.
  */
class VolFileImageStack(acquisitionZip: AcquisitionZip, framesWithVolumes: List[Frame]) extends VirtualStack {

  // Constructor
  private val _frame = framesWithVolumes.head
  private val _file:File = Await.ready(
    acquisitionZip.getFile(_frame.pathInZip(_frame.volume.get)),
    5 second
  ).value.get.get
  private val _decoder = ImageCodec.createImageDecoder("tiff", _file, null)
  private val _firstSlice = _decoder.decodeAsRenderedImage(0)
  private val fileCache: mutable.HashMap[Int, (File, SeekableStream)] = new mutable.HashMap[Int, (File, SeekableStream)]()
  private val decoderCache: mutable.WeakHashMap[Int, ImageDecoder] = new mutable.WeakHashMap[Int, ImageDecoder]
  fileCache += ((0, (_file, new FileSeekableStream(_file))))

  // image stack data
  setBitDepth(32)
  val slicesPerFrame: Int = _decoder.getNumPages
  val width: Int = _firstSlice.getWidth
  val height: Int = _firstSlice.getHeight
  override def getSize: Int = framesWithVolumes.size * slicesPerFrame
  val minMax: (Double, Double) = {
    val pixelData = _firstSlice.getData.getPixels(0, 0, width, height, null.asInstanceOf[Array[Float]])

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
  val minMaxRi: Array[Float] = _firstSlice.getProperty("tiff_directory").asInstanceOf[TIFFDirectory].getField(65000).getAsFloats
  val scale: Double = (minMaxRi(1) - minMaxRi(0)) / (minMax._2 - minMax._1)

  def getFile(frame: Frame): SeekableStream = fileCache.getOrElseUpdate(
    frame.time,
    {
      val file = Await.ready(
        acquisitionZip.getFile(frame.pathInZip(frame.volume.get)),
        5 seconds
      ).value.get.get
      (file, new FileSeekableStream(file))
    }
  )._2

  override def getProcessor(n: Int): ImageProcessor = {

    val frameNo = (n-1) / slicesPerFrame
    val sliceNo = (n-1) % slicesPerFrame

    val decoder = decoderCache
      .getOrElse(
        frameNo,
        new TIFFImageDecoder(getFile(framesWithVolumes(frameNo)), null)
      )

    val image = decoder.decodeAsRenderedImage(sliceNo)

    val pixelData = image.getData.getPixels(0, 0, width, height, null.asInstanceOf[Array[Float]]).map(v => minMaxRi(0) + v * scale)

    val processor = new FloatProcessor(width, height, pixelData)
    processor.setMinAndMax(minMaxRi(0), minMaxRi(1))
    processor.flipVertical()
    processor
  }

  val shutdownHook = new Thread {
    override def run(): Unit = {
      fileCache.foreach(fai => {
        fai._2._2.close()
      })
      fileCache.foreach(fai => {
        println(fai._2._1.getName + ": " + fai._2._1.delete())
      })
    }
  }

  Runtime.getRuntime.addShutdownHook(shutdownHook)

  override def finalize(): Unit = {
    fileCache.foreach(fai => {
      fai._2._2.close()
    })
    fileCache.foreach(fai => {
      println(fai._2._1.getName + ": " + fai._2._1.delete())
    })
    Runtime.getRuntime.removeShutdownHook(shutdownHook)
    super.finalize()
  }
}
