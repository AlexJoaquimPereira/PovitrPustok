package com.biblia.povitrpustok.data.liturgy

import com.google.gson.annotations.SerializedName

/**
 * DTOs for the Catholic Readings API (Phase 10, plan §2, §18).
 *
 * Only the actually documented fields are modeled; every field is nullable
 * because the API does not guarantee all readings (or all calendar
 * details) exist for every day.
 */

data class ReadingsDto(
    @SerializedName("date") val date: String? = null,
    @SerializedName("monthDay") val monthDay: String? = null,
    @SerializedName("season") val season: String? = null,
    @SerializedName("readings") val readings: ReadingsFieldsDto? = null,
    @SerializedName("usccbLink") val usccbLink: String? = null,
    @SerializedName("apiEndpoint") val apiEndpoint: String? = null
)

data class ReadingsFieldsDto(
    @SerializedName("firstReading") val firstReading: String? = null,
    @SerializedName("psalm") val psalm: String? = null,
    @SerializedName("secondReading") val secondReading: String? = null,
    @SerializedName("gospel") val gospel: String? = null
)

data class CalendarDto(
    @SerializedName("date") val date: String? = null,
    @SerializedName("monthDay") val monthDay: String? = null,
    @SerializedName("season") val season: String? = null,
    @SerializedName("celebration") val celebration: CelebrationDto? = null,
    @SerializedName("apiEndpoint") val apiEndpoint: String? = null
)

data class CelebrationDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("quote") val quote: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("image") val image: String? = null
)
