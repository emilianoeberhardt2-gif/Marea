package com.example.domain

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class SpeciesCandidateComparison(
    val speciesName: String,
    val matchPercent: Int,
    val keyDifferences: String,
)

data class SpeciesIdentificationResult(
    val primarySpecies: String,
    val scientificName: String,
    val confidencePercent: Int,
    val waterType: String,
    val identifyingTraits: List<String>,
    val candidates: List<SpeciesCandidateComparison>,
    val careAndReleaseNote: String,
)

class FishIdentifierService {
    private val speciesSchema = Schema.obj(
        properties = mapOf(
            "primarySpecies" to Schema.string(description = "Nombre común en español de la especie más probable"),
            "scientificName" to Schema.string(description = "Nombre científico en latín"),
            "confidencePercent" to Schema.integer(description = "Porcentaje de confianza de 0 a 100"),
            "waterType" to Schema.enumeration(
                values = listOf("dulce", "salada", "estuario"),
                description = "Hábitat principal de la especie"
            ),
            "identifyingTraits" to Schema.array(
                items = Schema.string(),
                description = "Lista de 3 a 4 rasgos visuales observados en la foto"
            ),
            "candidates" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "speciesName" to Schema.string(description = "Nombre de la especie comparada"),
                        "matchPercent" to Schema.integer(description = "Similitud estimada 0-100"),
                        "keyDifferences" to Schema.string(description = "Diferencias morfológicas frente a la captura de la foto")
                    )
                ),
                description = "Comparativa con 3 especies candidatas similares"
            ),
            "careAndReleaseNote" to Schema.string(description = "Breve recomendación de manejo o captura y suelta")
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = speciesSchema
            }
        )
    }

    suspend fun identifyAndCompare(bitmap: Bitmap): SpeciesIdentificationResult {
        try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Analiza esta fotografía de una captura de pesca deportiva y compárala con posibles especies de peces (agua dulce, mar o estuario). " +
                                "Determina cuál es la especie más probable en español, describe los rasgos visuales concretos de la imagen (aletas, boca, escamas, coloración, línea lateral) " +
                                "y compara la captura frente a 3 especies candidatas similares explicando las diferencias clave para distinguirlas."
                        )
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("El modelo devolvió una respuesta vacía.")
            val root = JSONObject(jsonText)
            val traitsArray = root.optJSONArray("identifyingTraits")
            val traits = if (traitsArray != null) {
                (0 until traitsArray.length()).map { traitsArray.getString(it) }
            } else emptyList()

            val candidatesArray = root.optJSONArray("candidates")
            val candidates = if (candidatesArray != null) {
                (0 until candidatesArray.length()).map { idx ->
                    val obj = candidatesArray.getJSONObject(idx)
                    SpeciesCandidateComparison(
                        speciesName = obj.optString("speciesName", "Especie similar"),
                        matchPercent = obj.optInt("matchPercent", 0).coerceIn(0, 100),
                        keyDifferences = obj.optString("keyDifferences", ""),
                    )
                }
            } else emptyList()

            return SpeciesIdentificationResult(
                primarySpecies = root.optString("primarySpecies", "Especie no determinada"),
                scientificName = root.optString("scientificName", ""),
                confidencePercent = root.optInt("confidencePercent", 0).coerceIn(0, 100),
                waterType = root.optString("waterType", "dulce").let {
                    if (it in listOf("dulce", "salada", "estuario")) it else "dulce"
                },
                identifyingTraits = traits,
                candidates = candidates,
                careAndReleaseNote = root.optString("careAndReleaseNote", ""),
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Species identification timed out", e)
            throw IllegalStateException("El análisis tardó más de 60 segundos. Intenta de nuevo.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Species identification failed", e)
            throw e
        }
    }

    private companion object {
        const val TAG = "FishIdentifier"
    }
}
