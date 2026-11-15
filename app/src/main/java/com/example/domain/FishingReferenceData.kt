package com.example.domain

data class FishingKnotGuide(
    val name: String,
    val strengthLabel: String,
    val bestFor: String,
    val steps: List<String>,
)

object FishingReferenceData {
    val commonSpecies = listOf(
        "Lubina / Róbalo",
        "Trucha Arcoíris",
        "Dorado",
        "Black Bass",
        "Pejerrey",
        "Tararira",
        "Corvina",
        "Surubí",
        "Pargo",
        "Carpa",
    )

    val knotsGuide = listOf(
        FishingKnotGuide(
            name = "Nudo Palomar",
            strengthLabel = "95% resistencia",
            bestFor = "Anzuelos, emerillones y señuelos con multifilamento o fluorocarbono.",
            steps = listOf(
                "Dobla unos 15 cm de línea formando un bucle y pásalo por el ojal del anzuelo.",
                "Haz un nudo simple alrededor de la línea doble sin apretarlo del todo.",
                "Pasa el anzuelo completo por dentro del extremo del bucle.",
                "Humedece bien el nudo y tira de ambos extremos hasta asentar.",
            ),
        ),
        FishingKnotGuide(
            name = "Nudo Rapala (Lazo libre)",
            strengthLabel = "90% resistencia · Acción natural",
            bestFor = "Señuelos minnow, paseantes y crankbaits sin grapa para darles movilidad.",
            steps = listOf(
                "Haz un nudo simple abierto a unos 12 cm de la punta de la línea.",
                "Pasa el extremo por el ojal del señuelo y vuelve por dentro del nudo simple.",
                "Da 4 o 5 vueltas alrededor de la línea principal hacia arriba.",
                "Introduce la punta de nuevo por el nudo simple, humedece y ajusta el lazo.",
            ),
        ),
        FishingKnotGuide(
            name = "Nudo Clinch Mejorado",
            strengthLabel = "92% resistencia",
            bestFor = "Monofilamento y fluorocarbono en anzuelos simples o cucharillas.",
            steps = listOf(
                "Pasa el extremo por el ojal y da 5 o 6 vueltas sobre la línea principal.",
                "Mete la punta por el primer bucle junto al ojal.",
                "Lleva esa misma punta hacia atrás por el gran lazo recién formado.",
                "Humedece, desliza suavemente contra el ojal y recorta el sobrante.",
            ),
        ),
        FishingKnotGuide(
            name = "Nudo Albright",
            strengthLabel = "88% unión de líneas",
            bestFor = "Unir multifilamento fino con bajo de fluorocarbono o acero.",
            steps = listOf(
                "Forma una U con el bajo grueso (fluorocarbono).",
                "Introduce el multifilamento y enróllalo 10 veces sobre sí mismo y el bajo.",
                "Vuelve pasando el multifilamento por el bucle inicial en la misma dirección que entró.",
                "Humedece y tensa progresivamente tirando de ambas líneas.",
            ),
        ),
    )
}
