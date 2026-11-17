package net.newyorkrp;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Main extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, Long> lastWork = new ConcurrentHashMap<>();
    private final Map<UUID, String> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> balances = new ConcurrentHashMap<>();
    private YamlConfiguration data;
    private File dataFile;

    private static final class CityMenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : data.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                balances.put(id, data.getInt(key + ".balance", getConfig().getInt("economy.starting-balance", 500)));
                jobs.put(id, data.getString(key + ".job", "Безработный"));
            } catch (IllegalArgumentException ignored) {}
        }
        for (String cmd : List.of("ny", "job", "work", "wallet", "pay")) {
            Objects.requireNonNull(getCommand(cmd)).setExecutor(this);
            Objects.requireNonNull(getCommand(cmd)).setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NEW YORK RP enabled. Human NPCs and cars are provided by the external Paper plugins installed with INSTALL-RP-ADDONS.ps1.");
    }

    @Override public void onDisable() {
        savePlayers();
    }

    private void savePlayers() {
        for (UUID id : new HashSet<>(balances.keySet())) {
            data.set(id + ".balance", balances.getOrDefault(id, 0));
            data.set(id + ".job", jobs.getOrDefault(id, "Безработный"));
        }
        try { data.save(dataFile); }
        catch (IOException ex) { getLogger().severe("Could not save players.yml: " + ex.getMessage()); }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        balances.putIfAbsent(p.getUniqueId(), getConfig().getInt("economy.starting-balance", 500));
        jobs.putIfAbsent(p.getUniqueId(), "Безработный");
        p.sendMessage(ChatColor.DARK_AQUA + "✦ " + ChatColor.AQUA + "Добро пожаловать в NEW YORK RP!");
        p.sendMessage(ChatColor.GRAY + "Напиши " + ChatColor.WHITE + "/ny" + ChatColor.GRAY + ", чтобы открыть меню города.");
    }

    private ItemStack menuItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(List.of(Component.text(lore)));
        item.setItemMeta(meta);
        return item;
    }

    private void openCityMenu(Player player) {
        CityMenuHolder holder = new CityMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("NEW YORK RP • Город"));
        holder.inventory = inv;
        inv.setItem(10, menuItem(Material.YELLOW_CONCRETE, "Таксист", "Устроиться в городское такси"));
        inv.setItem(11, menuItem(Material.GOLDEN_APPLE, "Медик", "Работа в городской больнице"));
        inv.setItem(12, menuItem(Material.BLUE_CONCRETE, "Полицейский", "Городская служба порядка"));
        inv.setItem(13, menuItem(Material.IRON_INGOT, "Механик", "Ремонт и обслуживание автомобилей"));
        inv.setItem(14, menuItem(Material.PAPER, "Курьер", "Доставка заказов по городу"));
        inv.setItem(22, menuItem(Material.EMERALD, "Мой кошелёк", "Посмотреть баланс"));
        inv.setItem(26, menuItem(Material.BARRIER, "Закрыть", "Закрыть меню"));
        player.openInventory(inv);
    }

    @EventHandler public void onCityMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CityMenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        switch (event.getRawSlot()) {
            case 10 -> setJobFromMenu(player, "Таксист");
            case 11 -> setJobFromMenu(player, "Медик");
            case 12 -> setJobFromMenu(player, "Полицейский");
            case 13 -> setJobFromMenu(player, "Механик");
            case 14 -> setJobFromMenu(player, "Курьер");
            case 22 -> player.sendMessage("§6Кошелёк: §e$" + balances.getOrDefault(player.getUniqueId(), 500));
            case 26 -> player.closeInventory();
            default -> { }
        }
    }

    private void setJobFromMenu(Player player, String job) {
        jobs.put(player.getUniqueId(), job);
        savePlayers();
        player.sendMessage("§aТы устроился на работу: §f" + job);
        player.closeInventory();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        if (c.equals("ny")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("menu")) {
                Player target;
                if (args.length >= 2) {
                    if (!sender.hasPermission("newyorkrp.admin") && !(sender instanceof Player p && p.getName().equalsIgnoreCase(args[1]))) {
                        sender.sendMessage("§cНет прав открыть меню другому игроку.");
                        return true;
                    }
                    target = Bukkit.getPlayerExact(args[1]);
                } else target = sender instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage("§cИспользование: /ny menu [игрок онлайн]");
                    return true;
                }
                openCityMenu(target);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("money")) {
                if (!sender.hasPermission("newyorkrp.admin")) {
                    sender.sendMessage("§cНедостаточно прав. Нужны права newyorkrp.admin.");
                    return true;
                }
                if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
                    sender.sendMessage("§e/ny money give <игрок> <сумма> §7— выдать деньги");
                    sender.sendMessage("§e/ny money set <игрок> <сумма> §7— установить баланс");
                    sender.sendMessage("§e/ny money balance <игрок> §7— посмотреть баланс");
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) {
                    sender.sendMessage("§cИгрок должен быть в сети. Использование: /ny money <give|set|balance> <игрок> [сумма]");
                    return true;
                }
                if (args[1].equalsIgnoreCase("balance")) {
                    sender.sendMessage("§eБаланс " + target.getName() + ": $" + balances.getOrDefault(target.getUniqueId(), getConfig().getInt("economy.starting-balance", 500)));
                    return true;
                }
                if (args.length != 4 || !(args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("set"))) {
                    sender.sendMessage("§cИспользование: /ny money give <игрок> <сумма> или /ny money set <игрок> <сумма>");
                    return true;
                }
                long amount;
                try { amount = Long.parseLong(args[3]); }
                catch (NumberFormatException ex) { sender.sendMessage("§cСумма должна быть целым числом."); return true; }
                if (amount < 0 || amount > 2_000_000_000L) {
                    sender.sendMessage("§cСумма должна быть от 0 до 2000000000.");
                    return true;
                }
                int oldBalance = balances.getOrDefault(target.getUniqueId(), getConfig().getInt("economy.starting-balance", 500));
                long newBalance = args[1].equalsIgnoreCase("give") ? (long) oldBalance + amount : amount;
                if (newBalance > 2_000_000_000L) {
                    sender.sendMessage("§cУ игрока будет слишком большой баланс.");
                    return true;
                }
                balances.put(target.getUniqueId(), (int) newBalance);
                savePlayers();
                sender.sendMessage("§aБаланс " + target.getName() + " теперь: $" + newBalance);
                target.sendMessage("§aАдминистратор изменил твой баланс: $" + newBalance);
                return true;
            }
            sender.sendMessage("§b§lNEW YORK RP §7— меню города: /ny menu");
            sender.sendMessage("§b/job §7— профессия   §b/work §7— смена   §b/wallet §7— баланс");
            sender.sendMessage("§b/pay <игрок> <сумма> §7— перевод денег");
            sender.sendMessage("§b/ny money give|set <игрок> <сумма> §7— админская экономика");
            sender.sendMessage("§7Человеческие NPC и машины подключаются отдельными плагинами.");
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Эта команда доступна игроку."); return true; }
        if (c.equals("job")) {
            if (args.length == 0) {
                sender.sendMessage("§bТвоя профессия: §f" + jobs.getOrDefault(p.getUniqueId(), "Безработный"));
                sender.sendMessage("§7/job <taxi|medic|police|mechanic|courier>");
                return true;
            }
            Map<String,String> names = Map.of("taxi","Таксист","medic","Медик","police","Полицейский","mechanic","Механик","courier","Курьер");
            String job = names.get(args[0].toLowerCase(Locale.ROOT));
            if (job == null) { p.sendMessage("§cДоступно: taxi, medic, police, mechanic, courier."); return true; }
            jobs.put(p.getUniqueId(), job); savePlayers(); p.sendMessage("§aТы устроился на работу: §f" + job); return true;
        }
        if (c.equals("wallet")) { p.sendMessage("§6Кошелёк: §e$" + balances.getOrDefault(p.getUniqueId(), 500)); return true; }
        if (c.equals("work")) {
            long now = System.currentTimeMillis(), cooldown = getConfig().getLong("economy.work-cooldown-seconds",60)*1000L;
            long prev = lastWork.getOrDefault(p.getUniqueId(), 0L);
            if (now-prev < cooldown) { p.sendMessage("§cСледующая смена через " + ((cooldown-(now-prev))/1000) + " сек."); return true; }
            String job = jobs.getOrDefault(p.getUniqueId(), "Безработный");
            if (job.equals("Безработный")) { p.sendMessage("§cСначала устройся на работу: /ny menu"); return true; }
            int min=getConfig().getInt("economy.work-payment-min",45), max=getConfig().getInt("economy.work-payment-max",90);
            int reward=min + new Random().nextInt(Math.max(1,max-min+1));
            balances.merge(p.getUniqueId(), reward, Integer::sum); lastWork.put(p.getUniqueId(),now); savePlayers();
            p.sendMessage("§aСмена завершена: §e+$" + reward + " §7(" + job + ")"); return true;
        }
        if (c.equals("pay")) {
            if (args.length != 2) { p.sendMessage("§cИспользование: /pay <игрок> <сумма>"); return true; }
            Player target=Bukkit.getPlayerExact(args[0]); int amount;
            try { amount=Integer.parseInt(args[1]); } catch(NumberFormatException ex) { p.sendMessage("§cУкажи целое число."); return true; }
            if(target==null || target.equals(p) || amount<1) { p.sendMessage("§cИгрок не найден или сумма неверна."); return true; }
            int own=balances.getOrDefault(p.getUniqueId(),500);
            if(own<amount) { p.sendMessage("§cНедостаточно средств."); return true; }
            balances.put(p.getUniqueId(),own-amount); balances.merge(target.getUniqueId(),amount,Integer::sum); savePlayers();
            p.sendMessage("§aПереведено §e$"+amount+" §aигроку "+target.getName());
            target.sendMessage("§aПолучено §e$"+amount+" §aот "+p.getName()); return true;
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("job") && args.length==1) return List.of("taxi","medic","police","mechanic","courier");
        if (command.getName().equalsIgnoreCase("ny") && args.length==1) return List.of("menu","money");
        if (command.getName().equalsIgnoreCase("ny") && args.length==2 && args[0].equalsIgnoreCase("money")) return List.of("give","set","balance","help");
        return Collections.emptyList();
    }
}
