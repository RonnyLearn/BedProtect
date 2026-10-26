package net.ronnylearn.bedprotect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BedProtectConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("bedprotect.json");

	public static final float DEFAULT_MAX_DAMAGE_HEARTS = 5.0F;
	public static final float DEFAULT_RESERVE_HEARTS = 2.0F;
	public static final float MIN_MAX_DAMAGE = 0.5F;
	public static final float MAX_MAX_DAMAGE = 20.0F;
	public static final float MIN_RESERVE = 0.0F;
	public static final float MAX_RESERVE = 10.0F;

	private boolean bedsAllowed = false;
	private boolean protection = true;
	private boolean hud = true;
	private boolean chat = true;
	private float maxDamageHearts = DEFAULT_MAX_DAMAGE_HEARTS;
	private float reserveHearts = DEFAULT_RESERVE_HEARTS;
	private boolean pingGuard = true;
	private boolean confirm = true;
	private boolean blockMessages = true;
	private boolean confirmations = true;
	private boolean welcomeShown = false;

	public boolean isBlockMessages() {
		return blockMessages;
	}

	public void setBlockMessages(boolean blockMessages) {
		this.blockMessages = blockMessages;
	}

	public boolean isConfirmations() {
		return confirmations;
	}

	public void setConfirmations(boolean confirmations) {
		this.confirmations = confirmations;
	}

	public boolean isPingGuard() {
		return pingGuard;
	}

	public void setPingGuard(boolean pingGuard) {
		this.pingGuard = pingGuard;
	}

	public boolean isConfirm() {
		return confirm;
	}

	public void setConfirm(boolean confirm) {
		this.confirm = confirm;
	}

	public boolean isBedsAllowed() {
		return bedsAllowed;
	}

	public void setBedsAllowed(boolean bedsAllowed) {
		this.bedsAllowed = bedsAllowed;
	}

	public boolean isProtection() {
		return protection;
	}

	public void setProtection(boolean protection) {
		this.protection = protection;
	}

	public boolean isHud() {
		return hud;
	}

	public void setHud(boolean hud) {
		this.hud = hud;
	}

	public boolean isChat() {
		return chat;
	}

	public void setChat(boolean chat) {
		this.chat = chat;
	}

	public float getMaxDamageHearts() {
		return maxDamageHearts;
	}

	public void setMaxDamageHearts(float maxDamageHearts) {
		this.maxDamageHearts = clampOr(roundToHalf(maxDamageHearts), MIN_MAX_DAMAGE, MAX_MAX_DAMAGE, DEFAULT_MAX_DAMAGE_HEARTS);
	}

	public int maxDamageHalves() {
		return Math.round(maxDamageHearts * 2.0F);
	}

	public float getReserveHearts() {
		return reserveHearts;
	}

	public void setReserveHearts(float reserveHearts) {
		this.reserveHearts = clampOr(roundToHalf(reserveHearts), MIN_RESERVE, MAX_RESERVE, DEFAULT_RESERVE_HEARTS);
	}

	public int reserveHalves() {
		return Math.round(reserveHearts * 2.0F);
	}

	public boolean isWelcomeShown() {
		return welcomeShown;
	}

	public void setWelcomeShown(boolean welcomeShown) {
		this.welcomeShown = welcomeShown;
	}

	public void reset() {
		BedProtectConfig defaults = new BedProtectConfig();
		bedsAllowed = defaults.bedsAllowed;
		protection = defaults.protection;
		hud = defaults.hud;
		chat = defaults.chat;
		pingGuard = defaults.pingGuard;
		confirm = defaults.confirm;
		blockMessages = defaults.blockMessages;
		confirmations = defaults.confirmations;
		maxDamageHearts = defaults.maxDamageHearts;
		reserveHearts = defaults.reserveHearts;
	}

	public static BedProtectConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				BedProtectConfig config = GSON.fromJson(reader, BedProtectConfig.class);
				if (config != null) {
					config.clamp();
					return config;
				}
			} catch (IOException | JsonParseException e) {
				BedProtectMod.LOGGER.error("[BedProtect] Could not read {}, using defaults", FILE, e);
			}
		}
		BedProtectConfig config = new BedProtectConfig();
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			BedProtectMod.LOGGER.error("[BedProtect] Could not save {}", FILE, e);
		}
	}

	private void clamp() {
		setMaxDamageHearts(maxDamageHearts);
		setReserveHearts(reserveHearts);
	}

	private static float roundToHalf(float hearts) {
		return Math.round(hearts * 2.0F) / 2.0F;
	}

	private static float clampOr(float value, float min, float max, float fallback) {
		if (Float.isNaN(value) || Float.isInfinite(value)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}
}
