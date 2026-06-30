package it.ciano.expensetracker.data.repository

import kotlinx.coroutines.flow.Flow
import it.ciano.expensetracker.data.dao.TagDao
import it.ciano.expensetracker.data.model.Tag

class TagRepository(private val tagDao: TagDao) {

    fun getAllTags(): Flow<List<Tag>> {
        return tagDao.getAllTags()
    }

    suspend fun insertTag(tag: Tag): Long {
        return tagDao.insertTag(tag)
    }

    suspend fun deleteTag(tag: Tag) {
        tagDao.deleteTag(tag)
    }
}
