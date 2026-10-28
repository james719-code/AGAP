package com.travelingsalesman.agap.data.mock

import android.content.Context
import kotlinx.serialization.json.Json

object VariantOfflineTest {
    fun create(context: Context, json: Json): OfflineTestDataSource = DisabledOfflineTestDataSource
}
