package net.ronnylearn.bedprotect.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.ronnylearn.bedprotect.BedProtectMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void bedProtect$checkBed(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = BedProtectMod.onUseBlockSafely(player, player.level(), hand, hit);
		if (result != InteractionResult.PASS) {
			cir.setReturnValue(result);
		}
	}
}
