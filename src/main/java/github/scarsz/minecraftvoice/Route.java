package github.scarsz.minecraftvoice;

import javafx.geometry.Point3D;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class Route {

    public static final int MAX_DISTANCE = 25;

    public static Set<Route> computeAllRoutes() {
        Set<Route> routes = new HashSet<>();

        for (Player player1 : Bukkit.getOnlinePlayers()) {
            for (Player player2 : Bukkit.getOnlinePlayers()) {
                if (player1.equals(player2)) continue;

                boolean routeExists = routes.stream()
                        .anyMatch(route ->
                                (player1.equals(route.getPlayer1()) || player1.equals(route.getPlayer2())) &&
                                (player2.equals(route.getPlayer1()) || player2.equals(route.getPlayer2()))
                        );

                if (routeExists) continue;

                Route route = new Route(player1, player2);

                if (route.getDistance() > MAX_DISTANCE) continue;

                routes.add(route);
            }
        }

        return routes;
    }

    private final Player[] players;

    public Route(Player player1, Player player2) {
        this.players = new Player[] { player1, player2 };
    }

    public double getDistance() {
        return getPlayer1().getLocation().distance(getPlayer2().getLocation());
    }
    public Player getPlayer1() {
        return players[0];
    }
    public Player getPlayer2() {
        return players[1];
    }
    public Point3D getPoint1() {
        return new Point3D(
                getPlayer1().getLocation().getX(),
                getPlayer1().getLocation().getY(),
                getPlayer1().getLocation().getZ()
        );
    }
    public Point3D getPoint2() {
        return new Point3D(
                getPlayer2().getLocation().getX(),
                getPlayer2().getLocation().getY(),
                getPlayer2().getLocation().getZ()
        );
    }

}
