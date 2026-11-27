package dev.bladetetra.export;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BladeInscriptionTimelineTest {
    @Test void phasesAndFinalStrokeHaveOneSharedBoundary() {
        assertEquals(0, BladeInscriptionTimeline.sample(-1).phase());
        assertEquals(1, BladeInscriptionTimeline.sample(1200).phase());
        assertEquals(2, BladeInscriptionTimeline.sample(3000).phase());
        var impact = BladeInscriptionTimeline.sample(4500);
        assertEquals(3, impact.phase());
        assertEquals(1, impact.writing());
        assertEquals(1, impact.impact());
        assertEquals(0, impact.name());
        assertEquals(4, BladeInscriptionTimeline.sample(6000).phase());
        assertEquals(BladeInscriptionTimeline.sample(8000), BladeInscriptionTimeline.sample(Long.MAX_VALUE));
    }

    @Test void sparseFramesCoalesceAudioAndReplayRearmsIt() {
        var timeline = new BladeInscriptionTimeline();
        assertEquals(-1, timeline.consumeCue(0));
        assertEquals(0, timeline.consumeCue(1200));
        assertEquals(-1, timeline.consumeCue(1210));
        assertEquals(2, timeline.consumeCue(4700));
        assertEquals(-1, timeline.consumeCue(4701));
        assertEquals(3, timeline.consumeCue(7999));
        assertEquals(-1, timeline.consumeCue(8000));
        timeline.reset();
        assertEquals(2, timeline.consumeCue(4500));
    }

    @Test void frameRateDoesNotChangeCompletionOrDuplicateCues() {
        for (int fps : new int[]{15, 30, 60, 144}) {
            var timeline = new BladeInscriptionTimeline();
            int cues = 0;
            for (int frame = 0; frame <= fps * 8; frame++) {
                long time = frame * 1000L / fps;
                if (timeline.consumeCue(time) >= 0) cues++;
                var sample = BladeInscriptionTimeline.sample(time);
                assertTrue(sample.name() >= 0 && sample.name() <= 1);
                assertTrue(sample.release() >= 0 && sample.release() <= 1);
            }
            assertEquals(4, cues, "fps=" + fps);
            assertEquals(1, BladeInscriptionTimeline.sample(8000).name());
        }
    }
}
