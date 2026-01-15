package com.example.examplemod;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class MessageType
{
    private final String name;
    private final Map<String, List<String>> messagesByLang;
    private final List<String> clickValues; // Общие для всех языков
    private final int intervalSeconds;
    private final boolean clickable;
    private final String clickType;
    private long lastSentTime;
    private final Random random;
    private int lastMessageIndex; // Индекс последнего выбранного сообщения

    public MessageType(String name, Map<String, List<String>> messagesByLang, List<String> clickValues,
                      int intervalSeconds, boolean clickable, String clickType)
    {
        this.name = name;
        this.messagesByLang = messagesByLang;
        this.clickValues = clickValues;
        this.intervalSeconds = intervalSeconds;
        this.clickable = clickable;
        this.clickType = clickType;
        this.lastSentTime = 0;
        this.random = new Random();
        this.lastMessageIndex = 0;
    }

    public String getName()
    {
        return name;
    }

    // Получить случайное сообщение для указанного языка
    public String getRandomMessageForLang(String lang)
    {
        List<String> messages = messagesByLang.get(lang);
        
        // Fallback на английский если язык не найден
        if (messages == null || messages.isEmpty())
        {
            messages = messagesByLang.get("en");
        }
        
        // Fallback на первый доступный язык
        if (messages == null || messages.isEmpty())
        {
            messages = messagesByLang.values().stream().findFirst().orElse(null);
        }
        
        if (messages == null || messages.isEmpty())
        {
            lastMessageIndex = 0;
            return "§7[" + name + "] §fNo messages configured";
        }
        
        lastMessageIndex = random.nextInt(messages.size());
        return messages.get(lastMessageIndex);
    }

    // Получить clickValue для последнего сообщения
    public String getClickValueForLastMessage()
    {
        if (clickValues == null || clickValues.isEmpty())
        {
            return "";
        }
        
        // Если индекс выходит за пределы списка clickValues, используем последний элемент
        int index = Math.min(lastMessageIndex, clickValues.size() - 1);
        return clickValues.get(index);
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
        return (currentTime - lastSentTime) >= (intervalSeconds * 1000L);
    }
}
