package haven;

final class MarkerNameFormatter {
    private MarkerNameFormatter() {
    }

    static String prettify(String basename) {
        StringBuilder buf = new StringBuilder();
        for (String word : basename.split("[_-]+")) {
            if (word.isEmpty())
                continue;
            if (buf.length() > 0)
                buf.append(' ');
            buf.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return (buf.length() > 0) ? buf.toString() : basename;
    }
}
