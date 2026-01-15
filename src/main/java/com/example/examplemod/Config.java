package com.example.examplemod;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Конфигурация мода - использует прямое чтение TOML без ForgeConfigSpec
 * чтобы избежать автоматической корректировки и удаления пользовательских значений
 */
public class Config
{
    private static final String CONFIG_FILE_NAME = "notify_mod-common.toml";
    private static CommentedFileConfig config;

    // Публичные статические поля для доступа к конфигурации
    public static List<String> messageTypes = new ArrayList<>();
    public static List<String> supportedLanguages = new ArrayList<>();
    public static Map<String, MessageConfig> messageConfigs = new HashMap<>();

    // Класс для хранения конфигурации одного типа сообщения
    public static class MessageConfig
    {
        public Map<String, List<String>> messagesByLang;  // Сообщения по языкам (ru, en, и т.д.)
        public List<String> clickValues; // Команды/ссылки (общие для всех языков)
        public int intervalSeconds;
        public boolean clickable;
        public String clickType;  // "COMMAND" или "URL"

        public MessageConfig(Map<String, List<String>> messagesByLang, List<String> clickValues, 
                           int intervalSeconds, boolean clickable, String clickType)
        {
            this.messagesByLang = messagesByLang;
            this.clickValues = clickValues;
            this.intervalSeconds = intervalSeconds;
            this.clickable = clickable;
            this.clickType = clickType;
        }
        
        // Получить сообщения для языка (с fallback на английский)
        public List<String> getMessagesForLang(String lang)
        {
            List<String> msgs = messagesByLang.get(lang);
            if (msgs != null && !msgs.isEmpty())
                return msgs;
            // Fallback на английский
            msgs = messagesByLang.get("en");
            if (msgs != null && !msgs.isEmpty())
                return msgs;
            // Fallback на первый доступный язык
            return messagesByLang.values().stream().findFirst().orElse(List.of("No messages"));
        }
    }

    /**
     * Загружает конфигурацию из файла
     */
    public static void load()
    {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
        
        // Если конфиг не существует - копируем из config-example.toml
        if (!java.nio.file.Files.exists(configPath))
        {
            try
            {
                // Пытаемся скопировать config-example.toml из jar
                java.io.InputStream exampleStream = Config.class.getResourceAsStream("/config-example.toml");
                if (exampleStream != null)
                {
                    java.nio.file.Files.copy(exampleStream, configPath);
                    ExampleMod.LOGGER.info("Created config from config-example.toml");
                }
                else
                {
                    // Если не нашли в jar, создаем программно
                    ExampleMod.LOGGER.warn("config-example.toml not found in jar, creating default config");
                }
            }
            catch (Exception e)
            {
                ExampleMod.LOGGER.error("Failed to copy config-example.toml: " + e.getMessage());
            }
        }
        
        // Создаем конфиг
        config = CommentedFileConfig.builder(configPath)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();
        
        config.load();
        
        // Если конфиг все еще пустой - создаем дефолтный программно
        if (!config.contains("messageTypes"))
        {
            createDefaultConfig();
            config.save();
        }
        
        // Загружаем значения
        loadConfig();
        
        ExampleMod.LOGGER.info("Config loaded from: " + configPath);
        ExampleMod.LOGGER.info("Message types: " + messageTypes.size());
    }

    /**
     * Перезагружает конфигурацию из файла
     */
    public static void reload()
    {
        if (config != null)
        {
            config.load();
            loadConfig();
            ExampleMod.LOGGER.info("Config reloaded! Message types: " + messageTypes.size());
        }
    }

    /**
     * Создает дефолтный конфиг
     */
    private static void createDefaultConfig()
    {
        config.setComment("messageTypes", "List of message types (e.g., Donate, Tips, Rules, etc.)");
        config.set("messageTypes", List.of("Donate", "Tips", "Rules"));
        
        config.setComment("supportedLanguages", "List of supported language codes (e.g., ru, en, de, fr, etc.)");
        config.set("supportedLanguages", List.of("ru", "en"));
        
        // Создаем дефолтные сообщения для каждого типа
        for (String type : List.of("Donate", "Tips", "Rules"))
        {
            String typeLower = type.toLowerCase();
            
            config.set("messages." + typeLower + ".interval", getDefaultIntervalForType(typeLower));
            config.set("messages." + typeLower + ".clickable", getDefaultClickableForType(typeLower));
            config.set("messages." + typeLower + ".clickType", getDefaultClickTypeForType(typeLower));
            
            if (getDefaultClickableForType(typeLower))
            {
                config.set("messages." + typeLower + ".clickValues", getDefaultClickValuesForType(typeLower));
            }
            
            for (String lang : List.of("ru", "en"))
            {
                config.set("messages." + typeLower + "." + lang + ".texts", getDefaultMessagesForType(typeLower, lang));
            }
        }
    }

