package dev.bladetetra.export;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** Keep source versions as provenance and write compatibility ranges separately. */
final class ExportDependencyVersions {
    private static final Pattern NUMERIC_VERSION = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\.[0-9]+)*(?:[+_-][a-zA-Z0-9_.+_-]+)?");
    static final String SLASHBLADE = "[1.9.63,2)";

    static String range(String modId, String observed) {
        if ("slashblade".equals(modId)) return SLASHBLADE;
        var version = NUMERIC_VERSION.matcher(observed);
        // Without a numeric release series there is no defensible inferred upper bound.
        if (!version.matches()) return "[" + observed + ",)";
        var major = new BigInteger(version.group(1));
        var minor = new BigInteger(version.group(2));
        String upper = major.signum() == 0 ? "0." + minor.add(BigInteger.ONE) : major.add(BigInteger.ONE).toString();
        return "[" + observed + "," + upper + ")";
    }

    private ExportDependencyVersions() {}
}
