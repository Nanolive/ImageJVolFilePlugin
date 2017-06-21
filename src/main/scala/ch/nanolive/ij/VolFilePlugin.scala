package ch.nanolive.ij

import java.awt.{FileDialog, Frame => AwtFrame}
import java.io.File
import java.util.concurrent.{ExecutorService, Executors}

import ch.nanolive.acquisition.models.{AcquiredFile, Acquisition, Frame}
import ch.nanolive.acquisition.services.io.AcquisitionZip
import ij.plugin.PlugIn
import ij.{IJ, ImagePlus}

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

    IJ.showStatus("Reading Metadata")

    val volFile = new AcquisitionZip(new File(fileName))
    val acquisitionF = volFile.getAcquisition
    val framesF = acquisitionF
      .flatMap((acq: Acquisition) => {
        Future.sequence(
          acq
            .frames
            .map(volFile.getFrame)
        )
      })

    // display RI Vol
    framesF
      .map(_.filter(_.volume.isDefined))
      .map(lf => {
        IJ.showStatus("Opening image")
        val imageStack = new VolFileImageStack(volFile, lf)
        val image = new ImagePlus("RI: " + fileDialog.getFile, imageStack)
        image.setOpenAsHyperStack(true)
        image.setDimensions(1, imageStack.slicesPerFrame, lf.size)
        IJ.showStatus("Ready")
        image.show()
      })
      .onFailure({
        case e =>
          e.printStackTrace()
          IJ.showMessage(e.getMessage)
          IJ.showStatus(e.getMessage)
      })

    // display fluo images
    acquisitionF.map(
      _.fluoChannels.foreach(channel => {
        framesF
          .map((frames: List[Frame]) => frames.filter(_.fluoImage(channel).isDefined))
          .map(frames => {
            if (frames.nonEmpty) {
              IJ.showStatus("Opening image")
              val imageStack = new VolFileFluoStack(volFile, channel, frames)
              val image = new ImagePlus(channel + ": " + fileDialog.getFile, imageStack)
              image.setOpenAsHyperStack(true)
              image.setDimensions(1, imageStack.slicesPerFrame, frames.size)
              IJ.showStatus("Ready")
              image.show()
            }
          })

      })
    )
  }
}
