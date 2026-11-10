package de.cooldowntracker;

import com.mojang.blaze3d.platform.InputConstants;
import de.cooldowntracker.config.ConfigScreen;
import de.cooldowntracker.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

public class CooldownTrackerClient implements ClientModInitializer {
	public static final String MOD_ID = "cooldowntracker";
	public static final Logger LOGGER = LoggerFactory.getLogger("Cooldown Tracker");

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping toggleAllKey;
	private static KeyMapping openConfigKey;
	private static final Map<CooldownType, KeyMapping> TYPE_KEYS = new EnumMap<>(CooldownType.class);

	@Override
	public void onInitializeClient() {
		ModConfig.load();

		// --- Tastenbelegungen (Optionen > Steuerung > Tastenbelegung > "Cooldown Tracker")
		toggleAllKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cooldowntracker.toggle_all", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
		openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cooldowntracker.open_config", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		for (CooldownType type : CooldownType.values()) {
			TYPE_KEYS.put(type, KeyMappingHelper.registerKeyMapping(new KeyMapping(
					"key.cooldowntracker.toggle_" + type.id, InputConstants.Type.KEYSYM,
					InputConstants.UNKNOWN.getValue(), CATEGORY)));
		}

		// --- Erkennung
		ClientEntityEvents.ENTITY_LOAD.register(CooldownTracker::onEntityLoad);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			CooldownTracker.tick(client);
			handleKeys(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CooldownTracker.clear());

		// Rendering passiert per Mixin direkt beim Zeichnen der Spieler (siehe CooldownWorldRenderer)
	}

	private static void handleKeys(Minecraft client) {
		ModConfig cfg = ModConfig.get();
		boolean changed = false;

		while (toggleAllKey.consumeClick()) {
			cfg.enabled = !cfg.enabled;
			changed = true;
			notify(client, Component.translatable("cooldowntracker.title"), cfg.enabled);
		}
		for (Map.Entry<CooldownType, KeyMapping> e : TYPE_KEYS.entrySet()) {
			while (e.getValue().consumeClick()) {
				CooldownType type = e.getKey();
				boolean v = !type.isEnabled(cfg);
				type.setEnabled(cfg, v);
				changed = true;
				notify(client, type.displayName(), v);
			}
		}
		while (openConfigKey.consumeClick()) {
			if (client.gui.screen() == null) client.gui.setScreen(new ConfigScreen(null));
		}
		if (changed) ModConfig.save();
	}

	private static void notify(Minecraft client, Component what, boolean on) {
		Component state = on
				? Component.translatable("cooldowntracker.on").withStyle(s -> s.withColor(0x55FF55))
				: Component.translatable("cooldowntracker.off").withStyle(s -> s.withColor(0xFF5555));
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.empty().append(what).append(": ").append(state));
		}
	}
}
