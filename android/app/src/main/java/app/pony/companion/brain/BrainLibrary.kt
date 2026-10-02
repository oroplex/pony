package app.pony.companion.brain

import java.io.File
import java.util.UUID

/**
 * Saved brains. Provider metadata is separate from secrets.
 * The API key is never stored in [providers.json] and never returned on [ProviderRecord].
 */
class BrainLibrary(private val root: File, box: SecretBox) {
    private val recordsFile = File(root, "providers.json")
    private val vault = FileKeyVault(File(root, "secrets.json"), box)

    fun list(): List<ProviderRecord> = load()

    fun active(): ProviderRecord? = load().firstOrNull { it.active }

    fun get(id: String): ProviderRecord? = load().firstOrNull { it.id == id }

    /** Runtime only. Callers must not log or relay the result. */
    fun secret(id: String): String? = vault.get(id)

    fun save(draft: ProviderDraft): ProviderRecord {
        val name = draft.name.trim()
        val model = draft.model.trim()
        val base = draft.baseUrl.trim().trimEnd('/')
        if (name.isEmpty()) error("Name the brain.")
        if (model.isEmpty()) error("Set a model.")
        if (!base.startsWith("https://") && !base.startsWith("http://")) {
            error("Base URL must start with https://")
        }
        val existing = draft.id?.let { get(it) }
        val key = draft.newKey?.trim().orEmpty()
        if (existing == null && key.length < 8) error("Paste an API key of at least 8 characters.")
        if (key.isNotEmpty() && key.length < 8) error("Paste an API key of at least 8 characters.")
        val id = existing?.id ?: UUID.randomUUID().toString()
        val last4 = if (key.isNotEmpty()) key.takeLast(4) else existing?.keyLast4 ?: error("Paste an API key.")
        if (key.isNotEmpty()) vault.put(id, key)
        val record = ProviderRecord(
            id = id,
            name = name,
            preset = draft.preset,
            model = model,
            baseUrl = base,
            keyLast4 = last4,
            active = existing?.active ?: load().isEmpty(),
        )
        val next = load().filter { it.id != id } + record
        write(next)
        return record
    }

    fun activate(id: String): ProviderRecord? {
        val all = load()
        if (all.none { it.id == id }) return null
        val next = all.map { it.copy(active = it.id == id) }
        write(next)
        return next.first { it.id == id }
    }

    fun delete(id: String) {
        vault.delete(id)
        val rest = load().filter { it.id != id }
        val next = if (rest.isNotEmpty() && rest.none { it.active }) {
            rest.mapIndexed { index, record -> record.copy(active = index == 0) }
        } else {
            rest
        }
        write(next)
    }

    /**
     * Forgets every brain in one shot: wipes all sealed keys from the vault and
     * removes every provider record. Used by "Disconnect everything".
     */
    fun clearAll() {
        vault.clear()
        recordsFile.delete()
    }

    fun providerText(): String = if (recordsFile.exists()) recordsFile.readText() else ""

    fun secretText(): String = vault.raw()

    private fun load(): List<ProviderRecord> {
        if (!recordsFile.exists() || recordsFile.length() == 0L) return emptyList()
        val rootJson = JsonValue.parse(recordsFile.readText()) as? JsonValue.Obj ?: return emptyList()
        val items = rootJson.get("providers") as? JsonValue.Arr ?: return emptyList()
        val records = items.items.mapNotNull { item ->
            val obj = item as? JsonValue.Obj ?: return@mapNotNull null
            val preset = runCatching {
                ProviderPreset.valueOf((obj.get("preset") as? JsonValue.Str)?.value ?: return@mapNotNull null)
            }.getOrNull() ?: return@mapNotNull null
            ProviderRecord(
                id = (obj.get("id") as? JsonValue.Str)?.value ?: return@mapNotNull null,
                name = (obj.get("name") as? JsonValue.Str)?.value ?: return@mapNotNull null,
                preset = preset,
                model = (obj.get("model") as? JsonValue.Str)?.value ?: "",
                baseUrl = (obj.get("baseUrl") as? JsonValue.Str)?.value ?: "",
                keyLast4 = (obj.get("keyLast4") as? JsonValue.Str)?.value ?: "",
                active = (obj.get("active") as? JsonValue.Bool)?.value ?: false,
            )
        }
        val migrated = migrateDefaults(records)
        if (migrated != records) write(migrated)
        return migrated
    }

    /** Moves a brain off a replaced built-in default; a hand-picked model is never touched. */
    private fun migrateDefaults(records: List<ProviderRecord>): List<ProviderRecord> =
        records.map { record ->
            val superseded = ProviderPreset.supersededModels[record.preset].orEmpty()
            if (record.model in superseded) record.copy(model = record.preset.defaultModel) else record
        }

    private fun write(records: List<ProviderRecord>) {
        root.mkdirs()
        val body = JsonValue.obj(
            "providers" to JsonValue.arr(records.map { record ->
                JsonValue.obj(
                    "id" to JsonValue.str(record.id),
                    "name" to JsonValue.str(record.name),
                    "preset" to JsonValue.str(record.preset.name),
                    "model" to JsonValue.str(record.model),
                    "baseUrl" to JsonValue.str(record.baseUrl),
                    "keyLast4" to JsonValue.str(record.keyLast4),
                    "active" to JsonValue.bool(record.active),
                )
            }),
        )
        recordsFile.writeText(body.encode())
    }
}
