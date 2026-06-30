package it.ciano.expensetracker.data.repository

import android.app.Application
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.dao.TagDao
import it.ciano.expensetracker.data.model.Tag
import kotlinx.coroutines.flow.Flow

class TagRepository(application: Application) {
    private val tagDao = AppDatabase.getDatabase(application).tagDao()

    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun getTagByName(name: String): Tag? = tagDao.getTagByName(name)

    suspend fun insertTag(tag: Tag): Long = tagDao.insertTag(tag)

    suspend fun deleteTag(tag: Tag) = tagDao.deleteTag(tag)
}
