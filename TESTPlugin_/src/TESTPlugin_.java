// Importation des paquets nécessaires. Le plugin n'est pas lui-même un paquet (pas de mot-clé package)
import ij.*; // pour classes ImagePlus et IJ
import ij.plugin.filter.PlugInFilter; // pour interface PlugInFilter
import ij.process.*; // pour classe ImageProcessor
import java.awt.*; // pour classe Rectangle

// Nom de la classe = nom du fichier. Implémente l'interface PlugInFilter
public class TESTPlugin_ implements PlugInFilter {
	public int setup(String arg, ImagePlus imp) {
		if (IJ.versionLessThan("1.37j")) // Requiert la version 1.37j d'ImageJ
			return DONE; // Ne pas appeler la méthode run()
		else // Accepte tous types d'images, piles d'images et RoIs, même non rectangulaires
			return DOES_8G+DOES_RGB+DOES_STACKS+SUPPORTS_MASKING;
	}
	
	public void run(ImageProcessor ip) {
		Rectangle r = ip.getRoi(); // Région d'intérêt sélectionnée (r.x=r.y=0 si aucune)
		for (int y=r.y; y<(r.y+r.height); y++)
			for (int x=r.x; x<(r.x+r.width); x++)
				ip.set(x, y, ~ip.get(x,y)); // Complément bit à bit des valeurs des pixels
	}
}