//
// MIT License
//
// Copyright (c) 2022 Alexander Söderberg & Contributors
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package cloud.commandframework.arguments.suggestion;

import cloud.commandframework.CommandManager;
import cloud.commandframework.CommandTree;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.context.CommandContextFactory;
import cloud.commandframework.context.CommandInput;
import cloud.commandframework.services.State;
import cloud.commandframework.setting.ManagerSetting;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Command suggestion engine that delegates to a {@link cloud.commandframework.CommandTree}
 *
 * @param <C> command sender type
 * @param <S> suggestion type
 */
@API(status = API.Status.INTERNAL, consumers = "cloud.commandframework.*")
final class DelegatingSuggestionFactory<C, S extends Suggestion> implements SuggestionFactory<C, S> {

    private static final List<Suggestion> SINGLE_EMPTY_SUGGESTION =
            Collections.unmodifiableList(Collections.singletonList(Suggestion.simple("")));

    private final CommandManager<C> commandManager;
    private final CommandTree<C> commandTree;
    private final SuggestionMapper<S> suggestionMapper;
    private final CommandContextFactory<C> contextFactory;

    DelegatingSuggestionFactory(
            final @NonNull CommandManager<C> commandManager,
            final @NonNull CommandTree<C> commandTree,
            final @NonNull SuggestionMapper<S> suggestionMapper,
            final @NonNull CommandContextFactory<C> contextFactory
    ) {
        this.commandManager = commandManager;
        this.commandTree = commandTree;
        this.suggestionMapper = suggestionMapper;
        this.contextFactory = contextFactory;
    }

    @Override
    public @NonNull CompletableFuture<List<@NonNull S>> suggest(
            final @NonNull CommandContext<C> context,
            final @NonNull String input
    ) {
        return this.suggestFromTree(context, input).thenApply(suggestions -> suggestions.stream()
                .map(this.suggestionMapper::map)
                .collect(Collectors.toList())
        );
    }

    @Override
    public @NonNull CompletableFuture<List<@NonNull S>> suggest(final @NonNull C sender, final @NonNull String input) {
        return this.suggest(this.contextFactory.create(true /* suggestions */, sender), input);
    }

    private @NonNull CompletableFuture<List<? extends @NonNull Suggestion>> suggestFromTree(
            final @NonNull CommandContext<C> context,
            final @NonNull String input
    ) {
        final @NonNull CommandInput commandInput = CommandInput.of(input);
        /* Store a copy of the input queue in the context */
        context.store("__raw_input__", commandInput.copy());

        if (this.commandManager.preprocessContext(context, commandInput) != State.ACCEPTED) {
            if (this.commandManager.settings().get(ManagerSetting.FORCE_SUGGESTION)) {
                return CompletableFuture.completedFuture(SINGLE_EMPTY_SUGGESTION);
            }
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return this.commandTree.getSuggestions(context, commandInput).thenApply(suggestions -> {
            if (this.commandManager.settings().get(ManagerSetting.FORCE_SUGGESTION) && suggestions.isEmpty()) {
                return SINGLE_EMPTY_SUGGESTION;
            }
            return suggestions;
        });
    }
}