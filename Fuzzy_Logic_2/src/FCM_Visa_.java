import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.frame.*;
import ij.process.ImageProcessor.*;
import ij.plugin.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.lang.Math.*;
import java.lang.Object.*;
import java.lang.String.*;
import java.awt.TextArea;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.Window.*;
   

public class FCM_Visa_ implements PlugIn 
{

	class Vec 
	{
		int[] data = new int[3];	//*pointeur sur les composantes*/
	} 

	////////////////////////////////////////////////////
Random r = new Random();    
public int rand(int min, int max) 
    {
        return min + (int)(r.nextDouble()*(max-min));
    }

	
////////////////////////////////////////////////////////////////////////////////////////////
                    public void run(String arg) 
	{

		// LES PARAMETRES

		
		ImageProcessor ip;
		ImageProcessor ipseg;
		ImageProcessor ipJ;  
		ImagePlus imp;
		ImagePlus impseg;
		ImagePlus impJ;
		IJ.showMessage("Algorithme FCM","If ready, Press OK");
                                        ImagePlus cw;

		imp = WindowManager.getCurrentImage();
		ip = imp.getProcessor();

		int width = ip.getWidth();
		int height = ip.getHeight();
 		

		impseg=NewImage.createImage("Image segment�e par FCM",width,height,1,24,0);
		ipseg = impseg.getProcessor();
		impseg.show();

		
		int nbclasses,nbpixels,iter;
		double stab,seuil,valeur_seuil;
		int i,j,k,l,imax,jmax,kmax;

		String demande =JOptionPane.showInputDialog("Nombre de classes : ");
		nbclasses =Integer.parseInt(demande);
		nbpixels = width * height; // taille de l'image en pixels
		
		demande =JOptionPane.showInputDialog("Valeur de m : ");
		double m =Double.parseDouble(demande);

		demande =JOptionPane.showInputDialog("Nombre it�ration max : ");
		int itermax =Integer.parseInt(demande);

		demande =JOptionPane.showInputDialog("Valeur du seuil de stabilit� : ");
		valeur_seuil =Double.parseDouble(demande);

		demande =JOptionPane.showInputDialog("Randomisation am�lior�e ? ");
		int valeur=Integer.parseInt(demande);


		double c[][] = new double[nbclasses][3]; 
		double cprev[][] = new double[nbclasses][3]; 
		int cidx[] = new int[nbclasses];
		//double m;
		double Dmat[][] = new double[nbclasses][nbpixels];
		double Dprev[][] = new double[nbclasses][nbpixels];
		double Umat[][] = new double[nbclasses][nbpixels];
		double Uprev[][] = new double[nbclasses][nbpixels];
		double red[] = new double[nbpixels];
		double green[] = new double[nbpixels];
		double blue[] = new double[nbpixels];
		int[] colorarray = new int[3];
		int[] init=new int[3];
		double figJ[]=new double[itermax];
		for(i=0;i<itermax;i++)
		{
		  figJ[i]=0;
		}

		// R�cup�ration des donn�es images 
		l = 0;
		for(i = 0; i < width; i++)
		{
			for(j = 0; j < height; j++)
			{
				ip.getPixel(i,j,colorarray);
				red[l] = (double)colorarray[0];
				green[l] =(double) colorarray[1];
				blue[l] = (double)colorarray[2];
				l++;
			}
		}
		////////////////////////////////
		// FCM
		///////////////////////////////
		
		imax = nbpixels;  // nombre de pixels dans l'image
		jmax = 3;  // nombre de composantes couleur
		kmax=nbclasses;
		double data[][] = new double[nbclasses][3];
		int[] fixe=new int[3]; 
		int xmin = 0;
       	                    int xmax = width;
       		int ymin = 0;
		int ymax = height;
		int rx, ry;	
		int x,y;
		int epsilonx,epsilony;


		// Initialisation des centro�des (al�atoirement )

		for(i=0;i<nbclasses;i++)
		{
                                         if(valeur==1) 
		{  epsilonx=rand((int)(width/(i+2)),(int)(width/2));
                                           epsilony=rand((int)(height/(4)),(int)(height/2));
		}
		else
		{
		   epsilonx=0;
                                           epsilony=0;
		}
		  rx = rand(xmin+epsilonx, xmax-epsilonx);
            		  ry = rand(ymin+epsilony, ymax-epsilony);
		  ip.getPixel(rx,ry,init);
		  c[i][0] = init[0]; c[i][1] =init[1]; c[i][2] = init[2];
		}
			
		// Calcul de distance entre data et centroides
		for(l = 0; l < nbpixels; l++)
		{
			for(k = 0; k < kmax; k++)
			{
				double r2 = Math.pow(red[l] - c[k][0], 2);
				double g2 = Math.pow(green[l] - c[k][1], 2);
				double b2 = Math.pow(blue[l] - c[k][2], 2);
				Dprev[k][l] = r2 + g2 + b2;
			}
		}

		// Initialisation des degr�s d'appartenance
		//A COMPLETER

		////////////////////////////////////////////////////////////
		// FIN INITIALISATION FCM
		///////////////////////////////////////////////////////////


		/////////////////////////////////////////////////////////////
		// BOUCLE PRINCIPALE
		////////////////////////////////////////////////////////////
		iter = 0;
		stab = 2;
		seuil = valeur_seuil;
		

		/////////////////// A COMPLETER ///////////////////////////////
		while ((iter < itermax) && (stab > seuil)) 
		{

			
		// Update  the matrix of centroids
			// Compute Dmat, the matrix of distances (euclidian) with the centro�ds

			// Calculate difference between the previous partition and the new partition (performance index)

			iter++;
		////////////////////////////////////////////////////////
	
		// Affichage de l'image segment�e 
		double[] mat_array=new double[nbclasses];
		l = 0;
		for(i=0;i<width;i++)
		{
		  for(j = 0; j<height; j++)
		{
			for(k = 0; k<nbclasses; k++)
			{ 
			 mat_array[k]=Umat[k][l];
			}
			int indice= IndiceMaxOfArray(mat_array,nbclasses) ;
			int array[] = new int[3];
			array[0] = (int)c[indice][0];
			array[1] = (int)c[indice][1];
			array[2] = (int)c[indice][2];
			ipseg.putPixel(i, j, array);
			l++;
		}
		}
		impseg.updateAndDraw();
	//////////////////////////////////
	}  // Fin boucle

	double[] xplot= new double[itermax];
                    double[] yplot=new double[itermax];
	for(int w = 0; w < itermax; w++)
	{
	xplot[w]=(double)w;	yplot[w]=(double) figJ[w];
	}
	Plot plot = new Plot("Performance Index (FCM)","iterations","J(P) value",xplot,yplot);
	plot.setLineWidth(2);
        plot.setColor(Color.blue);
                    plot.show();
	} // Fin FCM
int indice;
double min,max;

//Returns the maximum of the array

public int  IndiceMaxOfArray(double[] array,int val) 
{
    max=0;
    for (int i=0; i<val; i++)
    {
       if (array[i]>max) 
      {max=array[i];
       indice=i;
    }
    }
return indice;
}

}


