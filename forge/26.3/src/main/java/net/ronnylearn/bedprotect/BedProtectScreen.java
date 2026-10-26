package net.ronnylearn.bedprotect;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.CommonComponents;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

import static net.ronnylearn.bedprotect.Palette.*;
import static net.ronnylearn.bedprotect.Texts.text;
import static net.ronnylearn.bedprotect.Texts.tr;

public class BedProtectScreen extends Screen {
	private static final int COLUMN = 150;
	private static final int GAP = 10;
	private static final int ROW = 24;

	private final @Nullable Screen parent;
	private final BedProtectConfig config = BedProtectMod.config();
	private int protectionTitleY;
	private int displayTitleY;

	public BedProtectScreen(@Nullable Screen parent) {
		super(Component.translatable("bedprotect.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = width / 2 - COLUMN - GAP / 2;
		int right = width / 2 + GAP / 2;
		int y = 44;

		protectionTitleY = y - 12;
		toggle(left, y, () -> setting("beds", Texts.bedsState(config.isBedsAllowed())),
			() -> config.setBedsAllowed(!config.isBedsAllowed()), "beds");
		toggle(right, y, () -> setting("protection", Texts.onOff(config.isProtection())),
			() -> config.setProtection(!config.isProtection()), "protection");
		y += ROW;
		toggle(left, y, () -> setting("ping", Texts.onOff(config.isPingGuard())),
			() -> config.setPingGuard(!config.isPingGuard()), "ping");
		toggle(right, y, () -> setting("confirm", Texts.onOff(config.isConfirm())),
			() -> config.setConfirm(!config.isConfirm()), "confirm");
		y += ROW;
		stepper(left, y, "maxdamage", config::getMaxDamageHearts, config::setMaxDamageHearts);
		stepper(right, y, "reserve", config::getReserveHearts, config::setReserveHearts);
		y += ROW + 18;

		displayTitleY = y - 12;
		toggle(left, y, () -> setting("hud", Texts.onOff(config.isHud())),
			() -> config.setHud(!config.isHud()), "hud");
		toggle(right, y, () -> setting("chat", Texts.onOff(config.isChat())),
			() -> config.setChat(!config.isChat()), "chat");
		y += ROW;
		toggle(left, y, () -> setting("block_messages", Texts.onOff(config.isBlockMessages())),
			() -> config.setBlockMessages(!config.isBlockMessages()), "block_messages");
		toggle(right, y, () -> setting("confirmations", Texts.onOff(config.isConfirmations())),
			() -> config.setConfirmations(!config.isConfirmations()), "confirmations");
		y += ROW;
		Button welcome = Button.builder(tr("bedprotect.screen.welcome", TEXT), button -> BedProtectCommands.sendWelcome())
			.bounds(left, y, COLUMN, 20).tooltip(Tooltip.create(tr("bedprotect.screen.tip.welcome", TEXT))).build();
		welcome.active = minecraft.player != null;
		addRenderableWidget(welcome);
		addRenderableWidget(Button.builder(tr("bedprotect.button.reset", TEXT), button -> {
			config.reset();
			applied();
		}).bounds(right, y, COLUMN, 20).tooltip(Tooltip.create(tr("bedprotect.hover.reset", TEXT))).build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(width / 2 - 100, Math.min(y + ROW + 16, height - 28), 200, 20).build());
	}

	private void toggle(int x, int y, Supplier<Component> label, Runnable flip, String tip) {
		addRenderableWidget(Button.builder(label.get(), button -> {
			flip.run();
			applied();
		}).bounds(x, y, COLUMN, 20).tooltip(Tooltip.create(tr("bedprotect.screen.tip." + tip, TEXT))).build());
	}

	private void stepper(int x, int y, String key, Supplier<Float> get, java.util.function.Consumer<Float> set) {
		float value = get.get();
		Button minus = Button.builder(text("−", TEXT), button -> {
			set.accept(get.get() - 0.5F);
			applied();
		}).bounds(x, y, 20, 20).tooltip(Tooltip.create(tr("bedprotect.hover.less", TEXT))).build();
		Button plus = Button.builder(text("+", TEXT), button -> {
			set.accept(get.get() + 0.5F);
			applied();
		}).bounds(x + COLUMN - 20, y, 20, 20).tooltip(Tooltip.create(tr("bedprotect.hover.more", TEXT))).build();
		Button shown = Button.builder(setting(key, Texts.hearts(value, TEXT)), button -> {
		}).bounds(x + 22, y, COLUMN - 44, 20).tooltip(Tooltip.create(tr("bedprotect.screen.tip." + key, TEXT))).build();
		shown.active = false;
		minus.active = get.get() - 0.5F >= (key.equals("reserve") ? BedProtectConfig.MIN_RESERVE : BedProtectConfig.MIN_MAX_DAMAGE) - 1.0E-4F;
		plus.active = get.get() + 0.5F <= (key.equals("reserve") ? BedProtectConfig.MAX_RESERVE : BedProtectConfig.MAX_MAX_DAMAGE) + 1.0E-4F;
		addRenderableWidget(minus);
		addRenderableWidget(shown);
		addRenderableWidget(plus);
	}

	private static MutableComponent setting(String key, Component value) {
		return Component.empty().append(tr("bedprotect.screen." + key, TEXT)).append(text(": ", TEXT)).append(value);
	}

	private void applied() {
		config.save();
		BedProtectMod.refreshNow();
		if (minecraft.player != null) {
			ChatPanels.update(ChatPanels.Panel.SETTINGS, ChatPanels.settings(config));
		}
		rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, Component.empty().append(text("BedProtect", ACCENT)).append(text(" · ", DIM))
			.append(tr("bedprotect.panel.settings", TITLE)), width / 2, 14, 0xFFFFFFFF);
		graphics.centeredText(font, tr("bedprotect.screen.section.protection", ACCENT), width / 2, protectionTitleY, 0xFFFFFFFF);
		graphics.centeredText(font, tr("bedprotect.screen.section.display", ACCENT), width / 2, displayTitleY, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
