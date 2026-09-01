package nurgling.widgets.compass;

public final class NCompassMath {
    public enum Region {
        FRONT,
        REAR_LEFT,
        REAR_RIGHT
    }

    public static final class Projection {
        public final Region region;
        public final double relative;
        public final int x;

        Projection(Region region, double relative, int x) {
            this.region = region;
            this.relative = relative;
            this.x = x;
        }
    }

    private NCompassMath() {
    }

    public static double normalize(double angle) {
        while(angle >= Math.PI)
            angle -= Math.PI * 2.0;
        while(angle < -Math.PI)
            angle += Math.PI * 2.0;
        return angle;
    }

    public static double cameraHeading(double cameraAngle) {
        return normalize(cameraAngle);
    }

    public static Projection project(double targetBearing, double cameraAngle, int width) {
        int w = Math.max(0, width);
        double relative = normalize(targetBearing - cameraHeading(cameraAngle));
        if(relative < -Math.PI / 2)
            return new Projection(Region.REAR_LEFT, relative, 0);
        if(relative > Math.PI / 2)
            return new Projection(Region.REAR_RIGHT, relative, w);
        int x = (int)Math.round(((relative + Math.PI / 2) / Math.PI) * w);
        return new Projection(Region.FRONT, relative, Math.max(0, Math.min(w, x)));
    }
}
