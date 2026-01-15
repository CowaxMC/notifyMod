package com.covalscy.minimap.config;

/**
 * Конфигурация одной карты
 * Содержит параметры тайловой системы для конкретной карты
 */
public class MapConfig {
    private int mapId;
    private int tileSize;
    private int tilesPerSide;
    private int startX;
    private int startZ;
    
    public MapConfig() {
    }
    
    public MapConfig(int mapId, int tileSize, int tilesPerSide, int startX, int startZ) {
        this.mapId = mapId;
        this.tileSize = tileSize;
        this.tilesPerSide = tilesPerSide;
        this.startX = startX;
        this.startZ = startZ;
    }
    
    // Конструктор для обратной совместимости (startX=0, startZ=0)
    public MapConfig(int mapId, int tileSize, int tilesPerSide) {
        this(mapId, tileSize, tilesPerSide, 0, 0);
    }
    
    // Getters
    public int getMapId() {
        return mapId;
    }
    
    public int getTileSize() {
        return tileSize;
    }
    
    public int getTilesPerSide() {
        return tilesPerSide;
    }
    
    public int getStartX() {
        return startX;
    }
    
    public int getStartZ() {
        return startZ;
    }
    
    // Setters
    public void setMapId(int mapId) {
        this.mapId = mapId;
    }
    
    public void setTileSize(int tileSize) {
        this.tileSize = tileSize;
    }
    
    public void setTilesPerSide(int tilesPerSide) {
        this.tilesPerSide = tilesPerSide;
    }
    
    public void setStartX(int startX) {
        this.startX = startX;
    }
    
    public void setStartZ(int startZ) {
        this.startZ = startZ;
    }
    
    // Вычисляемые свойства
    
    /**
     * Возвращает общий размер карты в блоках
     */
    public int getTotalMapSize() {
        return tileSize * tilesPerSide;
    }
    
    /**
     * Возвращает общее количество тайлов
     */
    public int getTotalTiles() {
        return tilesPerSide * tilesPerSide;
    }
    
    /**
     * Валидация конфигурации
     */
    public boolean isValid() {
        if (tileSize <= 0) return false;
        if (tilesPerSide <= 0) return false;
        
        // Проверяем, что tileSize - степень двойки (для оптимизации GPU)
        if (!isPowerOfTwo(tileSize)) return false;
        
        // Ограничение на максимальное количество тайлов (8×8 = 64)
        if (tilesPerSide > 8) return false;
        
        return true;
    }
    
    /**
     * Проверяет, является ли число степенью двойки
     */
    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    @Override
    public String toString() {
        return "MapConfig{" +
                "mapId=" + mapId +
                ", tileSize=" + tileSize +
                ", tilesPerSide=" + tilesPerSide +
                ", startX=" + startX +
                ", startZ=" + startZ +
                ", totalSize=" + getTotalMapSize() +
                ", totalTiles=" + getTotalTiles() +
                '}';
    }
}