    /**
     * Загружает конфиг из CommentedFileConfig
     */
    private static void loadConfig()
    {
        messageTypes = new ArrayList<>(config.getOrElse("messageTypes", List.of("Donate", "Tips", "Rules")));
        supportedLanguages = new ArrayList<>(config.getOrElse("supportedLanguages", List.of("ru", "en")));
        messageConfigs.clear();

        for (String type : messageTypes)
        {
            String typeLower = type.toLowerCase();
            
            Map<String, List<String>> messagesByLang = new HashMap<>();
            
            // Загружаем сообщения для каждого языка
            for (String lang : supportedLanguages)
            {
                String textsPath = "messages." + typeLower + "." + lang + ".texts";
                List<String> messages = config.getOrElse(textsPath, getDefaultMessagesForType(typeLower, lang));
                messagesByLang.put(lang, messages);
            }
            
            // Загружаем общие параметры
            int interval = config.getIntOrElse("messages." + typeLower + ".interval", getDefaultIntervalForType(typeLower));
            boolean clickable = config.getOrElse("messages." + typeLower + ".clickable", getDefaultClickableForType(typeLower));
            String clickType = config.getOrElse("messages." + typeLower + ".clickType", getDefaultClickTypeForType(typeLower));
            
            // Загружаем clickValues
            List<String> clickValues;
            if (clickable)
            {
                clickValues = config.getOrElse("messages." + typeLower + ".clickValues", getDefaultClickValuesForType(typeLower));
            }
            else
            {
                clickValues = List.of();
            }
            
            messageConfigs.put(type, new MessageConfig(messagesByLang, clickValues, interval, clickable, clickType));
        }
    }

    // Дефолтные значения

    private static List<String> getDefaultMessagesForType(String type, String lang)
    {
        if (lang.equals("ru"))
        {
            return switch (type) {
                case "donate" -> List.of(
                    "§6[Донат] §eПоддержи сервер! §b§nНажми сюда!",
                    "§6[Донат] §eПомоги нам расти! §b§nЗадонать!",
                    "§6[Донат] §eТвоя поддержка важна! §b§nКликни для доната!"
                );
                case "tips" -> List.of(
                    "§a[Совет] §7Используй §e/help §7для списка команд!",
                    "§a[Совет] §7Нажми §eF3 + H §7чтобы видеть ID предметов!",
                    "§a[Совет] §7Используй §e/spawn §7чтобы вернуться на спавн!"
                );
                case "rules" -> List.of(
                    "§c[Правила] §7Не забывай следовать правилам! §e/rules",
                    "§c[Правила] §7Будь вежлив с другими игроками!",
                    "§c[Правила] §7Гриферство запрещено! §e/rules"
                );
                default -> List.of("§7[" + type + "] §fСообщение по умолчанию");
            };
        }
        else // English and other languages
        {
            return switch (type) {
                case "donate" -> List.of(
                    "§6[Donate] §eSupport our server! §b§nClick here!",
                    "§6[Donate] §eHelp us grow! §b§nDonate now!",
                    "§6[Donate] §eYour support matters! §b§nClick to donate!"
                );
                case "tips" -> List.of(
                    "§a[Tip] §7Use §e/help §7to see all available commands!",
                    "§a[Tip] §7Press §eF3 + H §7to see item IDs!",
                    "§a[Tip] §7Use §e/spawn §7to return to spawn!"
                );
                case "rules" -> List.of(
                    "§c[Rules] §7Remember to follow server rules! Type §e/rules",
                    "§c[Rules] §7Be respectful to other players!",
                    "§c[Rules] §7No griefing allowed! Check §e/rules"
                );
                default -> List.of("§7[" + type + "] §fDefault message for " + type);
            };
        }
    }

    private static int getDefaultIntervalForType(String type)
    {
        return switch (type) {
            case "donate" -> 300;  // 5 минут
            case "tips" -> 180;    // 3 минуты
            case "rules" -> 600;   // 10 минут
            default -> 300;
        };
    }

    private static boolean getDefaultClickableForType(String type)
    {
        return switch (type) {
            case "donate" -> true;
            case "tips" -> false;
            case "rules" -> true;
            default -> false;
        };
    }

    private static String getDefaultClickTypeForType(String type)
    {
        return switch (type) {
            case "donate" -> "URL";
            case "tips" -> "COMMAND";
            case "rules" -> "COMMAND";
            default -> "COMMAND";
        };
    }

    private static List<String> getDefaultClickValuesForType(String type)
    {
        return switch (type) {
            case "donate" -> List.of(
                "https://example.com/donate",
                "https://example.com/donate",
                "https://example.com/donate"
            );
            case "tips" -> List.of("", "", "");
            case "rules" -> List.of("/rules", "/rules", "/rules");
            default -> List.of("");
        };
    }
}
