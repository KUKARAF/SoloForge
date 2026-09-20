package com.kbul.spicycrab.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoteMeta(
    val id: String,
    val title: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val version: String? = null,
)

@Serializable
data class NoteResponse(
    val meta: NoteMeta,
    val content: String,
)

@Serializable
data class CreateNoteRequest(
    @SerialName("id_or_title") val idOrTitle: String,
    val content: String,
)

@Serializable
data class PutNoteRequest(
    val content: String,
    @SerialName("expected_version") val expectedVersion: String? = null,
)

@Serializable
data class StatRequest(
    val key: String,
    val value: Double,
    val at: String? = null,
    val date: String? = null,
)
