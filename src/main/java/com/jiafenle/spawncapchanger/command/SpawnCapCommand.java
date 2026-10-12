package com.jiafenle.spawncapchanger.command;

import com.jiafenle.spawncapchanger.core.SpawnCapManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.MobCategory;

import java.util.Arrays;

/**
 * Registers the {@code /spawncap} command tree:
 * <pre>
 * /spawncap                         – list all caps
 * /spawncap list                    – list all caps
 * /spawncap get &lt;category&gt;          – show one cap
 * /spawncap set &lt;category&gt; &lt;value&gt; – change one cap (takes effect immediately)
 * /spawncap setall &lt;value&gt;         – change every natural-spawn category at once (misc excluded)
 * /spawncap reset [category]        – reset one category, or all of them, to vanilla defaults
 * </pre>
 * Requires permission level 2 (gamemaster / op), like vanilla game rules.
 */
public final class SpawnCapCommand {

    private static final DynamicCommandExceptionType ERROR_UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(name ->
                    Component.translatable("command.spawncapchanger.unknown.category", name));

    /** 100k per chunk is absurdly high but keeps the argument bounded; -1 means "unlimited" (misc default). */
    private static final int MIN_CAP = -1;
    private static final int MAX_CAP = 100_000;

    private SpawnCapCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawncap")
                .requires(source -> source.hasPermission(2))
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("get")
                        .then(categoryArgument()
                                .executes(SpawnCapCommand::get)))
                .then(Commands.literal("set")
                        .then(categoryArgument()
                                .then(Commands.argument("value",
                                                IntegerArgumentType.integer(MIN_CAP, MAX_CAP))
                                        .executes(SpawnCapCommand::set))))
                .then(Commands.literal("setall")
                        .then(Commands.argument("value",
                                        IntegerArgumentType.integer(0, MAX_CAP))
                                .executes(SpawnCapCommand::setAll)))
                .then(Commands.literal("reset")
                        .executes(context -> resetAll(context.getSource()))
                        .then(categoryArgument()
                                .executes(SpawnCapCommand::resetOne))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> categoryArgument() {
        return Commands.argument("category", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(MobCategory.values())
                                .map(MobCategory::getSerializedName)
                                .toList(),
                        builder));
    }

    private static MobCategory getCategory(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "category");
        MobCategory category = SpawnCapManager.byName(name);
        if (category == null) {
            throw ERROR_UNKNOWN_CATEGORY.create(name);
        }
        return category;
    }

    private static int list(CommandSourceStack source) {
        MutableComponent message = Component.translatable("command.spawncapchanger.list.header");
        for (MobCategory category : MobCategory.values()) {
            message.append("\n")
                    .append(Component.translatable("command.spawncapchanger.list.entry",
                            category.getSerializedName(),
                            SpawnCapManager.getCap(category),
                            SpawnCapManager.getVanillaDefault(category)));
        }
        source.sendSuccess(() -> message, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MobCategory category = getCategory(context);
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("command.spawncapchanger.get.result",
                category.getSerializedName(),
                SpawnCapManager.getCap(category),
                SpawnCapManager.getVanillaDefault(category)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MobCategory category = getCategory(context);
        int value = IntegerArgumentType.getInteger(context, "value");
        CommandSourceStack source = context.getSource();

        if (!SpawnCapManager.isAvailable()) {
            source.sendFailure(Component.translatable("command.spawncapchanger.unavailable"));
            return 0;
        }
        if (!SpawnCapManager.setCap(category, value)) {
            source.sendFailure(Component.translatable("command.spawncapchanger.unavailable"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.spawncapchanger.set.success",
                category.getSerializedName(), value), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setAll(CommandContext<CommandSourceStack> context) {
        int value = IntegerArgumentType.getInteger(context, "value");
        CommandSourceStack source = context.getSource();

        if (!SpawnCapManager.isAvailable()) {
            source.sendFailure(Component.translatable("command.spawncapchanger.unavailable"));
            return 0;
        }

        int changed = 0;
        for (MobCategory category : MobCategory.values()) {
            if (category.getSerializedName().equals(SpawnCapManager.MISC)) {
                continue;
            }
            if (SpawnCapManager.setCap(category, value)) {
                changed++;
            }
        }

        int changedFinal = changed;
        source.sendSuccess(() -> Component.translatable("command.spawncapchanger.setall.success",
                changedFinal, value), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetOne(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        MobCategory category = getCategory(context);
        CommandSourceStack source = context.getSource();

        if (!SpawnCapManager.resetCap(category)) {
            source.sendFailure(Component.translatable("command.spawncapchanger.unavailable"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.spawncapchanger.reset.success",
                category.getSerializedName(), SpawnCapManager.getVanillaDefault(category)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetAll(CommandSourceStack source) {
        if (!SpawnCapManager.resetAll()) {
            source.sendFailure(Component.translatable("command.spawncapchanger.unavailable"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable("command.spawncapchanger.resetall.success"), true);
        return Command.SINGLE_SUCCESS;
    }
}
