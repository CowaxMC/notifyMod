package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public class NotifyCommand
{
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("notify")
                .requires(source -> source.hasPermission(2)) // Требует OP уровень 2
                .then(Commands.literal("reload")
                    .executes(NotifyCommand::reload))
                .then(Commands.literal("resetlangs")
                    .executes(NotifyCommand::resetLanguages))
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

            source.sendSuccess(() -> Component.literal("§a[Notify] §7Конфигурация перезагружена!"), true);
            source.sendSuccess(() -> Component.literal("§7Загружено типов сообщений: §e" + MessageScheduler.getActiveMessageTypeCount()), false);

            return 1;
        }
        catch (Exception e)
        {
            ExampleMod.LOGGER.error("[Notify] Ошибка при перезагрузке конфигурации", e);
            source.sendFailure(Component.literal("§c[Notify] §7Ошибка при перезагрузке: " + e.getMessage()));
            return 0;
        }
    }

    private static int resetLanguages(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();

        try
        {
            ExampleMod.LOGGER.info("Очистка кэша языков игроков через команду /notify resetlangs");

            MessageScheduler.resetLanguages();

            MinecraftServer server = source.getServer();
            if (server != null)
            {
                for (ServerPlayer player : server.getPlayerList().getPlayers())
                {
                    MessageScheduler.updatePlayerLanguagePublic(player);
                }
            }

            if (source.getEntity() instanceof ServerPlayer)
            {
                source.sendSuccess(() -> Component.literal("§a[Notify] §7Кэш языков очищен! Языки онлайн-игроков обновлены."), true);
            }
            else
            {
                source.sendSuccess(() -> Component.literal("§a[Notify] §7Кэш языков очищен! Языки онлайн-игроков обновлены."), true);
            }

            return 1;
        }
        catch (Exception e)
        {
            ExampleMod.LOGGER.error("[Notify] Ошибка при очистке кэша языков", e);
            source.sendFailure(Component.literal("§c[Notify] §7Ошибка при очистке кэша: " + e.getMessage()));
            return 0;
        }
    }
}
