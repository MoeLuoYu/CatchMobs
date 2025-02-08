package xyz.moeluoyu.catchmobs.Tasks;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import xyz.moeluoyu.catchmobs.Main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CatchCommandExecutor implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final FileConfiguration config;

    public CatchCommandExecutor(Main plugin) {
        this.plugin = plugin;
        File dataFile = new File(plugin.getDataFolder(), "data.yml");
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        this.config = plugin.getConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("catchmobs")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "请输入 /catchmobs help 获取帮助");
                return true;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "item":
                    if (args.length == 2) {
                        try {
                            Material material = Material.valueOf(args[1].toUpperCase());
                            config.set("catch-item", material.name());
                            plugin.saveConfig();
                            sender.sendMessage(ChatColor.GREEN + "捕捉物品已设置为 " + material.name());
                        } catch (IllegalArgumentException e) {
                            sender.sendMessage(ChatColor.RED + "无效的物品材料名: " + args[1]);
                        }
                    } else {
                        String currentItem = config.getString("catch-item");
                        if (currentItem != null) {
                            sender.sendMessage(ChatColor.YELLOW + "当前设置的捕捉物品是: " + currentItem);
                        } else {
                            sender.sendMessage(ChatColor.RED + "尚未设置捕捉物品。");
                        }
                    }
                    return true;
                case "list":
                    String mode = config.getString("mode", "black");
                    List<String> list = mode.equals("black") ? config.getStringList("blacklist") : config.getStringList("whitelist");
                    if (args.length == 1) {
                        if (list.isEmpty()) {
                            sender.sendMessage(ChatColor.RED + "当前 " + mode + " 名单为空。");
                        } else {
                            sender.sendMessage(ChatColor.YELLOW + "当前 " + mode + " 名单中的生物: " + String.join(", ", list));
                        }
                    } else {
                        String action = args[1].toLowerCase();
                        if (args.length == 3) {
                            try {
                                EntityType entityType = EntityType.valueOf(args[2].toUpperCase());
                                if ("add".equals(action)) {
                                    if (!list.contains(entityType.name())) {
                                        list.add(entityType.name());
                                        if (mode.equals("black")) {
                                            config.set("blacklist", list);
                                        } else {
                                            config.set("whitelist", list);
                                        }
                                        plugin.saveConfig();
                                        sender.sendMessage(ChatColor.AQUA + "已将 " + entityType.name() + " 添加到 " + mode + " 名单。");
                                    } else {
                                        sender.sendMessage(ChatColor.RED + entityType.name() + " 已经在 " + mode + " 名单中。");
                                    }
                                } else if ("remove".equals(action)) {
                                    if (list.contains(entityType.name())) {
                                        list.remove(entityType.name());
                                        if (mode.equals("black")) {
                                            config.set("blacklist", list);
                                        } else {
                                            config.set("whitelist", list);
                                        }
                                        plugin.saveConfig();
                                        sender.sendMessage(ChatColor.AQUA + "已将 " + entityType.name() + " 从 " + mode + " 名单移除。");
                                    } else {
                                        sender.sendMessage(ChatColor.RED + entityType.name() + " 不在 " + mode + " 名单中。");
                                    }
                                } else {
                                    sender.sendMessage(ChatColor.RED + "无效的操作，可用操作: add, remove");
                                }
                            } catch (IllegalArgumentException e) {
                                sender.sendMessage(ChatColor.RED + "无效的生物名称: " + args[2]);
                            }
                        } else {
                            sender.sendMessage(ChatColor.GOLD + "用法: /catchmobs list add/remove <生物名称>");
                        }
                    }
                    return true;
                case "debug":
                    boolean debug = !config.getBoolean("debug");
                    config.set("debug", debug);
                    plugin.saveConfig();
                    if (debug) {
                        sender.sendMessage(ChatColor.GREEN + "调试模式已开启，将在控制台输出日志信息。");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "调试模式已关闭，不再在控制台输出日志信息。");
                    }
                    return true;
                case "mode":
                    if (args.length == 2) {
                        String newMode = args[1].toLowerCase();
                        if ("white".equals(newMode) || "black".equals(newMode)) {
                            config.set("mode", newMode);
                            plugin.saveConfig();
                            sender.sendMessage(ChatColor.GREEN + "已切换到 " + newMode + " 名单模式。");
                        } else {
                            sender.sendMessage(ChatColor.RED + "无效的模式，可用模式: white, black");
                        }
                    } else {
                        String currentMode = config.getString("mode", "black");
                        sender.sendMessage(ChatColor.YELLOW + "当前模式是: " + currentMode);
                    }
                    return true;
                case "help":
                    sender.sendMessage(ChatColor.GOLD + "========== CatchMobs Help ==========");
                    sender.sendMessage(ChatColor.YELLOW + "/catchmobs item <物品材料名> - 设置捕捉物品");
                    sender.sendMessage(ChatColor.YELLOW + "/catchmobs list - 查看当前模式的名单");
                    sender.sendMessage(ChatColor.YELLOW + "/catchmobs list add/remove <生物名称> - 根据当前模式向名单添加或移除生物");
                    sender.sendMessage(ChatColor.YELLOW + "/catchmobs debug - 切换调试模式开关");
                    sender.sendMessage(ChatColor.YELLOW + "/catchmobs mode <white|black> - 切换黑白名单模式");
                    return true;
                default:
                    sender.sendMessage(ChatColor.RED + "未知子命令: " + subCommand);
                    return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("catchmobs")) {
            if (args.length == 1) {
                completions = Arrays.asList("item", "list", "debug", "mode", "help");
            } else if (args.length == 2) {
                String subCommand = args[0].toLowerCase();
                switch (subCommand) {
                    case "item":
                        for (Material material : Material.values()) {
                            completions.add(material.name().toLowerCase());
                        }
                        break;
                    case "list":
                        completions = Arrays.asList("add", "remove");
                        break;
                    case "mode":
                        completions = Arrays.asList("white", "black");
                        break;
                }
            } else if (args.length == 3) {
                String subCommand = args[0].toLowerCase();
                String action = args[1].toLowerCase();
                if ("list".equals(subCommand)) {
                    if ("add".equals(action)) {
                        for (EntityType entityType : EntityType.values()) {
                            completions.add(entityType.name().toLowerCase());
                        }
                    } else if ("remove".equals(action)) {
                        String mode = config.getString("mode", "black");
                        List<String> list = mode.equals("black") ? config.getStringList("blacklist") : config.getStringList("whitelist");
                        completions = list.stream().map(String::toLowerCase).collect(Collectors.toList());
                    }
                }
            }
        }
        return completions;
    }
}