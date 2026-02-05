package com.theveloper.pixelplay.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import com.theveloper.pixelplay.data.database.LyricsDao
import com.theveloper.pixelplay.data.database.LyricsEntity
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SearchHistoryEntity
import com.theveloper.pixelplay.data.database.TransitionDao
import com.theveloper.pixelplay.data.database.TransitionRuleEntity
import com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupSection(val key: String) {
    PREFERENCES("preferences"),
    FAVORITES("favorites"),
    LYRICS("lyrics"),
    SEARCH_HISTORY("search_history"),
    TRANSITIONS("transitions");

    companion object {
        val defaultSelection: Set<BackupSection> = entries.toSet()
    }
}

data class AppDataBackupPayload(
    val formatVersion: Int = 1,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val preferences: List<PreferenceBackupEntry>? = null,
    val favorites: List<FavoritesEntity>? = null,
    val lyrics: List<LyricsEntity>? = null,
    val searchHistory: List<SearchHistoryEntity>? = null,
    val transitions: List<TransitionRuleEntity>? = null
)

@Singleton
class AppDataBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val favoritesDao: FavoritesDao,
    private val lyricsDao: LyricsDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val transitionDao: TransitionDao
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToUri(
        uri: Uri,
        sections: Set<BackupSection>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = AppDataBackupPayload(
                preferences = if (BackupSection.PREFERENCES in sections) {
                    userPreferencesRepository.exportPreferencesForBackup()
                } else {
                    null
                },
                favorites = if (BackupSection.FAVORITES in sections) {
                    favoritesDao.getAllFavoritesOnce()
                } else {
                    null
                },
                lyrics = if (BackupSection.LYRICS in sections) {
                    lyricsDao.getAll()
                } else {
                    null
                },
                searchHistory = if (BackupSection.SEARCH_HISTORY in sections) {
                    searchHistoryDao.getAll()
                } else {
                    null
                },
                transitions = if (BackupSection.TRANSITIONS in sections) {
                    transitionDao.getAllRulesOnce()
                } else {
                    null
                }
            )

            val json = gson.toJson(payload)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(json)
            } ?: error("Unable to open output stream")
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        sections: Set<BackupSection>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error("Unable to open backup file")

            val payload = gson.fromJson(json, AppDataBackupPayload::class.java)
                ?: error("Backup file is invalid")

            if (BackupSection.PREFERENCES in sections) {
                payload.preferences?.let {
                    userPreferencesRepository.importPreferencesFromBackup(
                        entries = it,
                        clearExisting = true
                    )
                }
            }

            if (BackupSection.FAVORITES in sections) {
                favoritesDao.clearAll()
                payload.favorites?.let { favorites ->
                    if (favorites.isNotEmpty()) {
                        favoritesDao.insertAll(favorites)
                    }
                }
            }

            if (BackupSection.LYRICS in sections) {
                lyricsDao.deleteAll()
                payload.lyrics?.let { lyrics ->
                    if (lyrics.isNotEmpty()) {
                        lyricsDao.insertAll(lyrics)
                    }
                }
            }

            if (BackupSection.SEARCH_HISTORY in sections) {
                searchHistoryDao.clearAll()
                payload.searchHistory?.let { history ->
                    if (history.isNotEmpty()) {
                        searchHistoryDao.insertAll(history)
                    }
                }
            }

            if (BackupSection.TRANSITIONS in sections) {
                transitionDao.clearAllRules()
                payload.transitions?.let { rules ->
                    if (rules.isNotEmpty()) {
                        transitionDao.setRules(rules)
                    }
                }
            }
        }
    }
}

