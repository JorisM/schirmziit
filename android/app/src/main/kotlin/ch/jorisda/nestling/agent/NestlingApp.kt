package ch.jorisda.nestling.agent

import android.app.Application
import android.util.Log
import ch.jorisda.nestling.agent.notify.OngoingNotice
import ch.jorisda.nestling.agent.store.AgentStore
import ch.jorisda.nestling.agent.sync.SyncWorker

class NestlingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OngoingNotice.createChannel(this)

        // AndroidKeyStore is not universally available (JVM tests, some OEM
        // images, a device mid-restore). Failing to read pairing state must not
        // take the process down at startup — the UI can re-pair.
        runCatching { AgentStore(this) }
            .onSuccess { store ->
                if (store.isPaired) {
                    SyncWorker.schedule(this)
                    OngoingNotice.update(this, store)
                }
            }
            .onFailure { failure -> Log.w(TAG, "could not read pairing state", failure) }
    }

    private companion object {
        const val TAG = "NestlingApp"
    }
}
