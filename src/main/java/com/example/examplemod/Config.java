package com.example.examplemod;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Конфигурация мода — использует прямое чтение TOML без ForgeConfigSpec,
 * чтобы избежать автоматической корректировки и удаления пользовательских значений.
 */
public class Config
{
    private static final String CONFIG_FILE_NAME = "notify_mod-common.toml";
    private static final List<String> DEFAULT_MESSAGE_TYPES = List.of("Donate", "Tips", "Rules");
    private static final List<String> DEFAULT_SUPPORTED_LANGUAGES = List.of("ru", "en");
    private static final Set<String> VALID_CLICK_TYPES = Set.of("URL", "COMMAND", "SUGGEST_COMMAND");

    private static Path activeConfigPath;

    // Публичные статические поля для доступа к конфигурации
    public static List<String> messageTypes = new ArrayList<>();
    public static List<String> supportedLanguages = new ArrayList<>();
    public static Map<String, MessageConfig> messageConfigs = new LinkedHashMap<>();

    // Класс для хранения конфигурации одного типа сообщения
    public static class MessageConfig
    {
        public final Map<String, List<String>> messagesByLang;  // Сообщения по языкам (ru, en и т.д.)
        public final List<String> clickValues; // Команды/ссылки (общие для всех языков)
        public final int intervalSeconds;
        public final boolean clickable;
        public final String clickType;  // "COMMAND", "SUGGEST_COMMAND" или "URL"

