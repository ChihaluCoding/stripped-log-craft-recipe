package chihalu.strippedlog.craft.recipe;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.StrippableBlockRegistry;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipeCategory;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public class LogStrippingRecipe extends SpecialCraftingRecipe {
        public LogStrippingRecipe(CraftingRecipeCategory category) {
                super(category);
        }

        @Override
        public boolean matches(RecipeInputInventory inventory, World world) {
                boolean foundAxe = false;
                boolean foundLog = false;

                for (int i = 0; i < inventory.size(); i++) {
                        ItemStack stack = inventory.getStack(i);
                        if (stack.isEmpty()) {
                                continue;
                        }

                        if (stack.isIn(ItemTags.AXES)) {
                                if (foundAxe) {
                                        return false;
                                }
                                foundAxe = true;
                                continue;
                        }

                        Block stripped = getStrippedBlock(stack);
                        if (stripped != null && stripped != Blocks.AIR) {
                                if (foundLog) {
                                        return false;
                                }
                                foundLog = true;
                                continue;
                        }

                        return false;
                }

                return foundAxe && foundLog;
        }

        @Override
        public ItemStack craft(RecipeInputInventory inventory, RegistryWrapper.WrapperLookup registries) {
                for (int i = 0; i < inventory.size(); i++) {
                        ItemStack stack = inventory.getStack(i);
                        if (stack.isEmpty() || stack.isIn(ItemTags.AXES)) {
                                continue;
                        }

                        Block stripped = getStrippedBlock(stack);
                        if (stripped != null && stripped != Blocks.AIR) {
                                return new ItemStack(stripped.asItem());
                        }
                }

                return ItemStack.EMPTY;
        }

        @Override
        public boolean fits(int width, int height) {
                return width * height >= 2;
        }

        @Override
        public DefaultedList<ItemStack> getRemainder(RecipeInputInventory inventory) {
                DefaultedList<ItemStack> remainder = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);

                for (int i = 0; i < inventory.size(); i++) {
                        ItemStack stack = inventory.getStack(i);
                        if (!stack.isEmpty() && stack.isIn(ItemTags.AXES)) {
                                ItemStack copy = stack.copy();
                                if (copy.isDamageable()) {
                                        copy.setDamage(copy.getDamage() + 1);
                                        if (copy.getDamage() >= copy.getMaxDamage()) {
                                                continue;
                                        }
                                }
                                remainder.set(i, copy);
                        }
                }

                return remainder;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
                return StrippedLogCraftRecipe.LOG_STRIPPING_RECIPE_SERIALIZER;
        }

        private Block getStrippedBlock(ItemStack stack) {
                if (!(stack.getItem() instanceof BlockItem blockItem)) {
                        return null;
                }

                return StrippableBlockRegistry.get(blockItem.getBlock());
        }
}
