package circlet.cli

import circlet.cli.confluence.ConfluenceCommand
import com.github.ajalt.clikt.completion.CompletionCommand
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        return Space().subcommands(
            ConfluenceCommand(),
            CompletionCommand()
        ).main(args)
    } catch (e: Exception) {
        println("Error: ${e.message}")
        exitProcess(1)
    }
}

class Space : CliktCommand(printHelpOnEmptyArgs = true) {
    override fun run() {

    }
}

