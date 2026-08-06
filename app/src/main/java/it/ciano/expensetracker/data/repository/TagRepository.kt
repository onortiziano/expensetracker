package it.ciano.expensetracker.data.repository

import it.ciano.expensetracker.data.dao.TagDao
import it.ciano.expensetracker.data.model.Tag
import kotlinx.coroutines.flow.Flow

class TagRepository(private val tagDao: TagDao) {
    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()
    
    suspend fun insertTag(tag: Tag): Long = tagDao.insertTag(tag)
    
    suspend fun updateTag(tag: Tag) = tagDao.updateTag(tag)
    
    suspend fun deleteTag(tag: Tag) = tagDao.deleteTag(tag)
}
