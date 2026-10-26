package net.ronnylearn.bedprotect;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import static net.ronnylearn.bedprotect.Palette.*;
import static net.ronnylearn.bedprotect.Texts.tr;

final class BedProtectCommands {
	private BedProtectCommands() {
	}

	private static boolean fromPanel;

	static <S> void register(CommandDispatcher<S> dispatcher) {
		BedProtectConfig config = BedProtectMod.config();
		LiteralCommandNode<S> root = dispatcher.register(LiteralArgumentBuilder.<S>literal("bedprotect")
			.executes(ctx -> settings())
			.then(LiteralArgumentBuilder.<S>literal("help").executes(ctx -> {
				ChatPanels.show(ChatPanels.Panel.HELP, ChatPanels.help(config));
				return Command.SINGLE_SUCCESS;
			}))
			.then(LiteralArgumentBuilder.<S>literal("status").executes(ctx -> settings()))
			.then(LiteralArgumentBuilder.<S>literal("beds")
				.executes(ctx -> setBeds(ctx.getSource(), !config.isBedsAllowed()))
				.then(LiteralArgumentBuilder.<S>literal("allow").executes(ctx -> setBeds(ctx.getSource(), true)))
				.then(LiteralArgumentBuilder.<S>literal("deny").executes(ctx -> setBeds(ctx.getSource(), false))))
			.then(toggle("protection", config::isProtection, (source, on) -> {
				config.setProtection(on);
				changed(source, tr("bedprotect.set.protection", TEXT, Texts.onOff(on)));
			}))
			.then(toggle("hud", config::isHud, (source, on) -> {
				config.setHud(on);
				changed(source, tr("bedprotect.set.hud", TEXT, Texts.onOff(on)));
			}))
			.then(toggle("chat", config::isChat, (source, on) -> {
				config.setChat(on);
				changed(source, tr("bedprotect.set.chat", TEXT, Texts.onOff(on)));
			}))
			.then(toggle("ping", config::isPingGuard, (source, on) -> {
				config.setPingGuard(on);
				changed(source, tr("bedprotect.set.ping", TEXT, Texts.onOff(on)));
			}))
			.then(toggle("confirm", config::isConfirm, (source, on) -> {
				config.setConfirm(on);
				changed(source, tr("bedprotect.set.confirm", TEXT, Texts.onOff(on)));
			}))
			.then(LiteralArgumentBuilder.<S>literal("check").executes(ctx -> check(ctx.getSource())))
			.then(LiteralArgumentBuilder.<S>literal("maxdamage")
				.executes(ctx -> settings())
				.then(RequiredArgumentBuilder.<S, Float>argument("hearts", FloatArgumentType.floatArg(BedProtectConfig.MIN_MAX_DAMAGE, BedProtectConfig.MAX_MAX_DAMAGE))
					.executes(ctx -> {
						config.setMaxDamageHearts(FloatArgumentType.getFloat(ctx, "hearts"));
						changed(ctx.getSource(), tr("bedprotect.set.maxdamage", TEXT, Texts.hearts(config.getMaxDamageHearts(), TEXT)));
						return Command.SINGLE_SUCCESS;
					})))
			.then(LiteralArgumentBuilder.<S>literal("reserve")
				.executes(ctx -> settings())
				.then(RequiredArgumentBuilder.<S, Float>argument("hearts", FloatArgumentType.floatArg(BedProtectConfig.MIN_RESERVE, BedProtectConfig.MAX_RESERVE))
					.executes(ctx -> {
						config.setReserveHearts(FloatArgumentType.getFloat(ctx, "hearts"));
						changed(ctx.getSource(), tr("bedprotect.set.reserve", TEXT, Texts.hearts(config.getReserveHearts(), TEXT)));
						return Command.SINGLE_SUCCESS;
					})))
			.then(LiteralArgumentBuilder.<S>literal("reset").executes(ctx -> {
				config.reset();
				changed(ctx.getSource(), tr("bedprotect.set.reset", TEXT));
				return Command.SINGLE_SUCCESS;
			})));
		dispatcher.register(LiteralArgumentBuilder.<S>literal("bedprotect").then(LiteralArgumentBuilder.<S>literal("panel").redirect(root, ctx -> {
			fromPanel = true;
			return ctx.getSource();
		})));
	}

	static void endTick() {
		fromPanel = false;
	}

	private static <S> LiteralArgumentBuilder<S> toggle(String name, BooleanSupplier current,
		BiConsumer<Object, Boolean> set) {
		return LiteralArgumentBuilder.<S>literal(name)
			.executes(ctx -> {
				set.accept(ctx.getSource(), !current.getAsBoolean());
				return Command.SINGLE_SUCCESS;
			})
			.then(LiteralArgumentBuilder.<S>literal("on").executes(ctx -> {
				set.accept(ctx.getSource(), true);
				return Command.SINGLE_SUCCESS;
			}))
			.then(LiteralArgumentBuilder.<S>literal("off").executes(ctx -> {
				set.accept(ctx.getSource(), false);
				return Command.SINGLE_SUCCESS;
			}));
	}

	private static int setBeds(Object source, boolean allowed) {
		BedProtectMod.config().setBedsAllowed(allowed);
		changed(source, tr("bedprotect.set.beds", TEXT, Texts.bedsState(allowed)));
		return Command.SINGLE_SUCCESS;
	}

	private static void changed(Object source, Component confirmation) {
		BedProtectConfig config = BedProtectMod.config();
		config.save();
		BedProtectMod.refreshNow();
		boolean updated = ChatPanels.update(ChatPanels.Panel.SETTINGS, ChatPanels.settings(config));
		boolean typed = !fromPanel;
		fromPanel = false;
		if (typed) {
			if (config.isConfirmations()) {
				ChatPanels.line(confirmation);
			}
			if (config.isBedsAllowed() && !config.isProtection()) {
				ChatPanels.line(tr("bedprotect.warn.vanilla", DANGER));
			}
		} else if (!updated) {
			ChatPanels.show(ChatPanels.Panel.SETTINGS, ChatPanels.settings(config));
		}
	}

	private static int settings() {
		ChatPanels.show(ChatPanels.Panel.SETTINGS, ChatPanels.settings(BedProtectMod.config()));
		return Command.SINGLE_SUCCESS;
	}

	static void sendWelcome() {
		ChatPanels.show(ChatPanels.Panel.WELCOME, ChatPanels.welcome(BedProtectMod.config()));
	}

	static void sendReminder(LocalPlayer player) {
		player.displayClientMessage(ChatPanels.reminder(BedProtectMod.config()), false);
	}

	private static int check(Object source) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		BlockState state = null;
		BlockPos pos = null;
		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			pos = hit.getBlockPos();
			state = client.level.getBlockState(pos);
		}
		if (state == null || !(state.getBlock() instanceof BedBlock)) {
			ChatPanels.line(tr("bedprotect.check.none", WARN));
			return Command.SINGLE_SUCCESS;
		}
		BlastAssessment assessment = BlastPredictor.assess(player, pos, state);
		if (assessment == null) {
			ChatPanels.line(tr("bedprotect.check.normal", GOOD));
			return Command.SINGLE_SUCCESS;
		}
		ChatPanels.show(ChatPanels.Panel.CHECK, ChatPanels.check(assessment, player, BedProtectMod.config()));
		return Command.SINGLE_SUCCESS;
	}
}
