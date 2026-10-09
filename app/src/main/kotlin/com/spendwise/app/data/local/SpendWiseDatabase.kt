package com.spendwise.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendwise.app.data.local.dao.*
import com.spendwise.app.data.local.entity.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        IncomeMonth::class,
        Expense::class,
        Category::class,
        CreditCardLedger::class,
        Budget::class,
        SyncOutbox::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SpendWiseDatabase : RoomDatabase() {
    abstract fun incomeMonthDao(): IncomeMonthDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun creditCardLedgerDao(): CreditCardLedgerDao
    abstract fun budgetDao(): BudgetDao
    abstract fun syncOutboxDao(): SyncOutboxDao

    /**
     * Executes PRAGMA wal_checkpoint(TRUNCATE) to flush all uncommitted/committed WAL pages
     * into the primary spendwise.db file and truncate the WAL file to 0 bytes.
     * This is crucial for Android Auto Backup which backs up the primary .db file while excluding *-wal and *-shm.
     */
    fun checkpointWal(truncate: Boolean = true) {
        try {
            val pragma = if (truncate) "PRAGMA wal_checkpoint(TRUNCATE);" else "PRAGMA wal_checkpoint(FULL);"
            openHelper.writableDatabase.execSQL(pragma)
            android.util.Log.d("SpendWiseDatabase", "$pragma flushed successfully")
        } catch (e: Exception) {
            android.util.Log.w("SpendWiseDatabase", "wal_checkpoint error: ${e.message}")
        }
    }

    override fun close() {
        checkpointWal()
        super.close()
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Migrate Category table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `category_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `color_hex` TEXT NOT NULL,
                        `icon_name` TEXT NOT NULL,
                        `is_default` INTEGER NOT NULL,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `category_new` (`id`, `name`, `color_hex`, `icon_name`, `is_default`, `device_id`, `is_deleted`, `created_at`, `updated_at`)
                    SELECT CAST(id AS TEXT), name, color_hex, icon_name, is_default, '', 0, created_at, created_at
                    FROM `category`
                """.trimIndent())
                db.execSQL("DROP TABLE `category`")
                db.execSQL("ALTER TABLE `category_new` RENAME TO `category`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_is_deleted` ON `category` (`is_deleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_updated_at` ON `category` (`updated_at`)")

                // 2. Migrate Expense table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expense_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `amount` REAL NOT NULL,
                        `payment_source` TEXT NOT NULL,
                        `category_id` TEXT NOT NULL,
                        `date_epoch` INTEGER NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `month_key` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `expense_new` (`id`, `amount`, `payment_source`, `category_id`, `date_epoch`, `year`, `month`, `month_key`, `note`, `device_id`, `is_deleted`, `created_at`, `updated_at`)
                    SELECT CAST(id AS TEXT), amount, payment_source, CAST(category_id AS TEXT), date_epoch, year, month, 
                           printf('%04d-%02d', year, month), note, '', 0, created_at, updated_at
                    FROM `expense`
                """.trimIndent())
                db.execSQL("DROP TABLE `expense`")
                db.execSQL("ALTER TABLE `expense_new` RENAME TO `expense`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_category_id` ON `expense` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_date_epoch` ON `expense` (`date_epoch`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_payment_source` ON `expense` (`payment_source`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_month_key` ON `expense` (`month_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_is_deleted` ON `expense` (`is_deleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_updated_at` ON `expense` (`updated_at`)")

                // 3. Migrate Budget table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `budget_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `category_id` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `month_key` TEXT NOT NULL,
                        `budget_amount` REAL NOT NULL,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `budget_new` (`id`, `category_id`, `year`, `month`, `month_key`, `budget_amount`, `device_id`, `is_deleted`, `created_at`, `updated_at`)
                    SELECT CAST(id AS TEXT), CAST(category_id AS TEXT), year, month, 
                           printf('%04d-%02d', year, month), budget_amount, '', 0, created_at, created_at
                    FROM `budget`
                """.trimIndent())
                db.execSQL("DROP TABLE `budget`")
                db.execSQL("ALTER TABLE `budget_new` RENAME TO `budget`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_category_id_month_key` ON `budget` (`category_id`, `month_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_is_deleted` ON `budget` (`is_deleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_updated_at` ON `budget` (`updated_at`)")

                // 4. Migrate IncomeMonth table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `income_month_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `month_key` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `salary` REAL NOT NULL,
                        `credit_card_due_carried` REAL NOT NULL,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `income_month_new` (`id`, `month_key`, `year`, `month`, `salary`, `credit_card_due_carried`, `device_id`, `is_deleted`, `created_at`, `updated_at`)
                    SELECT CAST(id AS TEXT), printf('%04d-%02d', year, month), year, month, salary, credit_card_due_carried, '', 0, created_at, updated_at
                    FROM `income_month`
                """.trimIndent())
                db.execSQL("DROP TABLE `income_month`")
                db.execSQL("ALTER TABLE `income_month_new` RENAME TO `income_month`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_income_month_month_key` ON `income_month` (`month_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_income_month_is_deleted` ON `income_month` (`is_deleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_income_month_updated_at` ON `income_month` (`updated_at`)")

                // 5. Migrate CreditCardLedger table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `credit_card_ledger_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `month_key` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `total_spend` REAL NOT NULL,
                        `carried_to_next_month` INTEGER NOT NULL,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `credit_card_ledger_new` (`id`, `month_key`, `year`, `month`, `total_spend`, `carried_to_next_month`, `device_id`, `is_deleted`, `created_at`, `updated_at`)
                    SELECT CAST(id AS TEXT), printf('%04d-%02d', year, month), year, month, total_spend, carried_to_next_month, '', 0, updated_at, updated_at
                    FROM `credit_card_ledger`
                """.trimIndent())
                db.execSQL("DROP TABLE `credit_card_ledger`")
                db.execSQL("ALTER TABLE `credit_card_ledger_new` RENAME TO `credit_card_ledger`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_credit_card_ledger_month_key` ON `credit_card_ledger` (`month_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_card_ledger_is_deleted` ON `credit_card_ledger` (`is_deleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_credit_card_ledger_updated_at` ON `credit_card_ledger` (`updated_at`)")

                // 6. Create sync_outbox table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sync_outbox` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `entity_type` TEXT NOT NULL,
                        `entity_id` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `retry_count` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entity_id` ON `sync_outbox` (`entity_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entity_type` ON `sync_outbox` (`entity_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_timestamp` ON `sync_outbox` (`timestamp`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `category` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `category` ADD COLUMN `aliases` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `category` ADD COLUMN `budget` REAL DEFAULT NULL")

                // Ensure deterministic Others category exists
                val now = System.currentTimeMillis()
                db.execSQL("""
                    INSERT OR IGNORE INTO `category` (`id`, `name`, `color_hex`, `icon_name`, `is_default`, `sort_order`, `aliases`, `budget`, `device_id`, `is_deleted`, `created_at`, `updated_at`)
                    VALUES ('cat_others', 'Others', '#6B7280', 'category', 1, 9999, 'other,miscellaneous,misc,general,unknown,etc,expenses', NULL, '', 0, $now, $now)
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add split expenses & wallet credit columns to expense table with safe defaults (0 / 0.0)
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `is_split` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `is_reimbursed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `receivable_amount` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `net_user_expense` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `extra_credit_amount` REAL NOT NULL DEFAULT 0.0")

                // Add indices for fast queries on split and reimbursement status
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_is_split` ON `expense` (`is_split`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_is_reimbursed` ON `expense` (`is_reimbursed`)")

                // Add extra_credit to income_month table with safe default
                db.execSQL("ALTER TABLE `income_month` ADD COLUMN `extra_credit` REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun create(context: Context, passphrase: ByteArray): SpendWiseDatabase {
            val factory = SupportOpenHelperFactory(passphrase)
            val callback = object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    try {
                        db.execSQL("PRAGMA auto_vacuum = FULL;")
                        android.util.Log.i("SpendWiseDatabase", "PRAGMA auto_vacuum = FULL applied on creation")
                    } catch (e: Exception) {
                        android.util.Log.w("SpendWiseDatabase", "Failed to set auto_vacuum = FULL: ${e.message}")
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    try {
                        db.query("PRAGMA wal_checkpoint(FULL);").use { cursor ->
                            cursor.moveToFirst()
                        }
                        android.util.Log.i("SpendWiseDatabase", "PRAGMA wal_checkpoint(FULL) applied on open")
                    } catch (e: Exception) {
                        android.util.Log.w("SpendWiseDatabase", "Failed to wal_checkpoint onOpen: ${e.message}")
                    }
                }
            }

            return Room.databaseBuilder(
                context.applicationContext,
                SpendWiseDatabase::class.java,
                "spendwise.db"
            )
                .openHelperFactory(factory)
                .addCallback(callback)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
