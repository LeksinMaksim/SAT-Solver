import kotlin.math.abs
import java.io.File
import kotlin.system.exitProcess

typealias Literal = Int
typealias Clause = Set<Literal>
typealias Formula = List<Clause>
typealias Model = Map<Int, Boolean>

object CnfParser {
    fun parse(filePath: String): Formula {
        val formula = mutableListOf<Clause>()
        val currentClause = mutableSetOf<Literal>()

        File(filePath).forEachLine { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith('c') || trimmedLine.startsWith('%')) {
                return@forEachLine
            }

            if (trimmedLine.startsWith('p')) {
                val parts = trimmedLine.split("\\s+".toRegex())
                if (parts.size == 4 && parts[1] == "cnf") {
                    println("Info: Parsing problem with ${parts[2]} variables and ${parts[3]} clauses.")
                }
                return@forEachLine
            }

            val literals = trimmedLine.split("\\s+".toRegex()).mapNotNull { it.toIntOrNull() }
            for (literal in literals) {
                if (literal == 0) {
                    if (currentClause.isNotEmpty()) {
                        formula.add(currentClause.toSet())
                        currentClause.clear()
                    }
                } else {
                    currentClause.add(literal)
                }
            }
        }
        if (currentClause.isNotEmpty()) {
            formula.add(currentClause)
        }

        return formula
    }
}

object DpllSolver {
    fun solve(formula: Formula): Pair<Boolean, Model?> {
        val (simplifiedFormula, initialModel, initialContradiction) = simplify(formula, emptyMap())
        if (initialContradiction) {
            return false to null
            }
        return dpll(simplifiedFormula, initialModel)
    }

    private fun dpll(formula: Formula, model: Model): Pair<Boolean, Model?> {
        val (simplifiedFormula, newModel, contradiction) = simplify(formula, model)

        if (contradiction) {
            return false to null
        }

        if (simplifiedFormula.isEmpty()) {
            return true to newModel
        }

        val variableToBranch = chooseVariable(simplifiedFormula)
        val resultIfTrue = dpll(simplifiedFormula + listOf(setOf(variableToBranch)), newModel)
        if (resultIfTrue.first) {
            return resultIfTrue
        }

        val resultIfFalse = dpll(simplifiedFormula + listOf(setOf(-variableToBranch)), newModel)
        if (resultIfFalse.first) {
            return resultIfFalse
        }
        return false to null
    }

    private fun propagate(formula: Formula, assignedLiteral: Literal): Formula {
        val simplified = mutableListOf<Clause>()
        val oppositeLiteral = -assignedLiteral

        for (clause in formula) {
            if (clause.contains(assignedLiteral)) {
                continue
            }

            val newClause = clause - oppositeLiteral
            simplified.add(newClause)
        }
        return simplified
    }

    private fun simplify(formula: Formula, model: Model): Triple<Formula, Model, Boolean> {
        var currentFormula = formula
        var currentModel = model

        var changed = true
        while (changed) {
            changed = false

            val unitClause = currentFormula.find { it.size == 1 }
            if (unitClause != null) {
                val literal = unitClause.first()
                val variable = abs(literal)

                if (!currentModel.containsKey(variable)) {
                    val value = literal > 0
                    currentModel = currentModel + (variable to value)
                    currentFormula = propagate(currentFormula, literal)
                    changed = true

                    if (currentFormula.any { it.isEmpty() }) {
                        return Triple(emptyList(), emptyMap(), true)
                    }
                } else {
                    currentFormula = currentFormula.filter{ it != unitClause }
                }
                continue
            }

            val allLiterals = currentFormula.flatten().toSet()
            val pureLiteral = allLiterals.find { !allLiterals.contains(-it) }

            if (pureLiteral != null) {
                val variable = abs(pureLiteral)

                if (!currentModel.containsKey(variable)) {
                    val value = pureLiteral > 0
                    currentModel = currentModel + (variable to value)
                    currentFormula = currentFormula.filter { !it.contains(pureLiteral) }
                    changed = true
                }
            }
        }
        return Triple(currentFormula, currentModel, false)
    }

    private fun chooseVariable(formula: Formula): Int {
        val minSize = formula.filter { it.size >= 2 }.minOfOrNull { it.size }
        if (minSize == null) {
            return abs(formula.first().first())
        }

        val minSizeClauses = formula.filter { it.size == minSize }
        val literalCounts = minSizeClauses.flatten()
            .groupingBy { it }
            .eachCount()

        if (literalCounts.isEmpty()) {
            return abs(formula.first().first())
        }

        val mostFrequentLiteral = literalCounts.maxByOrNull { it.value }!!.key
        return abs(mostFrequentLiteral)
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Ошибка: укажите путь к файлу в формате cnf.")
        println("Пример использования: java -jar SatSolver.jar path/to/your/file.cnf")
        exitProcess(1)
    }

    val filePath = args[0]
    println("Чтение файла: $filePath")

    try {
        val formula = CnfParser.parse(filePath)
        println("Файл успешно распарсен. Запускаем решатель...")
        println("---")

        val startTime = System.currentTimeMillis()
        val (isSatisfiable, model) = DpllSolver.solve(formula)
        val endTime = System.currentTimeMillis()

        println("Результат:")
        if (isSatisfiable) {
            println("SAT (Формула выполнима)")
            if (model != null) {
                val sortedModel = model.toSortedMap()
                println("Модель: ")
                sortedModel.forEach { (variable, value) ->
                    print("${if (value) variable else -variable} ")
                }
                println("0")
            }
        } else {
            println("UNSAT (Формула невыполнима)")
        }
        println("Время выполнения: ${endTime - startTime} мс")
    } catch (e: Exception) {
        println("Произошла ошибка: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}