        public MessageConfig(Map<String, List<String>> messagesByLang, List<String> clickValues,
                             int intervalSeconds, boolean clickable, String clickType)
        {
            Map<String, List<String>> copiedByLang = new LinkedHashMap<>();
            if (messagesByLang != null)
            {
                for (Map.Entry<String, List<String>> entry : messagesByLang.entrySet())
                {
                    if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty())
                    {
                        copiedByLang.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(entry.getValue()));
                    }
                }
            }
            this.messagesByLang = Collections.unmodifiableMap(copiedByLang);
            this.clickValues = clickValues == null ? List.of() : List.copyOf(clickValues);
            this.intervalSeconds = intervalSeconds;
            this.clickable = clickable;
            this.clickType = clickType == null ? "COMMAND" : clickType.trim().toUpperCase(Locale.ROOT);
        }

        /**
         * Возвращает сообщения для языка с фолбэком:
         * точный код -> базовый код (до '_' или '-') -> "en" -> первый доступный язык.
         */
        public List<String> getMessagesForLang(String lang)
        {
            return resolveMessagesForLang(this.messagesByLang, lang);
        }
    }

    private record LoadedConfig(
            List<String> messageTypes,
            List<String> supportedLanguages,
            Map<String, MessageConfig> messageConfigs
    ) {}

    /**
     * Загружает конфигурацию из стандартного файла в папке config.
     */
    public static void load()
    {
        loadFromPath(getDefaultConfigPath());
    }

    /**
     * Перезагружает конфигурацию из файла атомарно.
     * При ошибке чтения или синтаксиса прежняя рабочая конфигурация сохраняется.
     */
    public static void reload()
    {
        Path targetPath = activeConfigPath != null ? activeConfigPath : getDefaultConfigPath();
        reloadFromPath(targetPath);
    }

    static void loadFromPath(Path configPath)
    {
        activeConfigPath = configPath;
        ensureConfigFileExists(configPath);
        LoadedConfig loaded = readConfig(configPath, true);
        applyLoadedConfig(loaded);

        ExampleMod.LOGGER.info("Config loaded from: {}", configPath);
        ExampleMod.LOGGER.info("Message types loaded: {}", messageConfigs.size());
    }

    static void reloadFromPath(Path configPath)
    {
        activeConfigPath = configPath;
        ensureConfigFileExists(configPath);
        LoadedConfig loaded = readConfig(configPath, false);
        applyLoadedConfig(loaded);

        ExampleMod.LOGGER.info("Config reloaded! Message types loaded: {}", messageConfigs.size());
    }

    private static Path getDefaultConfigPath()
    {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
    }

    private static void ensureConfigFileExists(Path configPath)
    {
        try
        {
            Path parent = configPath.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
        }
        catch (IOException e)
        {
            ExampleMod.LOGGER.error("Failed to create config directory for {}: {}", configPath, e.getMessage());
            return;
        }

        if (Files.exists(configPath))
        {
            return;
        }

        try (InputStream exampleStream = Config.class.getResourceAsStream("/config-example.toml"))
        {
            if (exampleStream != null)
            {
                Files.copy(exampleStream, configPath);
                ExampleMod.LOGGER.info("Created config from config-example.toml");
            }
            else
            {
                ExampleMod.LOGGER.warn("config-example.toml not found in jar, default config will be generated");
            }
        }
        catch (IOException e)
        {
            ExampleMod.LOGGER.error("Failed to copy config-example.toml: {}", e.getMessage());
        }
    }

    private static LoadedConfig readConfig(Path configPath, boolean createDefaultsIfMissing)
    {
        try (CommentedFileConfig fileConfig = CommentedFileConfig.builder(configPath)
                .charset(StandardCharsets.UTF_8)
                .sync()
                .writingMode(WritingMode.REPLACE)
                .build())
        {
            fileConfig.load();

            if (createDefaultsIfMissing && !fileConfig.contains("messageTypes"))
            {
                createDefaultConfig(fileConfig);
                fileConfig.save();
            }

            return parseConfig(fileConfig);
        }
    }

    private static void applyLoadedConfig(LoadedConfig loaded)
    {
        messageTypes = new ArrayList<>(loaded.messageTypes());
        supportedLanguages = new ArrayList<>(loaded.supportedLanguages());
        messageConfigs = new LinkedHashMap<>(loaded.messageConfigs());
    }

    /**
     * Разбирает содержимое открытого TOML-конфига в неизменяемый снимок.
     */
    private static LoadedConfig parseConfig(CommentedFileConfig fileConfig)
    {
        List<String> parsedTypes = fileConfig.contains("messageTypes")
                ? readStringList(fileConfig, List.of("messageTypes"), false)
                : new ArrayList<>(DEFAULT_MESSAGE_TYPES);

        List<String> rawLanguages = fileConfig.contains("supportedLanguages")
                ? readStringList(fileConfig, List.of("supportedLanguages"), false)
                : new ArrayList<>(DEFAULT_SUPPORTED_LANGUAGES);

        Set<String> normalizedLangs = new LinkedHashSet<>();
        for (String lang : rawLanguages)
        {
            String normalized = lang.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty())
            {
                normalizedLangs.add(normalized);
            }
        }
        if (normalizedLangs.isEmpty())
        {
            ExampleMod.LOGGER.warn("[Notify] supportedLanguages is empty in config; falling back to {}", DEFAULT_SUPPORTED_LANGUAGES);
            normalizedLangs.addAll(DEFAULT_SUPPORTED_LANGUAGES);
        }
        List<String> parsedLanguages = new ArrayList<>(normalizedLangs);

        Map<String, MessageConfig> parsedConfigs = new LinkedHashMap<>();
        UnmodifiableConfig messagesRoot = getSubConfig(fileConfig, "messages");

        for (String type : parsedTypes)
        {
            String trimmedType = type.trim();
            if (trimmedType.isEmpty())
            {
                continue;
            }

            String typeLower = trimmedType.toLowerCase(Locale.ROOT);
            UnmodifiableConfig typeSection = findChildConfig(messagesRoot, typeLower, trimmedType);
            if (typeSection == null)
            {
                ExampleMod.LOGGER.warn("[Notify] Config section [messages.{}] not found for message type '{}', skipping",
                        typeLower, trimmedType);
                continue;
            }

            int interval = readInt(typeSection, "interval", getDefaultIntervalForType(typeLower), trimmedType);
            if (interval <= 0)
            {
                ExampleMod.LOGGER.warn("[Notify] Message type '{}' has non-positive interval ({}s); it will be disabled",
                        trimmedType, interval);
            }

            boolean clickable = readBoolean(typeSection, "clickable", getDefaultClickableForType(typeLower), trimmedType);
            String rawClickType = readString(typeSection, "clickType", getDefaultClickTypeForType(typeLower));
            String normalizedClickType = rawClickType.trim().toUpperCase(Locale.ROOT);

            if (clickable && !VALID_CLICK_TYPES.contains(normalizedClickType))
            {
                ExampleMod.LOGGER.warn("[Notify] Unknown clickType '{}' for message type '{}'; click action disabled",
                        rawClickType, trimmedType);
                clickable = false;
            }

            List<String> clickValues = List.of();
            if (clickable)
            {
                if (typeSection.contains("clickValues"))
                {
                    clickValues = readStringList(typeSection, List.of("clickValues"), true);
                }
                else if (typeSection.contains("clickValue"))
                {
                    // Совместимость со старым полем clickValue из документации
                    String singleValue = readString(typeSection, "clickValue", "");
                    if (!singleValue.isBlank())
                    {
                        clickValues = List.of(singleValue.trim());
                    }
                }
                else
                {
                    clickValues = List.of();
                }

                if ("URL".equals(normalizedClickType))
                {
                    for (String val : clickValues)
                    {
                        if (!val.isBlank() && !isValidHttpUrl(val))
                        {
                            ExampleMod.LOGGER.warn("[Notify] Invalid URL '{}' in [messages.{}].clickValues (expected http:// or https://)",
                                    val, typeLower);
                        }
                    }
                }
            }

            Map<String, List<String>> messagesByLang = new LinkedHashMap<>();
            for (String lang : parsedLanguages)
            {
                UnmodifiableConfig langSection = findChildConfig(typeSection, lang, lang);
                if (langSection != null && langSection.contains("texts"))
                {
                    List<String> texts = readStringList(langSection, List.of("texts"), false);
                    if (!texts.isEmpty())
                    {
                        messagesByLang.put(lang, texts);
                    }
                }
            }

            if (messagesByLang.isEmpty())
            {
                ExampleMod.LOGGER.warn("[Notify] No message texts configured for type '{}' under supportedLanguages {}, skipping",
                        trimmedType, parsedLanguages);
                continue;
            }

            parsedConfigs.put(trimmedType, new MessageConfig(messagesByLang, clickValues, interval, clickable, normalizedClickType));
        }

        return new LoadedConfig(parsedTypes, parsedLanguages, parsedConfigs);
    }

    /**
     * Общий резолвер списка сообщений по языку для MessageConfig и MessageType.
     */
    public static List<String> resolveMessagesForLang(Map<String, List<String>> messagesByLang, String lang)
    {
        if (messagesByLang == null || messagesByLang.isEmpty())
        {
            return List.of();
        }

        if (lang != null && !lang.isBlank())
        {
            String normalized = lang.trim().toLowerCase(Locale.ROOT);
            List<String> exact = messagesByLang.get(normalized);
            if (exact != null && !exact.isEmpty())
            {
                return exact;
            }

            int sepIndex = findLanguageSeparatorIndex(normalized);
            if (sepIndex > 0)
            {
                String base = normalized.substring(0, sepIndex);
                List<String> baseMsgs = messagesByLang.get(base);
                if (baseMsgs != null && !baseMsgs.isEmpty())
                {
                    return baseMsgs;
                }
            }
        }

        List<String> english = messagesByLang.get("en");
        if (english != null && !english.isEmpty())
        {
            return english;
        }

        for (List<String> candidate : messagesByLang.values())
        {
            if (candidate != null && !candidate.isEmpty())
            {
                return candidate;
            }
        }

        return List.of();
    }

    static int findLanguageSeparatorIndex(String normalizedLang)
    {
        int underscore = normalizedLang.indexOf('_');
        int hyphen = normalizedLang.indexOf('-');
        if (underscore < 0)
        {
            return hyphen;
        }
        if (hyphen < 0)
        {
            return underscore;
        }
        return Math.min(underscore, hyphen);
    }

    static boolean isValidHttpUrl(String value)
    {
        if (value == null)
        {
            return false;
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://"))
                && !trimmed.chars().anyMatch(Character::isWhitespace);
    }

    private static UnmodifiableConfig getSubConfig(UnmodifiableConfig parent, String key)
    {
        if (parent == null || !parent.contains(key))
        {
            return null;
        }
        Object raw = parent.getRaw(key);
        return raw instanceof UnmodifiableConfig sub ? sub : null;
    }

    private static UnmodifiableConfig findChildConfig(UnmodifiableConfig parent, String preferredLowerKey, String originalKey)
    {
        if (parent == null)
        {
            return null;
        }
        UnmodifiableConfig direct = getSubConfig(parent, preferredLowerKey);
        if (direct != null)
        {
            return direct;
        }
        if (!preferredLowerKey.equals(originalKey))
        {
            UnmodifiableConfig exact = getSubConfig(parent, originalKey);
            if (exact != null)
            {
                return exact;
            }
        }
        for (UnmodifiableConfig.Entry entry : parent.entrySet())
        {
            if (entry.getKey().equalsIgnoreCase(originalKey) && entry.getValue() instanceof UnmodifiableConfig sub)
            {
                return sub;
            }
        }
        return null;
    }

    private static List<String> readStringList(UnmodifiableConfig config, List<String> path, boolean keepEmptyStrings)
    {
        Object raw = config.getRaw(path);
        if (!(raw instanceof List<?> list))
        {
            if (raw != null)
            {
                ExampleMod.LOGGER.warn("[Notify] Expected list at config path '{}', got {}",
                        String.join(".", path), raw.getClass().getSimpleName());
            }
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list)
        {
            if (item == null)
            {
                continue;
            }
            String str = String.valueOf(item);
            if (keepEmptyStrings || !str.isBlank())
            {
                result.add(keepEmptyStrings ? str.trim() : str);
            }
        }
        return result;
    }

    private static int readInt(UnmodifiableConfig config, String key, int defaultValue, String typeName)
    {
        if (!config.contains(key))
        {
            return defaultValue;
        }
        Object raw = config.getRaw(key);
        if (raw instanceof Number num)
        {
            return num.intValue();
        }
        ExampleMod.LOGGER.warn("[Notify] Invalid integer value for 'messages.{}.{}' ({}); using default {}",
                typeName.toLowerCase(Locale.ROOT), key, raw, defaultValue);
        return defaultValue;
    }

    private static boolean readBoolean(UnmodifiableConfig config, String key, boolean defaultValue, String typeName)
    {
        if (!config.contains(key))
        {
            return defaultValue;
        }
        Object raw = config.getRaw(key);
        if (raw instanceof Boolean bool)
        {
            return bool;
        }
        ExampleMod.LOGGER.warn("[Notify] Invalid boolean value for 'messages.{}.{}' ({}); using default {}",
                typeName.toLowerCase(Locale.ROOT), key, raw, defaultValue);
        return defaultValue;
    }

    private static String readString(UnmodifiableConfig config, String key, String defaultValue)
    {
        if (!config.contains(key))
        {
            return defaultValue;
        }
        Object raw = config.getRaw(key);
        return raw == null ? defaultValue : String.valueOf(raw);
    }

    /**
     * Создаёт дефолтный конфиг, если файл отсутствовал или не содержал messageTypes.
     */
    private static void createDefaultConfig(CommentedFileConfig fileConfig)
    {
        fileConfig.setComment("messageTypes", "List of message types (e.g., Donate, Tips, Rules, etc.)");
        fileConfig.set("messageTypes", new ArrayList<>(DEFAULT_MESSAGE_TYPES));

        fileConfig.setComment("supportedLanguages", "List of supported language codes (e.g., ru, en, de, fr, etc.)");
        fileConfig.set("supportedLanguages", new ArrayList<>(DEFAULT_SUPPORTED_LANGUAGES));

        for (String type : DEFAULT_MESSAGE_TYPES)
        {
            String typeLower = type.toLowerCase(Locale.ROOT);

            fileConfig.set(List.of("messages", typeLower, "interval"), getDefaultIntervalForType(typeLower));
            fileConfig.set(List.of("messages", typeLower, "clickable"), getDefaultClickableForType(typeLower));
            fileConfig.set(List.of("messages", typeLower, "clickType"), getDefaultClickTypeForType(typeLower));

            if (getDefaultClickableForType(typeLower))
            {
                fileConfig.set(List.of("messages", typeLower, "clickValues"), getDefaultClickValuesForType(typeLower));
            }

            for (String lang : DEFAULT_SUPPORTED_LANGUAGES)
            {
                fileConfig.set(List.of("messages", typeLower, lang, "texts"), getDefaultMessagesForType(typeLower, lang));
            }
        }
    }

    // Дефолтные значения для первоначальной генерации конфига

    private static List<String> getDefaultMessagesForType(String type, String lang)
    {
        if ("ru".equals(lang))
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
        else
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
            case "donate", "rules" -> true;
            default -> false;
        };
    }

    private static String getDefaultClickTypeForType(String type)
    {
        return switch (type) {
            case "donate" -> "URL";
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
