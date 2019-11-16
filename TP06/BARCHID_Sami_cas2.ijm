/**
 * @author BARCHID Sami
 * 
 * Segmentation d'une image couleur d'un test d'Ishihara (correspondant au "cas 2" dans l'exercice, c'est-à-dire un nombre vert sur un fond composé de points jaunes et rouges/roses).
 * 
 * En résumé, le but est d'utiliser la composante red du color space RGB et d'appliquer un seuil. En résultat, nous obtiendrons l'image où les pixels correspondant au chiffre seront blanc, tandis que
 * le reste sera noir.
 * 
 * NOTE : on obtient des bons résultats aussi sur certaines images du cas 2 en seuillant sur la composante "B" (brightness) du color space HSB.
 * Cependant, ce seuillage sur B ne fonctionne pas sur toutes les images du cas 2 (par exemple : celle du 73 n'est pas assez bien binarisée)
 */

titre = getTitle();

// SÉPARER [les trois canaux du color space RGB] (l'image est déjà en RGB donc pas besoin d'utiliser le plugin "Color space converter")
run("Split Channels"); // NOTE IMPORTANTE : "Split channels" produit des images 8 bits et non 32 bits

// SUPPRIMER [le canal blue] (optionnel, c'est juste pour se débarrasser des infos inutiles)
selectWindow(titre + " (blue)");
close();

// SUPPRIMER [le canal green)] (optionnel, c'est juste pour se débarrasser des infos inutiles)
selectWindow(titre + " (green)");
close();

// SELECTIONNER [le canal red]
selectWindow(titre + " (red)");

// (à ce niveau là, il ne reste que le canal red qui nous intéresse)
// APPLIQUER SEUILLAGE [0, 213]
setThreshold(0, 213);
run("Convert to Mask");
