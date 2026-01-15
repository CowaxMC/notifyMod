package com.covalscy.minimap.config;

/**
 * Реестр конфигураций карт на сервере
 * Управляет текущей активной картой
 * Тайлы создаются динамически по требованию
 */
public class MapConfigRegistry {
    private static MapConfigRegistry INSTANCE;
    
    private int currentMapId = 0;
    
    private MapConfigRegistry() {
        // Загружаем ID карты из конфига
        loadMapId();
    }
    
    public static MapConfigRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MapConfigRegistry();
        }
        return INSTANCE;
    }
    
    /**
     * Получает ID текущей карты
     */
    public int getCurrentMapId() {
        // System.out.println("[MapConfigRegistry] getCurrentMapId() called, returning: " + currentMapId);
        // Thread.dumpStack(); // Показываем откуда вызывается
        return currentMapId;
    }
    
    /**
     * Устанавливает текущую карту
     */
    public void setCurrentMapId(int mapId) {
        this.currentMapId = mapId;
        System.out.println("[MapConfigRegistry] Current map ID set to: " + mapId);
    }
    
    /**
     * Загружает ID карты из конфига
     */
    private void loadMapId() {
        // Читаем ID текущей карты из конфига (если есть)
        int configMapId = MapConfigFile.getCurrentMapId();
        
        // Устанавливаем текущий ID карты
        this.currentMapId = configMapId;
        System.out.println("[MapConfigRegistry] Loaded map ID from config: " + configMapId);
    }
}
