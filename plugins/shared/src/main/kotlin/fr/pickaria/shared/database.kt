package fr.pickaria.shared

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

fun openDatabase(path: String) {
	// DB_CLOSE_DELAY: Reuse connection
	// AUTO_SERVER: Enable automatic mixed mode
	Database.connect("jdbc:h2:$path;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE", "org.h2.Driver")

	transaction {
		SchemaUtils.create(StarWarsFilms)
	}

	transaction {
		SchemaUtils.statementsRequiredToActualizeScheme(
			StarWarsFilms
		) + SchemaUtils.addMissingColumnsStatements(
			StarWarsFilms
		)
	}.forEach {
		transaction {
			try {
				TransactionManager.current().exec(it)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}
}

object StarWarsFilms : IntIdTable() {
	val sequelId = integer("sequel_id").uniqueIndex()
	val name = varchar("name", 50)
	val director = varchar("director", 50)
}

class StarWarsFilm(id: EntityID<Int>) : IntEntity(id) {
	companion object : IntEntityClass<StarWarsFilm>(StarWarsFilms)

	var sequelId by StarWarsFilms.sequelId
	var name by StarWarsFilms.name
	var director by StarWarsFilms.director
}

fun createMovie(name: String, sequelId: Int, director: String) = transaction {
	Movie(
		StarWarsFilm.new {
			this.name = name
			this.sequelId = sequelId
			this.director = director
		}
	)
}

class Movie(private val movie: StarWarsFilm) {
	val id: Int
		get() = movie.id.value

	var name: String
		get() = movie.name
		set(value) = transaction {
			movie.name = value
		}
}