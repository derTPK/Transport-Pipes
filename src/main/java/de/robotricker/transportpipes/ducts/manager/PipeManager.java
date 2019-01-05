package de.robotricker.transportpipes.ducts.manager;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.container.TPContainer;
import de.robotricker.transportpipes.ducts.Duct;
import de.robotricker.transportpipes.ducts.DuctRegister;
import de.robotricker.transportpipes.ducts.pipe.ExtractionPipe;
import de.robotricker.transportpipes.ducts.pipe.Pipe;
import de.robotricker.transportpipes.ducts.pipe.items.PipeItem;
import de.robotricker.transportpipes.ducts.types.BaseDuctType;
import de.robotricker.transportpipes.ducts.types.pipetype.ColoredPipeType;
import de.robotricker.transportpipes.ducts.types.pipetype.PipeType;
import de.robotricker.transportpipes.location.BlockLocation;
import de.robotricker.transportpipes.location.TPDirection;
import de.robotricker.transportpipes.protocol.ProtocolService;
import de.robotricker.transportpipes.utils.Constants;
import de.robotricker.transportpipes.utils.WorldUtils;

public class PipeManager extends DuctManager<Pipe> {

    private static final long EXTRACT_TICK_COUNT = 10;

    /**
     * ThreadSafe
     **/
    private Map<World, Map<BlockLocation, TPContainer>> containers;

    /**
     * THREAD-SAFE
     */
    private Map<Player, Set<PipeItem>> playerItems;

    private long tickCount;

    @Inject
    public PipeManager(TransportPipes transportPipes, DuctRegister ductRegister, GlobalDuctManager globalDuctManager, ProtocolService protocolService) {
        super(transportPipes, ductRegister, globalDuctManager, protocolService);
        playerItems = Collections.synchronizedMap(new HashMap<>());
        containers = Collections.synchronizedMap(new HashMap<>());
        tickCount = 0;
    }

    public Map<World, Map<BlockLocation, TPContainer>> getContainers() {
        return containers;
    }

    public Map<BlockLocation, TPContainer> getContainers(World world) {
        return containers.computeIfAbsent(world, v -> Collections.synchronizedMap(new TreeMap<>()));
    }

    public TPContainer getContainerAtLoc(World world, BlockLocation blockLoc) {
        Map<BlockLocation, TPContainer> containerMap = getContainers(world);
        return containerMap.get(blockLoc);
    }

    public TPContainer getContainerAtLoc(Location location) {
        return getContainerAtLoc(location.getWorld(), new BlockLocation(location));
    }

    @Override
    public void updateNonDuctConnections(Duct duct) {
        Pipe pipe = (Pipe) duct;
        pipe.getContainerConnections().clear();
        for (TPDirection tpDir : TPDirection.values()) {
            TPContainer neighborContainer = getContainerAtLoc(pipe.getWorld(), pipe.getBlockLoc().getNeighbor(tpDir));
            if (neighborContainer != null) {
                pipe.getContainerConnections().put(tpDir, neighborContainer);
            }
        }
    }

