/** Threshold_Median_Filter - 1.3 - Talon Chandler - Takes a 2D, 3D, or
hyperstack image and replaces pixels that have a neighborhood average less than
the slider threshold with the neighborhood median. 

By default the neighborhood average for each pixel is found by averaging each
adjacent pixel with equal weight. If the "Average weighted by PSF" box is
checked, the average will be taken over a 7x7x7 box weighted by the PSF.

By default the center pixel is ignored during neighborhood averaging. If the
"Average includes center pixel" box is checked, the average will include the
center pixel.

A pixel's neighborhood median is found by taking the median of each adjacent 
pixel excluding the center pixel. 
 */

import ij.*;
import ij.process.*;
import ij.plugin.Duplicator;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import java.awt.*;
import java.util.*;

public class Threshold_Median_Filter implements ExtendedPlugInFilter, DialogListener {

    private static int FLAGS =  DOES_8G | DOES_16 | DOES_32 | KEEP_PREVIEW;           

    private double threshold;
    private boolean preview;
    private boolean psf_average;
    private boolean center_pixel;
    private boolean[] checkValues;
    
    private ImagePlus imp;
    private ImagePlus impOriginal;
    private ImageStack is;
    private ImageStack isOriginal;
    private ImageProcessor ip;
    private ImageProcessor[] ipOriginal;
    
    public int setup (String arg, ImagePlus imp) {
	try{impOriginal = new Duplicator().run(imp);}
	catch(Exception e){IJ.error("Please open an image."); return DONE;}
       	this.imp = imp;
        return FLAGS;
    }

    public int showDialog (ImagePlus imp, String command, PlugInFilterRunner pfr) {

	// Build UI
        GenericDialog gd = new GenericDialog(command+"...");
        gd.addSlider("Threshold", 0.0, max(imp), 0.0);
	gd.setInsets(5,0,0);	
        gd.addPreviewCheckbox(pfr);
	gd.setInsets(0,0,0);	
	gd.addCheckbox("Average weighted by PSF", false);
	gd.setInsets(0,0,0);		
	gd.addCheckbox("Average includes center pixel", false);

	// Add checkbox for each channel
	String[] names = new String[imp.getNChannels()];
	boolean[] defaultValues = new boolean[imp.getNChannels()];
        for(int i = 0; i < imp.getNChannels(); i++) {
	    names[i] = "Channel " + Integer.toString(i+1);
	    defaultValues[i] = false;
	}
	defaultValues[imp.getC()-1] = true; // Check current channel by default
	gd.setInsets(0,0,0);	
	gd.addCheckboxGroup(imp.getNChannels(), 1, names, defaultValues);

	// Make UI responsive
	gd.addDialogListener(this);
        gd.showDialog();           // User input happens here
        if (gd.wasCanceled()) {    // Dialog cancelled?
	    this.imp = thresholdMedian(imp, 0, false); // Reset image
            return DONE;
	}
	if (gd.wasOKed()) {
	    this.imp = thresholdMedian(imp, threshold, true);
	}
        return FLAGS;
    }

    public boolean dialogItemChanged (GenericDialog gd, AWTEvent e) {
	// Store user input every time UI changes
        threshold = gd.getNextNumber();
	preview = gd.getNextBoolean();
	psf_average = gd.getNextBoolean();
	center_pixel = gd.getNextBoolean();	
	checkValues = new boolean[imp.getNChannels()];
	for(int i = 0; i < imp.getNChannels(); i++)
	    checkValues[i] = gd.getNextBoolean();

        return !gd.invalidNumber() && threshold>=0 && threshold <= max(imp);
    }
    
    public void run (ImageProcessor ip) {
	// Main function
	imp = thresholdMedian(imp, threshold, false);
    }

