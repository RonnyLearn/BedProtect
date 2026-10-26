package net.ronnylearn.bedprotect.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.ronnylearn.bedprotect.ForgeCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleCommands", at = @At("RETURN"))
	private void bedProtect$addCommands(ClientboundCommandsPacket packet, CallbackInfo ci) {
		ForgeCommands.addSuggestions(((ClientPacketListener) (Object) this).getCommands());
	}

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void bedProtect$sendCommand(String command, CallbackInfo ci) {
		if (ForgeCommands.handle(command)) {
			ci.cancel();
		}
	}

	@Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
	private void bedProtect$sendUnsignedCommand(String command, CallbackInfoReturnable<Boolean> cir) {
		if (ForgeCommands.handle(command)) {
			cir.setReturnValue(true);
		}
	}
}
