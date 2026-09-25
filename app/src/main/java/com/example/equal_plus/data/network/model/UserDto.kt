package com.example.equal_plus.data.network.model

import com.google.gson.annotations.SerializedName

data class UserProfileDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("screening_enabled") val screeningEnabled: Boolean = true,
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis()
)

data class UserPolicySyncDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("global_screening_enabled") val globalScreeningEnabled: Boolean,
    @SerializedName("auto_block_scam") val autoBlockScam: Boolean,
    @SerializedName("categories") val categories: Map<String, Boolean> = emptyMap(),
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)
