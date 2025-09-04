package com.cobblemon.mdks.cobblepass.listeners;

import com.cobblemon.mdks.cobblepass.CobblePass;
import com.cobblemon.mod.common.api.Priority;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.server.level.ServerPlayer;

public class EvolvePokemonListener {

    public static void register() {
        Function1<Object, Unit> handler = (evt) -> {
            ServerPlayer player = tryGetPlayer(evt);
            if (player != null) {
                CobblePass.battlePass.addXP(player, CobblePass.config.getEvolveXP());
            }
            return Unit.INSTANCE;
        };

        if (trySubscribe("com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionEvents", "COMPLETE", handler)) {
            CobblePass.LOGGER.info("[CobblePass] Subscribed to EvolutionEvents.COMPLETE");
            return;
        }
        if (trySubscribe("com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent", "TYPE", handler)) {
            CobblePass.LOGGER.info("[CobblePass] Subscribed to EvolutionCompleteEvent.TYPE");
            return;
        }
        if (trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "EVOLUTION_COMPLETE", handler)) {
            CobblePass.LOGGER.info("[CobblePass] Subscribed to CobblemonEvents.EVOLUTION_COMPLETE");
            return;
        }
        CobblePass.LOGGER.warn("[CobblePass] No evolution-complete event channel found; evolution XP disabled.");
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
