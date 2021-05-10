package de.robotricker.transportpipes.duct.pipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.api.TransportPipesContainer;
import de.robotricker.transportpipes.config.GeneralConf;
import de.robotricker.transportpipes.duct.Duct;
import de.robotricker.transportpipes.duct.manager.DuctManager;
import de.robotricker.transportpipes.duct.manager.GlobalDuctManager;
import de.robotricker.transportpipes.duct.manager.PipeManager;
import de.robotricker.transportpipes.duct.pipe.filter.ItemDistributorService;
import de.robotricker.transportpipes.duct.pipe.items.PipeItem;
import de.robotricker.transportpipes.duct.types.DuctType;
import de.robotricker.transportpipes.duct.types.pipetype.PipeType;
import de.robotricker.transportpipes.inventory.DuctSettingsInventory;
import de.robotricker.transportpipes.items.ItemService;
import de.robotricker.transportpipes.location.BlockLocation;
import de.robotricker.transportpipes.location.RelativeLocation;
import de.robotricker.transportpipes.location.TPDirection;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;

public class Pipe extends Duct {

	/**
	 * THREAD-SAFE contains all the items that are inside this pipe and should be updated
	 */
	private final ConcurrentLinkedQueue<PipeItem> items;
	/**
	 * THREAD-SAFE contains all the items that are just put inside this pipe and should be updated and put into the
	 * items list the next tick
	 */
	private final ConcurrentLinkedQueue<PipeItem> futureItems;

	/**
	 * THREAD-SAFE contains all the items that could not be put into the next pipe or container because it is inside an
	 * unloaded chunk. As the next pipe / container gets loaded again, these items get put into it one by one.
	 * <p />
	 * <p />
	 * This means that all of the pipeItems inside this list have got a blockLocation which differs from this pipe's
	 * blockLocation. The blockLocation of one of these pipeItems may be pointing on a container block or on a different
	 * pipe.
	 */
	private final ConcurrentLinkedDeque<PipeItem> unloadedItems;

	ItemDistributorService itemDistributor;
	private ConcurrentHashMap<TPDirection, TransportPipesContainer> connectedContainers;

	public Pipe(DuctType ductType, BlockLocation blockLoc, World world, Chunk chunk, DuctSettingsInventory settingsInv, GlobalDuctManager globalDuctManager, ItemDistributorService itemDistributor) {
		super(ductType, blockLoc, world, chunk, settingsInv, globalDuctManager);
		this.items = new ConcurrentLinkedQueue<>();
		this.futureItems = new ConcurrentLinkedQueue<>();
		this.unloadedItems = new ConcurrentLinkedDeque<>();
		this.itemDistributor = itemDistributor;

		this.connectedContainers = new ConcurrentHashMap<>();
	}

	public ConcurrentHashMap<TPDirection, TransportPipesContainer> getContainerConnections() {
		return connectedContainers;
	}

	@Override
	public TreeSet<TPDirection> getAllConnections() {
	    TreeSet<TPDirection> allConnections = super.getAllConnections();
		allConnections.addAll(getContainerConnections().keySet());
		return allConnections;
	}

	public ConcurrentLinkedQueue<PipeItem> getItems() {
		return items;
	}

	public ConcurrentLinkedQueue<PipeItem> getFutureItems() {
		return futureItems;
	}

	public ConcurrentLinkedDeque<PipeItem> getUnloadedItems() {
		return unloadedItems;
	}

	public void putPipeItem(PipeItem pipeItem) {
		futureItems.add(pipeItem);
	}

	double getPipeItemSpeed() {
		return 0.125d;
	}

