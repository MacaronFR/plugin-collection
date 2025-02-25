package fr.pickaria.job

import fr.pickaria.job.jobs.*
import fr.pickaria.shared.models.Job
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getServer
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

class JobController(private val plugin: Main) : Listener {
	private val bossBars: ConcurrentHashMap<Player, BossBar> = ConcurrentHashMap()
	private val bossBarsTasks: ConcurrentHashMap<BossBar, BukkitTask> = ConcurrentHashMap()

	init {
		getServer().pluginManager.run {
			registerEvents(this@JobController, plugin)

			registerEvents(Miner(), plugin)
			registerEvents(Hunter(), plugin)
			registerEvents(Farmer(), plugin)
			registerEvents(Breeder(), plugin)
			registerEvents(Alchemist(), plugin)
			registerEvents(Wizard(), plugin)
			registerEvents(Trader(), plugin)
		}
	}

	@EventHandler
	fun onPlayerQuit(event: PlayerQuitEvent) {
		bossBars.remove(event.player)
	}

	fun hasJob(playerUuid: UUID, jobName: String): Boolean {
		val job = Job.get(playerUuid, jobName)
		return job?.active == true
	}

	fun jobCount(playerUuid: UUID): Int = Job.get(playerUuid).filter { it.active }.size

	fun getCooldown(playerUuid: UUID, jobName: String): Int {
		val previousDay = LocalDateTime.now().minusHours(jobConfig.cooldown)

		val job = Job.get(playerUuid, jobName)
		return if (job == null || !job.active) {
			0
		} else {
			previousDay.until(job.lastUsed, ChronoUnit.MINUTES).minutes.toInt(DurationUnit.MINUTES)
		}
	}

	fun joinJob(playerUuid: UUID, jobName: String) {
		Job.get(playerUuid, jobName)?.apply {
			active = true
			lastUsed = LocalDateTime.now()
		} ?: run {
			Job.create(playerUuid, jobName, true)
		}
	}

	fun leaveJob(playerUuid: UUID, jobName: String) {
		Job.get(playerUuid, jobName)?.apply {
			active = false
		}
	}

	private fun getExperienceFromLevel(job: JobConfig.Configuration, level: Int): Int {
		return if (level >= 0) {
			ceil(job.startExperience * job.experiencePercentage.pow(level) + level * job.multiplier).toInt()
		} else {
			0
		}
	}

	fun getLevelFromExperience(job: JobConfig.Configuration, experience: Int): Int {
		var level = 0
		var levelExperience = job.startExperience.toDouble()
		while ((levelExperience + level * job.multiplier) < experience && level < jobConfig.maxLevel) {
			levelExperience *= job.experiencePercentage
			level++
		}
		return level
	}

	private fun addExperience(playerUuid: UUID, job: JobConfig.Configuration, exp: Int): JobErrorEnum {
		return Job.get(playerUuid, job.key)?.let {
			val previousLevel = getLevelFromExperience(job, it.experience)
			val newLevel = getLevelFromExperience(job, it.experience + exp)
			it.experience += exp

			val isNewLevel = newLevel > previousLevel

			return if (isNewLevel) {
				if (newLevel >= jobConfig.maxLevel) {
					JobErrorEnum.MAX_LEVEL_REACHED
				} else {
					JobErrorEnum.NEW_LEVEL
				}
			} else {
				JobErrorEnum.NOTHING
			}
		} ?: JobErrorEnum.NOTHING
	}

	fun addExperienceAndAnnounce(player: Player, job: JobConfig.Configuration, exp: Int): JobErrorEnum {
		return addExperience(player.uniqueId, job, exp).also {
			val experience = Job.get(player.uniqueId, job.key)?.experience ?: 0
			val level = getLevelFromExperience(job, experience)
			val currentLevelExperience = getExperienceFromLevel(job, level - 1)
			val nextLevelExperience = getExperienceFromLevel(job, level)
			val levelDiff = abs(nextLevelExperience - currentLevelExperience)
			val diff = abs(experience - currentLevelExperience)

			val bossBar: BossBar = bossBars[player] ?: run {
				val bossBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID)
				bossBar.addPlayer(player)
				bossBars[player] = bossBar
				bossBar
			}

			bossBar.setTitle("${job.label} | Niveau $level ($experience / $nextLevelExperience)")
			bossBar.isVisible = true
			bossBar.progress = (diff / levelDiff.toDouble()).coerceAtLeast(0.0).coerceAtMost(1.0)

			bossBarsTasks[bossBar]?.cancel()

			bossBarsTasks[bossBar] = object : BukkitRunnable() {
				override fun run() {
					bossBar.isVisible = false
				}
			}.runTaskLater(plugin, 80)

			if (it == JobErrorEnum.NEW_LEVEL) {
				player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
				player.sendMessage("§7Vous montez niveau §6$level§7 dans le métier §6${job.label}§7.")
			} else if (it == JobErrorEnum.MAX_LEVEL_REACHED) {
				player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f)
				player.sendMessage("§7Vous montez niveau §6$level§7 dans le métier §6${job.label}§7.")

				val mainTitle = Component.text("Niveau maximum atteint", NamedTextColor.GOLD);
				val subtitle = Component.text("Vous avez atteint le niveau maximum dans le métier ${job.label} !", NamedTextColor.GRAY);
				val title = Title.title(mainTitle, subtitle);

				player.showTitle(title);
			}
		}
	}
}
