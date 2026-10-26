package net.ronnylearn.bedprotect;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class BedProtectHud {
	private static final int WHITE = 0xFFFFFFFF;

	private BedProtectHud() {
	}

	public static void extract(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		BlastAssessment assessment = BedProtectMod.lookedAt();
		BedProtectConfig config = BedProtectMod.config();
		if (assessment == null || !config.isHud() || client.options.hideGui || client.player == null) {
			return;
		}
		Font font = client.font;
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 10;
		graphics.drawCenteredString(font, Texts.statusLine(assessment, assessment.verdict(config), config), x, y, WHITE);
		y += font.lineHeight + 1;
		Component details = Texts.detailsLine(assessment, config);
		if (!details.getString().isEmpty()) {
			graphics.drawCenteredString(font, details, x, y, WHITE);
			y += font.lineHeight + 1;
		}
		Component fall = Texts.fallLine(assessment);
		if (fall != null) {
			graphics.drawCenteredString(font, fall, x, y, WHITE);
			y += font.lineHeight + 1;
		}
		Component server = Texts.serverLine(assessment, config);
		if (server != null) {
			graphics.drawCenteredString(font, server, x, y, WHITE);
		}
	}
}