	@Override
	public void tick(boolean bigTick, TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, GeneralConf generalConf) {
		super.tick(bigTick, transportPipes, ductManager, generalConf);

        // activate futureItems
        Iterator<PipeItem> futureItemsIt = getFutureItems().iterator();
        outer: while (futureItemsIt.hasNext()) {
            PipeItem futureItem = futureItemsIt.next();
            if (generalConf.getMergePipeItems()) {
                for (PipeItem nextPipeItem : getItems()) {
                    if (nextPipeItem.getItem().isSimilar(futureItem.getItem()) && nextPipeItem.getMovingDir() == futureItem.getMovingDir()) {
                        nextPipeItem.getItem().setAmount(nextPipeItem.getItem().getAmount() + futureItem.getItem().getAmount());
                        int difference = nextPipeItem.getItem().getAmount() - nextPipeItem.getItem().getMaxStackSize();
                        if (difference <= 0) {
                            ((PipeManager) ductManager).despawnPipeItem(futureItem);
                            futureItemsIt.remove();
                            continue outer;
                        }
                        else {
                            nextPipeItem.getItem().setAmount(nextPipeItem.getItem().getMaxStackSize());
                            futureItem.getItem().setAmount(difference);
                        }
                    }
                }
            }
            getItems().add(futureItem);
            futureItemsIt.remove();
        }

		// extract items from unloaded list and put into next pipe
		if (bigTick) {
            if (!getUnloadedItems().isEmpty()) {
                PipeItem unloadedItem = getUnloadedItems().getLast();
                Duct newPipe = globalDuctManager.getDuctAtLoc(getWorld(), unloadedItem.getBlockLoc());
                if (newPipe instanceof Pipe && newPipe.isInLoadedChunk()) {
                    ((Pipe) newPipe).getItems().add(unloadedItem);
                    getUnloadedItems().removeLast();
                }
            }
		}

	}

