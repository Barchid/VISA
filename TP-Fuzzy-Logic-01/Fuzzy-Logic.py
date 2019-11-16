import matplotlib
import matplotlib.pyplot as plt
import numpy as np

plt.style.use('seaborn-whitegrid')  # pour l'affichage des graphique

############################################################
# Définition de la variable linguistique
V = "Température (°C)"
U_v = range(40)  # Défini de 0 à 45 °C

# Création des fonctions d'appartenances
# Fuzzy set pour "basse"


def basse(x):
    if x <= 10:
        return 1.
    if x >= 20:
        return 0.
    # équation de la droite qui va entre 10 et 20 °C (y = ax + b)
    return -1./10. * x + 2

# Fuzzy set pour "moyenne"


def moyenne(x):
    if x <= 10 or x >= 30:
        return 0

    if x <= 20:
        return 1./10. * x - 1
    else:
        return -1./10. * x + 3

# Fuzzy set pour "élevée"


def elevee(x):
    if x <= 20:
        return 0
    if x >= 30:
        return 1
    return 1./10. * x - 2


T_v = {
    "Basse": basse,
    "Moyenne": moyenne,
    "Elevee": elevee
}

temperature = (V, U_v, T_v)
############################################################

############################################################
# Affichage de l'univers du discours
plt.title("Partition floue de l'univers du discours")
plt.ylabel('Degré d\'appartenance')
plt.ylim(-0.2, 1.2)
plt.xlabel(V)
plt.xlim(0, 40)

plt.plot([0, 10, 20, 30, 40], [basse(0), basse(10), basse(
    20), basse(30), basse(40)], label="Basse", color='blue')
plt.plot([0, 10, 20, 30, 40], [moyenne(0), moyenne(10), moyenne(20),
                               moyenne(30), moyenne(40)], label="Moyenne", color='green')
plt.plot([0, 10, 20, 30, 40], [elevee(0), elevee(10), elevee(
    20), elevee(30), elevee(40)], label="Élevée", color='red')

plt.legend()
plt.show()


# Donner les degrés d'appartenance aux différents sous-ensemble pour une température mesurée de 16°C
print("µ_basse(16) = " + str(round(basse(16), 2)))
print("µ_moyenne(16) = " + str(round(moyenne(20), 2)))
print("µ_elevee(16) = " + str(round(elevee(16), 2)))


# Température basse OU température moyenne
def fuzzy_union(A, B, x):
    """
    Calcule l'union en logique floue pour un x donné entre les deux fuzzy sets A et B
    ==> A U B
    """
    return max(A(x), B(x))


plt.title("Température basse OU température moyenne")
plt.ylabel('Degré d\'appartenance')
plt.ylim(-0.2, 1.2)
plt.xlabel(V)
plt.xlim(0, 40)

plt.plot([0, 5, 10, 15, 20, 25, 30, 35, 40], [
    fuzzy_union(basse, moyenne, 0),
    fuzzy_union(basse, moyenne, 5),
    fuzzy_union(basse, moyenne, 10),
    fuzzy_union(basse, moyenne, 15),
    fuzzy_union(basse, moyenne, 20),
    fuzzy_union(basse, moyenne, 25),
    fuzzy_union(basse, moyenne, 30),
    fuzzy_union(basse, moyenne, 35),
    fuzzy_union(basse, moyenne, 40)
], color='blue')

plt.show()

# Température basse ET température moyenne


def fuzzy_intersection(A, B, x):
    """
    Calcule l'intersection en logique floue pour un x donné entre les deux fuzzy sets A et B.
    ==> A INTER B
    """
    return min(A(x), B(x))


plt.title("Température basse INTER température moyenne")
plt.ylabel('Degré d\'appartenance')
plt.ylim(-0.2, 1.2)
plt.xlabel(V)
plt.xlim(0, 40)

plt.plot([0, 5, 10, 15, 20, 25, 30, 35, 40], [
    fuzzy_intersection(basse, moyenne, 0),
    fuzzy_intersection(basse, moyenne, 5),
    fuzzy_intersection(basse, moyenne, 10),
    fuzzy_intersection(basse, moyenne, 15),
    fuzzy_intersection(basse, moyenne, 20),
    fuzzy_intersection(basse, moyenne, 25),
    fuzzy_intersection(basse, moyenne, 30),
    fuzzy_intersection(basse, moyenne, 35),
    fuzzy_intersection(basse, moyenne, 40)
], color='blue')

plt.show()

# Implication floue

# Afficher le fuzzy set "chauffer fort"


def chauffer_fort(x):
    if x <= 8:
        return 0
    if x >= 10:
        return 1
    return 1./2. * x - 4


plt.title("Chauffer fort")
plt.ylabel('Degré d\'appartenance')
plt.ylim(-0.2, 1.2)
plt.xlabel("Puissance chauffe (KW)")
plt.xlim(0, 15)

plt.plot([0, 5, 8, 10, 15], [
    chauffer_fort(0),
    chauffer_fort(5),
    chauffer_fort(8),
    chauffer_fort(10),
    chauffer_fort(15)
], color='red')

plt.legend()
plt.show()

# Implication floue
x_0 = 12  # température mesurée : 12°C

# valeurs à calculer
y_values = range(0, 16)

# calcul des valeurs de l'implication pour chaque y appartenant à U_y
inferences = []
predicate_x0 = basse(x_0)
for i in range(0, 16):
    inferences.append(min(predicate_x0, chauffer_fort(i)))

plt.title("Si température basse ALORS chauffer fort")
plt.ylabel('Degré d\'appartenance')
plt.ylim(-0.2, 1.2)
plt.xlabel("Puissance chauffe (KW)")
plt.xlim(0, 15)

plt.plot(y_values, inferences, color='red')

plt.legend()
plt.show()