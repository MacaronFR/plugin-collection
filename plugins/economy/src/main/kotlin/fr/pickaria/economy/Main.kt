package fr.pickaria.economy

import org.bukkit.plugin.java.JavaPlugin

class Main: JavaPlugin() {
	override fun onEnable() {
		super.onEnable()

		logger.info("Economy plugin loaded!")

		getCommand("test")?.setExecutor(TestCommand())
	}
}