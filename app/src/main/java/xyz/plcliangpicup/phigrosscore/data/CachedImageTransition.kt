package xyz.plcliangpicup.phigrosscore.data

import coil.decode.DataSource
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.transition.TransitionTarget

/** Local images are ready to display; only newly downloaded images need a fade. */
internal object CachedImageTransition : Transition.Factory {
    private val networkTransition = CrossfadeTransition.Factory(120)

    override fun create(target: TransitionTarget, result: ImageResult): Transition =
        if (result is SuccessResult && result.dataSource == DataSource.NETWORK) {
            networkTransition.create(target, result)
        } else {
            Transition.Factory.NONE.create(target, result)
        }
}
