package com.covalscy.minimap.config;

/**
 * Утилитный класс для работы с картами
 * Централизованное хранение информации о картах
 */
public class MapRegistry {
    
    /**
     * Получает название карты по её ID
     */
    public static String getMapName(int mapId) {
        switch (mapId) {
            case 0: return "Greenfield";
            case 1: return "Polsha";
            case 2: return "Large 4k";
            default: return "Unknown Map (ID " + mapId + ")";
        }
    }
    
    /**
     * Проверяет, существует ли карта с данным ID
     */
    public static boolean isValidMapId(int mapId) {
        return mapId >= 0 && mapId <= 2;
    }
    
    /**
     * Получает описание карты по её ID
     */
    public static String getMapDescription(int mapId) {
        switch (mapId) {
            case 0: return "Greenfield - основная карта";
            case 1: return "Polsha - стандартная карта";
            case 2: return "Large 4k - большая карта";
            default: return "Unknown map";
        }
    }
    
    /**
     * Возвращает массив всех доступных ID карт
     */
    public static int[] getAllMapIds() {
        return new int[] { 0, 1, 2 };
    }
    
    /**
     * Возвращает количество доступных карт
     */
    public static int getMapCount() {
        return 3;
    }
}
