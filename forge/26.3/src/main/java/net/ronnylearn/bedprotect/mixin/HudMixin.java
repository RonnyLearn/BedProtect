package net.ronnylearn.bedprotect.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.ronnylearn.bedprotect.BedProtectHud;
import net.ronnylearn.bedprotect.BedProtectMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {
	@Inject(method = "extractEffects", at = @At("RETURN"))
	private void bedProtect$drawStatus(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		BedProtectMod.safely("hud", () -> BedProtectHud.extract(graphics, deltaTracker));
	}
}
