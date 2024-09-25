package com.owen2k6.timezones;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

import static org.bukkit.Bukkit.getLogger;

public class main extends JavaPlugin implements Listener {

    private static final long FULL_DAY_TICKS = 24000L;
    private static final double REAL_TICKS_PER_MINECRAFT_DAY = 72000.0;
    private static final double TICKS_PER_MINECRAFT_TICK = FULL_DAY_TICKS / REAL_TICKS_PER_MINECRAFT_DAY;

    private double internalTime = 0.0;

    private File timeFile;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        timeFile = new File(getDataFolder(), "time.yml");
        internalTime = loadInternalTime();
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::updateInternalTime, 0L, 1L);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::saveInternalTime, 0L, 6000L);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::updatePlayerTimes, 0L, 100L);
        getLogger().info("Timezones Plugin Enabled");
    }

    @Override
    public void onDisable() {
        saveInternalTime();
        getLogger().info("Timezones Plugin Disabled");
    }
    private void saveInternalTime() {
        saveWorldTime(internalTime);
    }

    private void updateInternalTime() {
        internalTime = (internalTime + TICKS_PER_MINECRAFT_TICK) % FULL_DAY_TICKS;
        for (World world : Bukkit.getWorlds()) {
            world.setFullTime(0L);
        }
    }

    private void updatePlayerTimes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            long adjustedTime = calculatePlayerTime(location);
            player.setPlayerTime(adjustedTime, true);
            player.sendMessage("Your adjusted time is: " + adjustedTime);
            player.sendMessage("The time zone is: " + (location.getBlockZ() / 1000));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        long adjustedTime = calculatePlayerTime(location);
        player.setPlayerTime(adjustedTime, true);
    }

    private long calculatePlayerTime(Location location) {
        int z = location.getBlockZ();
        int timeZoneOffset = z / 1000;
        double fractionalProgress = (z % 1000) / 1000.0;
        long currentTime = (long) ((internalTime - (timeZoneOffset * 1000L)) % FULL_DAY_TICKS);
        if (currentTime < 0) currentTime += FULL_DAY_TICKS;
        long nextTime = (long) ((internalTime - ((timeZoneOffset + 1) * 1000L)) % FULL_DAY_TICKS);
        if (nextTime < 0) nextTime += FULL_DAY_TICKS;
        if (Math.abs(nextTime - currentTime) > FULL_DAY_TICKS / 2) {
            if (nextTime > currentTime) {
                nextTime -= FULL_DAY_TICKS;
            } else {
                nextTime += FULL_DAY_TICKS;
            }
        }
        return (long) (currentTime * (1 - fractionalProgress) + nextTime * fractionalProgress);
    }

    private void saveWorldTime(double worldTime) {
        try {
            if (!timeFile.exists()) {
                timeFile.getParentFile().mkdirs();
                timeFile.createNewFile();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(timeFile))) {
                writer.write("internal-time: " + worldTime);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Failed to save internal time!");
        }
    }

    private double loadInternalTime() {
        if (!timeFile.exists()) {
            return 0.0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(timeFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("internal-time: ")) {
                    return Double.parseDouble(line.replace("internal-time: ", "").trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Failed to load internal time!");
        }

        return 0.0;
    }
}
