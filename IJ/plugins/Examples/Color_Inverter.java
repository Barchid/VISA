import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.filter.PlugInFilter;
import java.awt.*;

// This sample ImageJ plugin filter inverts RGB images.

public class Color_Inverter implements PlugInFilter {

	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		return DOES_RGB+SUPPORTS_MASKING+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		// Save the dimension and the ROI of the original image to local variables
		int w = ip.getWidth();
		int h = ip.getHeight();
		Rectangle roi = ip.getRoi();
		
		// Create a new RGB image of the same size, with one slice and initially black and get the new image’s processor
		ImagePlus inverted = NewImage.createRGBImage("Inverted image", w, h, 1, NewImage.FILL_BLACK);
		ImageProcessor inv_ip = inverted.getProcessor();
		
		//Copy the image from the original ImageProcessor to (0,0) in the new image, using COPY blitting mode
		inv_ip.copyBits(ip,0,0,Blitter.COPY);
		
		//Get the pixel array of the new image (identical to the old one). It’s a RGB image, so we get an int array
		int[] pixels = (int[]) inv_ip.getPixels();

		// Go through the bounding rectangle of the ROI with two nested loops
		for (int i=roi.y; i<roi.y+roi.height; i++) {
			int offset =i*w;
			for (int j=roi.x; j<roi.x+roi.width; j++) {
				int pos = offset+j;	//position of the current pixel in the one-dimensional array
				int c = pixels[pos]; //get the value of the current pixel
				// Extract the three color components
				int r = (c & 0xff0000)>>16;
				int g = (c & 0x00ff00)>>8;
				int b = (c & 0x0000ff);
				//Invert each component by subtracting it’s value from 255
				r=255-r;
				g=255-g;
				b=255-b;
				//pack the modified color components into an integer again
				pixels[pos] = ((r & 0xff) << 16) + ((g & 0xff) << 8) + (b & 0xff);
				//Our image is still not visible, so call show to open an ImageWindow that displays it.
				inverted.show();
				//Force the pixel array to be read and the image to be updated
				inverted.updateAndDraw();	
			}
		}

	}
	
	void showAbout() {
		IJ.showMessage("About Color_Inverter...",
		"This sample plugin filter inverts RGB images. Look\n" +
		"at the 'Color_Inverter.java' source file to see how easy it is\n" +
		"in ImageJ to process non-rectangular ROIs, to process\n" +
		"all the slices in a stack, and to display an About box."
		);
	}
}

