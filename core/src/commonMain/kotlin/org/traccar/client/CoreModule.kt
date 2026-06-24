package org.traccar.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun coreModule(): Module = module {
    single { ConfigStore(get()) }
    single<PositionQueue> { DatabaseQueue(get()) }
    single<Flow<Position>> { get<LocationSource>().positions }
    single<StateFlow<State>> { get<StateStore>().state }
    single { ComponentCoroutineScope() }

    single<Uploader> { HttpUploader(get(), get()) }
    single { LocationFilter(get(), get()) }

    single {
        TrackerEngine(
            config = get(),
            stateStore = get(),
            queue = get(),
            network = get(),
            locationSource = get(),
            signalSources = getAll<SignalSource>(),
            processors = listOf(get<LocationFilter>(), get()),
            uploader = get(),
            scope = get(),
        )
    }

    single {
        Tracker(
            config = get(),
            stateStore = get(),
            locationSource = get(),
            batteryProcessor = get(),
            uploader = get(),
            componentScope = get(),
        )
    }
}

internal expect fun platformModule(): Module
