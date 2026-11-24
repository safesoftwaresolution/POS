package com.sciencebot.pos.voicesales;

import java.io.InputStream;

public interface VoiceSaleFacade {
    VoiceSalePreviewDto preview(InputStream audio, String mediaType, long contentLength);
}
