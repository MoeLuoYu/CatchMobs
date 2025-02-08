package xyz.moeluoyu.catchmobs;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.moeluoyu.catchmobs.Listeners.EntityDeathListener;
import xyz.moeluoyu.catchmobs.Tasks.CatchCommandExecutor;
import xyz.moeluoyu.catchmobs.Listeners.PlayerInteractListener;

import java.util.Objects;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        // 获取根日志记录器
        Logger rootLogger = Logger.getLogger("");
        // 为根日志记录器添加自定义过滤器
        rootLogger.setFilter(new CustomLogFilter());

        // 保存默认配置文件
        saveDefaultConfig();
        getConfig().addDefault("debug", false);
        getConfig().options().copyDefaults(true);
        saveConfig();

        // 注册命令执行器
        Objects.requireNonNull(getCommand("catchmobs")).setExecutor(new CatchCommandExecutor(this));

        // 注册监听器
        getServer().getPluginManager().registerEvents(new EntityDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(this), this);

        getLogger().info("CatchMobs 插件已启用！");
        getLogger().info("定制插件找落雨，买插件上速德优，速德优（北京）网络科技有限公司出品，落雨QQ：1498640871");
    }

    @Override
    public void onDisable() {
        getLogger().info("CatchMobs 插件已禁用！");
    }

    // 过滤一些不重要也不影响正常运行的警告日志
    private static class CustomLogFilter implements Filter {
        // 要过滤的日志信息的正则表达式
        @SuppressWarnings({"RegExpRedundantEscape", "RegExpDuplicateCharacterInClass"})
        private static final String FILTER_REGEX = "^\\[ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup\\] Failed to remove entity.*";

        @Override
        public boolean isLoggable(LogRecord record) {
            // 只处理警告级别及以上的日志
            if (record.getLevel().intValue() < Level.WARNING.intValue()) {
                return true;
            }
            String message = record.getMessage();
            // 如果日志信息匹配正则表达式，则过滤掉该日志
            return message == null || !message.matches(FILTER_REGEX);
        }
    }
}