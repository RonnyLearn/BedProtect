package net.ronnylearn.bedprotect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.ronnylearn.bedprotect.mixin.ChatComponentAccessor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static net.ronnylearn.bedprotect.Palette.*;
import static net.ronnylearn.bedprotect.Texts.hearts;
import static net.ronnylearn.bedprotect.Texts.text;
import static net.ronnylearn.bedprotect.Texts.tr;

public final class ChatPanels {
	public enum Panel {
		WELCOME, SETTINGS, HELP, CHECK
	}

	static final String ROOT = "/bedprotect";
	static final String PANEL = ROOT + " panel";
	private static final Map<Panel, MessageSignature> SHOWN = new EnumMap<>(Panel.class);
	private static final Random RANDOM = new Random();

	private ChatPanels() {
	}

	public static void show(Panel panel, Component content) {
		remove(panel);
		byte[] bytes = new byte[MessageSignature.BYTES];
		RANDOM.nextBytes(bytes);
		MessageSignature signature = new MessageSignature(bytes);
		SHOWN.put(panel, signature);
		chat().invokeBedProtectAddMessage(content, signature, GuiMessageSource.SYSTEM_CLIENT, GuiMessageTag.systemSinglePlayer());
	}

	public static boolean update(Panel panel, Component content) {
		MessageSignature signature = SHOWN.get(panel);
		if (signature == null) {
			return false;
		}
		List<GuiMessage> messages = chat().getBedProtectAllMessages();
		for (int i = 0; i < messages.size(); i++) {
			GuiMessage old = messages.get(i);
			if (signature.equals(old.signature())) {
				messages.set(i, new GuiMessage(old.addedTime(), content, signature, old.source(), old.tag()));
				chat().invokeBedProtectRefreshTrimmedMessages();
				return true;
			}
		}
		SHOWN.remove(panel);
		return false;
	}

	private static void remove(Panel panel) {
		MessageSignature signature = SHOWN.remove(panel);
		if (signature != null && chat().getBedProtectAllMessages().removeIf(m -> signature.equals(m.signature()))) {
			chat().invokeBedProtectRefreshTrimmedMessages();
		}
	}

	private static ChatComponentAccessor chat() {
		return (ChatComponentAccessor) Minecraft.getInstance().gui.getChat();
	}

