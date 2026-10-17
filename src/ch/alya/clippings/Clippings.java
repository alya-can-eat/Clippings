package ch.alya.clippings;

import net.minecraft.server.Container;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Item;
import net.minecraft.server.ItemBlock;
import net.minecraft.server.Packet102WindowClick;
import net.minecraft.server.Slot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.packet.PacketListener;
import org.bukkit.event.packet.PacketReceivedEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import java.util.logging.Logger;

public class Clippings extends JavaPlugin {

    private static final Logger LOG = Logger.getLogger("Minecraft");

    // block and item ids (Beta 1.7.3)
    private static final int WEB = 30;
    private static final int LONG_GRASS = 31;
    private static final int DEAD_BUSH = 32;
    private static final int STRING = 287;
    private static final int SEEDS = 295;
    private static final int SHEARS = 359;

    private Configuration cfg;

    private boolean collectTallGrass;
    private boolean collectDeadBush;
    private boolean collectCobweb;
    private boolean fixTallGrassInventory;

    /** Item that was in Item.byId[31] before the swap, put back in onDisable(). */
    private Item originalTallGrassItem;
    private boolean itemSwapped;

    /**
     * Vanilla drop to remove after a shears break. Vanilla still runs its own
     * drop code right after BlockBreakEvent (seeds 1 in 8 for grass, string
     * for cobwebs). Only lives until the next tick.
     */
    private PendingBreak pending;

    private static class PendingBreak {
        World world;
        int x, y, z;
        int vanillaDropId;
    }

    // ================================================================== lifecycle

    public void onEnable() {
        cfg = getConfiguration();
        cfg.load();

        collectTallGrass = cfg.getBoolean("collect-tall-grass", true);
        collectDeadBush = cfg.getBoolean("collect-dead-bush", true);
        collectCobweb = cfg.getBoolean("collect-cobweb", true);
        fixTallGrassInventory = cfg.getBoolean("fix-tall-grass-inventory", true);

        cfg.setProperty("collect-tall-grass", collectTallGrass);
        cfg.setProperty("collect-dead-bush", collectDeadBush);
        cfg.setProperty("collect-cobweb", collectCobweb);
        cfg.setProperty("fix-tall-grass-inventory", fixTallGrassInventory);
        cfg.save();

        if (fixTallGrassInventory) {
            swapTallGrassItem();
        }

        PluginManager pm = getServer().getPluginManager();
        PlayerListener pl = new Players();
        // Monitor: protection plugins have already decided, the break will happen
        pm.registerEvent(Event.Type.BLOCK_BREAK, new Blocks(), Event.Priority.Monitor, this);
        pm.registerEvent(Event.Type.ITEM_SPAWN, new Entities(), Event.Priority.Highest, this);
        pm.registerEvent(Event.Type.PLAYER_PICKUP_ITEM, pl, Event.Priority.Monitor, this);
        pm.registerEvent(Event.Type.PACKET_RECEIVED, new Packets(), Event.Priority.Highest, this);

        LOG.info("[Clippings] Enabled. Tall grass: " + onOff(collectTallGrass)
                + ", dead bush: " + onOff(collectDeadBush)
                + ", cobweb: " + onOff(collectCobweb)
                + ", tall grass inventory fix: " + onOff(itemSwapped));
    }

    public void onDisable() {
        restoreTallGrassItem();
        pending = null;
    }

    private static String onOff(boolean b) {
        return b ? "on" : "off";
    }

    // ================================================================== item swap

    /**
     * Replaces the vanilla ItemBlock of block 31 with ItemTallGrass. Only the
     * Java object in memory changes, nothing different is written to the world.
     */
    private void swapTallGrassItem() {
        Item current = Item.byId[LONG_GRASS];

        if (current != null && current.getClass() != ItemBlock.class
                && !current.getClass().getName().equals(ItemTallGrass.class.getName())) {
            LOG.warning("[Clippings] Item 31 was already replaced by "
                    + current.getClass().getName() + ". Tall grass inventory fix skipped.");
            return;
        }

        // A leftover ItemTallGrass from an old class loader (e.g. a crash during
        // /reload) is not worth keeping: restore a fresh vanilla ItemBlock later.
        originalTallGrassItem = (current != null && current.getClass() == ItemBlock.class) ? current : null;

        Item.byId[LONG_GRASS] = null; // avoids the "CONFLICT @ -225" message
        new ItemTallGrass(LONG_GRASS - 256); // registers itself in Item.byId
        itemSwapped = true;
    }

    private void restoreTallGrassItem() {
        if (!itemSwapped) return;

        Item.byId[LONG_GRASS] = null;
        if (originalTallGrassItem != null) {
            Item.byId[LONG_GRASS] = originalTallGrassItem;
        } else {
            new ItemBlock(LONG_GRASS - 256); // same as Block's static init
        }
        originalTallGrassItem = null;
        itemSwapped = false;
    }

    // ================================================================== shears

    private boolean collects(int id) {
        return (id == LONG_GRASS && collectTallGrass)
                || (id == DEAD_BUSH && collectDeadBush)
                || (id == WEB && collectCobweb);
    }

