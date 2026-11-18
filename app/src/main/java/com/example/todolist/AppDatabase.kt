package com.example.todolist

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "tabla_tareas",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["correo"],
            childColumns = ["propietarioEmail"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["propietarioEmail", "estado", "createdAt"])]
)
data class TareaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titulo: String,
    val fechaHora: String,
    val completada: Boolean,
    @ColumnInfo(defaultValue = "'PENDIENTE'")
    val estado: String = EstadoTarea.PENDIENTE.name,
    val prioridad: String,
    val categoria: String,
    val propietarioEmail: String?,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)

enum class EstadoTarea {
    PENDIENTE,
    EN_PROGRESO,
    COMPLETADA
}

enum class OrdenTareas {
    RECIENTES,
    PRIORIDAD,
    TITULO
}

@Dao
interface TareaDao {
    @Query(
        """
        SELECT * FROM tabla_tareas
        WHERE propietarioEmail = :correo
          AND (:mostrarCompletadas OR estado != 'COMPLETADA')
          AND (:estado IS NULL OR estado = :estado)
          AND (
              :busqueda = '' OR
              titulo LIKE '%' || :busqueda || '%' COLLATE NOCASE OR
              categoria LIKE '%' || :busqueda || '%' COLLATE NOCASE
          )
        ORDER BY
          CASE WHEN :orden = 'PRIORIDAD' THEN
            CASE prioridad WHEN 'Alta' THEN 0 WHEN 'Media' THEN 1 ELSE 2 END
          END ASC,
          CASE WHEN :orden = 'RECIENTES' THEN createdAt END DESC,
          CASE WHEN :orden = 'TITULO' THEN titulo END COLLATE NOCASE ASC,
          id DESC
        """
    )
    fun buscarTareas(
        correo: String,
        mostrarCompletadas: Boolean,
        estado: String?,
        busqueda: String,
        orden: String
    ): List<TareaEntity>

    @Insert
    fun insertarTarea(tarea: TareaEntity): Long

    @Query(
        """
        UPDATE tabla_tareas SET
            titulo = :titulo,
            fechaHora = :fechaHora,
            completada = :completada,
            estado = :estado,
            prioridad = :prioridad,
            categoria = :categoria
        WHERE id = :id AND propietarioEmail = :correo
        """
    )
    fun actualizarTarea(
        id: Int,
        correo: String,
        titulo: String,
        fechaHora: String,
        completada: Boolean,
        estado: String,
        prioridad: String,
        categoria: String
    ): Int

    @Query("DELETE FROM tabla_tareas WHERE id = :id AND propietarioEmail = :correo")
    fun eliminarTarea(id: Int, correo: String): Int
}

@Entity(
    tableName = "usuarios",
    indices = [Index(value = ["correo"], unique = true)]
)
data class UsuarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val correo: String,
    val contrasenaSalt: String,
    val contrasenaHash: String
)

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios WHERE correo = :correo LIMIT 1")
    fun buscarPorCorreo(correo: String): UsuarioEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertar(usuario: UsuarioEntity): Long

    @Query("DELETE FROM usuarios WHERE correo = :correo")
    fun eliminarPorCorreo(correo: String): Int
}

@Database(entities = [TareaEntity::class, UsuarioEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tareaDao(): TareaDao
    abstract fun usuarioDao(): UsuarioDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tabla_tareas ADD COLUMN propietarioEmail TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS usuarios (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nombre TEXT NOT NULL,
                        correo TEXT NOT NULL,
                        contrasenaSalt TEXT NOT NULL,
                        contrasenaHash TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_usuarios_correo ON usuarios(correo)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tabla_tareas ADD COLUMN estado TEXT NOT NULL DEFAULT 'PENDIENTE'"
                )
                db.execSQL(
                    "ALTER TABLE tabla_tareas ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE tabla_tareas SET estado = 'COMPLETADA' WHERE completada = 1"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE tabla_tareas_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        titulo TEXT NOT NULL CHECK(length(trim(titulo)) BETWEEN 1 AND 120),
                        fechaHora TEXT NOT NULL,
                        completada INTEGER NOT NULL CHECK(completada IN (0, 1)),
                        estado TEXT NOT NULL DEFAULT 'PENDIENTE'
                            CHECK(estado IN ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADA')),
                        CHECK(completada = CASE WHEN estado = 'COMPLETADA' THEN 1 ELSE 0 END),
                        prioridad TEXT NOT NULL CHECK(prioridad IN ('Alta', 'Media', 'Baja')),
                        categoria TEXT NOT NULL CHECK(length(trim(categoria)) BETWEEN 1 AND 40),
                        propietarioEmail TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(propietarioEmail) REFERENCES usuarios(correo) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO tabla_tareas_new
                        (id, titulo, fechaHora, completada, estado, prioridad, categoria, propietarioEmail, createdAt)
                    SELECT t.id,
                           CASE WHEN length(trim(t.titulo)) = 0 THEN 'Tarea sin título' ELSE t.titulo END,
                           t.fechaHora,
                           CASE
                               WHEN t.estado = 'COMPLETADA'
                                 OR (t.estado NOT IN ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADA')
                                     AND t.completada = 1) THEN 1
                               ELSE 0
                           END,
                           CASE
                               WHEN t.estado IN ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADA') THEN t.estado
                               WHEN t.completada = 1 THEN 'COMPLETADA'
                               ELSE 'PENDIENTE'
                           END,
                           CASE WHEN t.prioridad IN ('Alta', 'Media', 'Baja') THEN t.prioridad ELSE 'Media' END,
                           CASE WHEN length(trim(t.categoria)) = 0 THEN 'General' ELSE t.categoria END,
                           CASE WHEN u.correo IS NULL THEN NULL ELSE t.propietarioEmail END,
                           t.createdAt
                    FROM tabla_tareas t
                    LEFT JOIN usuarios u ON u.correo = t.propietarioEmail
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE tabla_tareas")
                db.execSQL("ALTER TABLE tabla_tareas_new RENAME TO tabla_tareas")
                db.execSQL(
                    "CREATE INDEX index_tabla_tareas_propietarioEmail_estado_createdAt " +
                        "ON tabla_tareas(propietarioEmail, estado, createdAt)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val integrityCallback = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                createTaskValidationTriggers(db)
            }
        }

        internal fun createTaskValidationTriggers(db: SupportSQLiteDatabase) {
            val invalidTask =
                "NEW.propietarioEmail IS NULL OR length(trim(NEW.propietarioEmail)) = 0 OR " +
                    "length(trim(NEW.titulo)) NOT BETWEEN 1 AND 120 OR " +
                    "NEW.estado NOT IN ('PENDIENTE', 'EN_PROGRESO', 'COMPLETADA') OR " +
                    "NEW.prioridad NOT IN ('Alta', 'Media', 'Baja') OR " +
                    "length(trim(NEW.categoria)) NOT BETWEEN 1 AND 40 OR " +
                    "(NEW.estado = 'COMPLETADA') != (NEW.completada = 1)"
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS validate_task_before_insert
                BEFORE INSERT ON tabla_tareas
                WHEN $invalidTask
                BEGIN
                    SELECT RAISE(ABORT, 'La tarea contiene datos inválidos.');
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS validate_task_before_update
                BEFORE UPDATE ON tabla_tareas
                WHEN $invalidTask
                BEGIN
                    SELECT RAISE(ABORT, 'La tarea contiene datos inválidos.');
                END
                """.trimIndent()
            )
        }

        fun obtenerBaseDatos(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "db_todolist"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(integrityCallback)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
