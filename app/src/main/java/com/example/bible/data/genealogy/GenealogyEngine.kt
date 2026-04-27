package com.example.bible.data.genealogy

/**
 * Поиск по графу родства: предки, потомки, супруги, братья/сёстры, кратчайший путь.
 */
object GenealogyEngine {

    private val childrenByParent: Map<String, Set<String>>
    private val parentsByChild: Map<String, Set<String>>
    private val spouseOf: Map<String, Set<String>>
    private val edgeKind: Map<Pair<String, String>, ParentLinkKind>

    init {
        val c = mutableMapOf<String, MutableSet<String>>()
        val p = mutableMapOf<String, MutableSet<String>>()
        val kinds = mutableMapOf<Pair<String, String>, ParentLinkKind>()
        for (e in GenealogyData.parentChildEdges) {
            c.getOrPut(e.parentId) { mutableSetOf() }.add(e.childId)
            p.getOrPut(e.childId) { mutableSetOf() }.add(e.parentId)
            kinds[e.parentId to e.childId] = e.kind
        }
        childrenByParent = c.mapValues { it.value.toSet() }
        parentsByChild = p.mapValues { it.value.toSet() }
        edgeKind = kinds
        val s = mutableMapOf<String, MutableSet<String>>()
        for ((a, b) in GenealogyData.spousePairs) {
            s.getOrPut(a) { mutableSetOf() }.add(b)
            s.getOrPut(b) { mutableSetOf() }.add(a)
        }
        spouseOf = s.mapValues { it.value.toSet() }
    }

    fun getPerson(id: String): GenealogyPerson? = GenealogyData.persons[id]

    fun allPersons(): List<GenealogyPerson> = GenealogyData.persons.values.sortedBy { it.nameRu }

    fun search(query: String): List<GenealogyPerson> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return GenealogyData.persons.values.filter {
            it.nameRu.lowercase().contains(q) ||
                it.noteRu.lowercase().contains(q)
        }.sortedBy { it.nameRu }
    }

    fun parents(id: String): List<GenealogyPerson> =
        (parentsByChild[id] ?: emptySet()).mapNotNull { GenealogyData.persons[it] }

    fun children(id: String): List<GenealogyPerson> =
        (childrenByParent[id] ?: emptySet()).mapNotNull { GenealogyData.persons[it] }

    fun spouses(id: String): List<GenealogyPerson> =
        (spouseOf[id] ?: emptySet()).mapNotNull { GenealogyData.persons[it] }

    fun siblings(id: String): List<GenealogyPerson> {
        val ps = parentsByChild[id] ?: return emptyList()
        val sib = mutableSetOf<String>()
        for (pid in ps) {
            sib.addAll(childrenByParent[pid] ?: emptySet())
        }
        sib.remove(id)
        return sib.mapNotNull { GenealogyData.persons[it] }.sortedBy { it.nameRu }
    }

    private fun neighbors(id: String): Set<String> =
        (childrenByParent[id] ?: emptySet()) +
            (parentsByChild[id] ?: emptySet()) +
            (spouseOf[id] ?: emptySet())

    /**
     * Кратчайший путь по графу (родители, дети, супруги).
     */
    fun shortestPath(fromId: String, toId: String): GenealogyPathResult {
        val from = GenealogyData.persons[fromId]
            ?: return GenealogyPathResult.NotFound("Неизвестная персона: $fromId")
        val to = GenealogyData.persons[toId]
            ?: return GenealogyPathResult.NotFound("Неизвестная персона: $toId")
        if (fromId == toId) return GenealogyPathResult.SamePerson(from)

        val queue = ArrayDeque<List<String>>()
        queue.add(listOf(fromId))
        val visited = mutableSetOf(fromId)
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val cur = path.last()
            if (cur == toId) {
                return GenealogyPathResult.Found(buildSteps(path))
            }
            for (n in neighbors(cur)) {
                if (n !in visited) {
                    visited.add(n)
                    queue.add(path + n)
                }
            }
        }
        return GenealogyPathResult.NotFound(
            "В справочнике приложения нет связи между «${from.nameRu}» и «${to.nameRu}». " +
                "Добавлены не все библейские персоны.",
        )
    }

    private fun buildSteps(path: List<String>): List<GenealogyPathStep> {
        val steps = mutableListOf<GenealogyPathStep>()
        for (i in 0 until path.lastIndex) {
            val a = path[i]
            val b = path[i + 1]
            val pa = GenealogyData.persons[a]!!
            val pb = GenealogyData.persons[b]!!
            steps.add(GenealogyPathStep(pa, pb, describeStep(a, b)))
        }
        return steps
    }

    private fun describeStep(fromId: String, toId: String): String {
        val from = GenealogyData.persons[fromId]!!.nameRu
        val to = GenealogyData.persons[toId]!!.nameRu
        if (toId in (childrenByParent[fromId] ?: emptySet())) {
            val kind = edgeKind[fromId to toId] ?: ParentLinkKind.BIOLOGICAL
            return when (kind) {
                ParentLinkKind.BIOLOGICAL -> "↓ $from — родитель / предок для $to"
                ParentLinkKind.LEGAL -> "↓ $from — законный родитель для $to (как в Мф 1 для Иисуса)"
            }
        }
        if (fromId in (childrenByParent[toId] ?: emptySet())) {
            return "↑ $from — ребёнок / потомок по отношению к $to (идём к предку)"
        }
        if (toId in (spouseOf[fromId] ?: emptySet())) {
            return "⟷ супружеская связь: $from и $to"
        }
        return "→ от $from к $to"
    }

    /** Прямые потомки по одному шагу вниз (для карточки). */
    fun directDescendants(id: String): List<GenealogyPerson> = children(id)

    /** Прямые предки (родители). */
    fun directAncestors(id: String): List<GenealogyPerson> = parents(id)
}
