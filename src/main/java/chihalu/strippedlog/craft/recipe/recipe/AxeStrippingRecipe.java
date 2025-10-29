package chihalu.strippedlog.craft.recipe.recipe;

import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SpecialCraftingRecipe.SpecialRecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public class AxeStrippingRecipe extends SpecialCraftingRecipe {
    public static final SpecialRecipeSerializer<AxeStrippingRecipe> SERIALIZER =
            new SpecialRecipeSerializer<>(AxeStrippingRecipe::new);

    private static final Map<Block, Block> STRIPPABLES = Map.ofEntries(
            Map.entry(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG),
            Map.entry(Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD),
            Map.entry(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG),
            Map.entry(Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_WOOD),
            Map.entry(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG),
            Map.entry(Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_WOOD),
            Map.entry(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG),
            Map.entry(Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_WOOD),
            Map.entry(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG),
            Map.entry(Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_WOOD),
            Map.entry(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG),
            Map.entry(Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD),
            Map.entry(Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG),
            Map.entry(Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_WOOD),
            Map.entry(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG),
            Map.entry(Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_WOOD),
            Map.entry(Blocks.PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG),
            Map.entry(Blocks.PALE_OAK_WOOD, Blocks.STRIPPED_PALE_OAK_WOOD),
            Map.entry(Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK),
            Map.entry(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM),
            Map.entry(Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_HYPHAE),
            Map.entry(Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM),
            Map.entry(Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE));

    public AxeStrippingRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return !this.findResult(input).isEmpty();
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack result = this.findResult(input);
        return result.copy();
    }

    @Override
    public DefaultedList<ItemStack> getRecipeRemainders(CraftingRecipeInput input) {
        DefaultedList<ItemStack> remainders = DefaultedList.ofSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            Item item = stack.getItem();
            if (stack.isIn(ItemTags.AXES)) {
                ItemStack tool = stack.copy();
                tool.setCount(1);
                if (tool.isDamageable() && shouldDamage(tool)) {
                    int newDamage = tool.getDamage() + 1;
                    if (newDamage >= tool.getMaxDamage()) {
                        continue;
                    }
                    tool.setDamage(newDamage);
                }
                remainders.set(i, tool);
            } else {
                ItemStack remainder = item.getRecipeRemainder();
                if (!remainder.isEmpty()) {
                    remainders.set(i, remainder.copy());
                }
            }
        }

        return remainders;
    }

    @Override
    public SpecialRecipeSerializer<AxeStrippingRecipe> getSerializer() {
        return SERIALIZER;
    }

    private ItemStack findResult(CraftingRecipeInput input) {
        Block strippedBlock = null;
        ItemStack logStack = ItemStack.EMPTY;
        boolean foundAxe = false;

        List<ItemStack> stacks = input.getStacks();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.isIn(ItemTags.AXES)) {
                if (foundAxe) {
                    return ItemStack.EMPTY;
                }
                foundAxe = true;
                continue;
            }

            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                return ItemStack.EMPTY;
            }

            Block block = blockItem.getBlock();
            Block strippedCandidate = STRIPPABLES.get(block);
            if (strippedCandidate == null) {
                return ItemStack.EMPTY;
            }

            if (!logStack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            strippedBlock = strippedCandidate;
            logStack = stack;
        }

        if (strippedBlock == null || logStack.isEmpty() || !foundAxe) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(strippedBlock.asItem());
    }

    private boolean shouldDamage(ItemStack stack) {
        return true;
    }
}
