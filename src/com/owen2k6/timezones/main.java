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

    public static final long FULL_DAY_TICKS = 24000L;
    public static final double REAL_TICKS_PER_MINECRAFT_DAY = 864000.0;
    public static final double TICKS_PER_MINECRAFT_TICK = FULL_DAY_TICKS / REAL_TICKS_PER_MINECRAFT_DAY;

    private double internalTime = 0.0;
    private long worldDays = 0; // Add this field to track in-game days
    private boolean dayIncremented = false; // Add this flag
    private boolean starting;

    private File timeFile;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        timeFile = new File(getDataFolder(), "time.yml");
        internalTime = loadInternalTime();
        starting = true;

        // Register the /date command
        getCommand("date").setExecutor(new datecmd(this));

        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::updateInternalTime, 0L, 1L);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::saveInternalTime, 0L, 6000L);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::updatePlayerTimes, 0L, 10L);
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

        // Ensure worldDays increments once per day when crossing midnight
        if (internalTime >= 18000 && !dayIncremented && !starting) {
            worldDays++;  // Increment worldDays only once per day
            dayIncremented = true;  // Set the flag to prevent multiple increments

        }

        // Reset the flag after passing midnight (when the time goes below 18,000)
        if (internalTime < 18000) {
            dayIncremented = false;  // Reset flag so the next day can be incremented
            starting = false;
        }

        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld != null) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    try {
                        mainWorld.setFullTime(0L);  // Always set world time to tick 0
                    } catch (Exception e) {
                        getLogger().severe("Failed to set world time: " + e.getMessage());
                    }
                }
            });
        }
    }


    private void updatePlayerTimes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            long adjustedTime = calculatePlayerTime(location);
            player.setPlayerTime(adjustedTime, true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        long adjustedTime = calculatePlayerTime(location);
        player.setPlayerTime(adjustedTime, true);
    }

    long calculatePlayerTime(Location location) {
        int z = location.getBlockZ();
        int timeZoneOffset = z / 8333; // Each time zone covers 8333 blocks.
        double fractionalProgress = (z % 8333) / 8333.0; // Corrected the progression to match 8333 blocks.

        long currentTime = (long) ((internalTime - (timeZoneOffset * 1000)) % FULL_DAY_TICKS);
        if (currentTime < 0) currentTime += FULL_DAY_TICKS;

        long nextTime = (long) ((internalTime - ((timeZoneOffset + 1) * 1000)) % FULL_DAY_TICKS);
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
                writer.write("world-days: " + worldDays); // Save worldDays
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Failed to save internal time!");
        }
    }


    public long getWorldDays() {
        return worldDays;
    }

    public double getInternalTime() {
        return internalTime;
    }


    private double loadInternalTime() {
        if (!timeFile.exists()) {
            return 0.0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(timeFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("internal-time: ")) {
                    internalTime = Double.parseDouble(line.replace("internal-time: ", "").trim());
                } else if (line.startsWith("world-days: ")) {
                    worldDays = Long.parseLong(line.replace("world-days: ", "").trim()); // Load worldDays
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Failed to load internal time!");
        }

        return internalTime;
    }

}