    @Override
    public void registerDuctTypes() {
        PipeType pipeType;
        BaseDuctType<Pipe> pipeBaseDuctType = ductRegister.baseDuctTypeOf("pipe");

        pipeType = new ColoredPipeType(pipeBaseDuctType, "White", '7', DyeColor.WHITE);
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new ColoredPipeType(pipeBaseDuctType, "Blue", '1', DyeColor.BLUE);
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new ColoredPipeType(pipeBaseDuctType, "Red", '4', DyeColor.RED);
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new ColoredPipeType(pipeBaseDuctType, "Yellow", 'e', DyeColor.YELLOW);
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new ColoredPipeType(pipeBaseDuctType, "Green", '2', DyeColor.GREEN);
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new ColoredPipeType(pipeBaseDuctType, "Black", '8', DyeColor.BLACK);
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new PipeType(pipeBaseDuctType, "Golden", '6');
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new PipeType(pipeBaseDuctType, "Iron", '7');
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new PipeType(pipeBaseDuctType, "Ice", 'b');
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new PipeType(pipeBaseDuctType, "Void", '5');
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new PipeType(pipeBaseDuctType, "Extraction", 'd');
        pipeBaseDuctType.registerDuctType(pipeType);
        pipeType = new PipeType(pipeBaseDuctType, "Crafting", 'e');
        pipeBaseDuctType.registerDuctType(pipeType);

        //connect correctly
        pipeBaseDuctType.ductTypeOf("White").connectToAll();
        pipeBaseDuctType.ductTypeOf("Blue").connectToAll().disconnectFromClasses(ColoredPipeType.class).connectTo("White", "Blue");
        pipeBaseDuctType.ductTypeOf("Red").connectToAll().disconnectFromClasses(ColoredPipeType.class).connectTo("White", "Red");
        pipeBaseDuctType.ductTypeOf("Yellow").connectToAll().disconnectFromClasses(ColoredPipeType.class).connectTo("White", "Yellow");
        pipeBaseDuctType.ductTypeOf("Green").connectToAll().disconnectFromClasses(ColoredPipeType.class).connectTo("White", "Green");
        pipeBaseDuctType.ductTypeOf("Black").connectToAll().disconnectFromClasses(ColoredPipeType.class).connectTo("White", "Black");
        pipeBaseDuctType.ductTypeOf("Golden").connectToAll();
        pipeBaseDuctType.ductTypeOf("Iron").connectToAll();
        pipeBaseDuctType.ductTypeOf("Ice").connectToAll();
        pipeBaseDuctType.ductTypeOf("Void").connectToAll();
        pipeBaseDuctType.ductTypeOf("Extraction").connectToAll();
        pipeBaseDuctType.ductTypeOf("Crafting").connectToAll();
    }

