package com.covalscy.minimap.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Конфигурация для выбора текущей карты
 */
public class MapConfigFile {
    
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    public static final ForgeConfigSpec.IntValue CURRENT_MAP_ID;
    
    static {
        BUILDER.comment("Настройки карты для миникарты")
               .push("map");
        
        CURRENT_MAP_ID = BUILDER
            .comment(
                "ID текущей карты",
                "Доступные карты:",
                "  0 - Greenfield - основная карта",
                "  1 - Polsha - стандартная карта",
                "  2 - Large 4k - большая карта",
                "По умолчанию: 0"
            )
            .defineInRange("currentMapId", 0, 0, 999);
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
    
    /**
     * Регистрирует конфигурацию
     */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "covalscy-minimap-server.toml");
        System.out.println("[MapConfigFile] Config registered: covalscy-minimap-server.toml");
    }
    
    /**
     * Получает ID текущей карты из конфига
     */
    public static int getCurrentMapId() {
        return CURRENT_MAP_ID.get();
    }
    
    /**
     * Устанавливает ID текущей карты и сохраняет в конфиг
     */
    public static void setCurrentMapId(int mapId) {
        CURRENT_MAP_ID.set(mapId);
        SPEC.save();
        System.out.println("[MapConfigFile] Map ID saved to config: " + mapId);
    }
}
