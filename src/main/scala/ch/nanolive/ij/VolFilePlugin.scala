package ch.nanolive.ij

import java.awt.{FileDialog, Image, Frame => AwtFrame}
import java.io.{File, FileInputStream, IOException}
import java.util.concurrent.{ExecutorService, Executors}
import java.util.zip.ZipFile

import ch.nanolive.acquisition.models.{Acquisition, Frame}
import ch.nanolive.acquisition.models.metas.MetaData
import ch.nanolive.acquisition.services.io.AcquisitionZip
import ij.gui.StackWindow
import ij.io.{FileInfo, Opener, TiffDecoder}
import ij.{IJ, ImagePlus, ImageStack, VirtualStack}
import ij.plugin.PlugIn

//import loci.formats.IFormatReader
//import loci.plugins.util.{BFVirtualStack, ImageProcessorReader, VirtualImagePlus, VirtualReader}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by Paul on 24/05/2017.
  */
class VolFilePlugin extends ImagePlus with PlugIn {

  implicit val ec: ExecutionContext = new ExecutionContext() {
    private val threadPool: ExecutorService = Executors.newSingleThreadExecutor
    override def execute(runnable: Runnable) { threadPool.submit(runnable) }
    override def reportFailure(cause: Throwable) { cause.printStackTrace() }
    override def prepare: ExecutionContext = this
  }

  override def run(arg: String): Unit = {
    val fileDialog = new FileDialog(null.asInstanceOf[AwtFrame])
    fileDialog.setModal(true)
    fileDialog.show()
    if (fileDialog.getFile == null)
      return

    val fileName = fileDialog.getDirectory + fileDialog.getFile

    val volFile = new AcquisitionZip(new File(fileName))
    volFile
      .getAcquisition
      .flatMap((acq: Acquisition) => {
        Future.sequence(
          acq
            .frames
            .map(volFile.getFrame)
        )
      })
      .flatMap((lf: List[Frame]) => {
        Future.sequence(
          lf
            .filter(_.volume.isDefined)
            .map(f => {
              volFile.getFile(f.pathInZip(f.volume.get)).zip(Future.successful(f))
            })
          )
      })
      .map((lf: List[(File, Frame)]) => {
        lf.foreach(f => {
          val decoder = new TiffDecoder(new FileInputStream(f._1), f._1.getName)
          val ip = new Opener().openTiffStack(decoder.getTiffInfo)
          ip.setTitle("Frame at t=" + f._2.time + "ms")
          ip.show()
        })

      })
      .onFailure({
        case e =>
          e.printStackTrace()
      })
  }

  override def getImage: Image = super.getImage

  override def getImageStack: ImageStack = super.getImageStack
}
