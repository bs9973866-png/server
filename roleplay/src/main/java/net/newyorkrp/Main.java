package net.newyorkrp;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;

public final class Main extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, Long> lastWork = new ConcurrentHashMap<>();
    private final Map<UUID, String> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> balances = new ConcurrentHashMap<>();
    private YamlConfiguration data;
    private File dataFile;
    private HttpServer packServer;
    private File packFile;
    private final Map<UUID, VehicleData> vehicles = new ConcurrentHashMap<>();

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
        for (String cmd : List.of("ny", "nycity", "job", "work", "wallet", "pay", "car")) {
            Objects.requireNonNull(getCommand(cmd)).setExecutor(this);
            Objects.requireNonNull(getCommand(cmd)).setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        createResourcePack();
        startPackServer();
        if (getConfig().getBoolean("npc.spawn-on-first-start", true)) {
            Bukkit.getScheduler().runTaskLater(this, this::spawnStarterNpcs, 40L);
        }
        getLogger().info("NEW YORK RP enabled. Use /ny for help.");
    }

    @Override public void onDisable() {
        savePlayers();
        for (VehicleData vehicle : vehicles.values()) removeVehicleEntities(vehicle);
        vehicles.clear();
        if (packServer != null) packServer.stop(0);
    }

    private void savePlayers() {
        for (UUID id : new HashSet<>(balances.keySet())) {
            data.set(id + ".balance", balances.getOrDefault(id, 0));
            data.set(id + ".job", jobs.getOrDefault(id, "Безработный"));
        }
        try { data.save(dataFile); } catch (IOException ex) { getLogger().severe("Could not save players.yml: " + ex.getMessage()); }
    }

    private void createResourcePack() {
        try {
            File folder = new File(getDataFolder(), "resource-pack");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("Cannot create resource-pack folder");
            packFile = new File(folder, "new-york-rp.zip");
            File meta = new File(folder, "pack.mcmeta");
            Files.writeString(meta.toPath(), "{\"pack\":{\"description\":\"NEW YORK RP - City Pack\",\"pack_format\":88}}");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(packFile))) {
                zip.putNextEntry(new ZipEntry("pack.mcmeta"));
                zip.write(Files.readAllBytes(meta.toPath()));
                zip.closeEntry();
            }
            getLogger().info("Resource pack created: " + packFile.getAbsolutePath());
        } catch (Exception ex) { getLogger().severe("Resource pack creation failed: " + ex.getMessage()); }
    }

    private void startPackServer() {
        if (!getConfig().getBoolean("resource-pack.enabled", true) || packFile == null) return;
        try {
            int port = getConfig().getInt("resource-pack.http-port", 8765);
            packServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            packServer.createContext("/new-york-rp.zip", exchange -> {
                byte[] bytes = Files.readAllBytes(packFile.toPath());
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
            });
            packServer.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "NEW-YORK-RP-pack-http"); t.setDaemon(true); return t;
            }));
            packServer.start();
            getLogger().info("Resource-pack HTTP server listening on port " + port);
        } catch (Exception ex) { getLogger().severe("Could not start resource-pack HTTP server: " + ex.getMessage()); }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        balances.putIfAbsent(p.getUniqueId(), getConfig().getInt("economy.starting-balance", 500));
        jobs.putIfAbsent(p.getUniqueId(), "Безработный");
        p.sendMessage(ChatColor.DARK_AQUA + "✦ " + ChatColor.AQUA + "Добро пожаловать в NEW YORK RP!");
        p.sendMessage(ChatColor.GRAY + "Напиши " + ChatColor.WHITE + "/ny" + ChatColor.GRAY + ", чтобы посмотреть команды.");
        if (getConfig().getBoolean("resource-pack.enabled", true)) {
            String host = getConfig().getString("resource-pack.public-host", "127.0.0.1");
            int port = getConfig().getInt("resource-pack.http-port", 8765);
            try {
                byte[] hash = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(packFile.toPath()));
                p.setResourcePack("http://" + host + ":" + port + "/new-york-rp.zip", hash,
                    getConfig().getString("resource-pack.prompt", "NEW YORK RP resource pack"), false);
            } catch (Exception ex) { getLogger().warning("Could not send resource pack to " + p.getName() + ": " + ex.getMessage()); }
        }
    }

    private void spawnStarterNpcs() {
        World w = Bukkit.getWorlds().getFirst();
        Location base = w.getSpawnLocation().clone().add(4, 0, 4);
        spawnNpc(w, base, "§b§lМАРК • Мэрия", EntityType.VILLAGER, "Мэрия");
        spawnNpc(w, base.clone().add(3, 0, 0), "§e§lДЖЕЙ • Такси", EntityType.VILLAGER, "Такси");
        spawnNpc(w, base.clone().add(6, 0, 0), "§a§lЭММА • Больница", EntityType.VILLAGER, "Медик");
    }

    private void spawnNpc(World w, Location loc, String name, EntityType type, String role) {
        boolean exists = w.getNearbyEntities(loc, 1.5, 2, 1.5).stream()
            .anyMatch(e -> e.getScoreboardTags().contains("nyrp_npc_" + role));
        if (exists) return;
        Entity e = w.spawnEntity(loc, type);
        e.customName(net.kyori.adventure.text.Component.text(name.replace('§', ' ')));
        e.setCustomNameVisible(true);
        e.setPersistent(true);
        e.addScoreboardTag("nyrp_npc_" + role);
        if (e instanceof Villager v) { v.setProfession(Villager.Profession.NONE); v.setVillagerLevel(5); }
    }

    @EventHandler public void onNpcClick(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        for (String role : List.of("Мэрия", "Такси", "Медик")) {
            if (clicked.getScoreboardTags().contains("nyrp_npc_" + role)) {
                event.setCancelled(true);
                Player p = event.getPlayer();
                p.sendMessage(ChatColor.AQUA + "NEW YORK RP » " + ChatColor.WHITE +
                    (role.equals("Мэрия") ? "Добро пожаловать! Выбери профессию командой /job." :
                    role.equals("Такси") ? "Такси — выбери профессию /job taxi и выполняй смены через /work." :
                    "Медслужба города открыта. Профессия: /job medic."));
                break;
            }
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        if (c.equals("ny")) {
            sender.sendMessage(ChatColor.DARK_AQUA + "§lNEW YORK RP §7— команды");
            sender.sendMessage("§b/job §7— профессии: taxi, medic, police, mechanic, courier");
            sender.sendMessage("§b/work §7— рабочая смена    §b/wallet §7— баланс");
            sender.sendMessage("§b/pay <игрок> <сумма> §7— перевод денег");
            sender.sendMessage("§b/car §7— машины: /car spawn <sedan|sport|suv|taxi|police>, /car fuel, /car refuel, /car store");
            sender.sendMessage("§b/nycity generate §7— построить стартовый район (админ)");
            return true;
        }
        if (c.equals("nycity")) {
            if (!(sender instanceof Player cityAdmin)) { sender.sendMessage("Эту команду нужно выполнить в игре."); return true; }
            if (!sender.hasPermission("newyorkrp.admin")) { sender.sendMessage("§cНет прав."); return true; }
            if (args.length==0 || !args[0].equalsIgnoreCase("generate")) { sender.sendMessage("§e/nycity generate"); return true; }
            cityAdmin.sendMessage("§eГенерация стартового района Нью-Йорка началась. Строительство будет идти постепенно.");
            generateCity(cityAdmin.getWorld(), cityAdmin.getLocation().getBlockX(), cityAdmin.getLocation().getBlockZ());
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Эта команда доступна игроку."); return true; }
        if (c.equals("job")) {
            if (args.length == 0) { sender.sendMessage("§bТвоя профессия: §f" + jobs.getOrDefault(p.getUniqueId(), "Безработный")); sender.sendMessage("§7/job <taxi|medic|police|mechanic|courier>"); return true; }
            Map<String,String> names = Map.of("taxi","Таксист","medic","Медик","police","Полицейский","mechanic","Механик","courier","Курьер");
            String job = names.get(args[0].toLowerCase(Locale.ROOT));
            if (job == null) { p.sendMessage("§cТакой профессии нет. Доступно: taxi, medic, police, mechanic, courier."); return true; }
            jobs.put(p.getUniqueId(), job); savePlayers(); p.sendMessage("§aТы устроился на работу: §f" + job); return true;
        }
        if (c.equals("wallet")) { p.sendMessage("§6Кошелёк: §e$" + balances.getOrDefault(p.getUniqueId(), 500)); return true; }
        if (c.equals("work")) {
            long now = System.currentTimeMillis(), cooldown = getConfig().getLong("economy.work-cooldown-seconds",60)*1000L;
            long prev = lastWork.getOrDefault(p.getUniqueId(), 0L);
            if (now-prev < cooldown) { p.sendMessage("§cСледующая смена через " + ((cooldown-(now-prev))/1000) + " сек."); return true; }
            String job = jobs.getOrDefault(p.getUniqueId(), "Безработный");
            if (job.equals("Безработный")) { p.sendMessage("§cСначала устройся на работу: /job taxi"); return true; }
            int min=getConfig().getInt("economy.work-payment-min",45), max=getConfig().getInt("economy.work-payment-max",90);
            int reward=min + new Random().nextInt(Math.max(1,max-min+1));
            balances.merge(p.getUniqueId(), reward, Integer::sum); lastWork.put(p.getUniqueId(),now); savePlayers();
            p.sendMessage("§aСмена завершена: §e+$" + reward + " §7(" + job + ")"); return true;
        }
        if (c.equals("car")) return handleCarCommand(p, args);
        if (c.equals("pay")) {
            if (args.length != 2) { p.sendMessage("§cИспользование: /pay <игрок> <сумма>"); return true; }
            Player target=Bukkit.getPlayerExact(args[0]); int amount;
            try { amount=Integer.parseInt(args[1]); } catch(NumberFormatException ex) { p.sendMessage("§cУкажи целое число."); return true; }
            if(target==null || target.equals(p) || amount<1) { p.sendMessage("§cИгрок не найден или сумма неверна."); return true; }
            int own=balances.getOrDefault(p.getUniqueId(),500);
            if(own<amount) { p.sendMessage("§cНедостаточно средств."); return true; }
            balances.put(p.getUniqueId(),own-amount); balances.merge(target.getUniqueId(),amount,Integer::sum); savePlayers();
            p.sendMessage("§aПереведено §e$"+amount+" §aигроку "+target.getName()); target.sendMessage("§aПолучено §e$"+amount+" §aот "+p.getName()); return true;
        }
        if (c.equals("nycity")) {
            if (!sender.hasPermission("newyorkrp.admin")) { sender.sendMessage("§cНет прав."); return true; }
            if (args.length==0 || !args[0].equalsIgnoreCase("generate")) { sender.sendMessage("§e/nycity generate"); return true; }
            World w = p.getWorld();
            p.sendMessage("§eГенерация стартового района Нью-Йорка началась. Не уходи далеко.");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> generateCity(w, p.getLocation().getBlockX(), p.getLocation().getBlockZ()));
            return true;
        }
        return true;
    }

    private void generateCity(World w, int cx, int cz) {
        // Generate one row per tick on the main server thread.
        final int[] row = {-96};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            int z = row[0];
            for (int x=-96; x<=96; x++) {
                int wx=cx+x, wz=cz+z, y=w.getHighestBlockYAt(wx,wz);
                int ax=Math.floorMod(x+96,32), az=Math.floorMod(z+96,32);
                boolean road = ax<7 || az<7;
                w.getBlockAt(wx,y,wz).setType(road ? Material.GRAY_CONCRETE : Material.STONE_BRICKS,false);
                if (road && ax==3 && az==3) w.getBlockAt(wx,y+1,wz).setType(Material.YELLOW_CONCRETE,false);
                if (!road && ax==10 && az==10) buildTower(w,wx,y+1,wz,14+Math.floorMod(wx*31+wz*17,25));
            }
            row[0]++;
            if (row[0] > 96) {
                task.cancel();
                w.setSpawnLocation(new Location(w,cx+8,w.getHighestBlockYAt(cx+8,cz+8)+1,cz+8));
                getLogger().info("Starter New York district generated.");
            }
        },1L,1L);
    }

    private void buildTower(World w, int x, int y, int z, int height) {
        int width=14;
        for (int dy=0; dy<height; dy++) {
            for (int dx=0; dx<width; dx++) for (int dz=0; dz<width; dz++) {
                if (dx==0 || dz==0 || dx==width-1 || dz==width-1) {
                    Material block=(dy%5==0) ? Material.GRAY_CONCRETE :
                        ((dx+dz+dy)%4==0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.BLACKSTONE);
                    w.getBlockAt(x+dx,y+dy,z+dz).setType(block,false);
                }
            }
        }
        for (int dx=0; dx<width; dx++) for (int dz=0; dz<width; dz++)
            w.getBlockAt(x+dx,y+height,z+dz).setType(Material.SMOOTH_STONE,false);
    }


    // --- Driveable roleplay cars. The invisible pig is the vanilla steering controller;
    // block displays provide a server-side model, so players need no client mods.
    private static final class VehicleData {
        final UUID owner;
        final Pig driver;
        final List<BlockDisplay> parts;
        final String type;
        int fuel = 100;
        int tickCounter = 0;
        VehicleData(UUID owner, Pig driver, List<BlockDisplay> parts, String type) {
            this.owner = owner; this.driver = driver; this.parts = parts; this.type = type;
        }
    }

    private boolean handleCarCommand(Player p, String[] args) {
        UUID id = p.getUniqueId();
        VehicleData current = vehicles.get(id);
        if (args.length == 0) {
            p.sendMessage("§bМашины NEW YORK RP");
            p.sendMessage("§f/car spawn <sedan|sport|suv|taxi|police> §7— купить и создать машину");
            p.sendMessage("§f/car fuel §7— остаток топлива");
            p.sendMessage("§f/car refuel §7— заправить за $50");
            p.sendMessage("§f/car store §7— убрать свою машину");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (args.length < 2) { p.sendMessage("§eУкажи тип: sedan, sport, suv, taxi, police"); return true; }
                if (current != null) { p.sendMessage("§cСначала убери текущую машину: /car store"); return true; }
                String type = args[1].toLowerCase(Locale.ROOT);
                Material body = switch (type) {
                    case "sedan" -> Material.RED_CONCRETE;
                    case "sport" -> Material.PURPLE_CONCRETE;
                    case "suv" -> Material.GREEN_CONCRETE;
                    case "taxi" -> Material.YELLOW_CONCRETE;
                    case "police" -> Material.BLUE_CONCRETE;
                    default -> null;
                };
                if (body == null) { p.sendMessage("§cНеизвестная модель. Доступно: sedan, sport, suv, taxi, police."); return true; }
                if (type.equals("police") && !jobs.getOrDefault(id, "Безработный").equals("Полицейский")) {
                    p.sendMessage("§cПолицейская машина доступна только сотрудникам полиции."); return true;
                }
                int price = switch (type) { case "sport" -> 1200; case "suv" -> 900; case "taxi", "police" -> 700; default -> 500; };
                int balance = balances.getOrDefault(id, getConfig().getInt("economy.starting-balance", 500));
                if (balance < price) { p.sendMessage("§cНе хватает денег. Цена: $" + price); return true; }
                Location at = p.getLocation().clone().add(p.getLocation().getDirection().normalize().multiply(2));
                at.setY(p.getWorld().getHighestBlockYAt(at) + 1);
                Pig pig = p.getWorld().spawn(at, Pig.class, entity -> {
                    entity.setInvisible(true); entity.setSilent(true); entity.setSaddle(true);
                    entity.setCustomName("NEW YORK RP Vehicle"); entity.setCustomNameVisible(false);
                    entity.setPersistent(true); entity.addScoreboardTag("nyrp_vehicle_controller");
                });
                List<BlockDisplay> parts = new ArrayList<>();
                createCarPart(pig, parts, body, 0, 0.65, 0);
                createCarPart(pig, parts, Material.LIGHT_BLUE_STAINED_GLASS, 0, 1.55, -0.05);
                createCarPart(pig, parts, Material.BLACK_CONCRETE, -0.8, 0.15, 0.65);
                createCarPart(pig, parts, Material.BLACK_CONCRETE, 0.8, 0.15, 0.65);
                createCarPart(pig, parts, Material.BLACK_CONCRETE, -0.8, 0.15, -0.65);
                createCarPart(pig, parts, Material.BLACK_CONCRETE, 0.8, 0.15, -0.65);
                if (type.equals("taxi")) createCarPart(pig, parts, Material.WHITE_CONCRETE, 0, 2.15, 0);
                if (type.equals("police")) createCarPart(pig, parts, Material.REDSTONE_BLOCK, 0, 2.15, 0);
                VehicleData vehicle = new VehicleData(id, pig, parts, type);
                vehicles.put(id, vehicle);
                balances.put(id, balance - price); savePlayers();
                p.getInventory().addItem(new ItemStack(Material.CARROT_ON_A_STICK));
                pig.addPassenger(p);
                p.sendMessage("§aМашина создана: §f" + type + " §7| Цена: $" + price + " | Топливо: 100%");
                p.sendMessage("§7Управление: держи морковь на палке и смотри в сторону движения. ПКМ — ускорение.");
                Bukkit.getScheduler().runTaskTimer(this, task -> {
                    if (!pig.isValid() || !vehicles.containsKey(id) || vehicles.get(id) != vehicle) {
                        task.cancel(); return;
                    }
                    vehicle.tickCounter++;
                    if (vehicle.driver.getVelocity().lengthSquared() > 0.025 && vehicle.tickCounter % 20 == 0) {
                        vehicle.fuel--;
                        if (vehicle.fuel <= 0) {
                            vehicle.fuel = 0; vehicle.driver.setVelocity(new Vector());
                            Player owner = Bukkit.getPlayer(id);
                            if (owner != null) owner.sendMessage("§cТопливо закончилось! Используй /car refuel.");
                        }
                    }
                    updateCarParts(vehicle);
                }, 1L, 1L);
            }
            case "fuel" -> {
                if (current == null) p.sendMessage("§cУ тебя нет машины.");
                else p.sendMessage("§eТопливо: " + current.fuel + "%");
            }
            case "refuel" -> {
                if (current == null) { p.sendMessage("§cУ тебя нет машины."); return true; }
                if (current.fuel >= 100) { p.sendMessage("§aБак уже полный."); return true; }
                int balance = balances.getOrDefault(id, 0);
                if (balance < 50) { p.sendMessage("§cЗаправка стоит $50."); return true; }
                balances.put(id, balance - 50); current.fuel = 100; savePlayers();
                p.sendMessage("§aМашина заправлена до 100% за $50.");
            }
            case "store" -> {
                if (current == null) { p.sendMessage("§cУ тебя нет машины."); return true; }
                removeVehicleEntities(current); vehicles.remove(id);
                p.sendMessage("§aМашина убрана в гараж.");
            }
            default -> p.sendMessage("§eИспользуй /car для списка команд.");
        }
        return true;
    }

    private void createCarPart(Pig pig, List<BlockDisplay> parts, Material material, double x, double y, double z) {
        BlockDisplay display = pig.getWorld().spawn(pig.getLocation(), BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setGravity(false); entity.setInvulnerable(true); entity.setPersistent(true);
            entity.addScoreboardTag("nyrp_vehicle_part");
        });
        parts.add(display);
        display.teleport(vehiclePartLocation(pig, x, y, z));
    }

    private Location vehiclePartLocation(Pig pig, double x, double y, double z) {
        Location base = pig.getLocation();
        double radians = Math.toRadians(base.getYaw());
        double rx = x * Math.cos(radians) - z * Math.sin(radians);
        double rz = x * Math.sin(radians) + z * Math.cos(radians);
        return base.clone().add(rx, y, rz);
    }

    private void updateCarParts(VehicleData vehicle) {
        double[][] offsets = {{0,0.65,0},{0,1.55,-0.05},{-0.8,0.15,0.65},{0.8,0.15,0.65},{-0.8,0.15,-0.65},{0.8,0.15,-0.65},{0,2.15,0}};
        for (int i=0; i<vehicle.parts.size(); i++) {
            double[] o = offsets[i];
            vehicle.parts.get(i).teleport(vehiclePartLocation(vehicle.driver,o[0],o[1],o[2]));
        }
    }

    private void removeVehicleEntities(VehicleData vehicle) {
        if (vehicle.driver.isValid()) vehicle.driver.remove();
        for (BlockDisplay part : vehicle.parts) if (part.isValid()) part.remove();
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("job") && args.length==1) return List.of("taxi","medic","police","mechanic","courier");
        if (command.getName().equalsIgnoreCase("nycity") && args.length==1) return List.of("generate");
        if (command.getName().equalsIgnoreCase("car") && args.length==1) return List.of("spawn","fuel","refuel","store");
        if (command.getName().equalsIgnoreCase("car") && args.length==2 && args[0].equalsIgnoreCase("spawn")) return List.of("sedan","sport","suv","taxi","police");
        return Collections.emptyList();
    }
}