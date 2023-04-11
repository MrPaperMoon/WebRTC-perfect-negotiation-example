package open.mrpapermoon.perfectnegotiatione

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface RtcEvent {
    fun onIce(ice: IceCandidate)
    fun removeIce(ice: IceCandidate)
    fun onSdp(sdp: SessionDescription)
}