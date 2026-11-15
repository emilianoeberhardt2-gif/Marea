package com.example.domain

import android.util.Log
import com.example.data.model.AiChatMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class FishingAssistantService {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "Eres el asistente experto en pesca deportiva de la aplicación Marea. " +
                        "Responde SIEMPRE en español claro, amable, práctico y directo (máximo 160 palabras). " +
                        "REGLA ESTRICTA DE TEMÁTICA: Solo puedes responder preguntas relacionadas con la pesca " +
                        "(especies de peces, señuelos, carnadas, equipos, cañas, carretes, líneas, nudos, " +
                        "técnicas como spinning, surfcasting, jigging, trolling o pesca con mosca, mareas, " +
                        "condiciones solunares y meteorológicas para pescar, embarcaciones, seguridad en el agua, " +
                        "y conservación o captura y suelta). " +
                        "Si el usuario pregunta sobre cualquier otro tema ajeno a la pesca (por ejemplo: " +
                        "programación, matemáticas, política, historia general, medicina, farándula, etc.), " +
                        "indica educadamente en una o dos frases que solo estás capacitado para responder consultas de pesca."
                )
            }
        )
    }

    suspend fun askQuestion(
        question: String,
        conditionsContext: String,
        history: List<AiChatMessage>,
    ): String {
        val cleanQuestion = question.trim()
        require(cleanQuestion.isNotEmpty()) { "Escribe una pregunta sobre pesca." }

        val chatHistory = history.takeLast(6).map { msg ->
            content(if (msg.isUser) "user" else "model") {
                text(msg.text)
            }
        }

        val prompt = buildString {
            if (conditionsContext.isNotBlank()) {
                append("Contexto actual de pesca del usuario: ")
                append(conditionsContext)
                append("\n\n")
            }
            append("Pregunta del pescador: ")
            append(cleanQuestion)
        }

        return try {
            val response = withTimeout(60_000L) {
                val chat = model.startChat(history = chatHistory)
                chat.sendMessage(prompt)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                throw IllegalStateException("El asistente no pudo generar una respuesta. Intenta de nuevo.")
            }
            text
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Fishing assistant timed out", e)
            throw IllegalStateException("La consulta tardó más de 60 segundos. Intenta de nuevo.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fishing assistant failed", e)
            throw e
        }
    }

    private companion object {
        const val TAG = "FishingAssistant"
    }
}
