package xyz.moeluoyu.catchmobs.Listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xyz.moeluoyu.catchmobs.Tasks.NMSVersionConverter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class PlayerInteractListener implements Listener {

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final File dataFile;
    private FileConfiguration data;
    private String version;
    private Class<?> nbtCompoundClass;
    private Method saveMethod;
    private Method loadMethod;
    private Method parseTagMethod;
    private Method getMaxHealthMethod;
    private Method setHealthMethod;
    private Method removeTagMethod;
    private Method teleportMethod;
    private final NamespacedKey usedKey;
    private final NamespacedKey capturedUuidKey;

    public PlayerInteractListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        usedKey = new NamespacedKey(plugin, "used_for_catching");
        capturedUuidKey = new NamespacedKey(plugin, "captured_uuid");
        try {
            String serverVersion = plugin.getServer().getVersion();
            String mcVersion = NMSVersionConverter.extractMinecraftVersion(serverVersion);
            if (mcVersion == null) {
                throw new IllegalArgumentException("Could not determine server version from: " + serverVersion);
            }
            this.version = NMSVersionConverter.convertToNMSVersion(mcVersion);
            if (this.version == null) {
                throw new IllegalArgumentException("Unsupported Minecraft version: " + mcVersion);
            }

            // 动态加载 NBT 相关类和方法
            String nmsPackage = "net.minecraft.nbt";
            nbtCompoundClass = Class.forName(nmsPackage + ".CompoundTag");
            Class<?> entityClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftEntity");
            Class<?> nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");

            saveMethod = nmsEntityClass.getMethod("save", nbtCompoundClass);
            loadMethod = nmsEntityClass.getMethod("load", nbtCompoundClass);
            parseTagMethod = Class.forName(nmsPackage + ".TagParser").getMethod("parseTag", String.class);
            getMaxHealthMethod = livingEntityClass.getMethod("getMaxHealth");
            setHealthMethod = livingEntityClass.getMethod("setHealth", float.class);
            removeTagMethod = nbtCompoundClass.getMethod("remove", String.class);
            teleportMethod = nmsEntityClass.getMethod("teleportTo", double.class, double.class, double.class);

        } catch (Exception e) {
            if (config.getBoolean("debug")) {
                plugin.getLogger().severe("在初始化时发生异常: " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction().toString().contains("RIGHT_CLICK")) {
            ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
            if (item.getType() == Material.valueOf(config.getString("catch-item"))) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    PersistentDataContainer container = meta.getPersistentDataContainer();
                    if (container.has(usedKey, PersistentDataType.BYTE)) {
                        String uuid = container.get(capturedUuidKey, PersistentDataType.STRING);
                        if (uuid != null) {
                            // 重新加载 data.yml 文件
                            data = YamlConfiguration.loadConfiguration(dataFile);
                            if (config.getBoolean("debug")) {
                                plugin.getLogger().info("尝试释放生物，UUID: " + uuid);
                            }
                            if (data.contains("captured." + uuid)) {
                                org.bukkit.configuration.ConfigurationSection section = data.getConfigurationSection("captured." + uuid);
                                if (section != null) {
                                    Map<String, Object> entityData = section.getValues(false);
                                    String entityTypeName = (String) entityData.get("entity_type");
                                    String nbtDataStr = (String) entityData.get("nbt_data");
                                    EntityType entityType = EntityType.valueOf(entityTypeName);

                                    org.bukkit.block.Block clickedBlock = event.getClickedBlock();
                                    if (clickedBlock == null) {
                                        event.getPlayer().sendMessage("§7[§4!§7] §6请点击方块来释放生物。");
                                        return;
                                    }
                                    Location spawnLocation = clickedBlock.getLocation().add(0, 1, 0);
                                    Entity spawnedEntity = event.getPlayer().getWorld().spawnEntity(spawnLocation, entityType);
                                    try {
                                        Class<?> craftEntityClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftEntity");
                                        Object craftSpawnedEntity = craftEntityClass.cast(spawnedEntity);
                                        Method getHandleMethod = craftEntityClass.getMethod("getHandle");
                                        Object nmsSpawnedEntity = getHandleMethod.invoke(craftSpawnedEntity);

                                        Object nbt = parseTagMethod.invoke(null, nbtDataStr);
                                        loadMethod.invoke(nmsSpawnedEntity, nbt);

                                        // 再次确认血量为满血
                                        float maxHealth = (float) getMaxHealthMethod.invoke(nmsSpawnedEntity);
                                        setHealthMethod.invoke(nmsSpawnedEntity, maxHealth);

                                        // 将生物传送到右击点的位置
                                        teleportMethod.invoke(nmsSpawnedEntity, spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ());

                                        if (config.getBoolean("debug")) {
                                            plugin.getLogger().info("成功释放生物 " + entityType + "，UUID: " + uuid);
                                        }
                                    } catch (Exception e) {
                                        event.getPlayer().sendMessage("§7[§4!§7] §c生物释放失败。");
                                        if (config.getBoolean("debug")) {
                                            plugin.getLogger().severe("在释放生物时发生异常: " + e.getMessage());
                                        }
                                    }

                                    // 移除记录
                                    data.set("captured." + uuid, null);
                                    saveData();

                                    // 取消物品标记，使其可再次使用
                                    container.remove(usedKey);
                                    container.remove(capturedUuidKey);

                                    // 移除 lore
                                    List<String> lore = meta.getLore();
                                    if (lore != null) {
                                        lore.removeIf(line -> line.startsWith("§a已捕捉生物: §f") || line.startsWith("§a捕捉的 UUID: §f"));
                                        meta.setLore(lore);
                                    }


                                    item.setItemMeta(meta);
                                } else {
                                    event.getPlayer().sendMessage("§7[§4!§7] §c未找到生物记录。");
                                    if (config.getBoolean("debug")) {
                                        plugin.getLogger().warning("未找到 UUID 为 " + uuid + " 的生物记录。");
                                    }
                                }
                            } else {
                                event.getPlayer().sendMessage("§7[§4!§7] §c未找到生物记录。");
                                if (config.getBoolean("debug")) {
                                    plugin.getLogger().warning("数据文件中不包含 UUID 为 " + uuid + " 的生物记录。");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void saveData() {
        try {
            data.save(dataFile);
            if (config.getBoolean("debug")) {
                plugin.getLogger().info("数据文件已保存。");
            }
        } catch (IOException e) {
            if (config.getBoolean("debug")) {
                plugin.getLogger().severe("在保存数据文件时发生异常: " + e.getMessage());
            }
        }
    }
}