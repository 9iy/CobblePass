package com.cobblemon.mdks.cobblepass.listeners;

import com.cobblemon.mdks.cobblepass.CobblePass;
import com.cobblemon.mod.common.api.Priority;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.server.level.ServerPlayer;

public class HatchPokemonListener {

    public static void register() {
        Function1<Object, Unit> handler = (evt) -> {
            ServerPlayer player = tryGetPlayer(evt);
            if (player != null) {
                CobblePass.battlePass.addXP(player, CobblePass.config.getHatchXP());
            }
            return Unit.INSTANCE;
        };

        if (trySubscribe("com.cobblemon.mod.common.api.events.pokemon.PokemonEvents", "EGG_HATCHED", handler)) {
            CobblePass.LOGGER.info("[CobblePass] Subscribed to PokemonEvents.EGG_HATCHED");
            return;
        }
        if (trySubscribe("com.cobblemon.mod.common.api.events.pokemon.HatchEggEvent", "TYPE", handler)) {
            CobblePass.LOGGER.info("[CobblePass] Subscribed to HatchEggEvent.TYPE");
            return;
        }
        if (trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "HATCH_EGG_POST", handler)) {
            CobblePass.LOGGER.info("[CobblePass] Subscribed to CobblemonEvents.HATCH_EGG_POST");
            return;
        }
        CobblePass.LOGGER.warn("[CobblePass] No egg-hatch event channel found; hatch XP disabled.");
    }

    private static boolean trySubscribe(String className, String fieldName, Function1<Object, Unit> handler) {
        try {
            Class<?> clazz = Class.forName(className);
            Object channel = clazz.getField(fieldName).get(null);
            channel.getClass().getMethod("subscribe", Priority.class, Function1.class)
                    .invoke(channel, Priority.NORMAL, handler);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private static ServerPlayer tryGetPlayer(Object evt) {
        try {
            Object p = evt.getClass().getMethod("getPlayer").invoke(evt);
            return (p instanceof ServerPlayer) ? (ServerPlayer) p : null;
        } catch (Throwable ignored) { return null; }
    }
}