    @Override
    public void tick() {
        tickCount++;
        tickCount %= EXTRACT_TICK_COUNT;
        boolean extract = tickCount == 0;

        if (extract) {
            transportPipes.runTaskSync(() -> {
                Set<World> worlds = globalDuctManager.getDucts().keySet();
                synchronized (globalDuctManager.getDucts()) {
                    for (World world : worlds) {
                        Map<BlockLocation, Duct> ductMap = globalDuctManager.getDucts().get(world);
                        if (ductMap != null) {
                            for (Duct duct : ductMap.values()) {
                                if (duct instanceof ExtractionPipe && duct.isInLoadedChunk()) {
                                    Pipe pipe = (Pipe) duct;
                                    for (TPDirection dir : TPDirection.values()) {
                                        TPContainer container = getContainerAtLoc(pipe.getWorld(), pipe.getBlockLoc().getNeighbor(dir));
                                        if (container != null) {
                                            ItemStack item = container.extractItem(dir, 1);
                                            if (item != null) {
                                                PipeItem pipeItem = new PipeItem(item, pipe.getWorld(), pipe.getBlockLoc(), dir.getOpposite());
                                                createPipeItem(pipeItem);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // try to put items which are in unloadedItems list inside containers into the container as soon as the container gets loaded
                worlds = getContainers().keySet();
                synchronized (getContainers()) {
                    for (World world : worlds) {
                        Map<BlockLocation, TPContainer> containerMap = getContainers().get(world);
                        if (containerMap != null) {
                            for (BlockLocation blockLoc : containerMap.keySet()) {
                                TPContainer container = containerMap.get(blockLoc);
                                if (container != null) {
                                    if (container.isInLoadedChunk()) {
                                        synchronized (container.getUnloadedItems()) {
                                            if (!container.getUnloadedItems().isEmpty()) {
                                                PipeItem item = container.getUnloadedItems().remove(container.getUnloadedItems().size() - 1);
                                                ItemStack overflow = container.insertItem(item.getMovingDir(), item.getItem());
                                                if (overflow != null) {
                                                    world.dropItem(blockLoc.toLocation(world), overflow);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }

        Set<World> worlds = globalDuctManager.getDucts().keySet();
        synchronized (globalDuctManager.getDucts()) {
            for (World world : worlds) {
                Map<BlockLocation, Duct> ductMap = globalDuctManager.getDucts().get(world);
                if (ductMap != null) {
                    for (Duct duct : ductMap.values()) {
                        if (duct instanceof Pipe && duct.isInLoadedChunk()) {
                            Pipe pipe = (Pipe) duct;
                            // activate pipeItems which are in futureItems
                            synchronized (pipe.getFutureItems()) {
                                Iterator<PipeItem> itemIt = pipe.getFutureItems().iterator();
                                while (itemIt.hasNext()) {
                                    PipeItem futureItem = itemIt.next();
                                    pipe.getItems().add(futureItem);
                                    itemIt.remove();
                                }
                            }
                            // activate pipeItems which are in unloadedItems one by one
                            if (extract) {
                                synchronized (pipe.getUnloadedItems()) {
                                    if (!pipe.getUnloadedItems().isEmpty()) {
                                        PipeItem unloadedItem = pipe.getUnloadedItems().remove(pipe.getUnloadedItems().size() - 1);
                                        pipe.getItems().add(unloadedItem);
                                    }
                                }
                            }
                        }
                    }
                    //normal tick and item update
                    for (Duct duct : ductMap.values()) {
                        if (duct instanceof Pipe && duct.isInLoadedChunk()) {
                            duct.tick(transportPipes, this, globalDuctManager);
                        }
                    }
                }
            }
        }

    }

    public Set<PipeItem> getPlayerPipeItems(Player player) {
        return playerItems.computeIfAbsent(player, p -> Collections.synchronizedSet(new HashSet<>()));
    }

    public void createPipeItem(PipeItem pipeItem) {
        Pipe pipeAtBlockLoc = (Pipe) globalDuctManager.getDuctAtLoc(pipeItem.getWorld(), pipeItem.getBlockLoc());
        if (pipeAtBlockLoc == null) {
            throw new IllegalStateException("pipe item can't be created because on the given location is no pipe");
        }

        pipeAtBlockLoc.putPipeItem(pipeItem);
        List<Player> playerList = WorldUtils.getPlayerList(pipeItem.getWorld());
        for (Player p : playerList) {
            if (p.getLocation().distance(pipeItem.getBlockLoc().toLocation(pipeItem.getWorld())) <= Constants.DEFAULT_RENDER_DISTANCE) {
                getPlayerPipeItems(p).add(pipeItem);
                protocolService.sendPipeItem(p, pipeItem);
            }
        }
    }

    public void updatePipeItem(PipeItem pipeItem) {
        List<Player> playerList = WorldUtils.getPlayerList(pipeItem.getWorld());
        for (Player p : playerList) {
            if (getPlayerPipeItems(p).contains(pipeItem)) {
                protocolService.updatePipeItem(p, pipeItem);
            }
        }
    }

    public void destroyPipeItem(PipeItem pipeItem) {
        List<Player> playerList = WorldUtils.getPlayerList(pipeItem.getWorld());
        for (Player p : playerList) {
            if (getPlayerPipeItems(p).remove(pipeItem)) {
                protocolService.removePipeItem(p, pipeItem);
            }
        }
    }

    @Override
    public void notifyDuctShown(Duct duct, Player p) {
        super.notifyDuctShown(duct, p);
        Pipe pipe = (Pipe) duct;
        Set<PipeItem> playerPipeItems = getPlayerPipeItems(p);
        for (PipeItem pipeItem : pipe.getItems()) {
            if (playerPipeItems.add(pipeItem)) {
                protocolService.sendPipeItem(p, pipeItem);
            }
        }
    }

    @Override
    public void notifyDuctHidden(Duct duct, Player p) {
        super.notifyDuctHidden(duct, p);
        Pipe pipe = (Pipe) duct;
        Set<PipeItem> playerPipeItems = getPlayerPipeItems(p);
        for (PipeItem pipeItem : pipe.getItems()) {
            if (playerPipeItems.remove(pipeItem)) {
                protocolService.removePipeItem(p, pipeItem);
            }
        }
        for (PipeItem pipeItem : pipe.getFutureItems()) {
            if (playerPipeItems.remove(pipeItem)) {
                protocolService.removePipeItem(p, pipeItem);
            }
        }
        for (PipeItem pipeItem : pipe.getUnloadedItems()) {
            if (playerPipeItems.remove(pipeItem)) {
                protocolService.removePipeItem(p, pipeItem);
            }
        }
    }
}
