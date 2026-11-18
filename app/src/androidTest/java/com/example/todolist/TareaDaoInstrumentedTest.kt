package com.example.todolist

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TareaDaoInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    AppDatabase.createTaskValidationTriggers(db)
                }
            })
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun taskQuerySearchAndFiltersAreScopedToTheSignedInUser() {
        createUser("ana@example.com")
        createUser("luis@example.com")
        insertTask("Diseñar prototipo", "ana@example.com", EstadoTarea.EN_PROGRESO, "Alta", 10)
        insertTask("Leer diseño", "ana@example.com", EstadoTarea.PENDIENTE, "Media", 20)
        insertTask("Diseñar reporte", "luis@example.com", EstadoTarea.PENDIENTE, "Alta", 30)

        val allAnaTasks = database.tareaDao().buscarTareas(
            correo = "ana@example.com",
            mostrarCompletadas = true,
            estado = null,
            busqueda = "",
            orden = OrdenTareas.RECIENTES.name
        )
        assertEquals(listOf("Leer diseño", "Diseñar prototipo"), allAnaTasks.map { it.titulo })

        val onlyInProgress = database.tareaDao().buscarTareas(
            correo = "ana@example.com",
            mostrarCompletadas = true,
            estado = EstadoTarea.EN_PROGRESO.name,
            busqueda = "DISEÑAR",
            orden = OrdenTareas.PRIORIDAD.name
        )
        assertEquals(listOf("Diseñar prototipo"), onlyInProgress.map { it.titulo })
        assertTrue(allAnaTasks.none { it.propietarioEmail == "luis@example.com" })
    }

    @Test
    fun updatesAndDeletesCannotChangeAnotherUsersTasks() {
        createUser("ana@example.com")
        createUser("luis@example.com")
        val taskId = insertTask(
            "Tarea privada",
            "ana@example.com",
            EstadoTarea.PENDIENTE,
            "Media",
            1
        )

        assertEquals(
            0,
            database.tareaDao().actualizarTarea(
                id = taskId.toInt(),
                correo = "luis@example.com",
                titulo = "Modificada",
                fechaHora = "2026-11-09",
                completada = false,
                estado = EstadoTarea.PENDIENTE.name,
                prioridad = "Media",
                categoria = "Escuela"
            )
        )
        assertEquals(0, database.tareaDao().eliminarTarea(taskId.toInt(), "luis@example.com"))
        assertEquals(1, database.tareaDao().eliminarTarea(taskId.toInt(), "ana@example.com"))
    }

    @Test
    fun tasksRequireAnExistingOwnerAndAreRemovedWithThatAccount() {
        createUser("ana@example.com")
        try {
            insertTask("Sin usuario", "inexistente@example.com", EstadoTarea.PENDIENTE, "Baja", 1)
            throw AssertionError("La base debe rechazar propietarios inexistentes.")
        } catch (_: SQLiteConstraintException) {
            // Expected: the owner foreign key rejects this row.
        }

        insertTask("Tarea de Ana", "ana@example.com", EstadoTarea.PENDIENTE, "Baja", 1)
        assertEquals(1, database.usuarioDao().eliminarPorCorreo("ana@example.com"))
        assertTrue(
            database.tareaDao().buscarTareas(
                "ana@example.com",
                true,
                null,
                "",
                OrdenTareas.RECIENTES.name
            ).isEmpty()
        )
    }

    @Test
    fun databaseRejectsInvalidTaskFieldsEvenIfRepositoryIsBypassed() {
        createUser("ana@example.com")
        try {
            database.tareaDao().insertarTarea(
                TareaEntity(
                    titulo = " ",
                    fechaHora = "2026-11-09",
                    completada = false,
                    estado = EstadoTarea.COMPLETADA.name,
                    prioridad = "Urgente",
                    categoria = "Escuela",
                    propietarioEmail = "ana@example.com"
                )
            )
            throw AssertionError("El trigger debe rechazar tareas inválidas.")
        } catch (_: SQLiteConstraintException) {
            // Expected: database constraints also protect writes outside the UI.
        }
    }

    @Test
    fun localAuthenticationStoresOnlyPasswordHashAndChecksCredentials() {
        val repository = AuthRepository(database)
        try {
            var registered: Result<UsuarioEntity>? = null
            val registerLatch = CountDownLatch(1)
            repository.registrar(
                "Ana Torres",
                " ANA@EXAMPLE.COM ",
                "clave-segura-123".toCharArray()
            ) {
                registered = it
                registerLatch.countDown()
            }
            assertTrue(registerLatch.await(10, TimeUnit.SECONDS))
            val user = registered?.getOrThrow() ?: throw AssertionError("El registro no devolvió usuario.")
            assertEquals("ana@example.com", user.correo)
            assertFalse(user.contrasenaHash.contains("clave-segura-123"))
            assertFalse(user.contrasenaSalt.contains("clave-segura-123"))

            var authentication: Result<UsuarioEntity?>? = null
            val loginLatch = CountDownLatch(1)
            repository.autenticar("ana@example.com", "clave-segura-123".toCharArray()) {
                authentication = it
                loginLatch.countDown()
            }
            assertTrue(loginLatch.await(10, TimeUnit.SECONDS))
            assertEquals(user.id, authentication?.getOrThrow()?.id)

            var rejected: Result<UsuarioEntity?>? = null
            val rejectedLatch = CountDownLatch(1)
            repository.autenticar("ana@example.com", "incorrecta".toCharArray()) {
                rejected = it
                rejectedLatch.countDown()
            }
            assertTrue(rejectedLatch.await(10, TimeUnit.SECONDS))
            assertEquals(null, rejected?.getOrThrow())
        } finally {
            repository.close()
        }
    }


    private fun createUser(email: String) {
        database.usuarioDao().insertar(
            UsuarioEntity(
                nombre = email.substringBefore("@"),
                correo = email,
                contrasenaSalt = "salt",
                contrasenaHash = "hash"
            )
        )
    }

    private fun insertTask(
        title: String,
        owner: String,
        status: EstadoTarea,
        priority: String,
        createdAt: Long
    ): Long = database.tareaDao().insertarTarea(
        TareaEntity(
            titulo = title,
            fechaHora = "2026-11-09",
            completada = status == EstadoTarea.COMPLETADA,
            estado = status.name,
            prioridad = priority,
            categoria = "Escuela",
            propietarioEmail = owner,
            createdAt = createdAt
        )
    )
}
