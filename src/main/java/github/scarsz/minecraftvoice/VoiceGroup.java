package github.scarsz.minecraftvoice;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.core.Permission;
import github.scarsz.discordsrv.dependencies.jda.core.entities.Category;
import github.scarsz.discordsrv.dependencies.jda.core.entities.GuildVoiceState;
import github.scarsz.discordsrv.dependencies.jda.core.entities.PermissionOverride;
import github.scarsz.discordsrv.dependencies.jda.core.entities.VoiceChannel;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class VoiceGroup {

    public static final Set<VoiceGroup> GROUPS = new HashSet<>();

    private final Set<Player> members = new HashSet<>();
    private final UUID uuid = UUID.randomUUID();

    public VoiceGroup(Player... players) {
        addMembers(players);
    }

    public VoiceGroup(Set<Player> members) {
        members.forEach(this::addMembers);
    }

    public static Set<VoiceGroup> computeAllGroups() {
        Set<Network> networks = Network.computeAllNetworks();
        MinecraftVoice.get().getLogger().info("Networks: " + networks);

        // collect voice groups whose members exactly match one of the computed networks
        Set<VoiceGroup> unchangedGroups = GROUPS.stream().filter(voiceGroup -> networks.stream().anyMatch(network -> network.getMembers().containsAll(voiceGroup.members) && network.getMembers().size() == voiceGroup.members.size())).collect(Collectors.toSet());

        // destroy groups where their members have changed
        GROUPS.stream().filter(voiceGroup -> !unchangedGroups.contains(voiceGroup)).collect(Collectors.toSet()).forEach(VoiceGroup::destroy);

        // create new groups for new networks
        for (Network network : networks) {
            boolean matched = GROUPS.stream().anyMatch(voiceGroup -> voiceGroup.members.containsAll(network.getMembers()) && voiceGroup.members.size() == network.getMembers().size());
            if (!matched) GROUPS.add(new VoiceGroup(network.getMembers()));
        }

        MinecraftVoice.get().getLogger().info("Groups: " + GROUPS);

        return GROUPS;
    }

    public void addMembers(Player... players) {
        members.addAll(Arrays.asList(players));
    }

    public void removeMembers(Player... players) {
        members.removeAll(Arrays.asList(players));

        if (members.size() == 0) destroy();
    }

    public void tick() {
        getVoiceChannel().getMembers().stream()
                .filter(member -> members.stream().noneMatch(player -> DiscordSRV.getPlugin().getAccountLinkManager().getUuid(member.getUser().getId()).equals(player.getUniqueId())))
                .forEach(member -> member.getGuild().getController().moveVoiceMember(member, MinecraftVoice.get().getLobbyChannel()).queue());
        getVoiceChannel().getGuild().getVoiceStates().stream()
                .filter(GuildVoiceState::inVoiceChannel)
                .map(GuildVoiceState::getMember)
                .filter(Objects::nonNull)
                .filter(member -> members.stream().anyMatch(player -> DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId()) != null && DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId()).equals(member.getUser().getId())))
                .filter(member -> !getVoiceChannel().getMembers().contains(member))
                .forEach(member -> member.getGuild().getController().moveVoiceMember(member, getVoiceChannel()).queue());
    }

    public void destroy() {
        // move all members of voice channel to lobby
        getVoiceChannel()
                .getMembers()
                .forEach(member -> DiscordSRV.getPlugin()
                        .getMainGuild()
                        .getController()
                        .moveVoiceMember(
                                member,
                                MinecraftVoice.get().getLobbyChannel()
                        ).queue());

        // delete group's voice channel
        new Thread(() -> {
            while (getVoiceChannel().getMembers().size() > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            getVoiceChannel().delete().queue();
        }).start();

        // clear members
        this.members.clear();

        // remove group from list
        GROUPS.remove(this);
    }

    public VoiceChannel getVoiceChannel() {
        Category category = MinecraftVoice.get().getCategory();
        VoiceChannel voiceChannel = category.getVoiceChannels().stream()
                .filter(vc -> vc.getName().equals(uuid.toString()))
                .findFirst()
                .orElse(null);
        if (voiceChannel == null) {
            voiceChannel = (VoiceChannel) category.createVoiceChannel(uuid.toString())
                    .addPermissionOverride(category.getGuild().getPublicRole(), Collections.emptyList(), Arrays.asList(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT))
                    .complete();
        }

        // make sure category has correct override
        PermissionOverride override = voiceChannel.getPermissionOverride(MinecraftVoice.get().getGuild().getPublicRole());

        // create the override if it doesn't already exist
        if (override == null) {
            override = voiceChannel.createPermissionOverride(MinecraftVoice.get().getGuild().getPublicRole()).complete();
        }

        // denying voice connect permission so people can't channel hop through discord
        if (!override.getDenied().contains(Permission.VIEW_CHANNEL) || !override.getDenied().contains(Permission.VOICE_CONNECT)) {
            override.getManager().deny(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT).queue();
        }

        final VoiceChannel finalVoiceChannel = voiceChannel;
        finalVoiceChannel.getGuild().getVoiceStates().stream()
                .filter(GuildVoiceState::inVoiceChannel)
                .map(GuildVoiceState::getMember)
                .filter(Objects::nonNull)
                .filter(member -> members.stream().anyMatch(player -> DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId()) != null && DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId()).equals(member.getUser().getId())))
                .filter(member -> !finalVoiceChannel.getMembers().contains(member))
                .forEach(member -> member.getGuild().getController().moveVoiceMember(member, finalVoiceChannel).queue());

        return voiceChannel;
    }
    public Set<Player> getMembers() {
        return this.members;
    }
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return "VoiceGroup{" +
                "uuid=" + uuid +
                ", members=" + members +
                '}';
    }

}
