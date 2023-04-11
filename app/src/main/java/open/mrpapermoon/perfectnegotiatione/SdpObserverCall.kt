package open.mrpapermoon.perfectnegotiatione

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class SdpObserverCall<T> : SdpObserver {

    class GetForResult(override val continuation: Continuation<SessionDescription>) :
        SdpObserverCall<SessionDescription>() {
        override fun onCreateSuccess(sdp: SessionDescription) {
            continuation.resume(sdp)
        }
    }

    class Completable(override val continuation: Continuation<Unit>) : SdpObserverCall<Unit>() {
        override fun onSetSuccess() {
            continuation.resume(Unit)
        }
    }

    protected abstract val continuation: Continuation<T>

    override fun onCreateSuccess(sdp: SessionDescription) {
    }

    override fun onSetSuccess() {
    }

    override fun onCreateFailure(message: String) {
        setException(message)
    }

    override fun onSetFailure(message: String) {
        setException(message)
    }

    private fun setException(message: String) {
        val exception = IllegalArgumentException(message)
        continuation.resumeWithException(exception)
    }
}