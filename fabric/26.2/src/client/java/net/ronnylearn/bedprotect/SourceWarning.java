package net.ronnylearn.bedprotect;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

import java.net.URI;

public final class SourceWarning {
	public static final String OFFICIAL_PAGE = "https://modrinth.com/user/RonnyLearn";
	private static final String LINE = "━".repeat(40);
	private static OfficialSource source;

	private SourceWarning() {
	}

	static void tick(Minecraft client) {
		if (source == null) {
			source = new OfficialSource(BedProtectConfig.directory().toFile(), BedProtectMod.MOD_ID,
				"net/ronnylearn/bedprotect/BlastPredictor.class", "modrinth.com");
		}
		source.tick(client.player != null, SourceWarning::show);
	}

	static void hide() {
		if (source != null) {
			source.dismiss();
		}
		send(Component.translatable("bedprotect.source.hidden").withStyle(ChatFormatting.GRAY));
	}

	private static void show() {
		send(Component.literal(LINE).withStyle(ChatFormatting.DARK_RED));
		send(Component.translatable("bedprotect.source.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		send(Component.translatable("bedprotect.source.danger").withStyle(ChatFormatting.RED));
		send(Component.translatable("bedprotect.source.support").withStyle(ChatFormatting.GOLD));
		send(Component.translatable("bedprotect.source.link").withStyle(style -> style
			.withColor(ChatFormatting.AQUA).withUnderlined(true)
			.withClickEvent(new ClickEvent.OpenUrl(URI.create(OFFICIAL_PAGE)))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(OFFICIAL_PAGE)))));
		send(Component.translatable("bedprotect.source.hide").withStyle(style -> style
			.withColor(ChatFormatting.GRAY)
			.withClickEvent(new ClickEvent.RunCommand("/bedprotect hidewarning"))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("bedprotect.source.hide.hover")))));
		send(Component.literal(LINE).withStyle(ChatFormatting.DARK_RED));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.125F));
	}

	private static void send(Component message) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(message);
		}
	}
}
