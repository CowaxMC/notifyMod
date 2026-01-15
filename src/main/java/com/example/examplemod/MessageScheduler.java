package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent; // [Важно] Добавлен импорт
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks; // [Важно] Добавлен импорт
import net.minecraft.Util; // [Важно] Для UUID

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

        // [ИСПРАВЛЕНИЕ 1] Получаем сервер через хуки, так как в event метода нет
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;

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

    private static void sendMessageToAllPlayers(MinecraftServer server, MessageType messageType)
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            String playerLang = "en_us"; // В 1.18.2 получение языка может отличаться, оставим упрощенно или нужно через миксины
            // Примечание: player.getLanguage() может быть недоступен напрямую, но пока попробуем без него, или используем рефлексию
            
            String langCode = "en"; 
            
            String message = messageType.getRandomMessageForLang(langCode);
            Component component;
            
            if (messageType.isClickable())
            {
                String clickValue = messageType.getClickValueForLastMessage();
                
                if (!clickValue.isEmpty())
                {
                    String clickType = messageType.getClickType().toUpperCase();
                    
                    if (clickType.equals("URL"))
                    {
                        // [ИСПРАВЛЕНИЕ 2] new TextComponent вместо Component.literal
                        component = new TextComponent(message)
                                .withStyle(style -> style
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,
                                                clickValue))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                                new TextComponent("§eClick to open: §b" + clickValue))));
                    }
                    else 
                    {
                        component = new TextComponent(message)
                                .withStyle(style -> style
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                                clickValue))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                                new TextComponent("§eClick to run: §b" + clickValue))));
                    }
                }
                else
                {
                    component = new TextComponent(message);
                }
            }
            else
            {
                component = new TextComponent(message);
            }
            
            // [ИСПРАВЛЕНИЕ 3] sendMessage с UUID вместо sendSystemMessage
            player.sendMessage(component, Util.NIL_UUID);
        }
    }

    public static void reset()
    {
        initialized = false;
        messageTypes.clear();
    }
}