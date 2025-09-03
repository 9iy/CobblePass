package com.cobblemon.mdks.cobblepass.premium;

import java.util.Arrays;
import java.util.List;

import com.cobblemon.mdks.cobblepass.CobblePass;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Premium provider implementation that handles premium access through permission nodes.
 * Players with the configured permission node automatically have premium access.
 * This provider uses the Fabric Permissions API to integrate with permission mods like LuckPerms.
 */
public class PermissionPremiumProvider implements PremiumProvider {
    
    private boolean permissionSystemAvailable;
    
    @Override
    public void initialize() {
        String permissionNode = getPermissionNode();
        try {
            // Test if Fabric Permissions API is available by checking for its main class.
            this.permissionSystemAvailable = testPermissionSystem();
            
            if (permissionSystemAvailable) {
                CobblePass.LOGGER.info("Permission Premium Provider initialized - Using Fabric Permissions API. Permission node: " + permissionNode);
            } else {
                CobblePass.LOGGER.error("Permission Premium Provider FAILED to initialize. Fabric Permissions API not found. This mode will not function correctly.");
            }
        } catch (Exception e) {
            CobblePass.LOGGER.error("Failed to initialize Permission Premium Provider", e);
            this.permissionSystemAvailable = false;
        }
    }

    @Override
    public boolean hasPremium(ServerPlayer player) {
        if (!CobblePass.config.isSeasonActive() || !this.permissionSystemAvailable) {
            if (!this.permissionSystemAvailable) {
                CobblePass.LOGGER.warn("Fabric Permissions API is unavailable. Cannot check premium status for " + player.getName().getString() + ".");
            }
            return false;
        }

        try {
            // The permission system is the single source of truth. Use Fabric Permissions API.
            return Permissions.check(player, getPermissionNode(), false);
        } catch (Exception e) {
            CobblePass.LOGGER.error("An error occurred while checking permissions for " + player.getName().getString() + ". Defaulting to no premium.", e);
            return false;
        }
    }
    
    @Override
    public boolean grantPremium(ServerPlayer player) {
        // Granting premium via this command is not supported in permission mode.
        // Admins should use their permissions plugin.
        CobblePass.LOGGER.warn("Attempted to grant premium via command in PERMISSION mode for player " + player.getName().getString() + ". This should be done via a permissions plugin.");
        player.sendSystemMessage(Component.literal("§cPremium is managed by permissions. Use your permissions plugin to grant '" + getPermissionNode() + "'."));
        return false;
    }
    
    @Override
    public boolean revokePremium(ServerPlayer player) {
        // Revoking premium via this command is not supported in permission mode.
        // Admins should use their permissions plugin.
        CobblePass.LOGGER.warn("Attempted to revoke premium via command in PERMISSION mode for player " + player.getName().getString() + ". This should be done via a permissions plugin.");
        player.sendSystemMessage(Component.literal("§cPremium is managed by permissions. Use your permissions plugin to revoke '" + getPermissionNode() + "'."));
        return false;
    }
    
    @Override
    public String getStatusMessage(ServerPlayer player) {
        if (!CobblePass.config.isSeasonActive()) {
            return "§cNo active season";
        }

        if (!this.permissionSystemAvailable) {
            return "§cPermission System Unavailable §8- §7Contact administrator";
        }

        if (hasPremium(player)) {
            return "§aPremium Active §7(Permission: " + getPermissionNode() + ")";
        } else {
            return "§7Premium Unavailable §8- §7Requires permission: §e" + getPermissionNode();
        }
    }
    
    @Override
    public List<String> getBulkOperationCommands() {
        return Arrays.asList(
            "§6Bulk Permission Operations:",
            "§7Premium is managed via a permissions plugin (e.g., LuckPerms).",
            "§ePermission Node: §f" + getPermissionNode(),
            "§7Use your permission plugin's commands to grant/revoke this node in bulk.",
            "§7Current permission system: §e" + (permissionSystemAvailable ? "Available" : "Unavailable")
        );
    }
    
    @Override
    public PremiumMode getMode() {
        return PremiumMode.PERMISSION;
    }

    @Override
    public void shutdown() {
        CobblePass.LOGGER.info("Permission Premium Provider shutdown");
        this.permissionSystemAvailable = false;
    }
    
    /**
     * Tests if the Fabric Permissions API is available.
     * @return true if the API is available, false otherwise
     */
    private boolean testPermissionSystem() {
        try {
            Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
        return true;
     } catch (ClassNotFoundException e) {
        return false;
       }
    }

    
    private String getPermissionNode() {
        return CobblePass.config.getPremiumConfig().getPermissionNode();
    }
}