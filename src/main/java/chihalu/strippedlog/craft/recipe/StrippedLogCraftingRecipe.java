package chihalu.strippedlog.craft.recipe;

import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class StrippedLogCraftingRecipe extends SpecialCraftingRecipe {
    private static final TagKey<Item> AXE_TAG = TagKey.of(RegistryKeys.ITEM, new Identifier(StrippedLogCraftRecipe.MOD_ID, "axes"));
    private static final Ingredient AXE_INGREDIENT = Ingredient.fromTag(AXE_TAG);
    private static final Ingredient LOG_INGREDIENT = Ingredient.fromTag(ItemTags.LOGS);

    public StrippedLogCraftingRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingInventory inventory, World world) {
        boolean foundAxe = false;
        boolean foundLog = false;

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (AXE_INGREDIENT.test(stack)) {
                if (foundAxe || stack.getCount() != 1) {
                    return false;
                }
                foundAxe = true;
                continue;
            }

            if (stack.getCount() != 1) {
                return false;
            }

            if (foundLog) {
                return false;
            }

            if (getStrippedResult(stack).isEmpty()) {
                return false;
            }

            foundLog = true;
        }

        return foundAxe && foundLog;
    }

    @Override
    public ItemStack craft(CraftingInventory inventory, DynamicRegistryManager registryManager) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            Optional<ItemStack> result = getStrippedResult(stack);
            if (result.isPresent()) {
                return result.get().copy();
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public DefaultedList<ItemStack> getRemainder(CraftingInventory inventory) {
        DefaultedList<ItemStack> remainders = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            Item item = stack.getItem();

            if (AXE_INGREDIENT.test(stack)) {
                ItemStack remainder = stack.copy();
                remainder.setCount(1);
                if (remainder.isDamageable()) {
                    remainder.setDamage(remainder.getDamage() + 1);
                    if (remainder.getDamage() < remainder.getMaxDamage()) {
                        remainders.set(slot, remainder);
                    }
                } else {
                    remainders.set(slot, remainder);
                }
                continue;
            }

            if (item.hasRecipeRemainder()) {
                remainders.set(slot, new ItemStack(item.getRecipeRemainder()));
            }
        }

        return remainders;
    }

    @Override
    public DefaultedList<Ingredient> getIngredients() {
        return DefaultedList.copyOf(Ingredient.EMPTY, LOG_INGREDIENT, AXE_INGREDIENT);
    }

    @Override
    public ItemStack getResult(DynamicRegistryManager registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isIgnoredInRecipeBook() {
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return StrippedLogCraftRecipe.STRIPPED_LOG_RECIPE_SERIALIZER;
    }

    private static Optional<ItemStack> getStrippedResult(ItemStack stack) {
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == Blocks.AIR) {
            return Optional.empty();
        }

        BlockState strippedState = AxeItem.getStrippedState(block.getDefaultState());
        if (strippedState == null) {
            return Optional.empty();
        }

        Item resultItem = strippedState.getBlock().asItem();
        if (resultItem == Items.AIR) {
            return Optional.empty();
        }

        return Optional.of(new ItemStack(resultItem));
    }
}
