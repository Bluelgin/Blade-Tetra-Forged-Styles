package dev.bladetetra.client;

/** The pink/lilac hair is distinct from warm skin and saturated red accessories. */
final class MikageBackplateTint {
    static boolean isHair(int red, int green, int blue) {
        return red >= 100 && green >= 65 && blue >= green - 4 && red >= green;
    }

    private MikageBackplateTint() { }
}
