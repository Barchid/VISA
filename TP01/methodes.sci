/// \brief Calcule le NMSE
function ComputeNMSE(Attendue,Obtenue)
    diffe = (Attendue - Obtenue) ** 2;
    nmse = diffe / (Attendue**2);
    disp(nmse)
endfunction

/// \brief Calcule le NMSE
function ComputeMSE(Attendue,Obtenue)
    diffe = (Attendue - Obtenue) ** 2;
    nmse = diffe / (Attendue**2);
    disp(nmse)
endfunction

// -----------------------------------------------------------------------
/// \brief Calcule un terme de contrainte a partir d'une homographie.
///
/// \param H: matrice 3*3 définissant l'homographie.
/// \param i: premiere colonne.
/// \param j: deuxieme colonne.
/// \return vecteur definissant le terme de contrainte.
// -----------------------------------------------------------------------
function v = ZhangConstraintTerm(H, i, j)
  // application de la formule
  v = [
    H(1,i)*H(1,j)   ,   H(1,i)*H(2,j) + H(2,i)*H(1,j)   ,   H(2,i)*H(2,j)   ,   H(3,i)*H(1,j)+H(1,i)*H(3,j)   ,   H(3,i)*H(2,j)+H(2,i)*H(3,j)   ,   H(3,i)*H(3,j)
  ];
endfunction

// -----------------------------------------------------------------------
/// \brief Calcule deux equations de contrainte a partir d'une homographie
///
/// \param H: matrice 3*3 définissant l'homographie.
/// \return matrice 2*6 definissant les deux contraintes.
// -----------------------------------------------------------------------
function v = ZhangConstraints(H)
  v = [ZhangConstraintTerm(H, 1, 2); ...
    ZhangConstraintTerm(H, 1, 1) - ZhangConstraintTerm(H, 2, 2)];
endfunction

// -----------------------------------------------------------------------
/// \brief Calcule la matrice des parametres intrinseques.
///
/// \param b: vecteur resultant de l'optimisation de Zhang.
/// \return matrice 3*3 des parametres intrinseques.
// -----------------------------------------------------------------------
function A = IntrinsicMatrix(b)
    // Contenu de la matrice b
    B11 = b(1);
    B12 = b(2);
    B22 = b(3);
    B13 = b(4);
    B23 = b(5);
    B33 = b(6);
    
    // Calcul des paramètres intrinsèques
    v0 = (B12*B13 - B11*B23) / (B11*B22 - B12^2);
    lambda = B33 - (B13^2 + v0*(B12*B13 - B11*B23))/B11;
    alpha = sqrt(lambda/B11);
    bbeta = sqrt((lambda*B11)/(B11*B22 - B12^2));
    ggamma = -B12*alpha^2*bbeta/lambda;
    u0 = ggamma*v0/bbeta - B13 * alpha^2 / lambda;
    
    // Assemblage de la matrice des paramètres intrinsèques A
    A = [
        alpha,ggamma,u0;
        0, bbeta, v0;
        0, 0, 1
    ];
endfunction

// -----------------------------------------------------------------------
/// \brief Calcule la matrice des parametres extrinseques.
///
/// \param iA: inverse de la matrice intrinseque.
/// \param H: matrice 3*3 definissant l'homographie.
/// \return matrice 3*4 des parametres extrinseques.
// -----------------------------------------------------------------------
function E = ExtrinsicMatrix(iA, H)
    h1 = H(:,1); // 1ère colonne de la matrice H (h1)
    h2 = H(:,2);
    h3 = H(:,3);
    lambda = 1/norm(iA*h1);
    
    // Calcul des colonnes de la matrice des params extrinsèques
    r1 = lambda * iA * h1;
    r2 = lambda * iA * h2;
    r3 = cross(r1, r2);
    t = lambda * iA * h3;
    
    // Assemblade de la matrice
    E = [r1,r2,r3,t];
    //E = zeros(3,4);
endfunction

