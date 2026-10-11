package com.multitool;

import com.multitool.audio.Beeper;
import com.multitool.browser.BrowserBackend;
import com.multitool.browser.BrowserWindow;
import com.multitool.browser.MCEFBackend;
import com.multitool.browser.UnavailableBackend;
import com.multitool.calc.CalculatorWindow;
import com.multitool.clock.ClockService;
import com.multitool.clock.ClockWindow;
import com.multitool.clock.Toasts;
import com.multitool.config.ModConfig;
import com.multitool.overlay.Gfx;
import com.multitool.overlay.WindowManager;
import com.multitool.ui.MultiToolScreen;
import com.multitool.ui.SettingsWindow;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class MultiToolClient implements ClientModInitializer {
	public static final String MOD_ID = "multitool";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyBinding openKey;
	private static KeyBinding toggleKey;
	private static KeyBinding dismissKey;

	/** Chromium работает через MCEF Modern; без него окно честно сообщает, что страницы показать нельзя. */
	private static BrowserBackend createBrowserBackend() {
		if (FabricLoader.getInstance().isModLoaded("mcef-modern")) {
			return new MCEFBackend();
		}
		return new UnavailableBackend("The MCEF Modern mod is not installed, so pages cannot be displayed. "
				+ "Install MCEF Modern for Minecraft 1.21.11 (modrinth.com/project/mcef-modern).");
	}

	@Override
	public void onInitializeClient() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		WindowManager wm = WindowManager.INSTANCE;

		ModConfig cfg = ModConfig.INSTANCE;
		cfg.setFile(dir.resolve("config.properties"));
		cfg.load();
		cfg.addListener(Gfx::refresh);
		Gfx.refresh();

		ClockService clock = ClockService.INSTANCE;
		clock.setFile(dir.resolve("clock.properties"));
		clock.load();

		wm.register(new CalculatorWindow(dir.resolve("calculator_history.txt")));
		wm.register(new ClockWindow(clock));
		BrowserWindow browser = new BrowserWindow(dir.resolve("browser.txt"), createBrowserBackend());
		wm.register(browser);
		wm.register(new SettingsWindow());
		wm.setFile(dir.resolve("windows.properties"));
		wm.load();

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.multitool.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.multitool.toggle_hud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, category));
		dismissKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.multitool.dismiss_alerts", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_END, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clock.tick();
			while (dismissKey.wasPressed()) {
				clock.dismissAll();
			}
			while (openKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new MultiToolScreen());
				}
			}
			while (toggleKey.wasPressed()) {
				wm.hudVisible = !wm.hudVisible;
				wm.save();
			}
		});

		// закреплённые окна поверх игрового изображения (когда меню MultiTool закрыто)
		HudElementRegistry.addLast(Identifier.of(MOD_ID, "pinned_windows"), (context, tickCounter) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.currentScreen instanceof MultiToolScreen) {
				return;
			}
			Toasts.render(context, mc.textRenderer, context.getScaledWindowWidth(), -1000, -1000, false, clock);
			wm.render(context, mc.textRenderer, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
					-1000, -1000, false);
		});

		// браузеры нужно закрыть до того, как MCEF Modern освободит CefApp, поэтому отдельная ранняя фаза
		Identifier early = Identifier.of(MOD_ID, "early_shutdown");
		ClientLifecycleEvents.CLIENT_STOPPING.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ClientLifecycleEvents.CLIENT_STOPPING.register(early, client -> {
			wm.closeAll();
			browser.shutdown();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			wm.save();
			clock.flush();
			Beeper.shutdown();
		});
		LOGGER.info("MultiTool Overlay loaded");
	}
}
