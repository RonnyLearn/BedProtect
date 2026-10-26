package net.ronnylearn.bedprotect;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.BedBlock;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BedProtectMod implements ClientModInitializer {
	public static final String MOD_ID = "bedprotect";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int CHECK_INTERVAL = 2;
	private static final int WELCOME_DELAY = 40;

	private static BedProtectConfig config;
	private static @Nullable BlastAssessment lookedAt;
	private static int ticksInWorld;
	private static @Nullable ResourceKey<Level> lastDimension;
	private static final long REMINDER_COOLDOWN_MS = 10L * 60L * 1000L;
	private static final Map<ResourceKey<Level>, Long> REMINDED = new HashMap<>();

	public static BedProtectConfig config() {
		return config;
	}

	public static @Nullable BlastAssessment lookedAt() {
		return lookedAt;
	}

	@Override
	public void onInitializeClient() {
		config = BedProtectConfig.load();
		UseBlockCallback.EVENT.register(BedProtectMod::onUseBlockSafely);
		ClientTickEvents.END_CLIENT_TICK.register(client -> safely("tick", () -> onTick(client)));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> BedProtectCommands.register(dispatcher));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath(MOD_ID, "status"),
			(graphics, deltaTracker) -> safely("hud", () -> BedProtectHud.extract(graphics, deltaTracker)));
		LOGGER.info("[BedProtect] Ready: beds {}, protection {}",
			config.isBedsAllowed() ? "allowed" : "denied", config.isProtection() ? "on" : "off");
	}

	private static void safely(String where, Runnable fn) {
		try {
			fn.run();
		} catch (RuntimeException | LinkageError e) {
			if (REPORTED.add(where)) {
				LOGGER.error("[BedProtect] Error in {} (game keeps running, further errors here are not logged)", where, e);
			}
		}
	}

	private static final Set<String> REPORTED = new HashSet<>();

	private static InteractionResult onUseBlockSafely(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		try {
			return onUseBlock(player, level, hand, hit);
		} catch (RuntimeException | LinkageError e) {
			if (REPORTED.add("click")) {
				LOGGER.error("[BedProtect] Error while checking a click (game keeps running)", e);
			}
			boolean bed = level.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock;
			boolean dangerous = level.dimension() == Level.NETHER || level.dimension() == Level.END;
			return level.isClientSide() && hand == InteractionHand.MAIN_HAND && bed && dangerous
				? InteractionResult.FAIL : InteractionResult.PASS;
		}
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		boolean holdsSomething = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
		if (player.isSecondaryUseActive() && holdsSomething) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		BlastAssessment assessment = BlastPredictor.assess(player, hit.getBlockPos(), state);
		if (assessment == null) {
			return InteractionResult.PASS;
		}
		BlastAssessment.Verdict verdict = assessment.verdict(config);
		lookedAt = assessment;
		if (verdict.allowed() && assessment.explodes()) {
			PingGuard.Gate gate = PingGuard.check(assessment, config);
			if (gate != PingGuard.Gate.PASS) {
				player.sendOverlayMessage(Texts.gateLine(gate));
				return InteractionResult.FAIL;
			}
		}
		if (verdict.allowed()) {
			LOGGER.info("[BedProtect] Bed at {} allowed ({}): blast {} HP, {} HP left, fall up to {} HP",
				assessment.headPos(), verdict, assessment.blastDamage(), assessment.healthAfterBlast(), assessment.fallDamage());
			return InteractionResult.PASS;
		}
		if (config.isBlockMessages()) {
			player.sendOverlayMessage(Component.empty().append(Texts.statusLine(assessment, verdict, config))
				.append(Texts.text("  /bedprotect", Palette.DIM)));
		}
		return InteractionResult.FAIL;
	}

	private static void onTick(Minecraft client) {
		BedProtectCommands.endTick();
		SourceWarning.tick(client);
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			lookedAt = null;
			ticksInWorld = 0;
			lastDimension = null;
			REMINDED.clear();
			PingGuard.tick(null, null, config);
			return;
		}
		PingGuard.tick(player, lookedAt, config);
		ticksInWorld++;
		if (!config.isWelcomeShown() && ticksInWorld >= WELCOME_DELAY) {
			BedProtectCommands.sendWelcome();
			config.setWelcomeShown(true);
			config.save();
		}
		ResourceKey<Level> dimension = client.level.dimension();
		if (dimension != lastDimension && ticksInWorld >= WELCOME_DELAY) {
			lastDimension = dimension;
			long now = Util.getMillis();
			Long last = REMINDED.get(dimension);
			if (config.isChat() && (dimension == Level.NETHER || dimension == Level.END)
				&& (last == null || now - last >= REMINDER_COOLDOWN_MS)) {
				REMINDED.put(dimension, now);
				BedProtectCommands.sendReminder(player);
			}
		}
		if (client.level.getGameTime() % CHECK_INTERVAL == 0) {
			refreshLookedAt(client, player);
		}
	}

	private static void refreshLookedAt(Minecraft client, LocalPlayer player) {
		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = hit.getBlockPos();
			lookedAt = BlastPredictor.assess(player, pos, client.level.getBlockState(pos));
		} else {
			lookedAt = null;
		}
	}

	static void refreshNow() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.level != null) {
			refreshLookedAt(client, client.player);
		}
	}
}
