package com.cobblemon.mdks.cobblepass.listeners;

import com.cobblemon.mdks.cobblepass.CobblePass;
import com.cobblemon.mod.common.api.Priority;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.UUID;

public class DefeatPokemonListener {
    private static boolean REGISTERED = false;

    private static final Map<String, java.lang.reflect.Method> METHOD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static Object callOptCached(Object obj, String method) {
        if (obj == null) return null;
        try {
            String key = obj.getClass().getName() + "#" + method;
            java.lang.reflect.Method m = METHOD_CACHE.computeIfAbsent(key, k -> {
                try { var mm = obj.getClass().getMethod(method); mm.setAccessible(true); return mm; }
                catch (Exception e) { return null; }
            });
            return (m == null) ? null : m.invoke(obj);
        } catch (Throwable ignored) { return null; }
    }

    private static final Map<UUID, Set<UUID>> credited = new HashMap<>();

    public static void register() {
        if (REGISTERED) return;
        REGISTERED = true;

        Function1<Object, Unit> victoryHandler = (evt) -> {
            try {
                UUID battleId = getBattleId(evt);
                List<Object> winners = getActors(evt, "getWinners", "getWinnersList", "getWinner");
                if (winners.isEmpty()) {
                    Object battle = callOpt(evt, "getBattle");
                    winners = getActors(battle, "getWinners", "getWinnersList", "getWinner");
                }
                if (winners.isEmpty()) return Unit.INSTANCE;

                Set<UUID> seen = (battleId != null)
                        ? credited.computeIfAbsent(battleId, k -> new HashSet<>())
                        : null;

                for (Object actor : winners) {
                    ServerPlayer sp = winnerToPlayer(actor);
                    if (sp == null) continue;

                    if (seen == null || seen.add(sp.getUUID())) {
                        CobblePass.battlePass.addXP(sp, CobblePass.config.getDefeatXP());
                    }
                }

                if (battleId != null && credited.size() > 512) {
                    Iterator<UUID> it = credited.keySet().iterator();
                    for (int i = 0; i < 64 && it.hasNext(); i++) it.remove();
                }
            } catch (Throwable ignored) {}
            return Unit.INSTANCE;
        };

        trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "BATTLE_VICTORY", victoryHandler);
        trySubscribe("com.cobblemon.mod.common.api.events.CobblemonEvents", "BATTLE_WON", victoryHandler);
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

    private static UUID getBattleId(Object evt) {
        Object battle = callOpt(evt, "getBattle");
        if (battle == null) return null;
        try {
            Object v = battle.getClass().getMethod("getBattleId").invoke(battle);
            if (v instanceof UUID u) return u;
            if (v instanceof String s) return UUID.fromString(s);
        } catch (Throwable ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getActors(Object obj, String... getters) {
        List<Object> out = new ArrayList<>();
        if (obj == null) return out;
        for (String g : getters) {
            try {
                Object v = obj.getClass().getMethod(g).invoke(obj);
                if (v == null) continue;
                if (v instanceof Collection<?> c) {
                    for (Object e : c) if (e != null) out.add(e);
                    if (!out.isEmpty()) return out;
                } else {
                    out.add(v);
                    return out;
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static ServerPlayer winnerToPlayer(Object actor) {
        if (actor == null) return null;

        ServerPlayer direct = tryChainToPlayer(actor, "getPlayer");
        if (direct != null) return direct;

        UUID u = tryGetUUID(actor, "getUuid", "getUUID", "uuid");
        ServerPlayer viaUuid = playerByUuid(u);
        if (viaUuid != null) return viaUuid;

        return tryChainToPlayer(actor, "getTrainer", "getPlayer");
    }

    private static Object callOpt(Object obj, String method) {
        if (obj == null) return null;
        try { return obj.getClass().getMethod(method).invoke(obj); } catch (Throwable ignored) { return null; }
    }

    private static UUID tryGetUUID(Object obj, String... methods) {
        for (String m : methods) {
            try {
                Object v = obj.getClass().getMethod(m).invoke(obj);
                if (v instanceof UUID u) return u;
                if (v instanceof String s) { try { return UUID.fromString(s); } catch (Throwable ignored) {} }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static ServerPlayer playerByUuid(UUID u) {
        if (u == null || CobblePass.server == null) return null;
        try { return CobblePass.server.getPlayerList().getPlayer(u); } catch (Throwable ignored) { return null; }
    }

    private static ServerPlayer tryChainToPlayer(Object root, String... chain) {
        if (root == null) return null;
        try {
            Object cur = root;
            for (String m : chain) {
                if (cur == null) return null;
                cur = cur.getClass().getMethod(m).invoke(cur);
            }
            return (cur instanceof ServerPlayer sp) ? sp : null;
        } catch (Throwable ignored) { return null; }
    }
}
