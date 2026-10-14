package com.hafeziquran.app.data.repository

import android.content.Context
import com.hafeziquran.app.core.constants.AppConstants
import com.hafeziquran.app.core.util.AppJson
import com.hafeziquran.app.core.util.Resource
import com.hafeziquran.app.data.model.TasbeehItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the zikr catalogue from `assets/tasbeeh.json`. */
@Singleton
class TasbeehRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun loadZikrList(): Resource<List<TasbeehItem>> = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(AppConstants.TASBEEH_JSON_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }.mapCatching { AppJson.decodeFromString<List<TasbeehItem>>(it) }
            .fold(
                onSuccess = { Resource.Success(it) },
                onFailure = { Resource.Error(it, "তাসবীহ তালিকা লোড করা যায়নি") },
            )
    }
}
