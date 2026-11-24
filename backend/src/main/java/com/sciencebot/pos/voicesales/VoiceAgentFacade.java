package com.sciencebot.pos.voicesales;

import java.io.InputStream;

public interface VoiceAgentFacade {
    /** Audio to text only. Nothing is stored. */
    String transcribe(InputStream audio, String mediaType, long contentLength);

    /** Applies the cashier's words to the ticket and returns it. It never registers a sale. */
    VoiceAgentResponse turn(VoiceAgentRequest request);
}
