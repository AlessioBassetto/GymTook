package com.example.sport.tools.catalog

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import com.anthropic.models.beta.messages.StructuredMessageCreateParams
import com.anthropic.models.beta.messages.StructuredOutputConfig
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Schema della risposta strutturata (derivato automaticamente dall'SDK). */
data class TranslatedExercise(
    @get:JsonPropertyDescription("Copia esatta del campo sourceName ricevuto in input")
    val sourceName: String,
    @get:JsonPropertyDescription("Nome italiano usato nelle palestre italiane; anglicismi d'uso comune ammessi")
    val name: String,
    @get:JsonPropertyDescription("Da 0 a 3 sinonimi italiani o inglesi con cui l'esercizio viene cercato")
    val aliases: List<String>,
    @get:JsonPropertyDescription("Passaggi di esecuzione in italiano, frasi brevi all'imperativo, 3-6 voci")
    val instructions: List<String>,
    @get:JsonPropertyDescription("2-3 punti chiave tecnici, una riga ciascuno")
    val keyPoints: List<String>,
    @get:JsonPropertyDescription("2 errori comuni o avvertenze di sicurezza, una riga ciascuno")
    val commonMistakes: List<String>,
)

data class TranslationBatch(
    val items: List<TranslatedExercise>,
)

/** Payload compatto inviato al modello per ogni esercizio. */
@Serializable
private data class TranslationRequestItem(
    val sourceName: String,
    val proposedItalianName: String?,
    val equipment: String,
    val primaryMuscles: List<String>,
    val instructionsEn: List<String>,
)

/**
 * Traduce in italiano gli esercizi importati e genera punti chiave ed errori comuni, con Claude.
 *
 * - Modello `claude-opus-5-5`, output strutturato (JSON validato contro lo schema).
 * - Fallback lato server attivo (`fallbacks: "default"`): se una richiesta viene rifiutata dai
 *   classificatori di sicurezza, l'API la ripete su un modello alternativo.
 * - Salvataggio dopo ogni lotto: il comando è interrompibile e riprende da dove si era fermato.
 * - L'output è marcato `reviewed = false`: va revisionato da un esperto prima della pubblicazione.
 */
class ClaudeTranslator(
    private val client: AnthropicClient = AnthropicOkHttpClient.fromEnv(),
    private val model: String = DEFAULT_MODEL,
    private val effort: BetaOutputConfig.Effort = BetaOutputConfig.Effort.MEDIUM,
) {

    fun buildParams(items: List<SourceExercise>, names: Map<String, String>): StructuredMessageCreateParams<TranslationBatch> {
        val payload = items.map { source ->
            TranslationRequestItem(
                sourceName = source.name,
                proposedItalianName = names[source.name],
                equipment = source.equipment ?: "body only",
                primaryMuscles = source.primaryMuscles,
                instructionsEn = source.instructions,
            )
        }
        val userMessage = "Traduci questi ${items.size} esercizi. Restituisci un elemento per ciascuno, nello stesso ordine.\n\n" +
            ImporterJson.encodeToString(ListSerializer(TranslationRequestItem.serializer()), payload)

        return MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .addBeta(FALLBACK_BETA)
            .fallbacksDefault()
            .systemOfBetaTextBlockParams(
                listOf(
                    BetaTextBlockParam.builder()
                        .text(SYSTEM_PROMPT)
                        .cacheControl(BetaCacheControlEphemeral.builder().build())
                        .build(),
                ),
            )
            .addUserMessage(userMessage)
            .outputConfig(
                StructuredOutputConfig.builder<TranslationBatch>()
                    .format(TranslationBatch::class.java)
                    .effort(effort)
                    .build(),
            )
            .build()
    }

    /** Traduce un lotto. Ritorna null (con log) se il modello rifiuta o la risposta è incompleta. */
    fun translateBatch(items: List<SourceExercise>, names: Map<String, String>): List<TranslatedExercise>? {
        val response = client.beta().messages().create(buildParams(items, names))
        val stopReason = response.stopReason().orElse(null)
        if (stopReason == BetaStopReason.REFUSAL) {
            val details = response.stopDetails().map { "${it.category().orElse(null)}: ${it.explanation().orElse("")}" }.orElse("")
            System.err.println("Lotto rifiutato ($details): ${items.map { it.name }}")
            return null
        }
        if (stopReason == BetaStopReason.MAX_TOKENS) {
            System.err.println("Risposta troncata (max_tokens): riprova con un lotto più piccolo. ${items.map { it.name }}")
            return null
        }
        val batch = response.content().firstNotNullOfOrNull { block -> block.text().orElse(null)?.text() }
            ?: return null.also { System.err.println("Nessun contenuto testuale per ${items.map { it.name }}") }
        val usage = response.usage()
        println(
            "  token: input ${usage.inputTokens()} (cache ${usage.cacheReadInputTokens().orElse(0L)}), " +
                "output ${usage.outputTokens()}",
        )
        return batch.items
    }

    /**
     * Traduce tutti gli esercizi ancora senza contenuto italiano.
     * @return numero di esercizi tradotti in questa esecuzione.
     */
    fun translatePending(
        paths: ImporterPaths,
        sources: List<SourceExercise>,
        limit: Int,
        batchSize: Int,
        parallelism: Int,
    ): Int {
        val inputs = CatalogInputs.load(paths)
        val linked = inputs.curatedLinks.values.toSet()
        val pending = sources
            .filter(SourceFilter::accept)
            .filter { it.name !in linked && inputs.translations[it.name]?.hasContent != true }
            .sortedBy { it.name }
            .take(limit)
        if (pending.isEmpty()) {
            println("Nessun esercizio da tradurre.")
            return 0
        }
        println("Da tradurre: ${pending.size} esercizi in lotti da $batchSize (modello $model, effort $effort)")

        val translations = inputs.translations.toMutableMap()
        val lock = Any()
        var translatedCount = 0
        val executor = Executors.newFixedThreadPool(parallelism.coerceAtLeast(1))
        pending.chunked(batchSize).forEachIndexed { index, batch ->
            executor.submit {
                try {
                    val result = translateBatch(batch, inputs.names) ?: return@submit
                    val bySource = result.associateBy { it.sourceName }
                    synchronized(lock) {
                        batch.forEach { source ->
                            val item = bySource[source.name] ?: run {
                                System.err.println("Manca nella risposta: ${source.name}")
                                return@forEach
                            }
                            translations[source.name] = item.toEntry(model)
                            translatedCount++
                        }
                        CatalogInputs.saveTranslations(paths, translations)
                        println("Lotto ${index + 1}: ${batch.size} esercizi salvati (totale $translatedCount)")
                    }
                } catch (error: AnthropicServiceException) {
                    System.err.println("Errore API sul lotto ${index + 1} (${error.statusCode()}): ${error.message}")
                } catch (error: RuntimeException) {
                    System.err.println("Errore sul lotto ${index + 1}: ${error.message}")
                }
            }
        }
        executor.shutdown()
        executor.awaitTermination(6, TimeUnit.HOURS)
        return translatedCount
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val MAX_TOKENS = 16_000L

        val SYSTEM_PROMPT = """
            Sei un personal trainer italiano e un traduttore tecnico. Prepari le schede esercizio di
            un'app per il logging dell'allenamento in palestra, usata da principianti e avanzati.

            Per ogni esercizio ricevuto:
            - name: il nome con cui l'esercizio è conosciuto nelle palestre italiane. Parti da
              proposedItalianName se presente e mantienilo salvo errori evidenti. Gli anglicismi
              d'uso comune restano in inglese (curl, push down, leg press, hack squat, crunch,
              shoulder press, pullover, good morning); l'attrezzo va in coda ("con manubri",
              "con bilanciere", "ai cavi", "alla macchina", "al multipower", "con kettlebell").
            - instructions: traduci il senso delle istruzioni inglesi in 3-6 passaggi brevi,
              all'imperativo, in italiano naturale. Elimina ridondanze come "ripeti per il numero di
              ripetizioni consigliato". Non inventare varianti diverse da quella descritta.
            - keyPoints: 2-3 indicazioni tecniche concrete (postura, traiettoria, respirazione).
            - commonMistakes: 2 errori frequenti o avvertenze di sicurezza.
            - aliases: 0-3 sinonimi realmente usati (italiani o inglesi), senza ripetere il nome.

            Stile: frasi brevi, leggibili in pochi secondi tra una serie e l'altra. Usa i termini
            anatomici italiani (dorsali, femorali, deltoidi, glutei). Niente emoji, niente markdown.
            Riporta sourceName esattamente come ricevuto.
        """.trimIndent()

        private fun TranslatedExercise.toEntry(model: String) = TranslationEntry(
            name = name.trim(),
            aliases = aliases.map { it.trim() }.filter { it.isNotEmpty() && !it.equals(name, ignoreCase = true) }.distinct(),
            instructions = instructions.map { it.trim() }.filter { it.isNotEmpty() },
            keyPoints = keyPoints.map { it.trim() }.filter { it.isNotEmpty() },
            commonMistakes = commonMistakes.map { it.trim() }.filter { it.isNotEmpty() },
            translatedBy = model,
            reviewed = false,
        )
    }
}
