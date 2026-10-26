package net.ronnylearn.bedprotect;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ForgeCommands {
	private static final CommandDispatcher<Object> DISPATCHER = new CommandDispatcher<>();

	static {
		BedProtectCommands.register(DISPATCHER);
	}

	private ForgeCommands() {
	}

	public static <S> void addSuggestions(CommandDispatcher<S> dispatcher) {
		BedProtectCommands.register(dispatcher);
	}

	public static boolean handle(String command) {
		String line = command.startsWith("/") ? command.substring(1) : command;
		if (!line.equals("bedprotect") && !line.startsWith("bedprotect ")) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		try {
			DISPATCHER.execute(line, client);
		} catch (CommandSyntaxException e) {
			ChatPanels.line(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED));
		} catch (RuntimeException e) {
			BedProtectMod.LOGGER.error("[BedProtect] Command /{} failed", line, e);
		}
		return true;
	}
}
