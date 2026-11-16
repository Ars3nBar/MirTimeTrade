/*
 *    __  __ _        _____ _                
 *   |  \/  (_)_ __  |_   _(_)_ __ ___   ___ 
 *   | |\/| | | '__|   | | | | '_ ` _ \ / _ \
 *   | |  | | | |      | | | | | | | | |  __/
 *   |_|  |_|_|_|      |_| |_|_| |_| |_|\___|
 *                                           
 *   Project: Mir Time | Author: Ars3nBar
 */
package ru.mirtime.trade;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeTrade extends JavaPlugin implements CommandExecutor, Listener {

    private Object econ;
    private final Map<UUID, UUID> requests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new HashMap<>();

    private static final int[] LEFT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final int[] RIGHT_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};

    private static class TradeSession {
        final Player p1;
        final Player p2;
        final Inventory inv1;
        final Inventory inv2;
        double money1 = 0.0;
        double money2 = 0.0;
        boolean ready1 = false;
        boolean ready2 = false;
        boolean locked = false;
        BukkitTask countdownTask = null;

        TradeSession(Player p1, Player p2, Inventory inv1, Inventory inv2) {
            this.p1 = p1;
            this.p2 = p2;
            this.inv1 = inv1;
            this.inv2 = inv2;
        }
    }

    @Override
    public void onEnable() {
        setupEconomy();
        if (getCommand("trade") != null) getCommand("trade").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getConsoleSender().sendMessage(color("&#0055ff   __  __ _        _____ _                "));
        Bukkit.getConsoleSender().sendMessage(color("&#0055ff  |  \\/  (_)_ __  |_   _(_)_ __ ___   ___ "));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |\\/| | | '__|   | | | | '_ ` _ \\ / _ \\"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |  | | | |      | | | | | | | | |  __/"));
        Bukkit.getConsoleSender().sendMessage(color("&#00FF66  |_|  |_|_|_|      |_| |_|_| |_| |_|\\___|"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  MirTimeTrade v1.1.0 (Anti-Dupe Edition) запущен!"));
    }

    @Override
    public void onDisable() {
        for (TradeSession s : new HashSet<>(sessions.values())) {
            cancelTrade(s, "&cОбмен отменён из-за перезагрузки сервера.");
        }
    }

    private void setupEconomy() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = getServer().getServicesManager().getRegistration(econClass);
            if (rsp != null) econ = rsp.getProvider();
        } catch (Exception ignored) {}
    }

    private double getBalance(Player p) {
        if (econ == null) return 0.0;
        try {
            return (double) econ.getClass().getMethod("getBalance", OfflinePlayer.class).invoke(econ, p);
        } catch (Exception e) { return 0.0; }
    }

    private void withdraw(Player p, double amount) {
        if (econ == null || amount <= 0) return;
        try {
            econ.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(econ, p, amount);
        } catch (Exception ignored) {}
    }

    private void deposit(Player p, double amount) {
        if (econ == null || amount <= 0) return;
        try {
            econ.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(econ, p, amount);
        } catch (Exception ignored) {}
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String prefix = color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» ");
        if (args.length == 0) {
            p.sendMessage(prefix + color("&fИспользование: &#00FF66/trade <ник> &8| &#00E5FF/trade accept &8| &c/trade deny"));
            return true;
        }

        String arg = List.of(args).get(0);
        if (arg.equalsIgnoreCase("accept")) {
            UUID senderId = requests.remove(p.getUniqueId());
            Player target = (senderId != null) ? Bukkit.getPlayer(senderId) : null;
            if (target == null || !target.isOnline()) {
                p.sendMessage(prefix + color("&cУ вас нет активных входящих предложений обмена."));
                return true;
            }
            if (sessions.containsKey(p.getUniqueId()) || sessions.containsKey(target.getUniqueId())) {
                p.sendMessage(prefix + color("&cОдин из игроков уже находится в обмене!"));
                return true;
            }
            startTrade(target, p);
            return true;
        }

        if (arg.equalsIgnoreCase("deny")) {
            UUID senderId = requests.remove(p.getUniqueId());
            if (senderId != null) {
                Player target = Bukkit.getPlayer(senderId);
                if (target != null) target.sendMessage(prefix + color("&cИгрок &f" + p.getName() + " &cотклонил ваше предложение обмена."));
                p.sendMessage(prefix + color("&7Вы отклонили предложение обмена."));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(arg);
        if (target == null || target.equals(p)) {
            p.sendMessage(prefix + color("&cУкажите корректного игрока в сети!"));
            return true;
        }
        if (target.getWorld() != p.getWorld() || target.getLocation().distanceSquared(p.getLocation()) > 400) {
            p.sendMessage(prefix + color("&cИгрок слишком далеко! Подойдите ближе (до 20 блоков)."));
            return true;
        }

        requests.put(target.getUniqueId(), p.getUniqueId());
        p.sendMessage(prefix + color("&#00FF66Запрос на безопасный обмен отправлен игроку &f" + target.getName() + "&#00FF66!"));

        TextComponent msg = new TextComponent(color(prefix + "&fИгрок &#00FF66" + p.getName() + " &fпредлагает вам обмен! "));
        TextComponent acceptBtn = new TextComponent(color("&#00FF66&l[✔ ПРИНЯТЬ] "));
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"));
        TextComponent denyBtn = new TextComponent(color("&c&l[✖ ОТКЛОНИТЬ]"));
        denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"));
        target.spigot().sendMessage(msg, acceptBtn, denyBtn);
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        return true;
    }

    private void startTrade(Player p1, Player p2) {
        Inventory inv1 = Bukkit.createInventory(null, 54, color("&#0055ff&l⚡ ОБМЕН &8| &#00FF66" + p2.getName()));
        Inventory inv2 = Bukkit.createInventory(null, 54, color("&#0055ff&l⚡ ОБМЕН &8| &#00FF66" + p1.getName()));

        TradeSession session = new TradeSession(p1, p2, inv1, inv2);
        sessions.put(p1.getUniqueId(), session);
        sessions.put(p2.getUniqueId(), session);

        setupFrame(inv1, p1.getName(), p2.getName());
        setupFrame(inv2, p2.getName(), p1.getName());
        updateButtons(session);

        p1.openInventory(inv1);
        p2.openInventory(inv2);
        p1.playSound(p1.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.1f);
        p2.playSound(p2.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.1f);
    }

    private void setupFrame(Inventory inv, String leftName, String rightName) {
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack cyan = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF│", null);
        for (int s : new int[]{0,1,3,5,7,8,9,17,18,26,27,35,36,44,48,50,52,53}) inv.setItem(s, blue);
        for (int s : new int[]{4,13,22,31,40,49}) inv.setItem(s, cyan);

        inv.setItem(2, item(Material.PLAYER_HEAD, "&#00FF66&lВАША СТОРОНА &8(&f" + leftName + "&8)", List.of("&7Выкладывайте вещи в левую сетку (12 слотов).")));
        inv.setItem(6, item(Material.ENDER_CHEST, "&#00E5FF&lСТОРОНА ПАРТНЁРА &8(&f" + rightName + "&8)", List.of("&7Здесь отображаются вещи, которые вы получите.")));
        inv.setItem(46, item(Material.SUNFLOWER, "&e&l[+1.000 монет] &8| &6&lПКМ: +10.000", List.of("&7ЛКМ — добавить 1.000 монет в сделку.", "&7ПКМ — добавить 10.000 монет в сделку.", "&7СКМ (Колёсико) — обнулить вашу ставку.")));
        inv.setItem(47, item(Material.BARRIER, "&c&l[✖ ОТМЕНИТЬ ОБМЕН]", List.of("&7Закрыть окно и вернуть все предметы.")));
    }

    private void updateButtons(TradeSession s) {
        if (s.countdownTask != null) {
            s.countdownTask.cancel();
            s.countdownTask = null;
        }
        s.locked = false;

        s.inv1.setItem(1, item(Material.GOLD_INGOT, "&e&lВаша доплата: &#00FF66" + (int) s.money1 + " монет", null));
        s.inv1.setItem(7, item(Material.GOLD_INGOT, "&e&lДоплата партнёра: &#00FF66" + (int) s.money2 + " монет", null));
        s.inv2.setItem(1, item(Material.GOLD_INGOT, "&e&lВаша доплата: &#00FF66" + (int) s.money2 + " монет", null));
        s.inv2.setItem(7, item(Material.GOLD_INGOT, "&e&lДоплата партнёра: &#00FF66" + (int) s.money1 + " монет", null));

        s.inv1.setItem(45, s.ready1
                ? item(Material.LIME_DYE, "&#00FF66&l[✔ ВЫ ГОТОВЫ К СДЕЛКЕ]", List.of("&7Нажмите, чтобы снять подтверждение."))
                : item(Material.GRAY_DYE, "&e&l[⏳ ПОДТВЕРДИТЬ ОБМЕН]", List.of("&7Нажмите, когда выложите все нужные вещи.")));
        s.inv1.setItem(51, s.ready2
                ? item(Material.LIME_STAINED_GLASS_PANE, "&#00FF66&l✔ ПАРТНЁР ГОТОВ", List.of("&f" + s.p2.getName() + " &7подтвердил обмен."))
                : item(Material.RED_STAINED_GLASS_PANE, "&c&l⏳ ПАРТНЁР ЕЩЁ НЕ ГОТОВ", List.of("&7Ожидаем подтверждения от &f" + s.p2.getName())));

        s.inv2.setItem(45, s.ready2
                ? item(Material.LIME_DYE, "&#00FF66&l[✔ ВЫ ГОТОВЫ К СДЕЛКЕ]", List.of("&7Нажмите, чтобы снять подтверждение."))
                : item(Material.GRAY_DYE, "&e&l[⏳ ПОДТВЕРДИТЬ ОБМЕН]", List.of("&7Нажмите, когда выложите все нужные вещи.")));
        s.inv2.setItem(51, s.ready1
                ? item(Material.LIME_STAINED_GLASS_PANE, "&#00FF66&l✔ ПАРТНЁР ГОТОВ", List.of("&f" + s.p1.getName() + " &7подтвердил обмен."))
                : item(Material.RED_STAINED_GLASS_PANE, "&c&l⏳ ПАРТНЁР ЕЩЁ НЕ ГОТОВ", List.of("&7Ожидаем подтверждения от &f" + s.p1.getName())));

        if (s.ready1 && s.ready2) {
            s.locked = true;
            s.countdownTask = new BukkitRunnable() {
                int sec = 3;
                @Override
                public void run() {
                    if (!sessions.containsKey(s.p1.getUniqueId())) { cancel(); return; }
                    if (sec <= 0) { cancel(); completeTrade(s); return; }
                    ItemStack timerIcon = item(Material.CLOCK, "&#00FF66&l⚡ СДЕЛКА ЧЕРЕЗ " + sec + " СЕК...", List.of("&7Слоты заблокированы от подмены."));
                    s.inv1.setItem(49, timerIcon);
                    s.inv2.setItem(49, timerIcon);
                    s.p1.playSound(s.p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.3f);
                    s.p2.playSound(s.p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.3f);
                    sec--;
                }
            }.runTaskTimer(this, 0L, 20L);
        } else {
            s.inv1.setItem(49, item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF│", null));
            s.inv2.setItem(49, item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF│", null));
        }
    }
    private void syncItems(TradeSession s) {
        for (int i = 0; i < LEFT_SLOTS.length; i++) {
            ItemStack item1 = s.inv1.getItem(LEFT_SLOTS[i]);
            s.inv2.setItem(RIGHT_SLOTS[i], item1 != null ? item1.clone() : null);

            ItemStack item2 = s.inv2.getItem(LEFT_SLOTS[i]);
            s.inv1.setItem(RIGHT_SLOTS[i], item2 != null ? item2.clone() : null);
        }
    }

    private boolean isLeftSlot(int rawSlot) {
        for (int s : LEFT_SLOTS) if (s == rawSlot) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTradeDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && sessions.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTradeClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null) return;

        ClickType click = e.getClick();
        if (click == ClickType.NUMBER_KEY || click == ClickType.DROP || click == ClickType.CONTROL_DROP
                || click == ClickType.DOUBLE_CLICK || click == ClickType.SWAP_OFFHAND || e.isShiftClick()) {
            e.setCancelled(true);
            return;
        }

        int slot = e.getRawSlot();
        if (slot < 0) return;

        if (slot < 54) {
            if (slot == 47) {
                e.setCancelled(true);
                cancelTrade(s, "&cОбмен отменён одним из участников.");
                return;
            }
            if (slot == 46) {
                e.setCancelled(true);
                if (s.locked || econ == null) return;
                boolean isP1 = p.equals(s.p1);
                if (click == ClickType.MIDDLE) {
                    if (isP1) s.money1 = 0; else s.money2 = 0;
                } else {
                    double add = e.isRightClick() ? 10000.0 : 1000.0;
                    double cur = isP1 ? s.money1 : s.money2;
                    if (getBalance(p) < cur + add) {
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                    if (isP1) s.money1 += add; else s.money2 += add;
                }
                s.ready1 = false;
                s.ready2 = false;
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
                updateButtons(s);
                return;
            }
            if (slot == 45) {
                e.setCancelled(true);
                if (p.equals(s.p1)) s.ready1 = !s.ready1;
                else s.ready2 = !s.ready2;
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                updateButtons(s);
                return;
            }
            if (!isLeftSlot(slot) || s.locked) {
                e.setCancelled(true);
                return;
            }
        } else if (s.locked) {
            e.setCancelled(true);
            return;
        }

        s.ready1 = false;
        s.ready2 = false;
        Bukkit.getScheduler().runTask(this, () -> {
            if (sessions.containsKey(p.getUniqueId())) {
                syncItems(s);
                updateButtons(s);
            }
        });
    }

    private void completeTrade(TradeSession s) {
        sessions.remove(s.p1.getUniqueId());
        sessions.remove(s.p2.getUniqueId());

        if (econ != null) {
            if (s.money1 > 0 && getBalance(s.p1) >= s.money1) {
                withdraw(s.p1, s.money1);
                deposit(s.p2, s.money1);
            }
            if (s.money2 > 0 && getBalance(s.p2) >= s.money2) {
                withdraw(s.p2, s.money2);
                deposit(s.p1, s.money2);
            }
        }

        List<ItemStack> from1 = new ArrayList<>();
        List<ItemStack> from2 = new ArrayList<>();
        for (int slot : LEFT_SLOTS) {
            ItemStack i1 = s.inv1.getItem(slot);
            if (i1 != null && !i1.getType().isAir()) from1.add(i1.clone());
            ItemStack i2 = s.inv2.getItem(slot);
            if (i2 != null && !i2.getType().isAir()) from2.add(i2.clone());
        }

        s.inv1.clear();
        s.inv2.clear();
        s.p1.closeInventory();
        s.p2.closeInventory();

        giveItemsSafely(s.p1, from2);
        giveItemsSafely(s.p2, from1);

        s.p1.playSound(s.p1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        s.p2.playSound(s.p2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        s.p1.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Обмен с игроком &f" + s.p2.getName() + " &#00FF66успешно завершён!"));
        s.p2.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Обмен с игроком &f" + s.p1.getName() + " &#00FF66успешно завершён!"));
    }

    private void cancelTrade(TradeSession s, String reason) {
        if (sessions.remove(s.p1.getUniqueId()) == null && sessions.remove(s.p2.getUniqueId()) == null) return;
        if (s.countdownTask != null) s.countdownTask.cancel();

        List<ItemStack> from1 = new ArrayList<>();
        List<ItemStack> from2 = new ArrayList<>();
        for (int slot : LEFT_SLOTS) {
            ItemStack i1 = s.inv1.getItem(slot);
            if (i1 != null && !i1.getType().isAir()) from1.add(i1.clone());
            ItemStack i2 = s.inv2.getItem(slot);
            if (i2 != null && !i2.getType().isAir()) from2.add(i2.clone());
        }

        s.inv1.clear();
        s.inv2.clear();
        if (s.p1.isOnline()) {
            s.p1.closeInventory();
            giveItemsSafely(s.p1, from1);
            s.p1.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» " + reason));
        }
        if (s.p2.isOnline()) {
            s.p2.closeInventory();
            giveItemsSafely(s.p2, from2);
            s.p2.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» " + reason));
        }
    }

    private void giveItemsSafely(Player p, List<ItemStack> items) {
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack drop : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
        }
    }

    @EventHandler
    public void onTradeClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && sessions.containsKey(p.getUniqueId())) {
            cancelTrade(sessions.get(p.getUniqueId()), "&cОбмен прерван — все вещи возвращены владельцам.");
        }
    }

    @EventHandler
    public void onTradeQuit(PlayerQuitEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) {
            cancelTrade(sessions.get(e.getPlayer().getUniqueId()), "&cИгрок вышел с сервера — обмен отменён.");
        }
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> list = new ArrayList<>();
                for (String s : lore) list.add(color(s));
                meta.setLore(list);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(meta);
        }
        return is;
    }

    public static String color(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append('§').append(c);
            matcher.appendReplacement(buffer, rep.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
