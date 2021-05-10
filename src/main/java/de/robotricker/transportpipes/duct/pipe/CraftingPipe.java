package de.robotricker.transportpipes.duct.pipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import de.robotricker.transportpipes.TransportPipes;
import de.robotricker.transportpipes.config.GeneralConf;
import de.robotricker.transportpipes.duct.Duct;
import de.robotricker.transportpipes.duct.manager.DuctManager;
import de.robotricker.transportpipes.duct.manager.GlobalDuctManager;
import de.robotricker.transportpipes.duct.manager.PipeManager;
import de.robotricker.transportpipes.duct.pipe.filter.ItemData;
import de.robotricker.transportpipes.duct.pipe.filter.ItemDistributorService;
import de.robotricker.transportpipes.duct.pipe.items.PipeItem;
import de.robotricker.transportpipes.duct.types.DuctType;
import de.robotricker.transportpipes.inventory.DuctSettingsInventory;
import de.robotricker.transportpipes.items.ItemService;
import de.robotricker.transportpipes.location.BlockLocation;
import de.robotricker.transportpipes.location.TPDirection;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.StringTag;

public class CraftingPipe extends Pipe {

    private ItemData[] recipeItems;
    private Recipe recipe;
    private TPDirection outputDir;
    private List<ItemStack> cachedItems;
    private List<RecipeChoice> necessaryIngredients;

    public CraftingPipe(DuctType ductType, BlockLocation blockLoc, World world, Chunk chunk, DuctSettingsInventory settingsInv, GlobalDuctManager globalDuctManager, ItemDistributorService itemDistributor) {
        super(ductType, blockLoc, world, chunk, settingsInv, globalDuctManager, itemDistributor);
        recipeItems = new ItemData[9];
        outputDir = null;
        cachedItems = new ArrayList<>();
        necessaryIngredients = new ArrayList<>();
    }

    @Override
    public void tick(boolean bigTick, TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, GeneralConf generalConf) {
        super.tick(bigTick, transportPipes, ductManager, generalConf);
        if (bigTick) {
            performCrafting((PipeManager) ductManager, transportPipes);
        }
    }

    @Override
    protected Map<TPDirection, Integer> calculateItemDistribution(PipeItem pipeItem, TPDirection movingDir, List<TPDirection> dirs, TransportPipes transportPipes) {
        ItemStack overflow = addCachedItem(pipeItem.getItem(), transportPipes);
        if (overflow != null) {
            transportPipes.runTaskSync(() -> getWorld().dropItem(getBlockLoc().toLocation(getWorld()), overflow));
        }
        return null;
    }

    public void performCrafting(PipeManager pipeManager, TransportPipes transportPipes) {
        if (outputDir == null || recipe == null) {
            return;
        }

        // Create copy of the cached items
        List<ItemStack> cachedItems = new ArrayList<>();
        for (ItemStack cachedItem : this.cachedItems) {
            cachedItems.add(cachedItem.clone());
        }

        //iterate needed ingredients
        int neededIngredientsCount = necessaryIngredients.size();
        Iterator<RecipeChoice> neededIngredientsIt = necessaryIngredients.iterator();
        while (neededIngredientsIt.hasNext()) {
            RecipeChoice neededIngredient = neededIngredientsIt.next();

            //iterate cached items
            for (int i = 0; i < cachedItems.size(); i++) {
                if (neededIngredient.test(cachedItems.get(i))) {
                    if (cachedItems.get(i).getAmount() > 1) {
                        cachedItems.get(i).setAmount(cachedItems.get(i).getAmount() - 1);
                    } else {
                        cachedItems.remove(i);
                    }
                    neededIngredientsCount--;
                    break;
                }
            }
        }

        if (neededIngredientsCount == 0) {
            // update real cachedItems list
            this.cachedItems = cachedItems;

            transportPipes.runTaskSync(() -> {
                settingsInv.save(null);
                settingsInv.populate();
            });

            // output result item
            ItemStack resultItem = recipe.getResult();
            PipeItem pipeItem = new PipeItem(resultItem.clone(), getWorld(), getBlockLoc(), outputDir);
            pipeItem.getRelativeLocation().set(0.5d, 0.5d, 0.5d);
            pipeItem.resetOldRelativeLocation();
            pipeManager.spawnPipeItem(pipeItem);
            pipeManager.putPipeItemInPipe(pipeItem);

        }
    }

    public int spaceForItem(ItemData data) {
        int space = 0;

        for (int i = 0; i < 9; i++) {
            if (i >= cachedItems.size()) {
                space += data.toItemStack().getMaxStackSize();
            } else {
                ItemStack item = cachedItems.get(i);
                if (item.isSimilar(data.toItemStack()) && item.getAmount() < item.getMaxStackSize()) {
                    space += item.getMaxStackSize() - item.getAmount();
                }
            }
        }
        return space;
    }

    public ItemData[] getRecipeItems() {
        return recipeItems;
    }

    public TPDirection getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(TPDirection outputDir) {
        this.outputDir = outputDir;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
        updateIngredients(this.recipe);
    }

