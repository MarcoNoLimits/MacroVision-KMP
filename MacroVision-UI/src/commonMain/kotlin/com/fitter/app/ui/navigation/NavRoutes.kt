package com.fitter.app.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Destinations
@Serializable
object DashboardDestination

@Serializable
object CameraDestination

@Serializable
data class ResultDestination(val responseJson: String)

@Serializable
object SettingsDestination
