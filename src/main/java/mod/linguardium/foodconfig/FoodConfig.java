package mod.linguardium.foodconfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.datafixers.util.Pair;
import mod.linguardium.foodconfig.mixin.FoodComponentAccessor;
import mod.linguardium.foodconfig.mixin.ItemAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.fabric.api.event.registry.RegistryEntryRemovedCallback;
import net.fabricmc.fabric.api.registry.CommandRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.FoodComponents;
import net.minecraft.item.Item;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.core.jmx.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class FoodConfig implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("Food Item Config");
	public static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().registerTypeAdapter(Identifier.class, new TypeAdapter<Identifier>() {
		@Override
		public void write(JsonWriter jsonWriter, Identifier identifier) throws IOException {
			jsonWriter.value(identifier.toString());
		}

		@Override
		public Identifier read(JsonReader jsonReader) throws IOException {
			return new Identifier(jsonReader.nextString());
		}
	}) .create();

	public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("foodconfig.json");
	public static ConfigFile config;
	public static HashMap<Identifier, FoodComponent> FoodStatsDefaults=  new HashMap<>();
	@Override
	public void onInitialize() {

		loadConfig();
		getItemDefaults();

		// Allows modded status effects to be used. otherwise their identifiers would not be able to be looked up
		DynamicRegistrySetupCallback.EVENT.register(registryManager -> {
			applyChangesToCurrentItems();
		});

		// Allows reload in game if opped or single player
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {

			dispatcher.register(CommandManager.literal("foodconfig_reload").requires(source->source.hasPermissionLevel(2) || !source.getServer().isDedicated()).executes(context -> {
				resetItems();
				loadConfig();
				applyChangesToCurrentItems();
				context.getSource().sendFeedback(new LiteralText("Successfully reloaded config!"),false);
				return 0;
			}));
			dispatcher.register(CommandManager.literal("foodconfig_dump").requires(source->source.hasPermissionLevel(2) || !source.getServer().isDedicated()).executes(context -> {
				dumpComponents();
				context.getSource().sendFeedback(new LiteralText("Successfully dumped food components!"),false);
				return 0;
			}));
		});
	}
	public void loadConfig() {
		if (!Files.exists(CONFIG_PATH)) {
			config = new ConfigFile();

			ConfigFile.FoodStats stats = ConfigFile.FoodStats.fromFoodComponent(FoodComponents.ENCHANTED_GOLDEN_APPLE);
			config.foodMap.put(new Identifier("example:exampleitem"),stats);
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
	private static void getItemDefaults() {
		Registry.ITEM.getIds().forEach(id->{
			Item item = Registry.ITEM.get(id);
			if (item.isFood()) {
				FoodStatsDefaults.put(id, item.getFoodComponent());
			}
		});
		RegistryEntryAddedCallback.event(Registry.ITEM).register((rawId, id, item) -> {
			if (item.isFood()) {
				FoodStatsDefaults.put(id, item.getFoodComponent());
			}
		});
		RegistryEntryRemovedCallback.event(Registry.ITEM).register((rawId, id, object) -> {
			FoodStatsDefaults.remove(id);
		});
	}
	private static void applyValuesToItem(Item item, ConfigFile.FoodStats values) {
		FoodComponent fc = item.getFoodComponent();
		if (fc == null) {
			fc = new FoodComponent.Builder().build();
		}
		FoodComponent.Builder builder = new FoodComponent.Builder();
		if (values.saturation != null) builder.saturationModifier(values.saturation); else builder.saturationModifier(fc.getSaturationModifier());
		if (values.hunger != null) builder.hunger(values.hunger); else builder.hunger(fc.getHunger());
		if (	(values.alwaysEdible !=null && values.alwaysEdible) ||
				(fc.isAlwaysEdible() && values.alwaysEdible == null))
					builder.alwaysEdible();
		if (	(values.meat !=null && values.meat) ||
				(fc.isMeat() && values.meat == null))
			builder.meat();
		if (	(values.snack !=null && values.snack) ||
				(fc.isMeat() && values.snack == null))
			builder.snack();
		for (ConfigFile.EffectWithChance effect: values.effects) {
			builder.statusEffect(effect.effect.toStatusEffectInstance(), effect.chancePercent);
		}
		for (Pair<StatusEffectInstance,Float> p: fc.getStatusEffects()) {
			builder.statusEffect(p.getFirst(),p.getSecond());
		}
		((ItemAccessor)item).setFoodComponent(builder.build());
	}
	public static void applyChangesToCurrentItems() {
//		if (config == null)
//			return;
		config.foodMap.forEach((key,values)->{
			if (!key.getNamespace().equals("example") && Registry.ITEM.containsId(key)) {
				LOGGER.info("Modifying {}",key.toString());
				applyValuesToItem(Registry.ITEM.get(key),values);
			}
		});
	}
	private static void resetItems() {
		config.foodMap.forEach((key,value)->{
			if (Registry.ITEM.containsId(key)) {
				Item item = Registry.ITEM.get(key);
				((ItemAccessor) item).setFoodComponent(FoodStatsDefaults.getOrDefault(key, null));
			}
		});
	}
	private static void dumpComponents() {
		HashMap<Identifier,ConfigFile.FoodStats> map = new HashMap<>();
		Registry.ITEM.getIds().forEach(id->{
			Item item = Registry.ITEM.get(id);
			if (item.isFood()) {
				map.put(id, ConfigFile.FoodStats.fromFoodComponent(item.getFoodComponent()));
			}
		});
		try(FileWriter writer=new FileWriter(FabricLoader.getInstance().getGameDir().resolve("FoodComponents.json").toFile())) {
			writer.write(GSON.toJson(map));
		} catch (IOException e) {
			LOGGER.error("Failed to write FoodComponents.json to the game directory!");
			e.printStackTrace();
		}
	}
}
