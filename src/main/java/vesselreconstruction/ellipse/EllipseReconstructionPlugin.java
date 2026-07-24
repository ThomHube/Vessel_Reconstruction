package vesselreconstruction.ellipse;

import ij.IJ;
import ij.plugin.PlugIn;

/**
 * Diagnostic-only entry point for the Robust Ellipse Reconstruction prototype.
 * Ellipse fitting and reconstruction are not implemented yet.
 */
public final class EllipseReconstructionPlugin implements PlugIn {

    @Override
    public void run(String arg) {
        IJ.log("Robust Ellipse Reconstruction Prototype started.");
        IJ.log("Project directory is independent from the legacy contour project.");
        IJ.log("Fixed classifier mapping:");
        IJ.log("Class " + ClassifierLabels.WOOD + " = " + ClassifierLabels.nameOf(ClassifierLabels.WOOD));
        IJ.log("Class " + ClassifierLabels.RAY + " = " + ClassifierLabels.nameOf(ClassifierLabels.RAY));
        IJ.log("Class " + ClassifierLabels.LUMEN + " = " + ClassifierLabels.nameOf(ClassifierLabels.LUMEN));
        IJ.log("Class " + ClassifierLabels.BACKGROUND + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.BACKGROUND));
        IJ.log("Ellipse fitting is not implemented yet.");
        IJ.log("Reconstruction is not applied.");

        String message = "Clean ellipse prototype loaded successfully.\n"
                + "Class " + ClassifierLabels.WOOD + " = " + ClassifierLabels.nameOf(ClassifierLabels.WOOD) + "\n"
                + "Class " + ClassifierLabels.RAY + " = " + ClassifierLabels.nameOf(ClassifierLabels.RAY) + "\n"
                + "Class " + ClassifierLabels.LUMEN + " = " + ClassifierLabels.nameOf(ClassifierLabels.LUMEN) + "\n"
                + "Class " + ClassifierLabels.BACKGROUND + " = "
                + ClassifierLabels.nameOf(ClassifierLabels.BACKGROUND) + "\n"
                + "Ellipse fitting is not implemented yet.\n"
                + "Reconstruction is not applied.";

        IJ.showMessage("Robust Ellipse Reconstruction Prototype", message);
    }
}
