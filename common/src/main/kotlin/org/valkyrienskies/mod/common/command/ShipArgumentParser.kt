package org.valkyrienskies.mod.common.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.network.chat.Component
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.mod.mixinducks.feature.command.VSCommandSource

class ShipArgumentParser(private val source: VSCommandSource?, private var selectorOnly: Boolean) {
    private val shipOptions = setOf("slug", "limit", "id")
    private val selectorType = 'v'

    var suggestionProvider: (SuggestionsBuilder) -> Unit = {}
    var slug: String? = null
    var limit: Int? = null
    var id: ShipId? = null

    fun parse(reader: StringReader, isForSuggestion: Boolean): ShipSelector {
        val start = reader.cursor

        suggestSelectorOrSlug()

        if (!reader.canRead())
            throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(reader)

        // Read the selector type
        if (reader.read() == '@') {
            if (!reader.canRead()) {
                // Suggest "v"
                suggest { builder, _ ->
                    builder.suggest(selectorType.toString())
                }
            } else {
                val parsedSelectorType = reader.read()
                if (parsedSelectorType != selectorType) {
                    throw ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(reader, parsedSelectorType.toString())
                }

                if (!reader.canRead()) {
                    suggestOpenOptions()
                } else if (reader.read() == '[') {
                    val parsedOptions = hashSetOf<String>()
                    if (!reader.canRead()) {
                        suggestOptions()
                    } else {
                        // Read the selector arguments ex @v[slug=mogus,limit=1]
                        while (reader.canRead() && reader.peek() != ']') {
                            val i = reader.cursor
                            val s = reader.readString()

                            suggestOptions(s)
                            reader.skipWhitespace()

                            if (!isOption(s)) {
                                if (isForSuggestion) {
                                    reader.cursor = i
                                    throw ERROR_UNKNOWN_OPTION.createWithContext(reader, s)
                                }
                                // If not for suggestion then we cannot throw an exception
                                // otherwise MC won't generate suggestions for this argument
                                return ShipSelector(null, null, 0)
                            }

                            if (!parsedOptions.add(s)) {
                                throw ERROR_DUPLICATE_OPTION.createWithContext(reader, s)
                            }

                            reader.skipWhitespace()

                            suggestEquals()

                            if (!reader.canRead() || reader.peek() != '=') {
                                throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, s)
                            }

                            reader.skip()
                            reader.skipWhitespace()

                            suggestionsOfOption(s)

                            reader.skipWhitespace()
                            if (reader.canRead())
                                parseOption(s, reader)
                            else throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, s)
                            reader.skipWhitespace()

                            suggestOptionsNextOrClose()
                            reader.skipWhitespace()
                        }
                    }

                    if (!reader.canRead() || reader.read() != ']')
                        if (isForSuggestion) {
                            throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(reader)
                        }else {
                            // If not for suggestion then we cannot throw an exception
                            // otherwise MC won't generate suggestions for this argument
                            return ShipSelector(null, null, 0)
                        }

                    suggest { _, _ -> }
                }
            }
        } else if (!selectorOnly) {
            suggestionsOfOption("slug")
            // Reset cursor
            reader.cursor = start
            slug = reader.readUnquotedString()
        }

        return ShipSelector(slug, id, limit ?: Int.MAX_VALUE)
    }

    private fun isOption(s: String): Boolean = s in shipOptions

    private fun suggestOptions(textSoFar: String? = null) = suggest { builder, _ ->
        val optionSuggestions = shipOptions.asSequence().map { "$it=" }
        if (textSoFar == null) {
            optionSuggestions.forEach(builder::suggest)
        } else {
            optionSuggestions.filter { it.startsWith(textSoFar) }.forEach(builder::suggest)
        }
    }

    private fun suggestSelectorOrSlug() = suggest { builder, source ->
        builder.suggest("@v")
        if (!selectorOnly) {
            source.shipWorld.allShips
                .mapNotNull { it.slug }
                .filter { it.startsWith(builder.remaining) }
                .forEach { builder.suggest(it) }
        }
    }

    fun suggestionsOfOption(option: String): Unit = when (option) {
        "slug" ->
            suggest { builder, source ->
                source.shipWorld.allShips
                    .mapNotNull { it.slug }
                    .filter { it.startsWith(builder.remaining) }
                    .forEach { builder.suggest(it) }
            }
        "limit" -> {}
        "id" ->
            suggest { builder, source ->
                source.shipWorld.allShips
                    .map { it.id.toString() }
                    .filter { it.startsWith(builder.remaining) }
                    .forEach { builder.suggest(it) }
            }
        else -> throw ERROR_UNKNOWN_OPTION.create(option)
    }

    fun parseOption(option: String, reader: StringReader): Unit = when (option) {
        "slug" -> {
            val start = reader.cursor
            val slug = reader.readUnquotedString()

            if (source?.shipWorld?.allShips?.any { it.slug == slug } == false) {
                reader.cursor = start
                throw ERROR_INVALID_SLUG_OR_ID.create()
            }

            this.slug = slug
        }
        "id" -> id = reader.readLong()
        "limit" -> limit = reader.readInt()
        else -> throw ERROR_UNKNOWN_OPTION.create(option)
    }

    private fun suggestEquals() = suggest { builder, _ ->
        builder.suggest('='.toString())
    }

    private fun suggestOpenOptions() = suggest { builder, _ ->
        builder.suggest("[")
    }

    private fun suggestOptionsNextOrClose() = suggest { builder, _ ->
        builder.suggest(",")
        builder.suggest("]")
    }

    private fun suggest(builder: (SuggestionsBuilder, VSCommandSource) -> Unit) {
        if (source != null) suggestionProvider = { builder(it, source) }
    }

    private fun StringReader.expectR(s: Char) {
        suggest { builder, source -> builder.suggest(s.toString()) }
        this.expect(s)
    }

    companion object {
        val ERROR_MISSING_SELECTOR_TYPE =
            SimpleCommandExceptionType(Component.translatable("argument.ship.selector.missing"))
        val ERROR_INVALID_SLUG_OR_ID =
            SimpleCommandExceptionType(Component.translatable("argument.ship.invalid"))
        val ERROR_EXPECTED_END_OF_OPTIONS =
            SimpleCommandExceptionType(Component.translatable("argument.ship.options.unterminated"))
        val ERROR_EXPECTED_OPTION_VALUE =
            DynamicCommandExceptionType { Component.translatable("argument.ship.options.valueless", it) }
        val ERROR_UNKNOWN_OPTION =
            DynamicCommandExceptionType { Component.translatable("argument.entity.options.unknown", it) }
        val ERROR_DUPLICATE_OPTION =
            DynamicCommandExceptionType { Component.translatable("argument.ship.options.duplicate", it) }
        val ERROR_UNKNOWN_SELECTOR_TYPE =
            DynamicCommandExceptionType { Component.translatable("argument.entity.selector.unknown", it) }
    }
}
