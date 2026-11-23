package com.example.examplemod;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class MessageScheduler
{
    private static final List<MessageType> messageTypes = new ArrayList<>();
    private static boolean initialized = false;
    // Кэш последнего определённого языка игроков (UUID -> код языка)
    private static final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();

    public static void initialize()
    {
        if (initialized)
        {
            return;
        }

        messageTypes.clear();
        long now = System.currentTimeMillis();

        // Инициализируем типы сообщений из конфига
        for (String typeName : Config.messageTypes)
        {
            Config.MessageConfig config = Config.messageConfigs.get(typeName);
            if (config == null)
            {
                continue;
            }
            if (config.intervalSeconds <= 0)
            {
                ExampleMod.LOGGER.warn("[Notify] Skipping message type '{}' because intervalSeconds={} (must be > 0)",
                        typeName, config.intervalSeconds);
                continue;
            }
            messageTypes.add(new MessageType(typeName, config.messagesByLang, config.clickValues,
                    config.intervalSeconds, config.clickable, config.clickType, now));
        }

        initialized = true;
    }

    public static int getActiveMessageTypeCount()
    {
        return messageTypes.size();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        if (!initialized)
        {
            initialize();
        }

        MinecraftServer server = event.getServer();
        if (server == null)
        {
            return;
        }

        long currentTime = System.currentTimeMillis();

        for (MessageType messageType : messageTypes)
        {
            if (messageType.shouldSend(currentTime))
            {
                sendMessageToAllPlayers(server, messageType);
                messageType.setLastSentTime(currentTime);
            }
        }
    }

    /**
     * Определяет актуальный язык игрока напрямую по ServerPlayer#getLanguage()
     * (O(1) чтение поля) с кэшированием последнего результата для логов и сброса.
     */
    private static String getPlayerLanguageCode(ServerPlayer player)
    {
        return updatePlayerLanguage(player);
    }

    private static String updatePlayerLanguage(ServerPlayer player)
    {
        UUID playerUUID = player.getUUID();
        String rawLang = player.getLanguage();
        String resolved = resolveSupportedLanguage(rawLang, Config.supportedLanguages);

        String previous = playerLanguages.put(playerUUID, resolved);
        if (previous == null || !previous.equals(resolved))
        {
            ExampleMod.LOGGER.debug("[Notify] Language for player {}: '{}' -> '{}' (raw: '{}')",
                    player.getName().getString(), previous, resolved, rawLang);
        }

        return resolved;
    }

    /**
     * Нормализует язык клиента и подбирает наиболее подходящий код из supportedLanguages:
     * 1) точное совпадение (например, "zh_cn"),
     * 2) базовый код до '_' или '-' (например, "ru_ru" -> "ru", "fil_ph" -> "fil"),
     * 3) "en" (если поддерживается), иначе первый язык в списке supportedLanguages.
     */
    public static String resolveSupportedLanguage(String rawLang, List<String> supportedLanguages)
    {
        List<String> normalizedSupported = new ArrayList<>();
        if (supportedLanguages != null)
        {
            for (String lang : supportedLanguages)
            {
                if (lang != null && !lang.isBlank())
                {
                    normalizedSupported.add(lang.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        String fallback = normalizedSupported.contains("en")
                ? "en"
                : (normalizedSupported.isEmpty() ? "en" : normalizedSupported.get(0));

        if (rawLang == null || rawLang.isBlank())
        {
            return fallback;
        }

        String normalized = rawLang.trim().toLowerCase(Locale.ROOT);
        if (normalizedSupported.isEmpty())
        {
            int sep = Config.findLanguageSeparatorIndex(normalized);
            return sep > 0 ? normalized.substring(0, sep) : normalized;
        }

        if (normalizedSupported.contains(normalized))
        {
            return normalized;
        }

        int sep = Config.findLanguageSeparatorIndex(normalized);
        if (sep > 0)
        {
            String baseCode = normalized.substring(0, sep);
            if (normalizedSupported.contains(baseCode))
            {
                return baseCode;
            }
        }

        return fallback;
    }

    /**
     * Публичный метод для обновления языка игрока (используется в событиях и командах).
     */
    public static void updatePlayerLanguagePublic(ServerPlayer player)
    {
        updatePlayerLanguage(player);
    }

    private static void sendMessageToAllPlayers(MinecraftServer server, MessageType messageType)
    {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty())
        {
            return;
        }

        ExampleMod.LOGGER.debug("[Notify] Sending scheduled message '{}' to {} player(s)",
                messageType.getName(), players.size());

        for (ServerPlayer player : players)
        {
            String langCode = getPlayerLanguageCode(player);
            MessageType.ResolvedMessage resolved = messageType.pickRandomMessageForLang(langCode);
            Component component = buildMessageComponent(
                    resolved.text(),
                    messageType.isClickable(),
                    messageType.getClickType(),
                    resolved.clickValue()
            );
            player.sendSystemMessage(component);
        }
    }

    static Component buildMessageComponent(String message, boolean clickable, String clickType, String clickValue)
    {
        if (!clickable || clickValue == null || clickValue.isBlank())
        {
            return Component.literal(message);
        }

        String trimmedValue = clickValue.trim();
        String normalizedType = clickType == null ? "" : clickType.trim().toUpperCase(Locale.ROOT);

        return switch (normalizedType) {
            case "URL" -> {
                if (!Config.isValidHttpUrl(trimmedValue))
                {
                    ExampleMod.LOGGER.warn("[Notify] Skipping invalid click URL '{}'", trimmedValue);
                    yield Component.literal(message);
                }
                yield Component.literal(message)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, trimmedValue))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§eClick to open: §b" + trimmedValue))));
            }
            case "COMMAND" -> {
                String command = normalizeCommandValue(trimmedValue);
                yield Component.literal(message)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§eClick to run: §b" + command))));
            }
            case "SUGGEST_COMMAND" -> {
                String command = normalizeCommandValue(trimmedValue);
                yield Component.literal(message)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§eClick to suggest: §b" + command))));
            }
            default -> {
                ExampleMod.LOGGER.warn("[Notify] Unsupported clickType '{}', sending plain message", clickType);
                yield Component.literal(message);
            }
        };
    }

    static String normalizeCommandValue(String rawCommand)
    {
        String trimmed = rawCommand.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    public static void reset()
    {
        initialized = false;
        messageTypes.clear();
    }

    /**
     * Очищает кэш языков игроков (для команды /notify resetlangs и остановки сервера).
     */
    public static void resetLanguages()
    {
        int clearedCount = playerLanguages.size();
        playerLanguages.clear();
        ExampleMod.LOGGER.info("[Notify] Cleared language cache for {} players", clearedCount);
    }

    /**
     * Обработчик события входа игрока — сохраняет начальный язык.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            updatePlayerLanguagePublic(player);
        }
    }

    /**
     * Обработчик события респавна игрока — обновляет язык.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            updatePlayerLanguagePublic(player);
        }
    }

    /**
     * Обработчик события выхода игрока — удаляет из кэша.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            playerLanguages.remove(player.getUUID());
        }
    }
}
