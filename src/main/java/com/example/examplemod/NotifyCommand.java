package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent; // [Важно] Импорт
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class NotifyCommand
{
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
            ExampleMod.LOGGER.info("Reloading config via command...");
            
            Config.reload();
            
            MessageScheduler.reset();
            MessageScheduler.initialize();
            
            // [ИСПРАВЛЕНИЕ] Убраны лямбды () -> и заменен Component.literal на new TextComponent
            source.sendSuccess(new TextComponent("§a[Notify] §7Config reloaded!"), true);
            source.sendSuccess(new TextComponent("§7Loaded types: §e" + Config.messageTypes.size()), false);
            
            return 1;
        }
        catch (Exception e)
        {
            // [ИСПРАВЛЕНИЕ] new TextComponent
            source.sendFailure(new TextComponent("§c[Notify] §7Error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}