package chihalu.strippedlog.craft.recipe;

import net.fabricmc.api.ModInitializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StrippedLogCraftRecipe implements ModInitializer {
        public static final String MOD_ID = "stripped-log-craft-recipe";
        public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
        public static final SpecialRecipeSerializer<LogStrippingRecipe> LOG_STRIPPING_RECIPE_SERIALIZER = new SpecialRecipeSerializer<>(LogStrippingRecipe::new);

        @Override
        public void onInitialize() {
                Registry.register(Registries.RECIPE_SERIALIZER, Identifier.of(MOD_ID, "log_stripping"), LOG_STRIPPING_RECIPE_SERIALIZER);
                LOGGER.info("Registered stripped log crafting recipe");
        }
}
