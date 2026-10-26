package net.ronnylearn.bedprotect.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.ronnylearn.bedprotect.PingGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
	@Inject(method = "consumeClick", at = @At("RETURN"))
	private void bedProtect$markFreshUse(CallbackInfoReturnable<Boolean> cir) {
		Minecraft client = Minecraft.getInstance();
		if (cir.getReturnValueZ() && client.options != null && (Object) this == client.options.keyUse) {
			PingGuard.markFreshPress();
		}
	}
}
