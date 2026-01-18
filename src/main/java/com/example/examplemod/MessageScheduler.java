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
     * Получает код языка игрока, используя сохраненное значение
     * Оптимизировано: не вызывает player.getLanguage() если язык уже сохранен
     */
    private static String getPlayerLanguageCode(ServerPlayer player)
    {
        UUID playerUUID = player.getUUID();
        
        // Сначала проверяем сохраненное значение (быстрая операция)
        String savedLangCode = playerLanguages.get(playerUUID);
        if (savedLangCode != null && !savedLangCode.isEmpty())
        {
            // Используем сохраненное значение без вызова player.getLanguage()
            // Это значительно снижает нагрузку, так как getLanguage() может быть тяжелой операцией
            return savedLangCode;
        }
        
        // Если сохраненного значения нет, получаем текущий язык (только один раз)
        String playerLang = player.getLanguage();
        String currentLangCode = "en"; // По умолчанию английский
        
        if (playerLang != null && playerLang.length() >= 2)
        {
            currentLangCode = playerLang.substring(0, 2).toLowerCase();
        }
        
        // Сохраняем для будущего использования
        playerLanguages.put(playerUUID, currentLangCode);
        return currentLangCode;
    }
    
    /**
     * Обновляет сохраненный язык игрока
     */
    public static void updatePlayerLanguage(ServerPlayer player)
    {
        UUID playerUUID = player.getUUID();
        String playerLang = player.getLanguage();
        
        String langCode = "en"; // По умолчанию английский
        if (playerLang != null && playerLang.length() >= 2)
        {
            langCode = playerLang.substring(0, 2).toLowerCase();
        }
        
        playerLanguages.put(playerUUID, langCode);
    }

    private static void sendMessageToAllPlayers(MinecraftServer server, MessageType messageType)
    {
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
    }
    
    /**
     * Обработчик события входа игрока - сохраняем его язык
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            updatePlayerLanguage(player);
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
            updatePlayerLanguage(player);
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
            playerLanguages.remove(player.getUUID());
        }
    }
}
