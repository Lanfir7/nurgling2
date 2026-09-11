package nurgling.widgets;

/** Chooses the closest known province marker within a map segment. */
final class ProvinceNameResolver {
    static final class Candidate {
        final String name;
        final long segment;
        final long x;
        final long y;

        Candidate(String name, long segment, long x, long y) {
            this.name = name;
            this.segment = segment;
            this.x = x;
            this.y = y;
        }
    }

    private ProvinceNameResolver() {
    }

    static String nearest(long segment, long tileX, long tileY, Iterable<Candidate> candidates) {
        if(candidates == null)
            return null;

        String nearestName = null;
        long nearestX = 0;
        long nearestY = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for(Candidate candidate : candidates) {
            if(candidate == null || candidate.segment != segment || candidate.name == null)
                continue;
            String name = candidate.name.trim();
            if(name.isEmpty())
                continue;

            double dx = (double)candidate.x - tileX;
            double dy = (double)candidate.y - tileY;
            double distance = (dx * dx) + (dy * dy);
            int comparison = Double.compare(distance, nearestDistance);
            if(comparison < 0 || (comparison == 0 && isEarlier(name, candidate.x, candidate.y, nearestName, nearestX, nearestY))) {
                nearestName = name;
                nearestX = candidate.x;
                nearestY = candidate.y;
                nearestDistance = distance;
            }
        }
        return nearestName;
    }

    private static boolean isEarlier(String name, long x, long y, String otherName, long otherX, long otherY) {
        if(otherName == null)
            return true;
        int nameComparison = name.compareTo(otherName);
        if(nameComparison != 0)
            return nameComparison < 0;
        int xComparison = Long.compare(x, otherX);
        return (xComparison != 0) ? (xComparison < 0) : (Long.compare(y, otherY) < 0);
    }
}
