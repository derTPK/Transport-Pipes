package de.robotricker.transportpipes.duct;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.config.GeneralConf;
import de.robotricker.transportpipes.duct.manager.DuctManager;
import de.robotricker.transportpipes.duct.manager.GlobalDuctManager;
import de.robotricker.transportpipes.duct.types.DuctType;
import de.robotricker.transportpipes.inventory.DuctSettingsInventory;
import de.robotricker.transportpipes.items.ItemService;
import de.robotricker.transportpipes.location.BlockLocation;
import de.robotricker.transportpipes.location.TPDirection;
import net.querz.nbt.tag.CompoundTag;

public abstract class Duct {

    protected GlobalDuctManager globalDuctManager;
    protected DuctSettingsInventory settingsInv;
    private DuctType ductType;
    private BlockLocation blockLoc;
    private World world;
    private int chunkX;
    private int chunkZ;
    private ConcurrentHashMap<TPDirection, Duct> connectedDucts;
    private List<TPDirection> blockedConnections;
    private BlockData obfuscatedWith;

    public Duct(DuctType ductType, BlockLocation blockLoc, World world, Chunk chunk, DuctSettingsInventory settingsInv, GlobalDuctManager globalDuctManager) {
        this.ductType = ductType;
        this.blockLoc = blockLoc;
        this.world = world;
        chunkX = chunk.getX();
        chunkZ = chunk.getZ();
        this.connectedDucts = new ConcurrentHashMap<TPDirection, Duct>();
        this.blockedConnections = new ArrayList<TPDirection>();
        this.settingsInv = settingsInv;
        this.globalDuctManager = globalDuctManager;
    }

    public void initSettingsInv(TransportPipes transportPipes) {
        if (settingsInv != null) {
            Bukkit.getPluginManager().registerEvents(settingsInv, transportPipes);
            settingsInv.setDuct(this);
            settingsInv.create();
        }
    }

    public DuctSettingsInventory getSettingsInv() {
        return settingsInv;
    }

    public void notifyClick(Player p, boolean shift) {
        if (settingsInv != null)
            settingsInv.openInv(p);
    }

    public DuctType getDuctType() {
        return ductType;
    }

    public BlockLocation getBlockLoc() {
        return blockLoc;
    }

    public World getWorld() {
        return world;
    }

    public boolean isInLoadedChunk() {
        return ((CraftWorld) world).getHandle().getChunkProvider().isLoaded(chunkX, chunkZ);
    }

    public void notifyConnectionChange() {
        if (settingsInv != null) {
            settingsInv.populate();
        }
    }

    public BlockData obfuscatedWith() {
        return obfuscatedWith;
    }

    public void obfuscatedWith(BlockData obfuscatedWith) {
        this.obfuscatedWith = obfuscatedWith;
    }

    public void tick(boolean bigTick, TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, GeneralConf generalConf) {

    }

    public void postTick(boolean bigTick, TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, GeneralConf generalConf) {

    }

    public void syncBigTick(DuctManager<? extends Duct> ductManager) {

    }

    public ConcurrentHashMap<TPDirection, Duct> getDuctConnections() {
        return connectedDucts;
    }

    public TreeSet<TPDirection> getAllConnections() {
        return new TreeSet<>(getDuctConnections().keySet());
    }
    
    public List<TPDirection> getBlockedConnections() {
        return blockedConnections;
    }

    public Material getBreakParticleData() {
        return null;
    }

    /**
     * just for the purpose of dropping inside items or other baseDuctType specific stuff
     */
    public List<ItemStack> destroyed(TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, Player destroyer) {
        List<ItemStack> dropItems = new ArrayList<>();
        if (destroyer == null || destroyer.getGameMode() != GameMode.CREATIVE) {
            dropItems.add(getDuctType().getBaseDuctType().getItemManager().getClonedItem(getDuctType()));
        }

        if (settingsInv != null) {
            settingsInv.closeForAllPlayers(transportPipes);
        }

        //break particles
        if (destroyer != null && getBreakParticleData() != null) {
            transportPipes.runTaskSync(() -> destroyer.getWorld().spawnParticle(Particle.ITEM_CRACK, getBlockLoc().getX() + 0.5f, getBlockLoc().getY() + 0.5f, getBlockLoc().getZ() + 0.5f, 30, 0.25f, 0.25f, 0.25f, 0.05f, new ItemStack(getBreakParticleData())));
        }

        return dropItems;
    }

    public void saveToNBTTag(CompoundTag compoundTag, ItemService itemService) {
        if (obfuscatedWith != null) {
            compoundTag.putString("obfuscatedWith", obfuscatedWith.getAsString());
        }
    }

    public void loadFromNBTTag(CompoundTag compoundTag, ItemService itemService) {
        if (compoundTag.containsKey("obfuscatedWith")) {
            obfuscatedWith = Bukkit.createBlockData(compoundTag.getString("obfuscatedWith"));
            // replace barrier block with real obfuscation block
            if (getBlockLoc().toBlock(getWorld()).getType() == Material.BARRIER) {
                getBlockLoc().toBlock(getWorld()).setBlockData(obfuscatedWith);
            }
        }
    }

}
