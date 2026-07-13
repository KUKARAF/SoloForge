package com.kbul.spicycrab.domain.nutrition

import android.content.Context
import com.kbul.spicycrab.data.db.dao.FoodEntryDao
import com.kbul.spicycrab.data.db.dao.MealPresetDao
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.MealPreset
import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.network.OpenRouterClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

object FoodAnalysisModels {
    const val DEFAULT = "google/gemini-3.1-flash-lite"
    const val ESCALATION = "openai/gpt-5.4-mini"
    const val PREMIUM = "google/gemini-3.1-pro-preview"
}

@Singleton
class FoodRepository @Inject constructor(
    private val dao: FoodEntryDao,
    private val presetDao: MealPresetDao,
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val settings: SettingsRepo,
    @ApplicationContext private val context: Context,
) {

    fun observeAll(): Flow<List<FoodEntry>> = dao.observeAll()

    suspend fun analyze(imageFile: File, comment: String): Result<NutritionEstimate> {
        val base64 = runCatching { ImageUtils.fileToBase64Jpeg(imageFile) }
            .getOrElse { return Result.failure(it) }
        return analyzeWithChain(base64, comment)
    }

    suspend fun analyzeText(description: String): Result<NutritionEstimate> =
        analyzeWithChain(null, description)

    private suspend fun analyzeWithChain(base64: String?, comment: String): Result<NutritionEstimate> {
        val key = keyStore.getOpenRouterKey()
            ?: return Result.failure(IllegalStateException("Set your OpenRouter API key in Settings."))

        return runCatching {
            val first = analyzeWithModel(key, FoodAnalysisModels.DEFAULT, base64, comment)
            if (!first.needsEscalation()) return@runCatching first

            val second = analyzeWithModel(key, FoodAnalysisModels.ESCALATION, base64, comment)
            if (!second.needsEscalation()) {
                return@runCatching second.copy(modelUsed = "${FoodAnalysisModels.DEFAULT} -> ${second.modelUsed}")
            }

            val third = runCatching { analyzeWithModel(key, FoodAnalysisModels.PREMIUM, base64, comment) }
                .getOrElse {
                    return@runCatching second.copy(
                        modelUsed = "${FoodAnalysisModels.DEFAULT} -> ${FoodAnalysisModels.ESCALATION}",
                        detailPrompt = "Add details like portion size, cooking oil, sauces, and hidden ingredients, then re-analyze.",
                    )
                }
                .copy(modelUsed = "${FoodAnalysisModels.DEFAULT} -> ${FoodAnalysisModels.ESCALATION} -> ${FoodAnalysisModels.PREMIUM}")

            if (third.confidence.equals("low", ignoreCase = true)) {
                third.copy(
                    detailPrompt = "Add details like portion size, cooking oil, sauces, and hidden ingredients, then re-analyze.",
                )
            } else {
                third
            }
        }
    }

    suspend fun save(
        estimate: NutritionEstimate,
        comment: String,
        imageFile: File?,
    ): FoodEntry {
        val s = settings.current()
        val savedImagePath = if (s.savePhotoLocally && imageFile != null) {
            val dest = File(context.filesDir, "food_${System.currentTimeMillis()}.jpg")
            imageFile.copyTo(dest, overwrite = true)
            dest.absolutePath
        } else null

        val now = System.currentTimeMillis()
        val entry = FoodEntry(
            timestampEpoch = now,
            lastModifiedEpoch = now,
            itemName = estimate.itemName,
            grams = estimate.grams,
            kcal = estimate.kcal,
            proteinG = estimate.proteinG,
            carbsG = estimate.carbsG,
            fatG = estimate.fatG,
            fiberG = estimate.fiberG,
            sodiumMg = estimate.sodiumMg,
            comment = comment,
            modelUsed = estimate.modelUsed.ifBlank { FoodAnalysisModels.DEFAULT },
            confidence = estimate.confidence,
            imagePath = savedImagePath,
            peopleCount = estimate.peopleCount.coerceAtLeast(1),
            consumedEpoch = if (estimate.pendingConsumption) null else now,
            addedEpoch = now,
        )
        return entry.copy(id = dao.insert(entry))
    }

    suspend fun addManual(draft: FoodEntry): FoodEntry {
        val now = System.currentTimeMillis()
        val effectiveTimestamp = draft.timestampEpoch.takeIf { it > 0 } ?: now
        return insertEntry(
            draft.copy(
                timestampEpoch = effectiveTimestamp,
                lastModifiedEpoch = now,
                modelUsed = "manual",
                confidence = "user",
                imagePath = null,
                peopleCount = draft.peopleCount.coerceAtLeast(1),
                consumedEpoch = effectiveTimestamp,
                addedEpoch = now,
            )
        )
    }

    /** Confirms a pending (barcode-scanned but unconfirmed) entry as actually eaten. */
    suspend fun markConsumed(entry: FoodEntry, at: Long = System.currentTimeMillis()): FoodEntry =
        update(entry.copy(consumedEpoch = at))

    fun observePresets(): Flow<List<MealPreset>> = presetDao.observeAll()

    suspend fun saveAsPreset(source: FoodEntry, name: String): MealPreset {
        val preset = MealPreset(
            name = name.ifBlank { source.itemName }.trim(),
            grams = source.grams,
            kcal = source.kcal,
            proteinG = source.proteinG,
            carbsG = source.carbsG,
            fatG = source.fatG,
            fiberG = source.fiberG,
            sodiumMg = source.sodiumMg,
            comment = source.comment,
            createdEpoch = System.currentTimeMillis(),
        )
        return preset.copy(id = presetDao.insert(preset))
    }

    suspend fun deletePreset(preset: MealPreset) = presetDao.delete(preset)

    suspend fun logPreset(preset: MealPreset): FoodEntry {
        val now = System.currentTimeMillis()
        return insertEntry(
            FoodEntry(
                timestampEpoch = now,
                lastModifiedEpoch = now,
                itemName = preset.name,
                grams = preset.grams,
                kcal = preset.kcal,
                proteinG = preset.proteinG,
                carbsG = preset.carbsG,
                fatG = preset.fatG,
                fiberG = preset.fiberG,
                sodiumMg = preset.sodiumMg,
                comment = preset.comment,
                modelUsed = "preset",
                confidence = "user",
                imagePath = null,
                consumedEpoch = now,
                addedEpoch = now,
            )
        )
    }

    private suspend fun insertEntry(entry: FoodEntry): FoodEntry =
        entry.copy(id = dao.insert(entry))

    suspend fun update(updated: FoodEntry): FoodEntry {
        val bumped = updated.copy(lastModifiedEpoch = System.currentTimeMillis())
        dao.update(bumped)
        return bumped
    }

    suspend fun delete(entry: FoodEntry) = dao.delete(entry)

    fun todayTotals(entries: List<FoodEntry>): NutritionEstimate {
        val consumed = entries.filter { it.consumedEpoch != null }
        return NutritionEstimate(
            itemName = "today",
            grams = consumed.sumOf { it.grams },
            kcal = consumed.sumOf { it.shareKcal },
            proteinG = consumed.sumOf { it.shareProteinG },
            carbsG = consumed.sumOf { it.shareCarbsG },
            fatG = consumed.sumOf { it.shareFatG },
            fiberG = consumed.sumOf { it.shareFiberG },
            sodiumMg = consumed.sumOf { it.shareSodiumMg },
            confidence = "",
            notes = "",
        )
    }

    fun mostRecentEpoch(entries: List<FoodEntry>): Long? = entries.firstOrNull()?.timestampEpoch

    private suspend fun analyzeWithModel(
        key: String,
        model: String,
        base64: String?,
        comment: String,
    ): NutritionEstimate =
        client.analyzeFood(key, model, base64, comment).getOrThrow().let { dto ->
            NutritionEstimate(
                itemName = dto.itemName,
                grams = dto.estimatedGrams,
                kcal = dto.calories,
                proteinG = dto.proteinG,
                carbsG = dto.carbsG,
                fatG = dto.fatG,
                fiberG = dto.fiberG,
                sodiumMg = dto.sodiumMg,
                confidence = dto.confidence,
                notes = dto.notes,
                modelUsed = model,
            )
        }
}

/** This entry's nutrition divided across everyone who shared it, e.g. a bottle of soy sauce. */
val FoodEntry.shareDivisor: Int get() = peopleCount.coerceAtLeast(1)
val FoodEntry.shareKcal: Double get() = kcal / shareDivisor
val FoodEntry.shareProteinG: Double get() = proteinG / shareDivisor
val FoodEntry.shareCarbsG: Double get() = carbsG / shareDivisor
val FoodEntry.shareFatG: Double get() = fatG / shareDivisor
val FoodEntry.shareFiberG: Double get() = fiberG / shareDivisor
val FoodEntry.shareSodiumMg: Double get() = sodiumMg / shareDivisor

internal fun NutritionEstimate.needsEscalation(): Boolean {
    if (confidence.equals("low", ignoreCase = true)) return true
    val text = "$itemName $notes".lowercase()
    return listOf(
        "mixed meal",
        "mixed",
        "multiple",
        "assorted",
        "unclear",
        "hidden",
        "sauce",
        "dressing",
        "oil",
        "portion",
    ).any { it in text }
}
