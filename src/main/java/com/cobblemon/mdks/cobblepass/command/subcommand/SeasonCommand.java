package com.cobblemon.mdks.cobblepass.command.subcommand;

import com.cobblemon.mdks.cobblepass.CobblePass;
import com.cobblemon.mdks.cobblepass.season.*;
import com.cobblemon.mdks.cobblepass.util.Constants;
import com.cobblemon.mdks.cobblepass.util.LangManager;
import com.cobblemon.mdks.cobblepass.util.Subcommand;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SeasonCommand extends Subcommand {

    private static final Map<UUID, CompletableFuture<?>> activeOperations = new ConcurrentHashMap<>();

    public SeasonCommand() {
        super("§9Usage:\n§3- /battlepass season <start|stop|endseason>");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("season")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("start")
                        .executes(this::startSeason))
                .then(Commands.literal("stop")
                        .executes(this::confirmStopSeason)
                        .then(Commands.literal("confirm")
                                .executes(this::stopSeason)))
                .then(Commands.literal("endseason")
                        .executes(this::showEndSeasonConfirmation))
                .build();
    }

    private int startSeason(CommandContext<CommandSourceStack> context) {
        if (CobblePass.config.isSeasonActive()) {
            context.getSource().sendFailure(LangManager.get(Constants.MSG_SEASON_ALREADY_ACTIVE, CobblePass.config.getCurrentSeason()));
            return 0;
        }

        CobblePass.config.startNewSeason();
        context.getSource().sendSuccess(() -> LangManager.get(Constants.MSG_SEASON_STARTED, CobblePass.config.getCurrentSeason()), false);

        return 1;
    }

    private int confirmStopSeason(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("§eAre you sure you want to stop the season?"), false);
        context.getSource().sendSuccess(() -> Component.literal("§7This pauses the Battle Pass but §cDOES NOT§7 reset player data."), false);
        context.getSource().sendSuccess(() -> Component.literal("§7To properly reset for a new season, use §b/bp season endseason§7."), false);
        context.getSource().sendSuccess(() -> Component.literal("§7To proceed, run §b/bp season stop confirm§7."), false);
        return 1;
    }

    private int stopSeason(CommandContext<CommandSourceStack> context) {
        if (!CobblePass.config.isSeasonActive()) {
            context.getSource().sendFailure(LangManager.get(Constants.MSG_NO_ACTIVE_SEASON));
            return 0;
        }

        CobblePass.config.stopSeason();
        context.getSource().sendSuccess(() -> Component.literal("§aSuccessfully stopped the battle pass season. Player data has not been reset."), false);

        return 1;
    }

    private int showEndSeasonConfirmation(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().isPlayer()) {
            context.getSource().sendFailure(LangManager.get("lang.command.must_be_player"));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayer();

        if (!CobblePass.config.isSeasonActive()) {
            context.getSource().sendFailure(LangManager.get("lang.season.reset.error.no_active_season"));
            return 0;
        }

        SeasonManager seasonManager = SeasonManager.getInstance();
        if (seasonManager.isTransitionInProgress()) {
            context.getSource().sendFailure(LangManager.get("lang.season.reset.error.already_in_progress"));
            return 0;
        }

        showSeasonEndConfirmationGUI(player, new SeasonResetOptions());
        return 1;
    }

    private void showSeasonEndConfirmationGUI(ServerPlayer player, SeasonResetOptions options) {
        ChestTemplate.Builder templateBuilder = ChestTemplate.builder(6)
                .set(1, 4, createInfoButton(
                        Items.BARRIER,
                        LangManager.get("lang.season.reset.confirm.title"),
                        Arrays.asList(
                                LangManager.get("lang.season.reset.confirm.message"),
                                Component.literal(""),
                                LangManager.get("lang.season.reset.confirm.warning")
                        )
                ))
                .set(2, 2, createPreservationModeButton(player, options, PremiumPreservationMode.PRESERVE_ALL))
                .set(2, 3, createPreservationModeButton(player, options, PremiumPreservationMode.SYNC_PERMISSIONS))
                .set(2, 4, createPreservationModeButton(player, options, PremiumPreservationMode.PRESERVE_AND_SYNC))
                .set(2, 6, createPreservationModeButton(player, options, PremiumPreservationMode.NONE))

                .set(3, 2, createToggleButton(player, options, "Broadcast Messages", options.isBroadcastMessages(), (opts, val) -> opts.setBroadcastMessages(val)))
                .set(3, 4, createToggleButton(player, options, "Create Backup", options.isCreateBackup(), (opts, val) -> opts.setCreateBackup(val)))
                .set(3, 6, createToggleButton(player, options, "Validate Before Reset", options.isValidateBeforeReset(), (opts, val) -> opts.setValidateBeforeReset(val)))

                .set(4, 2, createConfirmButton(player, options))
                .set(4, 6, createCancelButton(player));

        GooeyPage page = GooeyPage.builder()
                .template(templateBuilder.build())
                .title("§4Confirm Season Reset")
                .build();

        UIManager.openUIForcefully(player, page);
    }

    private Button createInfoButton(net.minecraft.world.item.Item item, Component name, List<Component> lore) {
        return GooeyButton.builder()
                .display(new ItemStack(item))
                .with(DataComponents.CUSTOM_NAME, name)
                .with(DataComponents.LORE, new ItemLore(lore))
                .with(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
                .build();
    }

    private Button createPreservationModeButton(ServerPlayer player, SeasonResetOptions options, PremiumPreservationMode mode) {
        ItemStack display;
        String name;
        List<String> lore = new ArrayList<>();

        switch (mode) {
            case PRESERVE_ALL:
                display = new ItemStack(Items.DIAMOND_BLOCK);
                name = "§aPreserve All Premium";
                lore.add("§7Keeps premium for current holders.");
                break;
            case SYNC_PERMISSIONS:
                display = new ItemStack(Items.REDSTONE_BLOCK);
                name = "§eSync from Permissions";
                lore.add("§7Grants premium based on perms only.");
                break;
            case PRESERVE_AND_SYNC:
                display = new ItemStack(Items.EMERALD_BLOCK);
                name = "§bPreserve + Sync";
                lore.add("§7Keeps current holders AND syncs perms.");
                break;
            case NONE:
                display = new ItemStack(Items.COAL_BLOCK);
                name = "§cNo Preservation";
                lore.add("§7Removes premium from all players.");
                break;
            default:
                display = new ItemStack(Items.BARRIER);
                name = "§cUnknown Mode";
                break;
        }

        if (options.getPreservationMode() == mode) {
            lore.add("");
            lore.add("§a✓ Selected");
        } else {
            lore.add("");
            lore.add("§7Click to select");
        }

        // --- FIX IS HERE ---
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Component.literal(line));
        }
        // --- END FIX ---

        return GooeyButton.builder()
                .display(display)
                .with(DataComponents.CUSTOM_NAME, Component.literal(name))
                .with(DataComponents.LORE, new ItemLore(loreComponents))
                .with(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
                .onClick(action -> {
                    options.setPreservationMode(mode);
                    showSeasonEndConfirmationGUI(player, options);
                })
                .build();
    }

    @FunctionalInterface
    private interface ToggleAction {
        void apply(SeasonResetOptions options, boolean value);
    }

    private Button createToggleButton(ServerPlayer player, SeasonResetOptions options, String optionName, boolean currentValue, ToggleAction action) {
        ItemStack display = currentValue ? new ItemStack(Items.LIME_DYE) : new ItemStack(Items.GRAY_DYE);
        String name = (currentValue ? "§a✓ " : "§c✗ ") + optionName;
        List<String> lore = Arrays.asList(
                currentValue ? "§7Currently §aenabled" : "§7Currently §cdisabled",
                "§7Click to toggle"
        );

        // --- FIX IS HERE ---
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Component.literal(line));
        }
        // --- END FIX ---

        return GooeyButton.builder()
                .display(display)
                .with(DataComponents.CUSTOM_NAME, Component.literal(name))
                .with(DataComponents.LORE, new ItemLore(loreComponents))
                .with(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
                .onClick(clickAction -> {
                    action.apply(options, !currentValue);
                    showSeasonEndConfirmationGUI(player, options);
                })
                .build();
    }

    private Button createConfirmButton(ServerPlayer player, SeasonResetOptions options) {
        return GooeyButton.builder()
                .display(new ItemStack(Items.GREEN_CONCRETE))
                .with(DataComponents.CUSTOM_NAME, Component.literal("§a§lCONFIRM RESET"))
                .with(DataComponents.LORE, new ItemLore(Arrays.asList(
                        Component.literal("§7Click to proceed with the reset."),
                        Component.literal(""),
                        Component.literal("§c§lWARNING: This cannot be undone!")
                )))
                .with(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
                .onClick(action -> {
                    player.closeContainer();
                    executeSeasonReset(player, options);
                })
                .build();
    }

    private Button createCancelButton(ServerPlayer player) {
        return GooeyButton.builder()
                .display(new ItemStack(Items.RED_CONCRETE))
                .with(DataComponents.CUSTOM_NAME, Component.literal("§c§lCANCEL"))
                .with(DataComponents.LORE, new ItemLore(List.of(Component.literal("§7Click to cancel the operation."))))
                .with(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
                .onClick(action -> {
                    player.closeContainer();
                    player.sendSystemMessage(Component.literal("§7Season reset cancelled."));
                })
                .build();
    }

    private void executeSeasonReset(ServerPlayer player, SeasonResetOptions options) {
        UUID playerId = player.getUUID();
        if (activeOperations.containsKey(playerId)) {
            player.sendSystemMessage(LangManager.get("lang.season.reset.error.already_in_progress"));
            return;
        }

        SeasonManager seasonManager = SeasonManager.getInstance();
        player.sendSystemMessage(LangManager.get("lang.season.reset.progress.starting"));

        CompletableFuture<SeasonResetResult> resetFuture = seasonManager.endSeason(options);
        activeOperations.put(playerId, resetFuture);

        resetFuture.whenComplete((result, throwable) -> {
            activeOperations.remove(playerId);
            if (throwable != null) {
                CobblePass.LOGGER.error("Season reset failed with exception", throwable);
                player.sendSystemMessage(LangManager.get("lang.season.reset.error.operation_failed",
                        Map.of("error", throwable.getMessage())));
                return;
            }

            if (result.isSuccess()) {
                sendResetCompletionSummary(player, result);
            } else {
                player.sendSystemMessage(LangManager.get("lang.season.reset.error.operation_failed",
                        Map.of("error", result.getErrorMessage())));
            }
        });
    }

    private void sendResetCompletionSummary(ServerPlayer player, SeasonResetResult result) {
        SeasonResetSummary summary = result.getSummary();

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("playerCount", String.valueOf(summary.getTotalPlayersReset()));
        placeholders.put("premiumCount", String.valueOf(summary.getPremiumPlayersPreserved()));
        player.sendSystemMessage(LangManager.get("lang.season.reset.complete.summary", placeholders));

        if (summary.getNewSeasonId() != null) {
            placeholders.clear();
            placeholders.put("newSeasonId", summary.getNewSeasonId());
            player.sendSystemMessage(LangManager.get("lang.season.reset.complete.new_season", placeholders));
        }
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        return 0;
    }
}