package chihalu.strippedlog.craft.recipe;

import net.fabricmc.api.ModInitializer;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import chihalu.strippedlog.craft.recipe.recipe.AxeStrippingRecipe;

public class StrippedLogCraftRecipe implements ModInitializer {
	public static final String MOD_ID = "stripped-log-craft-recipe";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

                Registry.register(Registries.RECIPE_SERIALIZER, id("axe_stripping"), AxeStrippingRecipe.SERIALIZER);

                LOGGER.info("Registered axe stripping crafting recipe serializer");
        }

        private Identifier id(String path) {
                return Identifier.of(MOD_ID, path);
        }
}
