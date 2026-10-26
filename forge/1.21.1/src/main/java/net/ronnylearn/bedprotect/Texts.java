package net.ronnylearn.bedprotect;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.Set;

import static net.ronnylearn.bedprotect.Palette.*;

public final class Texts {
	private Texts() {
	}

	public static MutableComponent text(String text, int color) {
		return Component.literal(text).withColor(color);
	}

	public static MutableComponent tr(String key, int color, Object... args) {
		return Component.translatable(key, args).withColor(color);
	}

	public static MutableComponent prefix() {
		return Component.empty().append(text("[", DIM)).append(text("BedProtect", ACCENT)).append(text("] ", DIM));
	}

	public static Component chat(Component message) {
		return Component.empty().append(prefix()).append(message);
	}

	public static String halvesText(int halves) {
		if (halves <= 0) {
			return "0";
		}
		int whole = halves / 2;
		String half = halves % 2 != 0 ? "½" : "";
		return whole == 0 ? half : whole + half;
	}

	public static MutableComponent hearts(int halves, int color) {
		return Component.empty().append(text(halvesText(halves), color)).append(text("❤", HEART));
	}

	public static MutableComponent hearts(float hearts, int color) {
		return hearts(Math.round(hearts * 2.0F), color);
	}

	public static int halvesUp(float hp) {
		return Mth.ceil(Math.max(hp, 0.0F) - 1.0E-4F);
	}

	public static String percent(float fraction) {
		return Integer.toString(Math.round(fraction * 100.0F));
	}

	public static String blocks(double blocks) {
		return String.format(Locale.ROOT, "%.1f", blocks);
	}

	public static MutableComponent onOff(boolean on) {
		return tr(on ? "bedprotect.on" : "bedprotect.off", on ? GOOD : BAD);
	}

	public static MutableComponent bedsState(boolean allowed) {
		return tr(allowed ? "bedprotect.allowed" : "bedprotect.denied", allowed ? WARN : GOOD);
	}

	public static MutableComponent statusLine(BlastAssessment a, BlastAssessment.Verdict verdict, BedProtectConfig config) {
		return switch (verdict) {
			case BLOCKED_DISABLED -> tr("bedprotect.hud.disabled", BAD);
			case BLOCKED_HAZARD -> tr("bedprotect.hud.hazard", BAD, hazards(a.hazards()));
			case BLOCKED_LETHAL -> tr("bedprotect.hud.lethal", DANGER);
			case BLOCKED_DAMAGE -> tr("bedprotect.hud.damage", BAD, hearts(config.getMaxDamageHearts(), BAD));
			case BLOCKED_HEALTH -> tr("bedprotect.hud.health", BAD, hearts(config.getReserveHearts(), BAD));
			case BLOCKED_FALL -> tr("bedprotect.hud.fall", BAD);
			case ALLOWED_SAFE -> tr("bedprotect.hud.safe", GOOD);
			case ALLOWED_UNPROTECTED -> a.lethal() || a.fallCanKill() || !a.hazards().isEmpty()
				? tr("bedprotect.hud.unprotected_lethal", DANGER)
				: tr("bedprotect.hud.unprotected", WARN);
			case ALLOWED_HARMLESS -> tr("bedprotect.hud.harmless", MUTED);
		};
	}

	public static MutableComponent detailsLine(BlastAssessment a, BedProtectConfig config) {
		if (!a.explodes()) {
			return Component.empty();
		}
		if (a.invulnerable()) {
			return tr("bedprotect.hud.details_invulnerable", MUTED);
		}
		int damage = a.lethal() ? halvesUp(a.blastDamage()) : a.damageHalves();
		boolean tooMuch = damage > config.maxDamageHalves();
		boolean low = a.lethal() || a.halvesAfterBlast() < Math.max(config.reserveHalves(), 1);
		return tr("bedprotect.hud.details", MUTED,
			hearts(damage, tooMuch ? BAD : TEXT),
			a.lethal() ? text("☠", DANGER) : hearts(a.halvesAfterBlast(), low ? BAD : GOOD),
			text(percent(1.0F - a.exposure()) + "%", TEXT));
	}

	public static MutableComponent fallLine(BlastAssessment a) {
		if (!a.explodes() || a.invulnerable() || a.fallDamage() <= 0.0F || !a.hazards().isEmpty()) {
			return null;
		}
		return a.fallCanKill()
			? tr("bedprotect.hud.fall_lethal", DANGER, blocks(a.fallDistance()))
			: tr("bedprotect.hud.fall_risk", WARN, hearts(a.fallHalves(), WARN), blocks(a.fallDistance()));
	}

	public static MutableComponent gateLine(PingGuard.Gate gate) {
		return switch (gate) {
			case HELD_KEY -> tr("bedprotect.gate.held", WARN);
			case UNSTABLE -> tr("bedprotect.gate.unstable", WARN, Integer.toString(PingGuard.pingMs()));
			case CONFIRM -> tr("bedprotect.gate.confirm", WARN, Long.toString(PingGuard.CONFIRM_WINDOW_MS / 1000L));
			case PASS -> Component.empty();
		};
	}

	public static MutableComponent serverLine(BlastAssessment a, BedProtectConfig config) {
		if (!config.isPingGuard() || !PingGuard.onServer() || !a.explodes() || !a.verdict(config).allowed()) {
			return null;
		}
		if (PingGuard.awaitingConfirm(a.headPos())) {
			return tr("bedprotect.hud.confirm_pending", WARN);
		}
		return tr(config.isConfirm() ? "bedprotect.hud.server_confirm" : "bedprotect.hud.server", MUTED,
			Integer.toString(PingGuard.pingMs()));
	}

	public static MutableComponent hazards(Set<BlastAssessment.Hazard> hazards) {
		MutableComponent list = Component.empty();
		boolean first = true;
		for (BlastAssessment.Hazard hazard : hazards) {
			if (!first) {
				list.append(", ");
			}
			list.append(Component.translatable("bedprotect.hazard." + hazard.name().toLowerCase(Locale.ROOT)));
			first = false;
		}
		return list;
	}
}
