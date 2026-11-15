package ru.mirtime.trade;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeTrade extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, UUID> requests = new HashMap<>();
    private final Map<UUID, TradeSession> activeTrades = new HashMap<>();

    private static final int[] LEFT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final int[] RIGHT_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};

    public static class TradeSession {
        public final Player p1;
        public final Player p2;
        public final Inventory inv1;
        public final Inventory inv2;
        public boolean ready1 = false;
        public boolean ready2 = false;
        public boolean completed = false;
        public BukkitTask countdownTask = null;

        public TradeSession(Player p1, Player p2) {
            this.p1 = p1;
            this.p2 = p2;
            this.inv1 = Bukkit.createInventory(null, 54, color("&#0055ff&l⚡ &#00E5FF&lОбмен &8| &fВы ⇄ &#00FF66" + p2.getName()));
            this.inv2 = Bukkit.createInventory(null, 54, color("&#0055ff&l⚡ &#00E5FF&lОбмен &8| &fВы ⇄ &#00FF66" + p1.getName()));
        }
    }

    @Override
    public void onEnable() {
        if (getCommand("trade") != null) getCommand("trade").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MirTimeTrade успешно запущен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length == 0) {
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИспользование: &#00FF66/trade <ник>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            UUID senderId = requests.remove(p.getUniqueId());
            if (senderId == null) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cУ вас нет активных запросов на обмен."));
                return true;
            }
            Player target = Bukkit.getPlayer(senderId);
            if (target == null || !target.isOnline()) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок вышел с сервера."));
                return true;
            }
            if (activeTrades.containsKey(p.getUniqueId()) || activeTrades.containsKey(target.getUniqueId())) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cКто-то из вас уже находится в обмене!"));
                return true;
            }
            startTrade(target, p);
            return true;
        }

        if (args[0].equalsIgnoreCase("deny")) {
            UUID senderId = requests.remove(p.getUniqueId());
            if (senderId != null) {
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВы отклонили запрос на обмен."));
                Player target = Bukkit.getPlayer(senderId);
                if (target != null) target.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00E5FF" + p.getName() + " &cотклонил обмен."));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок не найден в сети."));
            return true;
        }
        if (target.equals(p)) {
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cНельзя отправить обмен самому себе."));
            return true;
        }
        if (activeTrades.containsKey(p.getUniqueId()) || activeTrades.containsKey(target.getUniqueId())) {
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cИгрок сейчас занят другим обменом."));
            return true;
        }

        requests.put(target.getUniqueId(), p.getUniqueId());
        p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fЗапрос на обмен отправлен игроку &#00FF66" + target.getName() + "&f!"));

        target.sendMessage(color("&#0055ff&l&m                                        "));
        target.sendMessage(color(" &#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &fИгрок &#00FF66" + p.getName() + " &fпредлагает обмен!"));
        TextComponent acceptBtn = new TextComponent(color("  &#00FF66&l[✔ ПРИНЯТЬ]   "));
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"));
        TextComponent denyBtn = new TextComponent(color("&c&l[✗ ОТКЛОНИТЬ]"));
        denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"));
        target.spigot().sendMessage(acceptBtn, denyBtn);
        target.sendMessage(color("&#0055ff&l&m                                        "));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        return true;
    }

    private void startTrade(Player p1, Player p2) {
        TradeSession s = new TradeSession(p1, p2);
        activeTrades.put(p1.getUniqueId(), s);
        activeTrades.put(p2.getUniqueId(), s);
        setupFrames(s);
        updateButtons(s);
        p1.openInventory(s.inv1);
        p2.openInventory(s.inv2);
        p1.playSound(p1.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        p2.playSound(p2.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    private void setupFrames(TradeSession s) {
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪");
        ItemStack cyan = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫");
        int[] borders = {0,1,2,3,4,5,6,7,8,9,13,17,18,22,26,27,31,35,36,40,44,45,46,47,49,51,52,53};
        for (int slot : borders) {
            ItemStack glass = (slot % 9 == 4 || slot < 9 || slot >= 45) ? blue : cyan;
            s.inv1.setItem(slot, glass);
            s.inv2.setItem(slot, glass);
        }
        s.inv1.setItem(4, item(Material.NETHER_STAR, "&#0055ff&l[★] &#00E5FF&lБезопасный обмен MIR-TIME"));
        s.inv2.setItem(4, item(Material.NETHER_STAR, "&#0055ff&l[★] &#00E5FF&lБезопасный обмен MIR-TIME"));
    }
    private void updateButtons(TradeSession s) {
        s.inv1.setItem(48, item(s.ready1 ? Material.LIME_DYE : Material.GRAY_DYE,
                s.ready1 ? "&#00FF66&l[✔] Вы подтвердили обмен" : "&7[●] Нажмите, чтобы подтвердить"));
        s.inv1.setItem(50, item(s.ready2 ? Material.LIME_DYE : Material.RED_DYE,
                s.ready2 ? "&#00FF66&l[✔] " + s.p2.getName() + " готов!" : "&c[✗] " + s.p2.getName() + " ещё выбирает"));

        s.inv2.setItem(48, item(s.ready2 ? Material.LIME_DYE : Material.GRAY_DYE,
                s.ready2 ? "&#00FF66&l[✔] Вы подтвердили обмен" : "&7[●] Нажмите, чтобы подтвердить"));
        s.inv2.setItem(50, item(s.ready1 ? Material.LIME_DYE : Material.RED_DYE,
                s.ready1 ? "&#00FF66&l[✔] " + s.p1.getName() + " готов!" : "&c[✗] " + s.p1.getName() + " ещё выбирает"));
    }

    private void syncItems(TradeSession s) {
        for (int i = 0; i < LEFT_SLOTS.length; i++) {
            s.inv2.setItem(RIGHT_SLOTS[i], s.inv1.getItem(LEFT_SLOTS[i]));
            s.inv1.setItem(RIGHT_SLOTS[i], s.inv2.getItem(LEFT_SLOTS[i]));
        }
    }

    private void cancelCountdown(TradeSession s) {
        if (s.countdownTask != null) {
            s.countdownTask.cancel();
            s.countdownTask = null;
        }
        s.ready1 = false;
        s.ready2 = false;
        s.inv1.setItem(49, item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪"));
        s.inv2.setItem(49, item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪"));
        updateButtons(s);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        TradeSession s = activeTrades.get(p.getUniqueId());
        if (s == null) return;

        int raw = e.getRawSlot();
        boolean isP1 = p.equals(s.p1);

        if (raw < 54) {
            e.setCancelled(true);
            if (raw == 48) {
                if (isP1) s.ready1 = !s.ready1;
                else s.ready2 = !s.ready2;

                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                if (!s.ready1 || !s.ready2) {
                    cancelCountdown(s);
                    if (isP1) s.ready1 = true;
                    updateButtons(s);
                } else {
                    updateButtons(s);
                    startCountdown(s);
                }
                return;
            }

            if (isLeftSlot(raw)) {
                ItemStack current = e.getCurrentItem();
                if (current != null && current.getType() != Material.AIR) {
                    e.getInventory().setItem(raw, null);
                    giveOrDrop(p, current);
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
                    cancelCountdown(s);
                    syncItems(s);
                }
            }
            return;
        }

        // Клик по своему нижнему инвентарю — положить предмет в трейд
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int freeSlot = findFreeLeftSlot(e.getView().getTopInventory());
        if (freeSlot == -1) {
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВ окне обмена больше нет места!"));
            return;
        }

        e.getView().getTopInventory().setItem(freeSlot, clicked.clone());
        e.setCurrentItem(null);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
        cancelCountdown(s);
        syncItems(s);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (activeTrades.containsKey(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    private void startCountdown(TradeSession s) {
        s.countdownTask = new BukkitRunnable() {
            int sec = 3;
            @Override
            public void run() {
                if (!s.ready1 || !s.ready2 || s.completed) {
                    cancel();
                    return;
                }
                if (sec > 0) {
                    ItemStack clock = new ItemStack(Material.CLOCK, sec);
                    ItemMeta meta = clock.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(color("&#00FF66&lОбмен через " + sec + " сек..."));
                        clock.setItemMeta(meta);
                    }
                    s.inv1.setItem(49, clock);
                    s.inv2.setItem(49, clock);
                    s.p1.playSound(s.p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                    s.p2.playSound(s.p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                    sec--;
                } else {
                    cancel();
                    completeTrade(s);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void completeTrade(TradeSession s) {
        if (s.completed) return;
        s.completed = true;
        activeTrades.remove(s.p1.getUniqueId());
        activeTrades.remove(s.p2.getUniqueId());

        for (int slot : LEFT_SLOTS) {
            ItemStack itemFrom1 = s.inv1.getItem(slot);
            ItemStack itemFrom2 = s.inv2.getItem(slot);
            if (itemFrom1 != null && itemFrom1.getType() != Material.AIR) giveOrDrop(s.p2, itemFrom1);
            if (itemFrom2 != null && itemFrom2.getType() != Material.AIR) giveOrDrop(s.p1, itemFrom2);
        }

        s.p1.closeInventory();
        s.p2.closeInventory();
        s.p1.playSound(s.p1.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        s.p2.playSound(s.p2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        s.p1.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Обмен успешно завершён!"));
        s.p2.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66Обмен успешно завершён!"));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        abortTrade(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        abortTrade(e.getPlayer().getUniqueId());
    }

    private void abortTrade(UUID uuid) {
        TradeSession s = activeTrades.remove(uuid);
        if (s == null || s.completed) return;
        s.completed = true;
        if (s.countdownTask != null) s.countdownTask.cancel();

        activeTrades.remove(s.p1.getUniqueId());
        activeTrades.remove(s.p2.getUniqueId());

        for (int slot : LEFT_SLOTS) {
            ItemStack i1 = s.inv1.getItem(slot);
            ItemStack i2 = s.inv2.getItem(slot);
            if (i1 != null && i1.getType() != Material.AIR) giveOrDrop(s.p1, i1);
            if (i2 != null && i2.getType() != Material.AIR) giveOrDrop(s.p2, i2);
        }

        if (s.p1.isOnline()) {
            s.p1.closeInventory();
            s.p1.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cОбмен отменён, вещи возвращены."));
        }
        if (s.p2.isOnline()) {
            s.p2.closeInventory();
            s.p2.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cОбмен отменён, вещи возвращены."));
        }
    }

    private boolean isLeftSlot(int slot) {
        for (int ls : LEFT_SLOTS) if (ls == slot) return true;
        return false;
    }

    private int findFreeLeftSlot(Inventory inv) {
        for (int ls : LEFT_SLOTS) {
            ItemStack it = inv.getItem(ls);
            if (it == null || it.getType() == Material.AIR) return ls;
        }
        return -1;
    }

    private void giveOrDrop(Player p, ItemStack item) {
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack drop : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
    }

    private static ItemStack item(Material mat, String name) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
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
