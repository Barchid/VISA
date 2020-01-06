// Importation des paquets n�cessaires. Le plugin n'est pas lui-m�me un paquet (pas de mot-cl� package)
import ij.*; // pour classes ImagePlus et IJ
import ij.plugin.filter.PlugInFilter; // pour interface PlugInFilter
import ij.process.*; // pour classe ImageProcessor
import java.awt.*; // pour classe Rectangle

// Nom de la classe = nom du fichier. Impl�mente l'interface PlugInFilter
public class TESTPlugin_ implements PlugInFilter {
	public int setup(String arg, ImagePlus imp) {
		if (IJ.versionLessThan("1.37j")) // Requiert la version 1.37j d'ImageJ
			return DONE; // Ne pas appeler la m�thode run()
		else // Accepte tous types d'images, piles d'images et RoIs, m�me non rectangulaires
			return DOES_8G+DOES_RGB+DOES_STACKS+SUPPORTS_MASKING;
	}
	
	public void run(ImageProcessor ip) {
		Rectangle r = ip.getRoi(); // R�gion d'int�r�t s�lectionn�e (r.x=r.y=0 si aucune)
		for (int y=r.y; y<(r.y+r.height); y++)
			for (int x=r.x; x<(r.x+r.width); x++)
				ip.set(x, y, ~ip.get(x,y)+10); // Compl�ment bit � bit des valeurs des pixels
				//ip.set(x, y, 10);
	}
}
