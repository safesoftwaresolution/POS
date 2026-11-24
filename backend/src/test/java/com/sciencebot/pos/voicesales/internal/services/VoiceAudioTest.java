package com.sciencebot.pos.voicesales.internal.services;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.*;

class VoiceAudioTest {
    @Test
    void chunkedOversizeInputIsRejectedEvenWithoutContentLength() {
        var input = new ByteArrayInputStream(new byte[VoiceAudio.MAX_BYTES + 1]);
        assertThatThrownBy(() -> VoiceAudio.read(input, "audio/wav", -1))
                .isInstanceOfSatisfying(PreviewRejected.class, e -> assertThat(e.status().value()).isEqualTo(413));
    }

    @Test
    void browserWebmWithCodecParametersIsRecognizedButMatroskaIsNotAcceptedAsWebm() {
        byte[] webm = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, (byte) 0x87, 0x42, (byte) 0x82,
                (byte) 0x84, 'w', 'e', 'b', 'm'};
        String type = VoiceAudio.type("audio/webm;codecs=opus");
        assertThat(VoiceAudio.read(new ByteArrayInputStream(webm), type, -1)).isEqualTo(webm);
        webm[8] = 'x';
        assertThatThrownBy(() -> VoiceAudio.read(new ByteArrayInputStream(webm), type, -1))
                .isInstanceOf(PreviewRejected.class);
    }
}
