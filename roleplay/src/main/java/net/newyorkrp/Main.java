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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    private static final class NpcMenuHolder implements InventoryHolder {
        private Inventory inventory;
        private final String role;
        private NpcMenuHolder(String role) { this.role = role; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private record CityNpc(String name, String skin, String role, int dx, int dz) {}

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
        Bukkit.getScheduler().runTaskLater(this, this::createCityNpcs, 40L);
        getLogger().info("NEW YORK RP enabled. Automatic city NPC setup scheduled.");
    }

    @Override public void onDisable() { savePlayers(); }

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

    private void openNpcMenu(Player player, String role) {
        if (role.equals("mayor") || role.equals("jobs")) { openCityMenu(player); return; }
        String title = switch (role) {
            case "taxi" -> "Такси • Диспетчер";
            case "medic" -> "Больница • Регистратура";
            case "mechanic" -> "Автосервис • Механик";
            case "courier" -> "Доставка • Начальник смены";
            default -> "Городская служба";
        };
        String job = switch (role) {
            case "taxi" -> "Таксист";
            case "medic" -> "Медик";
            case "mechanic" -> "Механик";
            case "courier" -> "Курьер";
            default -> "Безработный";
        };
        Material icon = switch (role) {
            case "taxi" -> Material.YELLOW_CONCRETE;
            case "medic" -> Material.GOLDEN_APPLE;
            case "mechanic" -> Material.IRON_INGOT;
            default -> Material.PAPER;
        };
        NpcMenuHolder holder = new NpcMenuHolder(role);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text(title));
        holder.inventory = inv;
        inv.setItem(11, menuItem(icon, "Устроиться: " + job, "Нажми, чтобы выбрать эту профессию"));
        inv.setItem(13, menuItem(Material.BOOK, "Информация", "Текущая профессия: " + jobs.getOrDefault(player.getUniqueId(), "Безработный")));
        inv.setItem(15, menuItem(Material.EMERALD, "Кошелёк", "Баланс: $" + balances.getOrDefault(player.getUniqueId(), 500)));
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

    @EventHandler public void onNpcMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof NpcMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        switch (event.getRawSlot()) {
            case 11 -> {
                String job = switch (holder.role) {
                    case "taxi" -> "Таксист";
                    case "medic" -> "Медик";
                    case "mechanic" -> "Механик";
                    case "courier" -> "Курьер";
                    default -> "Безработный";
                };
                if (!job.equals("Безработный")) setJobFromMenu(player, job);
            }
            case 13 -> player.sendMessage("§bТвоя профессия: §f" + jobs.getOrDefault(player.getUniqueId(), "Безработный"));
            case 15 -> player.sendMessage("§6Кошелёк: §e$" + balances.getOrDefault(player.getUniqueId(), 500));
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

    private Location safeNpcLocation(World world, Location base, int dx, int dz) {
        int x = base.getBlockX() + dx;
        int z = base.getBlockZ() + dz;
        Location surface = world.getHighestBlockAt(x, z).getLocation().add(0.5, 1.0, 0.5);
        if (surface.getBlock().isLiquid() || !surface.clone().add(0, 1, 0).getBlock().isPassable()) {
            surface = base.clone().add(dx + 0.5, 0, dz + 0.5);
            surface.setY(world.getHighestBlockYAt(x, z) + 1.0);
        }
        surface.setYaw(base.getYaw());
        surface.setPitch(0);
        return surface;
    }

    private void createCityNpcs() {
        Plugin npcsPlugin = Bukkit.getPluginManager().getPlugin("NPCS");
        if (npcsPlugin == null || !npcsPlugin.isEnabled()) {
            getLogger().severe("NPCS plugin is missing. Run INSTALL-RP-ADDONS.bat; city NPCs cannot be created.");
            return;
        }
        if (Bukkit.getWorlds().isEmpty()) return;
        World world = Bukkit.getWorlds().get(0);
        Location base = world.getSpawnLocation();
        List<CityNpc> cityNpcs = List.of(
            new CityNpc("§6Мэр Нью-Йорка", "Notch", "mayor", 0, 4),
            new CityNpc("§eДиспетчер такси", "jeb_", "taxi", 4, 0),
            new CityNpc("§cРегистратура больницы", "Alex", "medic", -4, 0),
            new CityNpc("§7Главный механик", "Steve", "mechanic", 0, -4),
            new CityNpc("§aЦентр занятости", "Dream", "jobs", 4, 4),
            new CityNpc("§bНачальник доставки", "Technoblade", "courier", -4, 4)
        );
        try {
            ClassLoader loader = npcsPlugin.getClass().getClassLoader();
            Class<?> controllerClass = Class.forName("me.hihelloy.work.npcs.control.NpcController", true, loader);
            Object controller = controllerClass.getMethod("get").invoke(null);
            Class<?> managedClass = Class.forName("me.hihelloy.work.npcs.control.ManagedNpc", true, loader);
            Class<?> triggerClass = Class.forName("me.hihelloy.work.npcs.control.TriggerType", true, loader);
            Object rightClick = Enum.valueOf((Class<Enum>) triggerClass.asSubclass(Enum.class), "RIGHT_CLICK");
            Method addCommand = managedClass.getMethod("addCommand", triggerClass, boolean.class, String.class);

            for (CityNpc spec : cityNpcs) {
                Location at = safeNpcLocation(world, base, spec.dx(), spec.dz());
                Optional<?> existing = (Optional<?>) controllerClass.getMethod("find", String.class).invoke(controller, ChatColor.stripColor(spec.name()));
                if (existing.isPresent()) {
                    Object npc = existing.get();
                    managedClass.getMethod("setSkin", String.class).invoke(npc, spec.skin());
                    managedClass.getMethod("setInvulnerable", boolean.class).invoke(npc, true);
                    managedClass.getMethod("setImmovable", boolean.class).invoke(npc, true);
                    managedClass.getMethod("setLookAtPlayers", boolean.class).invoke(npc, true);
                    managedClass.getMethod("teleportTo", Location.class).invoke(npc, at);
                    continue;
                }
                Object builder = controllerClass.getMethod("builder", String.class).invoke(controller, ChatColor.stripColor(spec.name()));
                builder = builder.getClass().getMethod("at", Location.class).invoke(builder, at);
                builder = builder.getClass().getMethod("skin", String.class).invoke(builder, spec.skin());
                builder = builder.getClass().getMethod("invulnerable", boolean.class).invoke(builder, true);
                builder = builder.getClass().getMethod("immovable", boolean.class).invoke(builder, true);
                builder = builder.getClass().getMethod("lookAtPlayers", boolean.class).invoke(builder, true);
                CompletableFuture<?> future = (CompletableFuture<?>) builder.getClass().getMethod("spawn").invoke(builder);
                final String role = spec.role();
                future.thenAccept(npc -> Bukkit.getScheduler().runTask(this, () -> {
                    try { addCommand.invoke(npc, rightClick, true, "ny npc " + role); }
                    catch (ReflectiveOperationException ex) { getLogger().warning("Could not attach click menu to " + spec.name() + ": " + ex.getMessage()); }
                }));
            }
            getLogger().info("Automatic city NPC setup started: " + cityNpcs.size() + " human NPCs.");
        } catch (ReflectiveOperationException ex) {
            getLogger().severe("Could not connect to NPCS API: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        if (c.equals("ny")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("npc")) {
                if (!(sender instanceof Player player)) { sender.sendMessage("Команда доступна игроку."); return true; }
                String role = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "mayor";
                if (!Set.of("mayor", "taxi", "medic", "mechanic", "jobs", "courier").contains(role)) {
                    player.sendMessage("§cНеизвестный персонаж."); return true;
                }
                openNpcMenu(player, role);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("menu")) {
                Player target;
                if (args.length >= 2) {
                    if (!sender.hasPermission("newyorkrp.admin") && !(sender instanceof Player p && p.getName().equalsIgnoreCase(args[1]))) {
                        sender.sendMessage("§cНет прав открыть меню другому игроку."); return true;
                    }
                    target = Bukkit.getPlayerExact(args[1]);
                } else target = sender instanceof Player p ? p : null;
                if (target == null) { sender.sendMessage("§cИспользование: /ny menu [игрок онлайн]"); return true; }
                openCityMenu(target); return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("money")) {
                if (!sender.hasPermission("newyorkrp.admin")) { sender.sendMessage("§cНедостаточно прав. Нужны права newyorkrp.admin."); return true; }
                if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
                    sender.sendMessage("§e/ny money give <игрок> <сумма> §7— выдать деньги");
                    sender.sendMessage("§e/ny money set <игрок> <сумма> §7— установить баланс");
                    sender.sendMessage("§e/ny money balance <игрок> §7— посмотреть баланс"); return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) { sender.sendMessage("§cИгрок должен быть в сети. Использование: /ny money <give|set|balance> <игрок> [сумма]"); return true; }
                if (args[1].equalsIgnoreCase("balance")) { sender.sendMessage("§eБаланс " + target.getName() + ": $" + balances.getOrDefault(target.getUniqueId(), getConfig().getInt("economy.starting-balance", 500))); return true; }
                if (args.length != 4 || !(args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("set"))) { sender.sendMessage("§cИспользование: /ny money give <игрок> <сумма> или /ny money set <игрок> <сумма>"); return true; }
                long amount;
                try { amount = Long.parseLong(args[3]); } catch (NumberFormatException ex) { sender.sendMessage("§cСумма должна быть целым числом."); return true; }
                if (amount < 0 || amount > 2_000_000_000L) { sender.sendMessage("§cСумма должна быть от 0 до 2000000000."); return true; }
                int oldBalance = balances.getOrDefault(target.getUniqueId(), getConfig().getInt("economy.starting-balance", 500));
                long newBalance = args[1].equalsIgnoreCase("give") ? (long) oldBalance + amount : amount;
                if (newBalance > 2_000_000_000L) { sender.sendMessage("§cУ игрока будет слишком большой баланс."); return true; }
                balances.put(target.getUniqueId(), (int) newBalance); savePlayers();
                sender.sendMessage("§aБаланс " + target.getName() + " теперь: $" + newBalance);
                target.sendMessage("§aАдминистратор изменил твой баланс: $" + newBalance); return true;
            }
            sender.sendMessage("§b§lNEW YORK RP §7— меню города: /ny menu");
            sender.sendMessage("§b/job §7— профессия   §b/work §7— смена   §b/wallet §7— баланс");
            sender.sendMessage("§b/pay <игрок> <сумма> §7— перевод денег");
            sender.sendMessage("§b/ny money give|set <игрок> <сумма> §7— админская экономика");
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Эта команда доступна игроку."); return true; }
        if (c.equals("job")) {
            if (args.length == 0) {
                sender.sendMessage("§bТвоя профессия: §f" + jobs.getOrDefault(p.getUniqueId(), "Безработный"));
                sender.sendMessage("§7/job <taxi|medic|police|mechanic|courier>"); return true;
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
        if (command.getName().equalsIgnoreCase("ny") && args.length==1) return List.of("menu","money","npc");
        if (command.getName().equalsIgnoreCase("ny") && args.length==2 && args[0].equalsIgnoreCase("npc")) return List.of("mayor","taxi","medic","mechanic","jobs","courier");
        if (command.getName().equalsIgnoreCase("ny") && args.length==2 && args[0].equalsIgnoreCase("money")) return List.of("give","set","balance","help");
        return Collections.emptyList();
    }
}
