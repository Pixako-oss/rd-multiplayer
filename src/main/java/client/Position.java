package client;

public class Position {
    public double x, y, z;
    public float yaw;
    public int ping;

    public Position(double x, double y, double z, float yaw, int ping) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.ping = ping;
    }
}
