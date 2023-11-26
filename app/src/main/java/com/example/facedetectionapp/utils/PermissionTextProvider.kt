package com.example.facedetectionapp.utils

interface PermissionTextProvider {
    fun getDescription(isPermanentlyDeclined: Boolean): String
}