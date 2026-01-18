package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class MessageScheduler
{
    private static final List<MessageType> messageTypes = new ArrayList<>();
    private static boolean initialized = false;
    // Хранилище языков игроков (UUID -> код языка)
    private static final Map<UUID, String> playerLanguages = new HashMap<>();
    // Время последней проверки языка для каждого игрока (UUID -> timestamp)
    private static final Map<UUID, Long> lastLanguageCheck = new HashMap<>();
    // Интервал проверки языка в миллисекундах (300 секунд)
    private static final long LANGUAGE_CHECK_INTERVAL_MS = 300_000L;

    public static void initialize()
    {
        if (initialized)
            return;

        messageTypes.clear();

        // Инициализируем типы сообщений из конфига
        for (String typeName : Config.messageTypes)
        {
            Config.MessageConfig config = Config.messageConfigs.get(typeName);
            if (config != null)
            {
                messageTypes.add(new MessageType(typeName, config.messagesByLang, config.clickValues,
                                                config.intervalSeconds, config.clickable, config.clickType));
            }
        }

        initialized = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        if (!initialized)
            initialize();

        MinecraftServer server = event.getServer();
        if (server == null)
            return;

        long currentTime = System.currentTimeMillis();

        // Проверяем каждый тип сообщения
        for (MessageType messageType : messageTypes)
        {
            if (messageType.shouldSend(currentTime))
            {
                // Отправляем сообщение всем игрокам (каждому на его языке)
                sendMessageToAllPlayers(server, messageType);
                messageType.setLastSentTime(currentTime);
            }
        }
    }

    /**
     * Получает код языка игрока, автоматически обновляя его периодически
     * Проверяет текущий язык игрока раз в LANGUAGE_CHECK_INTERVAL_MS миллисекунд
     */
    private static String getPlayerLanguageCode(ServerPlayer player)
    {
        UUID playerUUID = player.getUUID();
        long currentTime = System.currentTimeMillis();
        
        // Получаем время последней проверки языка
        Long lastCheckTime = lastLanguageCheck.get(playerUUID);
        
        // Проверяем язык только если:
        // 1. Язык еще не проверялся (lastCheckTime == null)
        // 2. Прошло достаточно времени с последней проверки (больше интервала)
        boolean shouldCheckLanguage = (lastCheckTime == null) || 
                                     (currentTime - lastCheckTime >= LANGUAGE_CHECK_INTERVAL_MS);
        
        if (shouldCheckLanguage)
        {
            // Получаем текущий язык игрока
            String currentLang = player.getLanguage();
            
            // Если язык не определен, используем кэшированный или "en"
            if (currentLang == null || currentLang.isEmpty())
            {
                String savedLangCode = playerLanguages.get(playerUUID);
                String langCode = (savedLangCode != null && !savedLangCode.isEmpty()) ? savedLangCode : "en";
                lastLanguageCheck.put(playerUUID, currentTime);
                return langCode;
            }
            
            // Извлекаем код языка из текущего языка игрока
            String normalized = currentLang.trim().toLowerCase();
            String extractedLangCode = "en";
            
            // Если есть подчеркивание или дефис, берем первую часть
            int separatorIndex = Math.max(normalized.indexOf('_'), normalized.indexOf('-'));
            if (separatorIndex > 0)
            {
                extractedLangCode = normalized.substring(0, separatorIndex);
            }
            else if (normalized.length() >= 2)
            {
                extractedLangCode = normalized.substring(0, 2);
            }
            
            // Проверяем, что язык поддерживается в конфиге
            if (!Config.supportedLanguages.contains(extractedLangCode))
            {
                extractedLangCode = "en";
            }
            
            // Получаем сохраненный язык
            String savedLangCode = playerLanguages.get(playerUUID);
            
            // Если язык изменился, обновляем кэш
            if (savedLangCode == null || !savedLangCode.equals(extractedLangCode))
            {
                playerLanguages.put(playerUUID, extractedLangCode);
                ExampleMod.LOGGER.debug("[Notify] Language auto-updated for player {}: '{}' -> '{}' (original: '{}')", 
                                       player.getName().getString(), savedLangCode, extractedLangCode, currentLang);
            }
            
            // Обновляем время последней проверки
            lastLanguageCheck.put(playerUUID, currentTime);
            
            return extractedLangCode;
        }
        else
        {
            // Используем кэшированный язык без проверки
            String savedLangCode = playerLanguages.get(playerUUID);
            return (savedLangCode != null && !savedLangCode.isEmpty()) ? savedLangCode : "en";
        }
    }
    
    /**
     * Обновляет сохраненный язык игрока и возвращает код языка
     */
    private static String updatePlayerLanguage(ServerPlayer player)
    {
        UUID playerUUID = player.getUUID();
        String playerLang = player.getLanguage();
        
        String langCode = "en"; // По умолчанию английский
        
        if (playerLang != null && !playerLang.isEmpty())
        {
            // Обработка различных форматов языковых кодов:
            // "ru_RU" -> "ru", "en_US" -> "en", "ru" -> "ru", "en" -> "en"
            String normalized = playerLang.trim().toLowerCase();
            
            // Если есть подчеркивание или дефис, берем первую часть
            int separatorIndex = Math.max(normalized.indexOf('_'), normalized.indexOf('-'));
            if (separatorIndex > 0)
            {
                langCode = normalized.substring(0, separatorIndex);
            }
            else if (normalized.length() >= 2)
            {
                // Если это короткий код (2+ символа), берем первые 2
                langCode = normalized.substring(0, 2);
            }
            
            // Проверяем, что язык поддерживается в конфиге, иначе fallback на "en"
            if (!Config.supportedLanguages.contains(langCode))
            {
                ExampleMod.LOGGER.debug("[Notify] Language '{}' (from '{}') not supported in config, using 'en' for player {}", 
                                       langCode, playerLang, player.getName().getString());
                langCode = "en";
            }
        }
        
        // Сохраняем для будущего использования
        playerLanguages.put(playerUUID, langCode);
        ExampleMod.LOGGER.debug("[Notify] Language '{}' detected for player {} (original: '{}')", 
                               langCode, player.getName().getString(), playerLang);
        
        return langCode;
    }
    
    /**
     * Публичный метод для обновления языка игрока (используется в событиях)
     */
    public static void updatePlayerLanguagePublic(ServerPlayer player)
    {
        updatePlayerLanguage(player);
    }

    private static void sendMessageToAllPlayers(MinecraftServer server, MessageType messageType)
    {
        // Логируем начало проверки языков игроков
        ExampleMod.LOGGER.info("[Notify] Проверка языков игроков для отправки сообщения типа '{}'", messageType.getName());
        
        // Отправляем каждому игроку персонализированное сообщение на его языке
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            // Получаем код языка игрока (используя сохраненное значение)
            String langCode = getPlayerLanguageCode(player);
            
            // Получаем сообщение для языка игрока
            String message = messageType.getRandomMessageForLang(langCode);
            Component component;
            
            if (messageType.isClickable())
            {
                // Получаем clickValue (общий для всех языков)
                String clickValue = messageType.getClickValueForLastMessage();
                
                if (!clickValue.isEmpty())
                {
                    String clickType = messageType.getClickType().toUpperCase();
                    
                    if (clickType.equals("URL"))
                    {
                        // Создаем кликабельное сообщение с URL
                        component = Component.literal(message)
                                .withStyle(style -> style
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                                                clickValue))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("§eClick to open: §b" + clickValue))));
                    }
                    else // COMMAND
                    {
                        // Создаем кликабельное сообщение с командой
                        component = Component.literal(message)
                                .withStyle(style -> style
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                                clickValue))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("§eClick to run: §b" + clickValue))));
                    }
                }
                else
                {
                    // Если clickValue пустой, создаем обычное сообщение
                    component = Component.literal(message);
                }
            }
            else
            {
                // Обычное сообщение
                component = Component.literal(message);
            }
            
            player.sendSystemMessage(component);
        }
    }

    public static void reset()
    {
        initialized = false;
        messageTypes.clear();
        // Не очищаем playerLanguages - языки игроков не меняются при перезагрузке конфига
        // playerLanguages.clear();
    }
    
    /**
     * Очищает кэш языков игроков (для команды /notify resetlangs)
     */
    public static void resetLanguages()
    {
        int clearedCount = playerLanguages.size();
        playerLanguages.clear();
        lastLanguageCheck.clear(); // Очищаем и кэш времени проверок
        ExampleMod.LOGGER.info("[Notify] Cleared language cache for {} players", clearedCount);
    }
    
    /**
     * Обработчик события входа игрока - сохраняем его язык
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            // Используем публичный метод для обновления языка
            updatePlayerLanguagePublic(player);
        }
    }
    
    /**
     * Обработчик события изменения языка клиента
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            // Обновляем язык после респавна (после смерти)
            updatePlayerLanguagePublic(player);
        }
    }
    
    /**
     * Обработчик события выхода игрока - удаляем из кэша
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            UUID playerUUID = player.getUUID();
            playerLanguages.remove(playerUUID);
            lastLanguageCheck.remove(playerUUID);
        }
    }
}
