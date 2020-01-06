/*
A PDF viewer plugin for ImageJ(C), using the jpedal and JAI libraries.
Copyright (C) 2005 Albert Cardona.
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. 

You may contact Albert Cardona at albert at pensament net, at http://www.pensament.net/java/
*/
import ij.plugin.PlugIn;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;
import ij.Menus;
import ij.IJ;
import ij.process.ColorProcessor;
import org.jpedal.PdfDecoder;
import java.awt.image.BufferedImage;
import java.io.File;


public class PDF_Viewer implements PlugIn {

	int scaling = 1; // 1 is 100%
	
	public void run(String argh) {
		
		// select a file
		OpenDialog od = new OpenDialog("Select PDF file.", null);
		// check that any PDF file was selected
		String file_name = od.getFileName();
		if (null == file_name) {
			return;
		} else if (file_name.lastIndexOf(".pdf") != file_name.length() - 4) {
			IJ.showMessage("Not a PDF file!");
			return;
		}
		String dir_name = od.getDirectory();
		// open the PDF
		try {
			PdfDecoder decoder = new PdfDecoder();
			decoder.setDefaultDisplayFont("SansSerif");
			decoder.setPageParameters(scaling, 1);
			decoder.openPdfFile(dir_name + File.separator + file_name);
			String msg = decoder.getPageFailureMessage();
			if (null != msg && !msg.equals("")) {
				IJ.log(msg);
			}
			int n_pages = decoder.getPageCount();
			if (0 == n_pages) {
				IJ.log("PDF file has zero pages.");
				System.gc();
				return;
			}
			// get first page
			BufferedImage bi_first = decoder.getPageAsImage(1);
			int width = bi_first.getWidth();
			int height = bi_first.getHeight();
			msg = decoder.getPageFailureMessage();
			if (null != msg && !msg.equals("")) {
				IJ.log(msg);
			}
			if (null == bi_first) {
				IJ.showMessage("Can't read first page.");
				System.gc();
				return;
			}
			ImageStack stack = new ImageStack(width, height);
			stack.addSlice("1", new ColorProcessor(bi_first));
			bi_first.flush();
			// get rest of pages
			for (int i=2; i<=n_pages; i++) {
				BufferedImage bi = decoder.getPageAsImage(i);
				ColorProcessor cp = null;
				if (bi.getWidth() == width && bi.getHeight() == height) {
					cp = new ColorProcessor(bi);
				} else {
					ColorProcessor cp2 = new ColorProcessor(bi);
					cp2 = (ColorProcessor)cp2.resize(width, cp2.getHeight() * width / cp2.getWidth());
					cp = new ColorProcessor(width, height);
					cp.insert(cp2, 0, 0);
				}
				stack.addSlice(Integer.toString(i+1), cp);
				bi.flush();
			}
			// show stack
			ImagePlus img = new ImagePlus(file_name, stack);
			img.show();

		} catch (Exception e) {
			IJ.log("Error: " + e);
			e.printStackTrace();
		}
		
	}
}
