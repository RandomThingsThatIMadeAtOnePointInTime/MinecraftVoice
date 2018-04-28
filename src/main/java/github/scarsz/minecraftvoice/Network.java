package github.scarsz.minecraftvoice;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Network {

    public static Set<Network> computeAllNetworks() {
        long startTime = System.currentTimeMillis();

        Set<Network> networks = new HashSet<>();
        Set<Route> routes = Route.computeAllRoutes();

        for (Route route : routes) {
            Network target = networks.stream()
                    .filter(network -> network.members.contains(route.getPlayer1()) || network.members.contains(route.getPlayer2()))
                    .findAny().orElse(null);

            if (target != null) {
                target.addMembers(route.getPlayer1(), route.getPlayer2());
            } else {
                target = new Network(route.getPlayer1(), route.getPlayer2());
                networks.add(target);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<Network> playerNetworks = networks.stream().filter(network -> network.members.contains(player)).collect(Collectors.toSet());

            if (playerNetworks.size() == 0) {
                // make sure all players are in a group, even if they're by themselves
                playerNetworks.add(new Network(player));
            } else if (playerNetworks.size() > 1) {
                // if a player is in multiple groups, merge them all together
                Network target = playerNetworks.iterator().next();
                playerNetworks.stream().filter(voiceGroup -> !voiceGroup.equals(target)).forEach(voiceGroup -> {
                    target.engulf(voiceGroup);
                    networks.remove(voiceGroup);
                });
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        MinecraftVoice.get().getLogger().info("Took " + elapsed + "ms to compute " + networks.size() + " networks (" + (elapsed / 50d) + "% of server tick)");

        return networks;
    }

    private Set<Player> members = new HashSet<>();

    public Network(Player... players) {
        addMembers(players);
    }

    public Set<Player> getMembers() {
        return members;
    }

    public void addMembers(Player... players) {
        members.addAll(Arrays.asList(players));
    }

    public void removeMembers(Player... players) {
        members.removeAll(Arrays.asList(players));
    }

    /**
     * Move all members from the given {@link Network} to this one
     * @param network the {@link Network} to engulf
     * @return the engulfed {@link Network}
     */
    private Network engulf(Network network) {
        network.members.forEach(this::addMembers);
        network.members.clear();
        return network;
    }

    @Override
    public String toString() {
        return "Network{" +
                "members=" + members +
                '}';
    }

}