    private ImagePlus thresholdMedian (ImagePlus imp, double threshold, Boolean filterAllSlices) {
	is = impOriginal.getStack();
	isOriginal = imp.getStack();

	int c_min = 0;
	int z_min = 0;
	int t_min = 0;
	int c_max = 0;
	int z_max = 0;
	int t_max = 0;

	// Filter all slices
	if (filterAllSlices) {
	    c_min = 1;
	    z_min = 1;
	    t_min = 1;		    
	    c_max = imp.getNChannels();
	    z_max = imp.getNSlices();
	    t_max = imp.getNFrames();
	}
	// Only filter the current slice (preview mode)
	else { 
	    z_min = imp.getZ();
	    c_min = imp.getC();
	    t_min = imp.getT();
	    z_max = z_min;
	    c_max = c_min;
	    t_max = t_min;	    
	}

	// Find fraction of channels to filter for progress bar
	int trueCount = 0;
	for (int i = 0; i < imp.getNChannels(); i++) {
	    if(checkValues[i])
		trueCount++;
	}
	double channelFrac = trueCount/ (double)checkValues.length;

	// Find averaging kernel
	double[][][] orig_kernel;
	double cp = (center_pixel) ? 1.0: 0.0;
	if (psf_average)
	    orig_kernel = new double[][][] {{{0.0017,0.0019,0.0017,0.0016,0.0017,0.0019,0.0017},{0.0019,0.0015,0.0011,0.0009,0.0011,0.0015,0.0019},{0.0017,0.0011,0.0009,0.0011,0.0009,0.0011,0.0017},{0.0016,0.0009,0.0011,0.0015,0.0011,0.0009,0.0016},{0.0017,0.0011,0.0009,0.0011,0.0009,0.0011,0.0017},{0.0019,0.0015,0.0011,0.0009,0.0011,0.0015,0.0019},{0.0017,0.0019,0.0017,0.0016,0.0017,0.0019,0.0017}},{{0.0059,0.0076,0.0082,0.0083,0.0082,0.0076,0.0059},{0.0076,0.0084,0.0111,0.0134,0.0111,0.0084,0.0076},{0.0082,0.0111,0.022,0.0302,0.022,0.0111,0.0082},{0.0083,0.0134,0.0302,0.0421,0.0302,0.0134,0.0083},{0.0082,0.0111,0.022,0.0302,0.022,0.0111,0.0082},{0.0076,0.0084,0.0111,0.0134,0.0111,0.0084,0.0076},{0.0059,0.0076,0.0082,0.0083,0.0082,0.0076,0.0059}},{{0.0048,0.0069,0.0116,0.0153,0.0116,0.0069,0.0048},{0.0069,0.02,0.0681,0.1041,0.0681,0.02,0.0069},{0.0116,0.0681,0.2415,0.3632,0.2415,0.0681,0.0116},{0.0153,0.1041,0.3632,0.5421,0.3632,0.1041,0.0153},{0.0116,0.0681,0.2415,0.3632,0.2415,0.0681,0.0116},{0.0069,0.02,0.0681,0.1041,0.0681,0.02,0.0069},{0.0048,0.0069,0.0116,0.0153,0.0116,0.0069,0.0048}},{{0.0011,0.0023,0.0101,0.0169,0.0101,0.0023,0.0011},{0.0023,0.0261,0.1155,0.1824,0.1155,0.0261,0.0023},{0.0101,0.1155,0.4392,0.6662,0.4392,0.1155,0.0101},{0.0169,0.1824,0.6662,cp,0.6662,0.1824,0.0169},{0.0101,0.1155,0.4392,0.6662,0.4392,0.1155,0.0101},{0.0023,0.0261,0.1155,0.1824,0.1155,0.0261,0.0023},{0.0011,0.0023,0.0101,0.0169,0.0101,0.0023,0.0011}},{{0.0048,0.0069,0.0116,0.0153,0.0116,0.0069,0.0048},{0.0069,0.02,0.0681,0.1041,0.0681,0.02,0.0069},{0.0116,0.0681,0.2415,0.3632,0.2415,0.0681,0.0116},{0.0153,0.1041,0.3632,0.5421,0.3632,0.1041,0.0153},{0.0116,0.0681,0.2415,0.3632,0.2415,0.0681,0.0116},{0.0069,0.02,0.0681,0.1041,0.0681,0.02,0.0069},{0.0048,0.0069,0.0116,0.0153,0.0116,0.0069,0.0048}},{{0.0059,0.0076,0.0082,0.0083,0.0082,0.0076,0.0059},{0.0076,0.0084,0.0111,0.0134,0.0111,0.0084,0.0076},{0.0082,0.0111,0.022,0.0302,0.022,0.0111,0.0082},{0.0083,0.0134,0.0302,0.0421,0.0302,0.0134,0.0083},{0.0082,0.0111,0.022,0.0302,0.022,0.0111,0.0082},{0.0076,0.0084,0.0111,0.0134,0.0111,0.0084,0.0076},{0.0059,0.0076,0.0082,0.0083,0.0082,0.0076,0.0059}},{{0.0017,0.0019,0.0017,0.0016,0.0017,0.0019,0.0017},{0.0019,0.0015,0.0011,0.0009,0.0011,0.0015,0.0019},{0.0017,0.0011,0.0009,0.0011,0.0009,0.0011,0.0017},{0.0016,0.0009,0.0011,0.0015,0.0011,0.0009,0.0016},{0.0017,0.0011,0.0009,0.0011,0.0009,0.0011,0.0017},{0.0019,0.0015,0.0011,0.0009,0.0011,0.0015,0.0019},{0.0017,0.0019,0.0017,0.0016,0.0017,0.0019,0.0017}}};
	else
	    orig_kernel = new double[][][] {{{1,1,1},{1,1,1},{1,1,1}},{{1,1,1},{1,cp,1},{1,1,1}},{{1,1,1},{1,1,1},{1,1,1}}};
	
	double[][][] kernel = normalize(orig_kernel);
	int kernel_size = orig_kernel.length;
	int kernel_middle_index = (int) (kernel_size-1)/2;

	// For each channel, slice, and time
	int slice_index = 0;
	for (int c=c_min; c<=c_max; c++) {
	    if (checkValues[c-1]) { // Only filter selected channels
		for (int z=z_min; z<=z_max; z++) {
		    for (int t=t_min; t<=t_max; t++) {

			// Update progress bar
			IJ.showProgress((double)slice_index/(channelFrac*imp.getImageStackSize()));
			slice_index += 1;

			// Find neighborhood slices
			ipOriginal = new ImageProcessor[kernel_size];
			int z_dim = imp.getNSlices();
			for (int kernel_z=0; kernel_z<kernel_size; kernel_z++) {
			    int z_index = reflectedIndex(kernel_z, kernel_size, z, z_dim);
			    ipOriginal[kernel_z] = is.getProcessor(imp.getStackIndex(c, z_index, t));
			}

			// Go through each pixel in the 2D image
			ip = isOriginal.getProcessor(imp.getStackIndex(c, z, t));
			int xdim = ip.getWidth();
			int ydim = ip.getHeight();
			for (int x=0; x<xdim; x++) {
			    for (int y=0; y<ydim; y++) {
				ArrayList<Integer> m_neighbors = new ArrayList<Integer>(); // Median neighbors
				ArrayList<Double> a_neighbors = new ArrayList<Double>(); // Average neighbors				

				// Find kernel weighted neighborhood pixels
				for (int kernel_x=0; kernel_x<kernel_size; kernel_x++) {
				    for (int kernel_y=0; kernel_y<kernel_size; kernel_y++) {
					for (int kernel_z=0; kernel_z<kernel_size; kernel_z++) {
					    int x_index = reflectedIndex(kernel_x, kernel_size, x, xdim);
					    int y_index = reflectedIndex(kernel_y, kernel_size, y, ydim);
					    a_neighbors.add(kernel[kernel_z][kernel_x][kernel_y]*ipOriginal[kernel_z].getPixel(x_index, y_index));
					}
				    }
				}

				// Find median neighborhood pixels
				int median_size = 3;
				int median_middle_index = (int) (median_size-1)/2;
				for (int kernel_x=0; kernel_x<median_size; kernel_x++) {
				    for (int kernel_y=0; kernel_y<median_size; kernel_y++) {
					for (int kernel_z=0; kernel_z<median_size; kernel_z++) {
					    int x_index = reflectedIndex(kernel_x, median_size, x, xdim);
					    int y_index = reflectedIndex(kernel_y, median_size, y, ydim);
					    if (!(x_index == x && y_index == y && kernel_z == (int) median_middle_index)) // Exclude center pixel
						m_neighbors.add(ipOriginal[((kernel_size - median_size)/2) + kernel_z].getPixel(x_index, y_index));
					}
				    }
				}

				// Convert median neighbors to floats
				ArrayList<Float> f_m_neighbors = new ArrayList<Float>();
				for(int i = 0; i < m_neighbors.size(); i++) {
				    int old = m_neighbors.get(i);
				    if (ipOriginal[1] instanceof FloatProcessor)
					f_m_neighbors.add(Float.intBitsToFloat(old));
				    else
					f_m_neighbors.add((float) old);
				}

				// Main filter. If neighborhood mean is less than threshold,
				// replace with the neighborhood median. Otherwise, leave 
				// the pixel alone.
				if(sum(a_neighbors) < threshold) { // The sum is actually a mean of pixels because the kernel is normalized
				    int i = (int) median(f_m_neighbors);
				    ip.putPixel(x,y,i);
				}
				else
				    ip.putPixel(x,y,ipOriginal[kernel_middle_index].getPixel(x,y));
			    }
			}
		    }
		}
	    }
	}
	return imp;
    }

