package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class MessageScheduler
{
    private static final List<MessageType> messageTypes = new ArrayList<>();
    private static boolean initialized = false;

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

    private static void sendMessageToAllPlayers(MinecraftServer server, MessageType messageType)
    {
        // Отправляем каждому игроку персонализированное сообщение на его языке
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            // Получаем язык игрока (например: "ru_ru", "en_us")
            String playerLang = player.getLanguage();
            
            // Извлекаем код языка (первые 2 символа: "ru", "en")
            String langCode = "en"; // По умолчанию английский
            if (playerLang != null && playerLang.length() >= 2)
            {
                langCode = playerLang.substring(0, 2).toLowerCase();
            }
            
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
}
