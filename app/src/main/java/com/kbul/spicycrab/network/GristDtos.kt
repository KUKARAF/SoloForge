package com.kbul.spicycrab.network

import kotlinx.serialization.Serializable

@Serializable
data class GristProductFields(
    val barcode: String,
    val name: String,
    val kcal100: Double,
    val proteinG100: Double,
    val carbsG100: Double,
    val fatG100: Double,
    val fiberG100: Double,
    val sodiumMg100: Double,
    val servingG: Double? = null,
    val source: String,
    val fetchedEpoch: Long,
)

@Serializable
data class GristRecord(
    val id: Long,
    val fields: GristProductFields? = null,
)

@Serializable
data class GristRecordsResponse(
    val records: List<GristRecord> = emptyList(),
)

@Serializable
data class GristInsertRecord(val fields: GristProductFields)

@Serializable
data class GristInsertRequest(val records: List<GristInsertRecord>)