	public static void line(Component message) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(Texts.chat(message));
		}
	}

	public static Component welcome(BedProtectConfig config) {
		int width = panelWidth();
		List<Component> lines = new ArrayList<>();
		lines.add(bar(text("BedProtect", ACCENT), width));
		lines.add(indent(tr("bedprotect.welcome.tagline", TITLE)));
		lines.add(Component.empty());
		lines.add(bullet(tr("bedprotect.welcome.now", TEXT, Texts.bedsState(config.isBedsAllowed()))));
		lines.add(indent(tr("bedprotect.welcome.now_detail", MUTED), 3));
		lines.add(Component.empty());
		lines.add(bullet(tr("bedprotect.welcome.smart", TEXT)));
		lines.add(indent(tr("bedprotect.welcome.smart_detail_1", MUTED, hearts(config.getMaxDamageHearts(), TEXT)), 3));
		lines.add(indent(tr("bedprotect.welcome.smart_detail_2", MUTED, hearts(config.getReserveHearts(), TEXT)), 3));
		lines.add(Component.empty());
		lines.add(bullet(tr("bedprotect.welcome.look", TEXT)));
		lines.add(indent(tr("bedprotect.welcome.look_detail", MUTED), 3));
		lines.add(Component.empty());
		lines.add(indent(Component.empty()
			.append(button(tr("bedprotect.button.settings", ACCENT), ROOT, tr("bedprotect.hover.settings", TEXT)))
			.append(text("   ", TEXT))
			.append(button(tr("bedprotect.button.commands", LINK), ROOT + " help", tr("bedprotect.hover.commands", TEXT)))));
		lines.add(rule(width));
		return join(lines);
	}

	public static Component settings(BedProtectConfig config) {
		int width = panelWidth();
		Component[] labels = {
			tr("bedprotect.settings.beds", TEXT), tr("bedprotect.settings.protection", TEXT),
			tr("bedprotect.settings.hud", TEXT), tr("bedprotect.settings.chat", TEXT),
			tr("bedprotect.settings.maxdamage", TEXT), tr("bedprotect.settings.reserve", TEXT),
			tr("bedprotect.settings.ping", TEXT), tr("bedprotect.settings.confirm", TEXT)
		};
		int column = valueColumn(labels);
		List<Component> lines = new ArrayList<>();
		lines.add(bar(title("bedprotect.panel.settings"), width));
		boolean allowed = config.isBedsAllowed();
		lines.add(row(labels[0], button(Texts.bedsState(allowed), PANEL + " beds " + (allowed ? "deny" : "allow"),
			tr(allowed ? "bedprotect.hover.deny" : "bedprotect.hover.allow", TEXT)), column));
		lines.add(row(labels[1], toggle("protection", config.isProtection()), column));
		lines.add(row(labels[2], toggle("hud", config.isHud()), column));
		lines.add(row(labels[3], toggle("chat", config.isChat()), column));
		lines.add(row(labels[4], stepper("maxdamage", config.getMaxDamageHearts(),
			BedProtectConfig.MIN_MAX_DAMAGE, BedProtectConfig.MAX_MAX_DAMAGE), column));
		lines.add(row(labels[5], stepper("reserve", config.getReserveHearts(),
			BedProtectConfig.MIN_RESERVE, BedProtectConfig.MAX_RESERVE), column));
		lines.add(row(labels[6], toggle("ping", config.isPingGuard()), column));
		lines.add(row(labels[7], toggle("confirm", config.isConfirm()), column));
		lines.add(Component.empty());
		if (!allowed) {
			lines.add(bullet(tr("bedprotect.mode.disabled", GOOD)));
		} else if (config.isProtection()) {
			lines.add(bullet(tr("bedprotect.mode.protected", WARN, hearts(config.getMaxDamageHearts(), WARN),
				hearts(config.getReserveHearts(), WARN))));
		} else {
			lines.add(bullet(tr("bedprotect.mode.vanilla", DANGER)));
		}
		lines.add(Component.empty());
		lines.add(indent(Component.empty()
			.append(button(tr("bedprotect.button.commands", LINK), ROOT + " help", tr("bedprotect.hover.commands", TEXT)))
			.append(text("   ", TEXT))
			.append(button(tr("bedprotect.button.reset", MUTED), PANEL + " reset", tr("bedprotect.hover.reset", TEXT)))));
		lines.add(rule(width));
		return join(lines);
	}

	public static Component help(BedProtectConfig config) {
		int width = panelWidth();
		String[][] entries = {
			{ROOT, ROOT, "status"},
			{ROOT + " beds allow|deny", ROOT + " beds ", "beds"},
			{ROOT + " protection on|off", ROOT + " protection ", "protection"},
			{ROOT + " hud on|off", ROOT + " hud ", "hud"},
			{ROOT + " chat on|off", ROOT + " chat ", "chat"},
			{ROOT + " ping on|off", ROOT + " ping ", "ping"},
			{ROOT + " confirm on|off", ROOT + " confirm ", "confirm"},
			{ROOT + " check", ROOT + " check", "check"},
			{ROOT + " maxdamage <❤>", ROOT + " maxdamage ", "maxdamage"},
			{ROOT + " reserve <❤>", ROOT + " reserve ", "reserve"},
			{ROOT + " reset", ROOT + " reset", "reset"}
		};
		List<Component> lines = new ArrayList<>();
		lines.add(bar(title("bedprotect.panel.help"), width));
		for (String[] entry : entries) {
			lines.add(indent(suggest(entry[0], entry[1])));
			lines.add(indent(tr("bedprotect.help." + entry[2], MUTED,
				hearts(config.getMaxDamageHearts(), TEXT), hearts(config.getReserveHearts(), TEXT)), 3));
		}
		lines.add(Component.empty());
		lines.add(indent(tr("bedprotect.help.tip", DIM)));
		lines.add(rule(width));
		return join(lines);
	}

	public static Component check(BlastAssessment a, LocalPlayer player, BedProtectConfig config) {
		int width = panelWidth();
		List<Component> lines = new ArrayList<>();
		lines.add(bar(title("bedprotect.panel.check"), width));
		BlockPos head = a.headPos();
		List<Component[]> rows = new ArrayList<>();
		rows.add(new Component[]{tr("bedprotect.check.bed", TEXT), text(head.getX() + " " + head.getY() + " " + head.getZ(), TEXT)});
		if (!a.explodes()) {
			rows.add(new Component[]{tr("bedprotect.check.kind", TEXT), tr("bedprotect.check.straw", MUTED)});
		} else if (a.invulnerable()) {
			rows.add(new Component[]{tr("bedprotect.check.damage", TEXT), tr("bedprotect.hud.details_invulnerable", MUTED)});
		} else {
			rows.add(new Component[]{tr("bedprotect.check.distance", TEXT), tr("bedprotect.check.blocks", TEXT, Texts.blocks(a.distance()))});
			rows.add(new Component[]{tr("bedprotect.check.cover", TEXT), tr("bedprotect.check.cover_value", TEXT,
				Texts.percent(1.0F - a.exposure()), Texts.percent(a.exposure()))});
			rows.add(new Component[]{tr("bedprotect.check.raw", TEXT), Component.empty()
				.append(hearts(Texts.halvesUp(a.scaledDamage()), TEXT))
				.append(tr("bedprotect.check.raw_detail", MUTED, player.level().getDifficulty().getDisplayName()))});
			rows.add(new Component[]{tr("bedprotect.check.armor", TEXT), Component.empty()
				.append(hearts(Texts.halvesUp(a.afterArmor()), TEXT))
				.append(tr("bedprotect.check.armor_detail", MUTED, Integer.toString(player.getArmorValue()),
					Long.toString(Math.round(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)))))});
			if (player.hasEffect(MobEffects.RESISTANCE)) {
				rows.add(new Component[]{tr("bedprotect.check.resistance", TEXT), hearts(Texts.halvesUp(a.afterEffects()), TEXT)});
			}
			rows.add(new Component[]{tr("bedprotect.check.enchant", TEXT), Component.empty()
				.append(hearts(Texts.halvesUp(a.blastDamage()), TEXT))
				.append(tr("bedprotect.check.enchant_detail", MUTED, Integer.toString(Math.round(Math.min(a.protection(), 20.0F)))))});
			if (a.brokenArmor() > 0) {
				rows.add(new Component[]{tr("bedprotect.check.broken", WARN), text(Integer.toString(a.brokenArmor()), WARN)});
			}
			rows.add(new Component[]{tr("bedprotect.check.health", TEXT), Component.empty()
				.append(hearts(a.halvesNow(), TEXT)).append(text(" → ", MUTED))
				.append(a.lethal() ? text("☠", DANGER) : hearts(a.halvesAfterBlast(), GOOD))
				.append(tr("bedprotect.check.health_detail", MUTED,
					hearts(a.lethal() ? Texts.halvesUp(a.blastDamage()) : a.damageHalves(), TEXT)))});
			if (a.knockback() > 0.01 || a.fallDistance() > 0.01) {
				rows.add(new Component[]{tr("bedprotect.check.fall", TEXT), a.fallDamage() > 0.0F
					? tr("bedprotect.check.fall_value", a.fallCanKill() ? DANGER : WARN, Texts.blocks(a.fallDistance()),
						hearts(a.fallHalves(), a.fallCanKill() ? DANGER : WARN))
					: tr("bedprotect.check.fall_safe", MUTED, Texts.blocks(a.fallDistance()))});
			}
			rows.add(new Component[]{tr("bedprotect.check.hazards", TEXT), a.hazards().isEmpty()
				? tr("bedprotect.check.no_hazards", GOOD) : Texts.hazards(a.hazards()).withColor(BAD)});
		}
		Component[] labels = rows.stream().map(r -> r[0]).toArray(Component[]::new);
		int column = valueColumn(labels);
		for (Component[] r : rows) {
			lines.add(row(r[0], r[1], column));
		}
		lines.add(Component.empty());
		lines.add(bullet(Texts.statusLine(a, a.verdict(config), config)));
		lines.add(rule(width));
		return join(lines);
	}

	public static Component reminder(BedProtectConfig config) {
		return Texts.chat(Component.empty()
			.append(tr("bedprotect.reminder", TEXT, Texts.bedsState(config.isBedsAllowed()), Texts.onOff(config.isProtection())))
			.append(text(" ", TEXT))
			.append(button(tr("bedprotect.button.settings", ACCENT), ROOT, tr("bedprotect.hover.settings", TEXT))));
	}

	private static MutableComponent title(String key) {
		return Component.empty().append(text("BedProtect", ACCENT)).append(text(" · ", DIM)).append(tr(key, TITLE));
	}

	private static MutableComponent toggle(String setting, boolean on) {
		return button(Texts.onOff(on), PANEL + " " + setting + (on ? " off" : " on"),
			tr(on ? "bedprotect.hover.turn_off" : "bedprotect.hover.turn_on", TEXT));
	}

	private static MutableComponent stepper(String setting, float value, float min, float max) {
		MutableComponent minus = value - 0.5F >= min - 1.0E-4F
			? button(text("−", LINK), PANEL + " " + setting + " " + number(value - 0.5F), tr("bedprotect.hover.less", TEXT))
			: text("[−]", DIM);
		MutableComponent plus = value + 0.5F <= max + 1.0E-4F
			? button(text("+", LINK), PANEL + " " + setting + " " + number(value + 0.5F), tr("bedprotect.hover.more", TEXT))
			: text("[+]", DIM);
		return Component.empty().append(minus).append(text(" ", TEXT)).append(hearts(value, TEXT)).append(text(" ", TEXT)).append(plus);
	}

	private static String number(float value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static MutableComponent button(Component label, String command, Component hover) {
		return Component.empty().append(text("[", DIM)).append(label).append(text("]", DIM))
			.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	private static MutableComponent suggest(String shown, String command) {
		return text(shown, LINK).withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(tr("bedprotect.hover.suggest", TEXT, command.trim()))));
	}

	private static MutableComponent bullet(Component content) {
		return Component.empty().append(text(" » ", ACCENT)).append(content);
	}

	private static MutableComponent indent(Component content) {
		return indent(content, 1);
	}

	private static MutableComponent indent(Component content, int spaces) {
		return Component.empty().append(text(" ".repeat(spaces), TEXT)).append(content);
	}

	private static MutableComponent bar(Component title, int width) {
		MutableComponent middle = Component.empty().append(text("  ", DIM)).append(title).append(text("  ", DIM));
		String side = repeatTo("▬", Math.max(width - font().width(middle), 0) / 2);
		return Component.empty().append(text(side, DIM)).append(middle).append(text(side, DIM));
	}

	private static MutableComponent rule(int width) {
		return text(repeatTo("▬", width), DIM);
	}

	private static MutableComponent row(Component label, Component value, int column) {
		MutableComponent line = Component.empty().append(text(" ", TEXT)).append(label);
		int dots = Math.max((column - font().width(line) - 2 * font().width(" ")) / Math.max(font().width("."), 1), 2);
		return line.append(text(" " + ".".repeat(dots) + " ", DIM)).append(value);
	}

	private static int valueColumn(Component[] labels) {
		int widest = 0;
		for (Component label : labels) {
			widest = Math.max(widest, font().width(Component.empty().append(" ").append(label)));
		}
		return widest + 16;
	}

	private static String repeatTo(String unit, int pixels) {
		return unit.repeat(Math.max(pixels / Math.max(font().width(unit), 1), 0));
	}

	private static Component join(List<Component> lines) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				out.append("\n");
			}
			out.append(lines.get(i));
		}
		return out;
	}

	static int panelWidth() {
		Minecraft minecraft = Minecraft.getInstance();
		double scale = Math.max(minecraft.options.chatScale().get(), 0.1);
		int chatWidth = (int) Math.floor(ChatComponent.getWidth(minecraft.options.chatWidth().get()) / scale);
		return Mth.clamp(chatWidth - 8, 180, 300);
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}
}
