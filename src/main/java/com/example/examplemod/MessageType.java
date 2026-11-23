package com.example.examplemod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Описывает один запланированный тип уведомлений и состояние его таймера.
 */
public class MessageType
{
    public record ResolvedMessage(String text, String clickValue) {}

    private final String name;
    private final Map<String, List<String>> messagesByLang;
    private final List<String> clickValues; // Общие для всех языков
    private final int intervalSeconds;
    private final boolean clickable;
    private final String clickType;
    private long lastSentTime;
    private final Random random;

    public MessageType(String name, Map<String, List<String>> messagesByLang, List<String> clickValues,
                       int intervalSeconds, boolean clickable, String clickType)
    {
        this(name, messagesByLang, clickValues, intervalSeconds, clickable, clickType, System.currentTimeMillis());
    }

    MessageType(String name, Map<String, List<String>> messagesByLang, List<String> clickValues,
                int intervalSeconds, boolean clickable, String clickType, long initialLastSentTime)
    {
        this.name = name;
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
        this.lastSentTime = initialLastSentTime;
        this.random = new Random();
    }

    public String getName()
    {
        return name;
    }

    /**
     * Выбирает случайное сообщение для указанного языка и соответствующий ему clickValue
     * без мутабельного промежуточного состояния.
     */
    public ResolvedMessage pickRandomMessageForLang(String lang)
    {
        List<String> messages = Config.resolveMessagesForLang(messagesByLang, lang);
        if (messages.isEmpty())
        {
            return new ResolvedMessage("§7[" + name + "] §fNo messages configured", "");
        }

        int index = random.nextInt(messages.size());
        String text = messages.get(index);
        String clickValue = resolveClickValue(index);
        return new ResolvedMessage(text, clickValue);
    }

    String resolveClickValue(int messageIndex)
    {
        if (!clickable || clickValues.isEmpty())
        {
            return "";
        }
        int clampedIndex = Math.min(Math.max(0, messageIndex), clickValues.size() - 1);
        String value = clickValues.get(clampedIndex);
        return value == null ? "" : value.trim();
    }

    public int getIntervalSeconds()
    {
        return intervalSeconds;
    }

    public boolean isClickable()
    {
        return clickable;
    }

    public String getClickType()
    {
        return clickType;
    }

    public long getLastSentTime()
    {
        return lastSentTime;
    }

    public void setLastSentTime(long time)
    {
        this.lastSentTime = time;
    }

    public boolean shouldSend(long currentTime)
    {
        if (intervalSeconds <= 0)
        {
            return false;
        }
        return (currentTime - lastSentTime) >= (intervalSeconds * 1000L);
    }
}
