/**
 * @author BARCHID Sami
 * 
 * Segmentation d'une image couleur d'un test d'Ishihara (correspondant au "cas 1" dans l'exercice, c'est-à-dire un nombre composé d'un chiffre rouge et un violet sur un fond composé de points gris/vert).
 * 
 * En résumé, le but est de convertir l'image en LAB pour ne sélectionner que la composante A, ce qui va permettre d'appliquer 
 * le seuil automatique qui va réussir à binariser l'image. En résultat, nous obtiendrons l'image où les pixels correspondant au nombre seront blanc, tandis que le reste sera noir.
 */

titre = getTitle();

// CONVERTIR [l'image de RGB en LAB]
run("Color Space Converter", "from=RGB to=LAB white=D65 separate");

// SUPPRIMER [le canal (b*)] (optionnel, c'est juste pour se débarrasser des infos inutiles)
selectWindow(titre + " (b*)");
close();

// SUPPRIMER [le canal (L*)] (optionnel, c'est juste pour se débarrasser des infos inutiles)
selectWindow(titre + " (L*)");
close();

// SELECTIONNER [le canal (a*)]
selectWindow(titre + " (a*)");

// (à ce niveau là, il ne reste que le canal a* qui nous intéresse)
// APPLIQUER [le seuil par défaut]
setAutoThreshold("Default dark");
setOption("BlackBackground", true);
run("Convert to Mask");
