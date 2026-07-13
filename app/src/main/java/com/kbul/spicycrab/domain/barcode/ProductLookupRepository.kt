package com.kbul.spicycrab.domain.barcode

import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.network.GristClient
import com.kbul.spicycrab.network.GristConfig
import com.kbul.spicycrab.network.GristProductFields
import com.kbul.spicycrab.network.OpenFoodFactsClient
import com.kbul.spicycrab.network.OpenFoodFactsProduct
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductLookupRepository @Inject constructor(
    private val gristClient: GristClient,
    private val offClient: OpenFoodFactsClient,
    private val keyStore: SecureKeyStore,
    private val settings: SettingsRepo,
) {

    suspend fun isConfigured(): Boolean {
        val s = settings.current()
        return !s.gristBaseUrl.isNullOrBlank() &&
            !s.gristDocId.isNullOrBlank() &&
            !s.gristTableId.isNullOrBlank() &&
            keyStore.hasGristApiKey()
    }

    /** Grist first, Open Food Facts on miss (written back to Grist). Never throws; `null` on any miss/failure. */
    suspend fun lookup(barcode: String): Product? {
        val config = gristConfig() ?: return null

        gristClient.findByBarcode(config, barcode).getOrNull()?.let { fields ->
            return fields.toProduct(barcode)
        }

        val offProduct = offClient.lookup(barcode).getOrNull() ?: return null
        val product = offProduct.toProduct(barcode) ?: return null

        gristClient.insert(config, product.toGristFields())

        return product
    }

    private suspend fun gristConfig(): GristConfig? {
        val s = settings.current()
        val baseUrl = s.gristBaseUrl?.takeIf { it.isNotBlank() } ?: return null
        val docId = s.gristDocId?.takeIf { it.isNotBlank() } ?: return null
        val tableId = s.gristTableId?.takeIf { it.isNotBlank() } ?: return null
        val apiKey = keyStore.getGristApiKey()?.takeIf { it.isNotBlank() } ?: return null
        return GristConfig(baseUrl, docId, tableId, apiKey)
    }
}

internal fun GristProductFields.toProduct(barcode: String): Product = Product(
    barcode = barcode,
    name = name,
    kcal100 = kcal100,
    proteinG100 = proteinG100,
    carbsG100 = carbsG100,
    fatG100 = fatG100,
    fiberG100 = fiberG100,
    sodiumMg100 = sodiumMg100,
    servingG = servingG,
    source = source,
)

internal fun Product.toGristFields(): GristProductFields = GristProductFields(
    barcode = barcode,
    name = name,
    kcal100 = kcal100,
    proteinG100 = proteinG100,
    carbsG100 = carbsG100,
    fatG100 = fatG100,
    fiberG100 = fiberG100,
    sodiumMg100 = sodiumMg100,
    servingG = servingG,
    source = source,
    fetchedEpoch = System.currentTimeMillis(),
)

/**
 * sodium_100g is grams per Open Food Facts convention (×1000 for mg); if a product only reports
 * salt, sodium is salt / 2.5. energy-kcal_100g (not the kJ field) is the calorie source.
 */
internal fun OpenFoodFactsProduct.toProduct(barcode: String): Product? {
    val n = nutriments ?: return null
    val kcal = n.energyKcal100g ?: return null
    val sodiumMg = n.sodium100g?.let { it * 1000.0 }
        ?: n.salt100g?.let { it / 2.5 * 1000.0 }
        ?: 0.0
    return Product(
        barcode = barcode,
        name = productName?.takeIf { it.isNotBlank() } ?: "Scanned product",
        kcal100 = kcal,
        proteinG100 = n.proteins100g ?: 0.0,
        carbsG100 = n.carbohydrates100g ?: 0.0,
        fatG100 = n.fat100g ?: 0.0,
        fiberG100 = n.fiber100g ?: 0.0,
        sodiumMg100 = sodiumMg,
        servingG = servingQuantity,
        source = "openfoodfacts",
    )
}
