package it.ciano.expensetracker.data.repository

import kotlinx.coroutines.flow.Flow
import it.ciano.expensetracker.data.dao.TagDao
import it.ciano.expensetracker.data.model.Tag

class TagRepository(private val tagDao: TagDao) {

    val allTags: Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun insert(tag: Tag): Long {
        return tagDao.insertTag(tag)
    }

    suspend fun delete(tag: Tag) {
        tagDao.deleteTag(tag)
    }
}
