package open.mrpapermoon.perfectnegotiatione

import org.webrtc.*

abstract class PeerConnectionObserver(private val webrtcLogger: (String) -> Unit) : PeerConnection.Observer {
    override fun onIceCandidate(iceCandidate: IceCandidate) {
        webrtcLogger("onIceCandidate(iceCandidate = $iceCandidate)")
    }

    override fun onIceCandidatesRemoved(iceCandidates: Array<out IceCandidate>) {
        webrtcLogger("onIceCandidatesRemoved(iceCandidates = $iceCandidates)")
    }

    override fun onDataChannel(dataChannel: DataChannel) {
        webrtcLogger("onDataChannel(dataChannel = $dataChannel)")
    }

    override fun onIceConnectionReceivingChange(change: Boolean) {
        webrtcLogger("onIceConnectionReceivingChange(change = $change)")
    }

    override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
        webrtcLogger("onIceConnectionChange(iceConnectionState = $iceConnectionState)")
    }

    override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
        webrtcLogger("onIceGatheringChange(iceGatheringState = $iceGatheringState)")
    }

    override fun onAddStream(mediaStream: MediaStream) {
        webrtcLogger("onAddStream(mediaStream = $mediaStream)")
    }

    override fun onRemoveStream(mediaStream: MediaStream) {
        webrtcLogger("onRemoveStream(mediaStream = $mediaStream)")
    }

    override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
        webrtcLogger("onSignalingChange(signalingState = $signalingState)")
    }

    override fun onRenegotiationNeeded() {
        webrtcLogger("onRenegotiationNeeded()")
    }

    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
        webrtcLogger("onAddTrack(receiver = $receiver, mediaStream = $mediaStreams)")
    }

    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
        webrtcLogger("onConnectionChange(newState = $newState)")
    }

    override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState) {
        webrtcLogger("onStandardizedIceConnectionChange(newState = $newState)")
    }

    override fun onTrack(transceiver: RtpTransceiver) {
        webrtcLogger("onTrack(transceiver = $transceiver)")
    }

    override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {
        webrtcLogger("onSelectedCandidatePairChanged(event = ${event.reason})")
    }
}