    private  void updateIngredients(Recipe recipe){
        // Collect needed ingredients from recipe
        necessaryIngredients.clear();
        if (recipe instanceof ShapelessRecipe) {
            necessaryIngredients.addAll(((ShapelessRecipe) recipe).getChoiceList());
        } else if (recipe instanceof ShapedRecipe) {
            Map<Character, Integer> charCounts = new HashMap<>();
            for (String row : ((ShapedRecipe) recipe).getShape()) {
                for (char c : row.toCharArray()) {
                    charCounts.put(c, charCounts.getOrDefault(c, 0) + 1);
                }
            }
            for (Character c : charCounts.keySet()) {
                RecipeChoice ingredientChoice = ((ShapedRecipe) recipe).getChoiceMap().get(c);
                if (ingredientChoice != null) {
                    necessaryIngredients.addAll(Collections.nCopies(charCounts.get(c), ingredientChoice));
                }
            }
        }
    }

    public List<ItemStack> getCachedItems() {
        return cachedItems;
    }

    public ItemStack addCachedItem(ItemStack item, TransportPipes transportPipes) {
        for (ItemStack cachedItem : cachedItems) {
            if (cachedItem.isSimilar(item)) {
                int cachedItemAmount = cachedItem.getAmount();
                cachedItem.setAmount(Math.min(cachedItem.getMaxStackSize(), cachedItemAmount + item.getAmount()));
                int overflow = cachedItemAmount + item.getAmount() - cachedItem.getMaxStackSize();
                if (overflow > 0) {
                    item.setAmount(overflow);
                } else {
                    item = null;
                    break;
                }
            }
        }
        if (cachedItems.size() < 9 && item != null) {
            cachedItems.add(item);
            item = null;
        }

        transportPipes.runTaskSync(() -> {
            settingsInv.save(null);
            settingsInv.populate();
        });
        return item;
    }

    public void updateOutputDirection(boolean cycle) {
        TPDirection oldOutputDirection = getOutputDir();
        Set<TPDirection> connections = getAllConnections();
        if (connections.isEmpty()) {
            outputDir = null;
        } else if (cycle || outputDir == null || !connections.contains(outputDir)) {
            do {
                if (outputDir == null) {
                    outputDir = TPDirection.NORTH;
                } else {
                    outputDir = outputDir.next();
                }
            } while (!connections.contains(outputDir));
        }
        if (oldOutputDirection != outputDir) {
            settingsInv.populate();
        }
    }

    @Override
    public void notifyConnectionChange() {
        super.notifyConnectionChange();
        updateOutputDirection(false);
    }

    @Override
    public Material getBreakParticleData() {
        return Material.CRAFTING_TABLE;
    }

    @Override
    public void saveToNBTTag(CompoundTag compoundTag, ItemService itemService) {
        super.saveToNBTTag(compoundTag, itemService);

        compoundTag.putInt("outputDir", outputDir != null ? outputDir.ordinal() : -1);

        ListTag<StringTag> recipeItemsListTag = new ListTag<>(StringTag.class);
        for (int i = 0; i < 9; i++) {
            ItemData itemData = recipeItems[i];
            if (itemData == null) {
                recipeItemsListTag.add(new StringTag());
            } else {
                recipeItemsListTag.addString(itemService.serializeItemStack(itemData.toItemStack()));
            }
        }
        compoundTag.put("recipeItems", recipeItemsListTag);

        ListTag<StringTag> cachedItemsListTag = new ListTag<>(StringTag.class);
        for (int i = 0; i < cachedItems.size(); i++) {
            ItemStack itemStack = cachedItems.get(i);
            cachedItemsListTag.addString(itemService.serializeItemStack(itemStack));
        }
        compoundTag.put("cachedItems", cachedItemsListTag);

    }

    @Override
    public void loadFromNBTTag(CompoundTag compoundTag, ItemService itemService) {
        super.loadFromNBTTag(compoundTag, itemService);

        outputDir = compoundTag.getInt("outputDir") != -1 ? TPDirection.values()[compoundTag.getInt("outputDir")] : null;

        ListTag<StringTag> recipeItemsListTag = compoundTag.getListTag("recipeItems").asStringTagList();
        for (int i = 0; i < 9; i++) {
            if (i >= recipeItemsListTag.size()) {
                recipeItems[i] = null;
                continue;
            }
            ItemStack deserialized = itemService.deserializeItemStack(recipeItemsListTag.get(i).getValue());
            recipeItems[i] = deserialized != null ? new ItemData(deserialized) : null;
        }

        cachedItems.clear();
        ListTag<StringTag> cachedItemsListTag = compoundTag.getListTag("cachedItems").asStringTagList();
        for (int i = 0; i < cachedItemsListTag.size(); i++) {
            ItemStack deserialized = itemService.deserializeItemStack(cachedItemsListTag.get(i).getValue());
            if (deserialized != null)
                cachedItems.add(deserialized);
        }

        settingsInv.populate();
        settingsInv.save(null);
    }

    @Override
    public List<ItemStack> destroyed(TransportPipes transportPipes, DuctManager<? extends Duct> ductManager, Player destroyer) {
        List<ItemStack> items = super.destroyed(transportPipes, ductManager, destroyer);
        for (int i = 0; i < 9; i++) {
            ItemData id = recipeItems[i];
            if (id != null) {
                items.add(id.toItemStack().clone());
            }
        }
        for (ItemStack cachedItem : cachedItems) {
            items.add(cachedItem.clone());
        }
        return items;
    }
}
