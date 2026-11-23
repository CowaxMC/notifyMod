package com.example.examplemod;

import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotifyModUnitTest
{
    @Test
    void testConfigLoadsDefaultExampleAndFallsBackCleanly(@TempDir Path tempDir) throws IOException
    {
        Path configPath = tempDir.resolve("nested/notify_mod-common.toml");
        Config.loadFromPath(configPath);

        assertTrue(Files.exists(configPath));
        assertEquals(List.of("Donate", "Tips", "Rules"), Config.messageTypes);
        assertEquals(List.of("ru", "en"), Config.supportedLanguages);
        assertEquals(3, Config.messageConfigs.size());
        assertNotNull(Config.messageConfigs.get("Donate"));
    }

    @Test
    void testCustomTypeDoesNotInjectDummyMissingLanguageAndSupportsCaseInsensitiveTables(@TempDir Path tempDir) throws IOException
    {
        Path configPath = tempDir.resolve("notify_mod-common.toml");
        Files.writeString(configPath, """
                messageTypes = ["Website", "Disabled", "Missing"]
                supportedLanguages = ["RU", "EN"]

                [messages]
                    [messages.Website]
                        interval = 120
                        clickable = true
                        clickType = "URL"
                        clickValues = ["https://example.org"]
                        [messages.Website.RU]
                            texts = ["Привет сайт"]

                    [messages.disabled]
                        interval = 0
                        clickable = false
                        [messages.disabled.ru]
                            texts = ["Не должно планироваться"]
                """, StandardCharsets.UTF_8);

        Config.loadFromPath(configPath);

        assertEquals(List.of("ru", "en"), Config.supportedLanguages);
        assertFalse(Config.messageConfigs.containsKey("Missing"));

        Config.MessageConfig website = Config.messageConfigs.get("Website");
        assertNotNull(website);
        assertEquals(1, website.messagesByLang.size());
        assertFalse(website.messagesByLang.containsKey("en"));
        // Фолбэк с отсутствующего en должен вернуть ru, а не заглушку
        assertEquals(List.of("Привет сайт"), website.getMessagesForLang("en_US"));

        MessageScheduler.reset();
        MessageScheduler.initialize();
        // Disabled (interval=0) должен быть отфильтрован планировщиком
        assertEquals(1, MessageScheduler.getActiveMessageTypeCount());
        MessageScheduler.reset();
    }

    @Test
    void testAtomicReloadPreservesPreviousValidConfigOnSyntaxError(@TempDir Path tempDir) throws IOException
    {
        Path configPath = tempDir.resolve("notify_mod-common.toml");
        Files.writeString(configPath, """
                messageTypes = ["Tips"]
                supportedLanguages = ["en"]

                [messages.tips]
                    interval = 60
                    clickable = false
                    [messages.tips.en]
                        texts = ["Original valid tip"]
                """, StandardCharsets.UTF_8);

        Config.loadFromPath(configPath);
        assertEquals(1, Config.messageConfigs.size());
        assertEquals(List.of("Original valid tip"), Config.messageConfigs.get("Tips").getMessagesForLang("en"));

        // Пишем битый TOML
        Files.writeString(configPath, "messageTypes = [\"broken", StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> Config.reloadFromPath(configPath));

        // Предыдущая конфигурация не потерялась
        assertEquals(1, Config.messageConfigs.size());
        assertEquals(List.of("Original valid tip"), Config.messageConfigs.get("Tips").getMessagesForLang("en"));
    }

    @Test
    void testMessageTypeTimerAndClickValueMatching()
    {
        long start = 1_000_000L;
        Map<String, List<String>> texts = new LinkedHashMap<>();
        texts.put("ru", List.of("msg0", "msg1", "msg2"));

        MessageType active = new MessageType("Donate", texts, List.of("https://a.test", "https://b.test"),
                60, true, "URL", start);

        // Сразу после создания/reload не срабатывает
        assertFalse(active.shouldSend(start));
        assertFalse(active.shouldSend(start + 59_999L));
        assertTrue(active.shouldSend(start + 60_000L));

        assertEquals("https://a.test", active.resolveClickValue(0));
        assertEquals("https://b.test", active.resolveClickValue(1));
        assertEquals("https://b.test", active.resolveClickValue(2)); // Защита от выхода за пределы

        MessageType disabled = new MessageType("Zero", texts, List.of(), 0, false, "COMMAND", 0L);
        assertFalse(disabled.shouldSend(System.currentTimeMillis()));
    }

    @Test
    void testLanguageResolutionHandlesExactSubtagsThreeLetterCodesAndCustomFallbacks()
    {
        assertEquals("ru", MessageScheduler.resolveSupportedLanguage("ru_RU", List.of("ru", "en")));
        assertEquals("en", MessageScheduler.resolveSupportedLanguage("en-GB", List.of("ru", "en")));
        assertEquals("zh_cn", MessageScheduler.resolveSupportedLanguage("zh_CN", List.of("zh_cn", "zh_tw", "en")));
        assertEquals("zh_tw", MessageScheduler.resolveSupportedLanguage("zh_TW", List.of("zh_cn", "zh_tw", "en")));
        assertEquals("tok", MessageScheduler.resolveSupportedLanguage("tok", List.of("tok", "en")));
        assertEquals("fil", MessageScheduler.resolveSupportedLanguage("fil_PH", List.of("fil", "en")));
        assertEquals("uk", MessageScheduler.resolveSupportedLanguage("fr_FR", List.of("uk", "pl")));
        assertEquals("en", MessageScheduler.resolveSupportedLanguage(null, List.of("ru", "en")));
    }

    @Test
    void testResolveClickActionNormalizesCommandAndValidatesUrls()
    {
        MessageScheduler.ClickActionSpec cmdWithoutSlash =
                MessageScheduler.resolveClickAction(true, "COMMAND", "rules");
        assertNotNull(cmdWithoutSlash);
        assertEquals(ClickEvent.Action.RUN_COMMAND, cmdWithoutSlash.action());
        assertEquals("/rules", cmdWithoutSlash.value());

        MessageScheduler.ClickActionSpec suggestCmd =
                MessageScheduler.resolveClickAction(true, "SUGGEST_COMMAND", "msg ");
        assertNotNull(suggestCmd);
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, suggestCmd.action());
        assertEquals("/msg", suggestCmd.value());

        MessageScheduler.ClickActionSpec validUrl =
                MessageScheduler.resolveClickAction(true, "URL", "https://example.com/donate");
        assertNotNull(validUrl);
        assertEquals(ClickEvent.Action.OPEN_URL, validUrl.action());
        assertEquals("https://example.com/donate", validUrl.value());

        assertNull(MessageScheduler.resolveClickAction(true, "URL", "javascript:alert(1)"));
        assertNull(MessageScheduler.resolveClickAction(true, "COMMAND", "   "));
        assertNull(MessageScheduler.resolveClickAction(true, "OPEN_FILE", "/etc/passwd"));
    }
}
