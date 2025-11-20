package com.whatsappcontactsender.model

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val isUnknown: Boolean = false
)