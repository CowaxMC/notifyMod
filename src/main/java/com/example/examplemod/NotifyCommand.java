package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class NotifyCommand
{
    private static ModConfig modConfig;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
        
        dispatcher.register(
            Commands.literal("notify")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                    .executes(NotifyCommand::reload))
        );
    }

    private static int reload(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        
        try
        {
            ExampleMod.LOGGER.info("Принудительная перезагрузка конфига через команду /notify reload");
            
            Config.reload();
            
            MessageScheduler.reset();
            MessageScheduler.initialize();
            
            // ИСПРАВЛЕНИЕ: Убрано "() ->" перед Component.literal
            source.sendSuccess(Component.literal("§a[Notify] §7Конфигурация перезагружена!"), true);
            source.sendSuccess(Component.literal("§7Загружено типов сообщений: §e" + Config.messageTypes.size()), false);
            
            return 1;
        }
        catch (Exception e)
        {
            source.sendFailure(Component.literal("§c[Notify] §7Ошибка при перезагрузке: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}