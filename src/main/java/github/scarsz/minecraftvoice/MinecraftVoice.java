package github.scarsz.minecraftvoice;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.core.Permission;
import github.scarsz.discordsrv.dependencies.jda.core.entities.Category;
import github.scarsz.discordsrv.dependencies.jda.core.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.core.entities.PermissionOverride;
import github.scarsz.discordsrv.dependencies.jda.core.entities.VoiceChannel;
import github.scarsz.discordsrv.dependencies.jda.core.managers.PermOverrideManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class MinecraftVoice extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        while (!DiscordSRV.isReady) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        getLogger().info("Minecraft voice initializing for \"" + getGuild() + "\"");
        getLogger().info("Using channel category " + getCategory());
        getLogger().info("Players can join the voice system through " + getLobbyChannel());

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            VoiceGroup[] voiceGroups = VoiceGroup.computeAllGroups().toArray(new VoiceGroup[0]);
            Arrays.stream(voiceGroups).forEach(VoiceGroup::tick);

            for (Player player : Bukkit.getOnlinePlayers()) {
                for (int i = 0; i < 10; i++) player.sendMessage("");
                player.sendMessage(voiceGroups.length + " groups:");
            }

            for (int i = 0; i < voiceGroups.length; i++) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String players = voiceGroups[i].getMembers().stream().map(p -> ChatColor.AQUA + p.getName()).collect(Collectors.joining(ChatColor.GRAY + ", "));
                    player.sendMessage("- Group " + voiceGroups[i].getUuid());
                    player.sendMessage("  - " + voiceGroups[i].getMembers().size() + " players: " + players);
                }
            }
        }, 0, 20);

        onDisable();

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // delete old channels
        getCategory().getVoiceChannels().stream()
                .filter(voiceChannel -> !voiceChannel.getName().equals(getConfig().getString("LobbyChannelName")))
                .forEach(voiceChannel -> voiceChannel.delete().queue());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        VoiceGroup group = VoiceGroup.GROUPS.stream().filter(voiceGroup -> voiceGroup.getMembers().contains(event.getPlayer())).findAny().orElse(null);
        if (group == null) return;

        double closest = Double.MAX_VALUE;
        for (Player player : group.getMembers()) {
            if (player.equals(event.getPlayer())) continue;
            double distance = player.getLocation().distance(event.getPlayer().getLocation());
            if (distance < closest) closest = distance;
        }

        // remove the player if the closest groupie is too far away
        boolean shouldRemove = closest >= getConfig().getInt("VoiceRadius");
        if (shouldRemove) group.removeMembers(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        VoiceGroup.GROUPS.forEach(voiceGroup -> voiceGroup.removeMembers(event.getPlayer()));
    }

    public Guild getGuild() {
        Guild guild = DiscordSRV.getPlugin().getJda().getGuilds().get(0);

        if (!guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)) {
            throw new RuntimeException("bot does not have administrator permission in " + guild);
        }

        return guild;
    }
    public Category getCategory() {
        List<Category> categories = getGuild().getCategoriesByName(getConfig().getString("CategoryName"), true);

        // create the voice category if it doesn't already exist
        if (categories.size() == 0) {
            Category created = (Category) getGuild().getController().createCategory(getConfig().getString("CategoryName"))
                    .addPermissionOverride(getGuild().getPublicRole(), Collections.emptyList(), Collections.singleton(Permission.VOICE_CONNECT))
                    .complete();
            categories.add(created);
        }

        // clean up extra categories
        if (categories.size() > 1) categories.stream().skip(1).forEach(category -> category.delete().reason("Purging extra category").queue());

        Category category = categories.get(0);

        // make sure category has correct override
        PermissionOverride override = category.getPermissionOverride(getGuild().getPublicRole());

        // create the override if it doesn't already exist
        if (override == null) {
            override = category.createPermissionOverride(getGuild().getPublicRole()).complete();
        }

        // denying voice connect permission so people can't channel hop through discord
        if (!override.getDenied().contains(Permission.VOICE_CONNECT)) {
            override.getManager().deny(Permission.VOICE_CONNECT).queue();
        }

        return category;
    }
    public VoiceChannel getLobbyChannel() {
        VoiceChannel channel = getCategory().getVoiceChannels().stream().filter(voiceChannel -> voiceChannel.getName().equals(getConfig().getString("LobbyChannelName"))).findFirst().orElse(null);

        // create the voice channel if it doesn't already exist
        if (channel == null) {
            channel = (VoiceChannel) getCategory().createVoiceChannel(getConfig().getString("LobbyChannelName"))
                    .addPermissionOverride(getGuild().getPublicRole(), Collections.singleton(Permission.VOICE_CONNECT), Collections.emptyList())
                    .addPermissionOverride(getGuild().getPublicRole(), Collections.emptyList(), Collections.singleton(Permission.VOICE_SPEAK))
                    .complete();
        }

        // get override so users can connect to this channel, opposing the category's permissions
        PermissionOverride override = channel.getPermissionOverride(getGuild().getPublicRole());

        // create the override if it doesn't already exist
        if (override == null) {
            override = channel.createPermissionOverride(getGuild().getPublicRole()).complete();
        }

        boolean changed = false;
        PermOverrideManager manager = override.getManager();
        // allowing voice connect permission so people can enter the voice channel system
        if (!override.getAllowed().contains(Permission.VOICE_CONNECT)) {
            changed = true;
            manager = manager.grant(Permission.VOICE_CONNECT);
        }
        // denying speak permission so people can't communicate in the lobby
        if (!override.getDenied().contains(Permission.VOICE_SPEAK)) {
            changed = true;
            manager = manager.deny(Permission.VOICE_SPEAK);
        }
        if (changed) manager.queue();

        return channel;
    }
    public static MinecraftVoice get() {
        return (MinecraftVoice) Bukkit.getPluginManager().getPlugin("MinecraftVoice");
    }

}
