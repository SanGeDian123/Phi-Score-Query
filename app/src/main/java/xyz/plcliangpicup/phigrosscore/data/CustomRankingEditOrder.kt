package xyz.plcliangpicup.phigrosscore.data

/** Keep the visible sources until an input session and its keyboard have ended. */
internal data class CustomRankingEditOrder(
    val rows: List<CustomRankingRow>? = null,
    val imeWasVisible: Boolean = false,
) {
    fun begin(currentRows: List<CustomRankingRow>) = if (rows == null) copy(rows = currentRows) else this

    fun advance(imeVisible: Boolean, inputFocused: Boolean): CustomRankingEditOrder = when {
        rows == null -> this
        imeVisible -> copy(imeWasVisible = true)
        imeWasVisible || !inputFocused -> CustomRankingEditOrder()
        else -> this // Focus arrives before the software keyboard opens.
    }
}