	@Override
	public void postTick(boolean bigTick, TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, GeneralConf generalConf) {
		super.postTick(bigTick, transportPipes, ductManager, generalConf);
        
		PipeManager pipeManager = (PipeManager) ductManager;
		if (items.size() > generalConf.getMaxItemsPerPipe()) {
			transportPipes.runTaskAsync(() -> {
				globalDuctManager.unregisterDuct(this);
				globalDuctManager.unregisterDuctInRenderSystem(this, true);
				globalDuctManager.updateNeighborDuctsConnections(this);
				globalDuctManager.updateNeighborDuctsInRenderSystems(this, true);
				globalDuctManager.playDuctDestroyActions(this, null);
			}, 0L);
			return;
		}

        List<PipeItem> copiedItems = new ArrayList<PipeItem>(items);
		int itemCount = copiedItems.size();
		for (int i = copiedItems.size() - 1; i >= 0; i--) {
			PipeItem pipeItem = copiedItems.get(i);

			long factor = (long) (getPipeItemSpeed() * RelativeLocation.PRECISION);
			pipeItem.getRelativeLocation().add(pipeItem.getMovingDir().getX() * factor, pipeItem.getMovingDir().getY() * factor, pipeItem.getMovingDir().getZ() * factor);
			pipeManager.updatePipeItemPosition(pipeItem);
			pipeItem.resetOldRelativeLocation();

			if (pipeItem.getRelativeLocation().isEquals(0.5d, 0.5d, 0.5d)) {

				// arrival at middle

				// calculate possible moving directions
				List<TPDirection> possibleMovingDirs = new ArrayList<>(getAllConnections());

				Map<TPDirection, Integer> distribution = calculateItemDistribution(pipeItem, pipeItem.getMovingDir(), possibleMovingDirs, transportPipes);
				if (distribution == null || distribution.isEmpty()) {
					if (distribution != null) {
						transportPipes.runTaskSync(() -> {

                            pipeItem.removeMovedDir(getBlockLoc());
                            Map<TPDirection, Integer> newDistribution = calculateItemDistribution(pipeItem, pipeItem.getMovingDir(), possibleMovingDirs, transportPipes);
                            if (newDistribution.isEmpty()) {
                                pipeItem.setMovingDir(pipeItem.getMovingDir().getOpposite());
                            }
                            else {
                                pipeItem.addMovedDir(getBlockLoc(), pipeItem.getMovingDir().getOpposite());
                            }
						});
					}
					else {
                        items.remove(pipeItem);
                        pipeManager.despawnPipeItem(pipeItem);
                        // drop item
                        //transportPipes.runTaskSync(() -> {
                        //    pipeItem.getWorld().dropItem(pipeItem.getBlockLoc().getNeighbor(pipeItem.getMovingDir()).toLocation(pipeItem.getWorld()), pipeItem.getItem());
                        //});
    					continue;
					}
				}

				ItemStack itemStack = pipeItem.getItem().clone();

				PipeItem tempPipeItem = null;
				BlockLocation location = getBlockLoc();
				for (TPDirection dir : distribution.keySet()) {
					int amount = distribution.get(dir);
					if (tempPipeItem == null) {
						tempPipeItem = pipeItem;
					}
					else {
						tempPipeItem = new PipeItem(itemStack.clone(), getWorld(), location, dir);
					}
					tempPipeItem.getItem().setAmount(amount);
					tempPipeItem.addMovedDir(location, dir);
					tempPipeItem.setMovingDir(dir);
					tempPipeItem.getRelativeLocation().set(0.5d, 0.5d, 0.5d);
					tempPipeItem.resetOldRelativeLocation();
					if (!items.contains(tempPipeItem)) {
						items.add(tempPipeItem);
						pipeManager.spawnPipeItem(tempPipeItem);
					}
				}
			}
			else if (pipeItem.getRelativeLocation().isXEquals(0d) || pipeItem.getRelativeLocation().isYEquals(0d) || pipeItem.getRelativeLocation().isZEquals(0d)
					|| pipeItem.getRelativeLocation().isXEquals(1d) || pipeItem.getRelativeLocation().isYEquals(1d) || pipeItem.getRelativeLocation().isZEquals(1d)) {
				// arrival at end of pipe

				Duct duct = getDuctConnections().get(pipeItem.getMovingDir());
				TransportPipesContainer transportPipesContainer = getContainerConnections().get(pipeItem.getMovingDir());

				if (duct instanceof Pipe) {

					Pipe pipe = (Pipe) duct;
					BlockLocation location = pipe.getBlockLoc();

					// make pipe item ready for next pipe
					pipeItem.setBlockLoc(location);
					if (!pipeItem.hasSourceDir(location)) {
						pipeItem.addSourceDir(location, pipeItem.getMovingDir().getOpposite());
					}
					pipeItem.addMovedDir(location, pipeItem.getMovingDir().getOpposite());
					pipeItem.getRelativeLocation().switchValues();
					pipeItem.resetOldRelativeLocation();

					// remove from current pipe and add to new one
					items.remove(pipeItem);
					if (pipe.isInLoadedChunk()) {
						pipe.putPipeItem(pipeItem);
					}
					else {
						unloadedItems.add(pipeItem);
					}
				}
				else {
					items.remove(pipeItem);
					pipeManager.despawnPipeItem(pipeItem);

					if (transportPipesContainer != null) {

						pipeItem.setBlockLoc(getBlockLoc().getNeighbor(pipeItem.getMovingDir()));
						pipeItem.getRelativeLocation().switchValues();
						pipeItem.resetOldRelativeLocation();

						transportPipes.runTaskSync(() -> {
							if (transportPipesContainer.isInLoadedChunk()) {

								ItemStack overflow = transportPipesContainer.insertItem(pipeItem.getMovingDir(), pipeItem.getItem());
								if (overflow != null) {
									// getWorld().dropItem(getBlockLoc().toLocation(getWorld()), overflow);
									pipeItem.getItem().setAmount(overflow.getAmount());
									pipeItem.setMovingDir(pipeItem.getMovingDir().getOpposite());
									pipeItem.setBlockLoc(this.getBlockLoc());
									pipeItem.getRelativeLocation().switchValues();
									pipeItem.resetOldRelativeLocation();
									pipeManager.spawnPipeItem(pipeItem);
									this.putPipeItem(pipeItem);
								}
							}
							else {
								unloadedItems.add(pipeItem);
							}
						});
					}
					else {
						// drop item
						transportPipes.runTaskSync(() -> {
							pipeItem.getWorld().dropItem(pipeItem.getBlockLoc().getNeighbor(pipeItem.getMovingDir()).toLocation(pipeItem.getWorld()), pipeItem.getItem());
						});
					}
				}
			}

		}

	}

	@Override
	public void syncBigTick(DuctManager<? extends Duct> ductManager) {
		super.syncBigTick(ductManager);

		PipeManager pipeManager = (PipeManager) ductManager;

		// put one of the unloaded items into the container block it belongs to or drop it if there is no longer a
		// container
        if (!getUnloadedItems().isEmpty()) {
            PipeItem unloadedItem = getUnloadedItems().getLast();
            TransportPipesContainer newContainer = pipeManager.getContainerAtLoc(getWorld(), unloadedItem.getBlockLoc());
            if (newContainer != null && newContainer.isInLoadedChunk()) {
                ItemStack overflow = newContainer.insertItem(unloadedItem.getMovingDir(), unloadedItem.getItem());
                getUnloadedItems().removeLast();
                if (overflow != null) {
                    getWorld().dropItem(getBlockLoc().toLocation(getWorld()), overflow);
                }
            }
            else if (newContainer == null && !(globalDuctManager.getDuctAtLoc(getWorld(), unloadedItem.getBlockLoc()) instanceof Pipe)) {
                // nothing there
                getWorld().dropItem(getBlockLoc().toLocation(getWorld()), unloadedItem.getItem());
                getUnloadedItems().removeLast();
            }
        }

	}

