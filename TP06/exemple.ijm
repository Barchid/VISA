// Une macro-squelette pour la couleur.
// Version: 0.1
// Date: 14/09/2011
// Author: L. Macaire
 
macro "augmentation_luminance" {
open();
// recuperation du ID de l'image
image = getImageID();

valeur = getNumber ("quelle augmentation (absolue) de luminance",valeur);

Dialog.create("Debut");
Dialog.addMessage(" Cliquer sur OK pour commencer le traitement ");
Dialog.show();





// recuperation de la taille W x H de l'image
W = getWidth();
H = getHeight();

run("Duplicate...", "title=luminance modifiee");
image_luminance_aug = getImageID();
setBatchMode(true);

for (j=0; j<H; j++) {
   for (i=0; i<W; i++) 
	{
	selectImage (image);
	couleur_avant = getPixel(i,j); // mot long de 32 bits (parce que imageJ fait des images couleurs en 32 bits)
	R_avant = (couleur_avant & 0xff0000) >> 16; // récupérer les 8 bits de R
	G_avant = (couleur_avant & 0x00ff00) >> 8; // récupérer les 8 bits de G
	B_avant = (couleur_avant & 0x0000ff) ; // récupérr les 8 bits de B
	
	// Ajouter la valeur à R, G et B
	R_apres = R_avant + valeur;// fonction de R_avant ;
	G_apres = G_avant + valeur;// fonction de G_avant ;
	B_apres = B_avant + valeur;// fonction de B_avant ;

	// reconstruire un mot en 32 bits pour afficher l'image avec luminance modifiée
	couleur_apres = ((R_apres & 0xff ) << 16) + ((G_apres & 0xff) << 8) + B_apres & 0xff;


	selectImage (image_luminance_aug);
	setPixel(i,j,couleur_apres);
      	}
   }

setBatchMode(false);

stop

selectImage (image);
run("8-bit");
getRawStatistics (n,mean,max,std,histo);

selectImage (image_luminance_aug);
run("8-bit");
getRawStatistics (nl,meanl,maxl,stdl,histol);

print ("moyenne avant",mean,"apres",maxl);


Dialog.create("Fin");
Dialog.addMessage(" Cliquer sur OK pour terminer le traitement");
Dialog.show();


}