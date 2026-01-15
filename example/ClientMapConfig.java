package com.covalscy.minimap.config;

/**
 * Singleton для хранения текущей конфигурации карты на клиенте
 * Синхронизируется с сервером через MapConfigSyncPacket
 * Тайлы создаются динамически, tileSize является константой
 */
public class ClientMapConfig {
    private static ClientMapConfig INSTANCE;
    
    // Константы
    private static final int DEFAULT_TILE_SIZE = 1024; // Размер тайла (константа)
    
    private int mapId = 0;
    private boolean isConfigured = false;
    
    private ClientMapConfig() {
        // Конфигурация по умолчанию (пока не получили от сервера)
    }
    
    public static ClientMapConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClientMapConfig();
        }
        return INSTANCE;
    }
    
    /**
     * Устанавливает ID карты от сервера
     */
    public void setMapId(int mapId) {
        this.mapId = mapId;
        this.isConfigured = true;
        System.out.println("[ClientMapConfig] Map ID set to: " + mapId);
    }
    
    /**
     * Проверяет, получена ли конфигурация от сервера
     */
    public boolean isConfigured() {
        return isConfigured;
    }
    
    /**
     * Сбрасывает конфигурацию (при отключении от сервера)
     */
    public void reset() {
        this.mapId = 0;
        this.isConfigured = false;
        System.out.println("[ClientMapConfig] Config reset to default");
    }
    
    // Shortcut методы для удобства
    
    public int getTileSize() {
        return DEFAULT_TILE_SIZE; // Константа
    }
    
    public int getTilesPerSide() {
        // Тайлы создаются динамически, возвращаем 1 для совместимости
        return 1;
    }
    
    public int getStartX() {
        // Тайлы создаются динамически, стартовая позиция не нужна
        return 0;
    }
    
    public int getStartZ() {
        // Тайлы создаются динамически, стартовая позиция не нужна
        return 0;
    }
    
    public int getMapId() {
        return mapId;
    }
    
    public int getTotalMapSize() {
        // Тайлы создаются динамически, размер не ограничен
        return Integer.MAX_VALUE;
    }
    
    public int getTotalTiles() {
        // Тайлы создаются динамически, количество не ограничено
        return Integer.MAX_VALUE;
    }
}