	/**
	 * can be overridden to calculate how a pipeItem which arrives at the middle of the pipe should be split and in
	 * which directions theses parts should go. Return null to fully remove the pipeItem and return an empty map to
	 * fully remove the item and additionally drop it in the world.
	 */
	protected Map<TPDirection, Integer> calculateItemDistribution(PipeItem pipeItem, TPDirection movingDir, List<TPDirection> dirs, TransportPipes transportPipes) {
		Map<TPDirection, Integer> absWeights = new HashMap<>();
		dirs.stream().filter(dir -> !dir.equals(movingDir.getOpposite())).forEach(dir -> absWeights.put(dir, 1));
		return itemDistributor.splitPipeItem(pipeItem, absWeights, this);
	}

	@Override
	public List<ItemStack> destroyed(TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, Player destroyer) {
		List<ItemStack> dropItems = super.destroyed(transportPipes, ductManager, destroyer);

        items.forEach(pipeItem -> {
            ((PipeManager) ductManager).despawnPipeItem(pipeItem);
            dropItems.add(pipeItem.getItem());
        });
        items.clear();
        futureItems.forEach(pipeItem -> {
            ((PipeManager) ductManager).despawnPipeItem(pipeItem);
            dropItems.add(pipeItem.getItem());
        });
        futureItems.clear();
        unloadedItems.forEach(pipeItem -> {
            ((PipeManager) ductManager).despawnPipeItem(pipeItem);
            dropItems.add(pipeItem.getItem());
        });
        unloadedItems.clear();

		return dropItems;
	}

	@Override
	public PipeType getDuctType() {
		return (PipeType) super.getDuctType();
	}

	@Override
	public void saveToNBTTag(CompoundTag compoundTag, ItemService itemService) {
		super.saveToNBTTag(compoundTag, itemService);

		List<PipeItem> accumulatedItems = new ArrayList<>();
		accumulatedItems.addAll(getItems());
		accumulatedItems.addAll(getFutureItems());
		List<PipeItem> unloadedItems = new ArrayList<>(getUnloadedItems());

		ListTag<CompoundTag> accumulatedItemsListTag = new ListTag<>(CompoundTag.class);
		ListTag<CompoundTag> unloadedItemsListTag = new ListTag<>(CompoundTag.class);

		for (PipeItem accumulatedItem : accumulatedItems) {
			CompoundTag itemTag = new CompoundTag();
			accumulatedItem.saveToNBTTag(itemTag, itemService);
			accumulatedItemsListTag.add(itemTag);
		}

		for (PipeItem unloadedItem : unloadedItems) {
			CompoundTag itemTag = new CompoundTag();
			unloadedItem.saveToNBTTag(itemTag, itemService);
			unloadedItemsListTag.add(itemTag);
		}

		compoundTag.put("pipeItems", accumulatedItemsListTag);
		compoundTag.put("unloadedPipeItems", unloadedItemsListTag);
	}

	@Override
	public void loadFromNBTTag(CompoundTag compoundTag, ItemService itemService) {
		super.loadFromNBTTag(compoundTag, itemService);

		ListTag<CompoundTag> accumulatedItemsListTag = compoundTag.getListTag("pipeItems").asCompoundTagList();
		ListTag<CompoundTag> unloadedItemsListTag = compoundTag.getListTag("unloadedPipeItems").asCompoundTagList();

		for (CompoundTag itemTag : accumulatedItemsListTag) {
			PipeItem pipeItem = new PipeItem();
			pipeItem.loadFromNBTTag(itemTag, getWorld(), itemService);
			getItems().add(pipeItem);
		}

		for (CompoundTag itemTag : unloadedItemsListTag) {
			PipeItem pipeItem = new PipeItem();
			pipeItem.loadFromNBTTag(itemTag, getWorld(), itemService);
			getUnloadedItems().add(pipeItem);
		}

	}
}