    // Helper functions
    
    // Returns the sum of an ArrayList    
    private double sum(ArrayList<Double> array){

	double sum = 0;
	for(int i = 0; i < array.size(); i++) {
	    sum += array.get(i);
	}
	return sum;
    }

    // Returns the median of an ArrayList
    private float median(ArrayList<Float> array){
	Collections.sort(array);
	int len = array.size();
	if(len%2==0)
	    return((array.get((len/2)-1)+array.get(len/2))/2);
	else
	    return array.get(((len-1)/2));
    }

    // Returns the maximum pixel value in an image stack. 
    private float max(ImagePlus imp) {
	ImageStack is = imp.getStack();
	float maxPix = 0;
	float pix = 0;
	int nslices = is.getSize();
	for (int z=1; z<=is.getSize(); z++) {
	    ImageProcessor ip = is.getProcessor(z);	    
	    for (int x=0; x<ip.getWidth(); x++) {
		for (int y=0; y<ip.getHeight(); y++) {
		    if (ip instanceof FloatProcessor)
			pix = Float.intBitsToFloat(ip.getPixel(x,y));
		    else
			pix = ip.getPixel(x,y);
		    if (pix > maxPix)
			maxPix = pix;
		}
	    }
	}
	return maxPix;
    }

    // Takes:
    // kernel_i - The current index of the kernel
    // kernel_i_size - The size of the kernel
    // i - The current index in the entire signal 
    // i_size - The size of the entire signal
    //
    // Returns the index of the entry that is at position kernel_i when the
    // kernel is centered on i. If the index is outside the bounds of the
    // signal, the reflected index is returned. 
    private int reflectedIndex(int kernel_i, int kernel_i_size, int i, int i_size) {
	int index = kernel_i + i - ((kernel_i_size - 1)/2);
	if (!((index > 0) && (index < i_size))) {
	    if (!((-index > 0) && (-index < i_size))) {
		return -index; // Lower bound reflection
	    }else if (!(((i_size - (index - i_size)) > 0) && ((i_size - (index - i_size)) < i_size))) {
		return i_size - (index - i_size); // Upper bound reflection
	    }else {
		IJ.log("Error: should not reach here");
		return 0;
	    }
	}
	return index;
    }

    // Normalizes a 3D array
    private double[][][] normalize(double[][][] input) {
	double sum = 0;
	double[][][] output = new double[input.length][input.length][input.length];

	// Calculate the sum of the array
	for (int i=0; i<input.length; i++) {
	    for (int j=0; j<input.length; j++) {
		for (int k=0; k<input.length; k++) {
		    sum = sum + input[i][j][k];
		}
	    }
	}
	// Divide each entry by the array sum
	for (int i=0; i<input.length; i++) {
	    for (int j=0; j<input.length; j++) {
		for (int k=0; k<input.length; k++) {
		    output[i][j][k] = (double) input[i][j][k]/sum;
		}
	    }
	}
	return output;
    }
	
    public void setNPasses (int nPasses) {}
}
