package net.ronnylearn.bedprotect.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.ronnylearn.bedprotect.BedProtectHud;
import net.ronnylearn.bedprotect.BedProtectMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class HudMixin {
	@Inject(method = "renderEffects", at = @At("RETURN"))
	private void bedProtect$drawStatus(GuiGraphics graphics, CallbackInfo ci) {
		BedProtectMod.safely("hud", () -> BedProtectHud.extract(graphics, 0.0F));
	}
}
