package net.ronnylearn.bedprotect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.Util;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

public final class PingGuard {
	public static final long CONFIRM_WINDOW_MS = 2000L;
	private static final long MIN_STABLE_MS = 300L;
	private static final long STABLE_MARGIN_MS = 150L;
	private static final long HEALTH_WINDOW_MS = 1000L;

	public enum Gate {
		PASS,
		HELD_KEY,
		UNSTABLE,
		CONFIRM
	}

	private record Sample(long time, float health, float absorption) {
	}

	private static final ArrayDeque<Sample> SAMPLES = new ArrayDeque<>();
	private static long clientTick;
	private static long pressTick = Long.MIN_VALUE / 2;
	private static boolean pressUsed = true;
	private static @Nullable BlockPos allowedHead;
	private static long allowedSince;
	private static @Nullable BlockPos pendingHead;
	private static long pendingUntil;

	private PingGuard() {
	}

	public static void markFreshPress() {
		pressTick = clientTick;
		pressUsed = false;
	}

	static void tick(@Nullable Player player, @Nullable BlastAssessment lookedAt, BedProtectConfig config) {
		clientTick++;
		long now = Util.getMillis();
		if (player == null) {
			SAMPLES.clear();
			allowedHead = null;
			pendingHead = null;
			return;
		}
		SAMPLES.addLast(new Sample(now, player.getHealth(), player.getAbsorptionAmount()));
		long window = HEALTH_WINDOW_MS + 2L * pingMs();
		while (!SAMPLES.isEmpty() && now - SAMPLES.peekFirst().time() > window) {
			SAMPLES.removeFirst();
		}
		if (lookedAt != null && lookedAt.verdict(config).allowed()) {
			if (!lookedAt.headPos().equals(allowedHead)) {
				allowedHead = lookedAt.headPos();
				allowedSince = now;
			}
		} else {
			allowedHead = null;
		}
		if (pendingHead != null && now > pendingUntil) {
			pendingHead = null;
		}
	}

	static Gate check(BlastAssessment assessment, BedProtectConfig config) {
		boolean fresh = !pressUsed && clientTick - pressTick <= 1;
		pressUsed = true;
		if (!config.isPingGuard()) {
			return Gate.PASS;
		}
		if (!fresh) {
			return Gate.HELD_KEY;
		}
		if (!onServer()) {
			return Gate.PASS;
		}
		long now = Util.getMillis();
		if (!assessment.headPos().equals(allowedHead) || now - allowedSince < stableTimeMs()) {
			return Gate.UNSTABLE;
		}
		if (config.isConfirm()) {
			if (assessment.headPos().equals(pendingHead) && now <= pendingUntil) {
				pendingHead = null;
				return Gate.PASS;
			}
			pendingHead = assessment.headPos();
			pendingUntil = now + CONFIRM_WINDOW_MS;
			return Gate.CONFIRM;
		}
		return Gate.PASS;
	}

	public static boolean awaitingConfirm(BlockPos head) {
		return head.equals(pendingHead) && Util.getMillis() <= pendingUntil;
	}

	public static long stableTimeMs() {
		return Math.max(MIN_STABLE_MS, pingMs() + STABLE_MARGIN_MS);
	}

	public static boolean onServer() {
		Minecraft client = Minecraft.getInstance();
		return client.getConnection() != null && !client.isLocalServer();
	}

	public static int pingMs() {
		Minecraft client = Minecraft.getInstance();
		ClientPacketListener connection = client.getConnection();
		if (connection == null || client.player == null || client.isLocalServer()) {
			return 0;
		}
		PlayerInfo info = connection.getPlayerInfo(client.player.getUUID());
		return info == null ? 0 : Math.max(info.getLatency(), 0);
	}

	static float health(Player player) {
		float health = player.getHealth();
		if (onServer()) {
			for (Sample sample : SAMPLES) {
				health = Math.min(health, sample.health());
			}
		}
		return health;
	}

	static float absorption(Player player) {
		float absorption = player.getAbsorptionAmount();
		if (onServer()) {
			for (Sample sample : SAMPLES) {
				absorption = Math.min(absorption, sample.absorption());
			}
		}
		return absorption;
	}
}