    /** The drop vanilla would spawn for this block, -1 if none. */
    private static int vanillaDropId(int id) {
        if (id == LONG_GRASS) return SEEDS;
        if (id == WEB) return STRING;
        return -1;
    }

    /**
     * Damages the shears in hand by 1, the same way vanilla does it for leaves.
     * Breaks and removes them when they are used up.
     */
    private static void damageShears(Player p) {
        EntityPlayer h = ((CraftPlayer) p).getHandle();
        net.minecraft.server.ItemStack s = h.G();
        if (s == null || s.id != SHEARS) return;

        s.damage(1, h);
        if (s.count <= 0) {
            s.a(h);
            h.H(); // removes the broken item from the hand
        }
    }

    private class Blocks extends BlockListener {
        public void onBlockBreak(BlockBreakEvent e) {
            if (e.isCancelled()) return;

            Player p = e.getPlayer();
            ItemStack hand = p.getItemInHand();
            if (hand == null || hand.getTypeId() != SHEARS) return;

            Block b = e.getBlock();
            int id = b.getTypeId();
            if (!collects(id)) return;
            if (!p.hasPermission("clippings.use")) return;

            // remember the vanilla drop, it is spawned right after this event
            int vanillaDrop = vanillaDropId(id);
            if (vanillaDrop != -1) {
                PendingBreak pb = new PendingBreak();
                pb.world = b.getWorld();
                pb.x = b.getX();
                pb.y = b.getY();
                pb.z = b.getZ();
                pb.vanillaDropId = vanillaDrop;
                pending = pb;
                getServer().getScheduler().scheduleSyncDelayedTask(Clippings.this, new Runnable() {
                    public void run() {
                        pending = null;
                    }
                }, 1L);
            }

            // grass keeps its variant (0 shrub, 1 grass, 2 fern), the others have none
            short data = (id == LONG_GRASS) ? (short) b.getData() : 0;
            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(id, 1, data));

            // vanilla already damages shears on cobwebs (ItemShears)
            if (id != WEB) {
                damageShears(p);
            }
        }
    }

    // ================================================================== drops

    private class Entities extends EntityListener {
        public void onItemSpawn(ItemSpawnEvent e) {
            PendingBreak pb = pending;
            if (pb == null || e.isCancelled()) return;
            if (!(e.getEntity() instanceof org.bukkit.entity.Item)) return;

            ItemStack stack = ((org.bukkit.entity.Item) e.getEntity()).getItemStack();
            if (stack == null || stack.getTypeId() != pb.vanillaDropId) return;

            Location l = e.getLocation();
            if (l.getWorld() != pb.world) return;
            if (l.getBlockX() != pb.x || l.getBlockY() != pb.y || l.getBlockZ() != pb.z) return;

            e.setCancelled(true);
        }
    }

    // ================================================================== client sync

    private class Players extends PlayerListener {
        /**
         * The vanilla client does not know that item 31 has subtypes and may show
         * grass and fern merged for a moment. Resend the inventory to be safe.
         */
        public void onPlayerPickupItem(PlayerPickupItemEvent e) {
            if (e.isCancelled() || !itemSwapped) return;
            if (e.getItem().getItemStack().getTypeId() != LONG_GRASS) return;

            final Player p = e.getPlayer();
            getServer().getScheduler().scheduleSyncDelayedTask(Clippings.this, new Runnable() {
                public void run() {
                    if (p.isOnline()) p.updateInventory();
                }
            }, 1L);
        }
    }

    private static boolean isTallGrass(net.minecraft.server.ItemStack s) {
        return s != null && s.id == LONG_GRASS;
    }

    /**
     * Inventory clicks with tall grass.
     *
     * The client thinks grass and fern are the same item and merges them when
     * clicking, the server keeps them apart. Normally the server accepts a click
     * when the clicked slot looked the same on both sides, and then marks its
     * own result as already known by the client, so nothing gets corrected.
     *
     * When tall grass is in the clicked slot or on the cursor, the expected item
     * in the packet is set to something that never matches. The server still
     * handles the click normally, but then takes the vanilla "rejected" path:
     * it sends the whole window and the cursor again, so the client shows
     * exactly what the server has.
     */
    private class Packets extends PacketListener {
        public void onPacketReceived(PacketReceivedEvent e) {
            if (!itemSwapped || e.isCancelled()) return;
            if (!(e.getPacket() instanceof Packet102WindowClick)) return;
            if (!(e.getPlayer() instanceof CraftPlayer)) return;

            Packet102WindowClick click = (Packet102WindowClick) e.getPacket();
            EntityPlayer h = ((CraftPlayer) e.getPlayer()).getHandle();
            Container c = h.activeContainer;
            if (c == null || c.windowId != click.a) return;

            boolean involved = isTallGrass(h.inventory.j()); // item on the cursor
            if (!involved && click.b >= 0 && click.b < c.e.size()) {
                involved = isTallGrass(((Slot) c.e.get(click.b)).getItem());
            }

            if (involved) {
                click.e = new net.minecraft.server.ItemStack(LONG_GRASS, -1, -1);
            }
        }
    }
}
