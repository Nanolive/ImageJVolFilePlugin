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
class VolFilePlugin extends PlugIn {

  implicit val ec: ExecutionContext = new ExecutionContext() {
    private val threadPool: ExecutorService = Executors.newCachedThreadPool()
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
        val ip = IJ.createHyperStack(fileDialog.getFile, 512, 512, 1, 96, acq.frames.size, 16)
        Future.sequence(
          acq
            .frames
            .map(volFile.getFrame)
        )
      })
      .map(_.filter(_.volume.isDefined))
      .map(lf => {
        val imageStack = new VolFileImageStack(volFile, lf)
        val hyperStackImg = IJ.createHyperStack("", 512, 512, 1, 96, lf.size, 32)
        hyperStackImg.setStack(imageStack)
        hyperStackImg.setTitle(fileDialog.getFile)
        hyperStackImg.show()
      })
      .onFailure({
        case e =>
          e.printStackTrace()
          IJ.showMessage(e.getMessage)
          IJ.showStatus(e.getMessage)
      })
  }
}
