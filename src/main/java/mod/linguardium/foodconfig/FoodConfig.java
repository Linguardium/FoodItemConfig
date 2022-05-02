package mod.linguardium.foodconfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mod.linguardium.foodconfig.mixin.FoodComponentAccessor;
import mod.linguardium.foodconfig.mixin.ItemAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FoodConfig implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("Food Item Config");
	public static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
	public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("foodconfig.json");
	public static ConfigFile config;
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		loadConfig();
		applyChangesToCurrentItems();
		RegistryEntryAddedCallback.event(Registry.ITEM).register((rawId, id, item) -> {
			if (config != null && config.foodMap.containsKey(id.toString())) {
				LOGGER.info("Modifying {}",id.toString());
				applyValuesToItem(item,config.foodMap.get(id.toString()));
			}
		});
	}
	public void loadConfig() {
		if (!Files.exists(CONFIG_PATH)) {
			config = new ConfigFile();
			config.foodMap.put("example:exampleitem",new ConfigFile.FoodStats(1,3.5f));
			try(FileWriter writer = new FileWriter(CONFIG_PATH.toFile())){
				writer.write(GSON.toJson(config));
			} catch (IOException e) {
				LOGGER.warn("Did not find config and could not save an empty one");
				e.printStackTrace();
			}
		}else {
			try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
				config = GSON.fromJson(reader,ConfigFile.class);
			} catch (FileNotFoundException e) {
				LOGGER.error("File exists but cannot be found! Maximum Stealth Engaged!");
				e.printStackTrace();
			} catch (IOException e) {
				LOGGER.warn("Cannot read config file. Mod will do nothing.");
			}
		}
	}
	private static void applyValuesToItem(Item item, ConfigFile.FoodStats values) {
		FoodComponent fc = item.getFoodComponent();
		if (fc != null) {
			if (values.saturation>=0) ((FoodComponentAccessor)fc).setSaturationValue(values.saturation);
			if (values.hunger>=0) ((FoodComponentAccessor)fc).setHungerValue(values.hunger);
		}else{
			FoodComponent.Builder builder = new FoodComponent.Builder();
			if (values.hunger >= 0) {builder.hunger(values.hunger);}
			if (values.saturation >= 0) {builder.saturationModifier(values.saturation);}
			fc = builder.build();
		}
		((ItemAccessor)item).setFoodComponent(fc);
	}
	public static void applyChangesToCurrentItems() {
//		if (config == null)
//			return;
		config.foodMap.forEach((key,values)->{
			Identifier itemId = Identifier.tryParse(key);
			if (itemId != null && !itemId.getNamespace().equals("example") && Registry.ITEM.containsId(itemId)) {
				LOGGER.info("Modifying {}",itemId.toString());
				applyValuesToItem(Registry.ITEM.get(itemId),values);
			}
		});
	}
}
