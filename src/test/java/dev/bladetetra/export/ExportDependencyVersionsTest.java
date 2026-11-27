package dev.bladetetra.export;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExportDependencyVersionsTest {
    @Test
    void slashBladePatchVersionsShareTheSameContract() throws Exception {
        var range = VersionRange.createFromVersionSpec(ExportDependencyVersions.range("slashblade", "1.9.65"));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("1.9.62")));
        assertTrue(range.containsVersion(new DefaultArtifactVersion("1.9.63")));
        assertTrue(range.containsVersion(new DefaultArtifactVersion("1.9.65")));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("2.0.0")));
    }

    @Test
    void derivesThirdPartyRangesFromTheInstalledRelease() {
        assertEquals("[3.2.1,4)", ExportDependencyVersions.range("example", "3.2.1"));
        assertEquals("[0.6.2,0.7)", ExportDependencyVersions.range("example", "0.6.2"));
        assertEquals("[release-12,)", ExportDependencyVersions.range("example", "release-12"));
    }
}
