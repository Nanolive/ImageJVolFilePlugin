package ch.nanolive.ij

import ch.nanolive.acquisition.models.Acquisition
import ij.ImagePlus
import ij.plugin.PlugIn

/**
  * Created by Paul on 24/05/2017.
  */
class VolFilePlugin extends ImagePlus with PlugIn {
  override def run(arg: String): Unit = {
    val acq: Acquisition = null
    throw new RuntimeException("hello world")
  }
}
