package com.cobblemon.mdks.cobblepass.listeners;

import com.cobblemon.mdks.cobblepass.CobblePass;
import com.cobblemon.mod.common.api.Priority;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class EvolvePokemonListener {
    private static boolean REGISTERED = false;

    // small reflection cache to keep CPU low
    private static final Map<String, java.lang.reflect.Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static Object callOptCached(Object obj, String method) {
        if (obj == null) return null;
        try {
            String key = obj.getClass().getName() + "#" + method;
            java.lang.reflect.Method m = METHOD_CACHE.computeIfAbsent(key, k -> {
                try {
                    var mm = obj.getClass().getMethod(method);
                    mm.setAccessible(true);
                    return mm;
                } catch (Exception e) { return null; }
            });
            return (m == null) ? null : m.invoke(obj);
        } catch (Throwable ignored) { return null; }
    }

    public static void register() {
        if (REGISTERED) return;
        REGISTERED = true;

        // One handler covers multiple possible event types/fields
        Function1<Object, Unit> handler = (evt) -> {
            try {
                // Try to get the evolved Pokémon object from various event APIs
                Object mon = firstNonNull(
                        callOptCached(evt, "getPokemon"),
                        callOptCached(evt, "getEvolvedPokemon"),
                        callOptCached(evt, "getRecipient"),
                        callOptCached(evt, "getTarget")
                );

                // Resolve the ServerPlayer who owns that Pokémon
                ServerPlayer owner = coalescePlayer(
                        tryChainToPlayer(evt, "getPlayer"),
                        tryChainToPlayer(evt, "getTrainer", "getPlayer"),
                        tryChainToPlayer(mon, "getOwner", "getPlayer"),
                        tryChainToPlayer(mon, "getPlayer")
                );
                if (owner == null) {
                    UUID ownerId = tryGetUUID(mon, "getOwnerUUID", "getOwnerUuid", "getUuidOwner", "ownerUuid", "ownerUUID");
                    owner = playerByUuid(ownerId);
                }
                if (owner == null) return Unit.INSTANCE;

                // Award evolve XP once per event
                CobblePass.battlePass.addXP(owner, CobblePass.config.getEvolveXP());
            } catch (Throwable ignored) {}
            return Unit.INSTANCE;
        };

        // Bind to several likely channels across versions
        trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "EVOLUTION_COMPLETE", handler);
        trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "EVOLUTION_COMPLETED", handler);
        trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "EVOLUTION_FINISHED", handler);
        trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "POKEMON_EVOLVED", handler);
        trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "EVOLVE_EVENT_POST", handler);

        // Also try the typed event constant (some builds expose TYPE on the class)
        trySubscribeTypeField("com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompletedEvent", "TYPE", handler);
        trySubscribeTypeField("com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionFinishedEvent", "TYPE", handler);
        trySubscribeTypeField("com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionEvent$Completed", "TYPE", handler);
    }

    // ---- subscribe helpers (quiet) ----
    private static boolean trySubscribe(String className, String fieldName, Function1<Object, Unit> handler) {
        try {
            Class<?> clazz = Class.forName(className);
            Object channel = clazz.getField(fieldName).get(null);
            channel.getClass().getMethod("subscribe", Priority.class, Function1.class)
                    .invoke(channel, Priority.NORMAL, handler);
            return true;
        } catch (Throwable ignored) { return false; }
    }
    private static boolean trySubscribeTypeField(String eventClass, String fieldName, Function1<Object, Unit> handler) {
        try {
            Class<?> clazz = Class.forName(eventClass);
            Object channel = clazz.getField(fieldName).get(null);
            channel.getClass().getMethod("subscribe", Priority.class, Function1.class)
                    .invoke(channel, Priority.NORMAL, handler);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    // ---- generic utilities (quiet) ----
    private static Object firstNonNull(Object... objs) {
        for (Object o : objs) if (o != null) return o;
        return null;
    }
    private static ServerPlayer playerByUuid(UUID u) {
        if (u == null || CobblePass.server == null) return null;
        try { return CobblePass.server.getPlayerList().getPlayer(u); } catch (Throwable ignored) { return null; }
    }
    private static UUID tryGetUUID(Object obj, String... methods) {
        for (String m : methods) {
            try {
                Object v = callOptCached(obj, m);
                if (v instanceof UUID u) return u;
                if (v instanceof String s) { try { return UUID.fromString(s); } catch (Throwable ignored) {} }
            } catch (Throwable ignored) {}
        }
        return null;
    }
    private static ServerPlayer coalescePlayer(ServerPlayer... players) {
        for (ServerPlayer p : players) if (p != null) return p;
        return null;
    }
    private static ServerPlayer tryChainToPlayer(Object root, String... chain) {
        if (root == null) return null;
        try {
            Object cur = root;
            for (String m : chain) {
                if (cur == null) return null;
                cur = callOptCached(cur, m);
            }
            return (cur instanceof ServerPlayer sp) ? sp : null;
        } catch (Throwable ignored) { return null; }
    }
}
