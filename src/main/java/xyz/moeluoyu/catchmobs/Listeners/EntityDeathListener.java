package xyz.moeluoyu.catchmobs.Listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class EntityDeathListener implements Listener {

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final File dataFile;
    private FileConfiguration data;
    private String version;
    private Class<?> nbtCompoundClass;
    private Method saveMethod;
    private Method getMaxHealthMethod;
    private Method removeTagMethod;
    private final NamespacedKey usedKey;
    private final NamespacedKey capturedUuidKey;

    public EntityDeathListener(JavaPlugin plugin) {
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
            Method loadMethod = nmsEntityClass.getMethod("load", nbtCompoundClass);
            Method parseTagMethod = Class.forName(nmsPackage + ".TagParser").getMethod("parseTag", String.class);
            getMaxHealthMethod = livingEntityClass.getMethod("getMaxHealth");
            Method setHealthMethod = livingEntityClass.getMethod("setHealth", float.class);
            removeTagMethod = nbtCompoundClass.getMethod("remove", String.class);
            Method teleportMethod = nmsEntityClass.getMethod("teleportTo", double.class, double.class, double.class);

        } catch (Exception e) {
            if (config.getBoolean("debug", false)) {
                plugin.getLogger().severe("在初始化时发生异常: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        ItemStack killerItem = event.getEntity().getKiller() != null ? event.getEntity().getKiller().getInventory().getItemInMainHand() : null;

        // 检查杀手物品是否符合配置
        if (killerItem != null && killerItem.getType() == Material.valueOf(config.getString("catch-item"))) {
            ItemMeta meta = killerItem.getItemMeta();
            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(usedKey, PersistentDataType.BYTE)) {
                    Player killer = event.getEntity().getKiller();
                    if (killer != null) {
                        killer.sendMessage(ChatColor.RED + "物品已使用过，请先释放生物后再次尝试！");
                    }
                    return; // 物品已使用过，不能再次捕捉
                }
            }
            String mode = config.getString("mode", "black");
            String entityType = entity.getType().name();
            boolean canCatch = false;

            if ("black".equals(mode)) {
                List<String> blacklist = config.getStringList("blacklist");
                canCatch = !blacklist.contains(entityType);
            } else if ("white".equals(mode)) {
                List<String> whitelist = config.getStringList("whitelist");
                canCatch = whitelist.contains(entityType);
            }
            // 检查生物是否可以捕捉
            if (canCatch) {
                // 阻止生物掉落战利品和装备
                event.getDrops().clear();
                event.setDroppedExp(0);
                Objects.requireNonNull(event.getEntity().getEquipment()).clear();

                // 保存生物的 NBT 数据
                String uuid = UUID.randomUUID().toString();
                try {
                    Class<?> craftEntityClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftEntity");
                    Object craftEntity = craftEntityClass.cast(entity);
                    Method getHandleMethod = craftEntityClass.getMethod("getHandle");
                    Object nmsEntity = getHandleMethod.invoke(craftEntity);

                    Object nbt = nbtCompoundClass.getDeclaredConstructor().newInstance();
                    saveMethod.invoke(nmsEntity, nbt);

                    // 获取最大血量
                    float maxHealth = (float) getMaxHealthMethod.invoke(nmsEntity);
                    // 设置血量为满血
                    Method setTagMethod = nbtCompoundClass.getMethod("putFloat", String.class, float.class);
                    setTagMethod.invoke(nbt, "Health", maxHealth);

                    // 移除位置相关的 NBT 标签
                    removeTagMethod.invoke(nbt, "Pos");
                    removeTagMethod.invoke(nbt, "Rotation");

                    String nbtDataStr = nbt.toString();

                    Map<String, Object> entityData = new HashMap<>();
                    entityData.put("entity_type", entityType);
                    entityData.put("nbt_data", nbtDataStr);

                    // 重新加载 data.yml 文件，确保数据是最新的
                    data = YamlConfiguration.loadConfiguration(dataFile);

                    // 检查是否重复添加
                    if (!data.contains("captured." + uuid)) {
                        data.set("captured." + uuid, entityData);
                        saveData();
                    }

                    // 标记物品已使用
                    if (meta != null) {
                        PersistentDataContainer container = meta.getPersistentDataContainer();
                        container.set(usedKey, PersistentDataType.BYTE, (byte) 1);
                        container.set(capturedUuidKey, PersistentDataType.STRING, uuid);

                        // 添加 lore 显示已捕捉的生物和 UUID
                        List<String> lore = meta.getLore();
                        if (lore == null) {
                            lore = new ArrayList<>();
                        }
                        lore.add("§a已捕捉生物: §f" + entityType);
                        lore.add("§a捕捉的 UUID: §f" + uuid);
                        meta.setLore(lore);

                        killerItem.setItemMeta(meta);
                    }
                    if (config.getBoolean("debug", false)) {
                        plugin.getLogger().info("成功捕捉生物 " + entityType + "，UUID: " + uuid);
                    }
                } catch (Exception e) {
                    if (event.getEntity().getKiller() != null) {
                        event.getEntity().getKiller().sendMessage("§7[§4!§7] §c生物捕捉失败。");
                    }
                    if (config.getBoolean("debug", false)) {
                        plugin.getLogger().severe("在捕捉生物时发生异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                if (config.getBoolean("debug", false)) {
                    plugin.getLogger().info(ChatColor.RED + "生物 " + entityType + " 无法捕捉。");
                }
            }
        }
    }

    public void saveData() {
        try {
            data.save(dataFile);
            if (config.getBoolean("debug", false)) {
                plugin.getLogger().info("数据文件已保存。");
            }
        } catch (IOException e) {
            if (config.getBoolean("debug", false)) {
                plugin.getLogger().severe("在保存数据文件时发生异常: " + e.getMessage());
            }
        }
    }
}