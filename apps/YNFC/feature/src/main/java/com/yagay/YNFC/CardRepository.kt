package com.yagay.YNFC

import android.content.Context
import com.yagay.suite.api.FeatureSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Small persistence boundary for saved card metadata. */
class CardRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = FeatureSettings.named(appContext, "cards")
    private val gson = Gson()
    private val listType = object : TypeToken<List<CardModel>>() {}.type

    fun load(): List<CardModel> {
        var json = prefs.string("list", null)
        if (json == null) {
            val legacy = appContext.getSharedPreferences("saved_cards", 0)
                .getString("cards_list", null)
            if (!legacy.isNullOrBlank()) {
                json = legacy
                prefs.putString("list", legacy)
            }
        }
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<CardModel>>(json, listType) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    fun save(cards: List<CardModel>) {
        prefs.putString("list", gson.toJson(cards))
    }
}
