package com.owen2k6.timezones;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.format.TextStyle;
import java.util.Locale;

public class datecmd implements CommandExecutor {

    private final main plugin;
    private final String[] weekdays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private final String[] mweekdays = {"Moleday", "Unityday", "Ekzday", "Ictoriaday", "Vikingday", "Floriday", "Crownday"};

    public datecmd(main main) {
        this.plugin = main;
    }

    private LocalDate calculateLocalDate(LocalDate centralDate, long internalTime, int timeZoneOffset) {
        // Calculate the local time in ticks, starting from 6 AM (6000 ticks)
        long localTimeInTicks = (internalTime - (timeZoneOffset * 1000) + 6000) % main.FULL_DAY_TICKS;

        // Adjust negative ticks to stay within the 24-hour range
        if (localTimeInTicks < 0) {
            localTimeInTicks += main.FULL_DAY_TICKS;
        }

        // Calculate the local hours from ticks (1,000 ticks = 1 hour)
        long localHours = (localTimeInTicks / 1000) % 24;

        // Determine if the local time shifts into a different day
        // For example, if the central time is 23:00 and time zone is -2, local time becomes 21:00 (same day)
        // But if the central time is 01:00 and time zone is +2, local time becomes 03:00 (next day)
        long centralHours = ((internalTime + 6000) / 1000) % 24;
        if (localHours < centralHours) {
            // Local time is "earlier" than central time: move to previous day
            return centralDate.minusDays(1);
        } else if (localHours > centralHours) {
            // Local time is "later" than central time: move to next day
            return centralDate.plusDays(1);
        } else {
            // No day shift, return the central date as local date
            return centralDate;
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Location location = player.getLocation();

            // Get the current central time from the main plugin
            long currentTime = plugin.calculatePlayerTime(location);  // Local time based on player's location
            long internalTime = (long) plugin.getInternalTime();      // Fetch the central internal time
            long worldDays = plugin.getWorldDays();                   // Fetch the central world days

            // Calculate central date starting from 1st January 1960
            LocalDate centralDate = LocalDate.of(1960, 1, 1).plus(worldDays, ChronoUnit.DAYS);

            // Calculate the current weekday (starting from Friday, 1st January 1960)
            String weekday = weekdays[(int) ((worldDays + 4) % 7)];  // Adjust for starting from Friday (index 4)
            String mweekday = mweekdays[(int) ((worldDays + 4) % 7)];  // Adjust for starting from Friday (index 4)

            // Format the date with ordinal suffix (st, nd, rd, th)
            String formattedDate = formatDateWithOrdinal(centralDate);

            // Calculate player's time zone offset based on their z-coordinate
            int z = location.getBlockZ();
            int timeZoneOffset = z / 8333;  // Every 1,000 blocks represents 1 hour difference

            // Adjust the time to the player's local time zone, with tick 0 as 6 AM
            long localTimeInTicks = (internalTime - (timeZoneOffset * 1000) + 6000) % main.FULL_DAY_TICKS;

            // If the time goes negative, adjust by adding 24,000 ticks to keep it positive
            if (localTimeInTicks < 0) {
                localTimeInTicks += main.FULL_DAY_TICKS;
            }

            // Calculate the local time in hours and minutes from the adjusted current time (in ticks)
            long localHours = (localTimeInTicks / 1000) % 24;  // Convert ticks to hours
            long localMinutes = (localTimeInTicks % 1000) * 60 / 1000;  // Convert remaining ticks to minutes

            // Create a LocalTime object based on the calculated hours and minutes
            LocalTime localTime = LocalTime.of((int) localHours, (int) localMinutes);
            LocalDate localDate = calculateLocalDate(centralDate, internalTime, timeZoneOffset);
            String localFormattedDate = formatDateWithOrdinal(localDate);
            String localWeekday = weekdays[localDate.getDayOfWeek().getValue() % 7];
            String localMweekday = mweekdays[localDate.getDayOfWeek().getValue() % 7];
            // Determine the time zone display (GT+ or GT-)
            String timeZoneDisplay;
            if (timeZoneOffset < 0) {
                timeZoneDisplay = "GCT+" + Math.abs(timeZoneOffset);
            } else if (timeZoneOffset > 0) {
                timeZoneDisplay = "GCT-" + timeZoneOffset;
            } else {
                timeZoneDisplay = "GCT";
            }

            // Central time as hours and minutes (tick 0 = 6 AM)
            long centralHours = ((internalTime + 6000) / 1000) % 24;
            long centralMinutes = ((internalTime + 6000) % 1000) * 60 / 1000;
            LocalTime centralTime = LocalTime.of((int) centralHours, (int) centralMinutes);

            // Send the formatted and coloured messages to the player
            player.sendMessage(ChatColor.GOLD + "========== Goplexia Centre Time ==========");
            player.sendMessage(ChatColor.GOLD + "Central date: " + ChatColor.AQUA + weekday + " the " + formattedDate);
            player.sendMessage(ChatColor.GOLD + "Central traditional date: " + ChatColor.AQUA + mweekday + " the " + formattedDate);
            player.sendMessage(ChatColor.GOLD + "Central time: " + ChatColor.AQUA + centralTime.toString());
            player.sendMessage(ChatColor.GOLD + "==========      Local Time      ==========");
            player.sendMessage(ChatColor.GOLD + "Local date: " + ChatColor.AQUA + localWeekday + " the " + localFormattedDate);
            player.sendMessage(ChatColor.GOLD + "Local traditional date: " + ChatColor.AQUA + localMweekday + " the " + localFormattedDate);
            player.sendMessage(ChatColor.GOLD + "Local time: " + ChatColor.AQUA + localTime.toString() + " (" + timeZoneDisplay + ")");

            return true;
        }
        return false;
    }

    // Helper method to format the date with ordinal suffixes (1st, 2nd, 3rd, etc.)
    private String formatDateWithOrdinal(LocalDate centralDate) {
        int day = centralDate.getDayOfMonth();  // Corrected variable name to centralDate
        String suffix = "th";
        if (day == 1 || day == 21 || day == 31) {
            suffix = "st";
        } else if (day == 2 || day == 22) {
            suffix = "nd";
        } else if (day == 3 || day == 23) {
            suffix = "rd";
        }
        String month = centralDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);  // Correct variable
        return day + suffix + " of " + month + " " + centralDate.getYear();
    }
}
