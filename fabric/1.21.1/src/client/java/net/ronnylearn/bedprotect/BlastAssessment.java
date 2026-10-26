package net.ronnylearn.bedprotect;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.Set;

public record BlastAssessment(
	BlockPos headPos,
	boolean explodes,
	boolean invulnerable,
	double distance,
	float exposure,
	float scaledDamage,
	float afterArmor,
	float afterEffects,
	float protection,
	int brokenArmor,
	float blastDamage,
	double knockback,
	double fallDistance,
	float fallDamage,
	float health,
	float absorption,
	Set<Hazard> hazards
) {
	public enum Hazard {
		EXPLOSIVES,
		BURNING,
		IN_LAVA,
		GLIDING,
		LAVA_NEARBY,
		DEADLY_DROP
	}

	public float healthAfterBlast() {
		return health - Math.max(blastDamage - absorption, 0.0F);
	}

	public float absorptionAfterBlast() {
		return Math.max(absorption - blastDamage, 0.0F);
	}

	public boolean lethal() {
		return explodes && healthAfterBlast() <= 0.0F;
	}

	public boolean fallCanKill() {
		return explodes && healthAfterBlast() - Math.max(fallDamage - absorptionAfterBlast(), 0.0F) <= 0.0F;
	}

	public int halvesNow() {
		return halves(health, absorption);
	}

	public int halvesAfterBlast() {
		return lethal() ? 0 : halves(healthAfterBlast(), absorptionAfterBlast());
	}

	public int damageHalves() {
		return halvesNow() - halvesAfterBlast();
	}

	public int halvesAfterFall() {
		if (fallCanKill()) {
			return 0;
		}
		float absorptionLeft = Math.max(absorptionAfterBlast() - fallDamage, 0.0F);
		float healthLeft = healthAfterBlast() - Math.max(fallDamage - absorptionAfterBlast(), 0.0F);
		return halves(healthLeft, absorptionLeft);
	}

	public int fallHalves() {
		return halvesAfterBlast() - halvesAfterFall();
	}

	static int halves(float health, float absorption) {
		return Mth.ceil(Math.max(health, 0.0F)) + Mth.ceil(Math.max(absorption, 0.0F));
	}

	public Verdict verdict(BedProtectConfig config) {
		if (!config.isBedsAllowed()) {
			return Verdict.BLOCKED_DISABLED;
		}
		if (!explodes) {
			return Verdict.ALLOWED_HARMLESS;
		}
		if (!config.isProtection()) {
			return Verdict.ALLOWED_UNPROTECTED;
		}
		if (!hazards.isEmpty()) {
			return Verdict.BLOCKED_HAZARD;
		}
		if (lethal()) {
			return Verdict.BLOCKED_LETHAL;
		}
		if (damageHalves() > config.maxDamageHalves()) {
			return Verdict.BLOCKED_DAMAGE;
		}
		if (halvesAfterBlast() < config.reserveHalves()) {
			return Verdict.BLOCKED_HEALTH;
		}
		if (fallCanKill()) {
			return Verdict.BLOCKED_FALL;
		}
		return Verdict.ALLOWED_SAFE;
	}

	public enum Verdict {
		BLOCKED_DISABLED(false),
		BLOCKED_HAZARD(false),
		BLOCKED_LETHAL(false),
		BLOCKED_DAMAGE(false),
		BLOCKED_HEALTH(false),
		BLOCKED_FALL(false),
		ALLOWED_SAFE(true),
		ALLOWED_UNPROTECTED(true),
		ALLOWED_HARMLESS(true);

		private final boolean allowed;

		Verdict(boolean allowed) {
			this.allowed = allowed;
		}

		public boolean allowed() {
			return allowed;
		}
	}
}
