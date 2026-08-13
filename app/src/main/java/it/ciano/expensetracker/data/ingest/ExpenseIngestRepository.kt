package it.ciano.expensetracker.data.ingest

import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.repository.CategoryRepository
import it.ciano.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first

class ExpenseIngestRepository(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {

    suspend fun insertExpense(parsed: ParsedExpense) {
        val categories = categoryRepository.getAllCategories().first()
        val categoryId = findCategoryId(categories, parsed.categoryName)
            ?: categoryRepository.insertCategory(
                Category(name = parsed.categoryName, parentCategoryId = null)
            ).toInt()
        transactionRepository.insertTransaction(toTransaction(parsed, categoryId), emptySet())
    }
}

fun findCategoryId(categories: List<Category>, name: String): Int? {
    return categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
}

fun toTransaction(parsed: ParsedExpense, categoryId: Int): Transaction {
    return Transaction(
        title = parsed.title,
        amount = parsed.amount,
        type = "EXPENSE",
        categoryId = categoryId,
        date = parsed.date,
        note = ""
    )
}
