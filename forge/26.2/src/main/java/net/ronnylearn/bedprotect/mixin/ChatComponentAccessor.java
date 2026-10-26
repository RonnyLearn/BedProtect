package net.ronnylearn.bedprotect.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> getBedProtectAllMessages();

	@Invoker("refreshTrimmedMessages")
	void invokeBedProtectRefreshTrimmedMessages();

	@Invoker("addMessage")
	void invokeBedProtectAddMessage(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag);
}
