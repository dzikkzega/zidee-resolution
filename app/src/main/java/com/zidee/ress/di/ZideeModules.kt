package com.zidee.ress.di

import com.zidee.ress.core.coroutines.DefaultDispatcherProvider
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.data.local.ZideeDatabase
import com.zidee.ress.data.mediastore.AndroidMediaStoreGateway
import com.zidee.ress.data.mediastore.MediaMetadataReader
import com.zidee.ress.data.mediastore.MediaStoreGateway
import com.zidee.ress.data.mediastore.ZideeMediaStoreSaver
import com.zidee.ress.data.preferences.UserPreferencesDataStore
import com.zidee.ress.data.repository.MediaRepositoryImpl
import com.zidee.ress.data.repository.OptimizationHistoryRepositoryImpl
import com.zidee.ress.domain.repository.MediaMetadataRepository
import com.zidee.ress.domain.repository.MediaOptimizerEngine
import com.zidee.ress.domain.repository.MediaStorageRepository
import com.zidee.ress.domain.repository.OptimizationHistoryRepository
import com.zidee.ress.domain.repository.UserPreferencesRepository
import com.zidee.ress.domain.usecase.CalculateOptimalSpecUseCase
import com.zidee.ress.domain.usecase.ObserveOptimizationHistoryUseCase
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import com.zidee.ress.engine.AndroidForegroundExecutionMonitor
import com.zidee.ress.engine.ForegroundExecutionMonitor
import com.zidee.ress.engine.OfflineMediaOptimizerEngine
import com.zidee.ress.engine.StagingDirectoryCleaner
import com.zidee.ress.engine.photo.ColorSpaceNormalizer
import com.zidee.ress.engine.photo.PhotoProcessingPipeline
import com.zidee.ress.engine.video.FFmpegKitCommandExecutor
import com.zidee.ress.engine.video.FfmpegCommandExecutor
import com.zidee.ress.engine.video.FfmpegFiltergraphBuilder
import com.zidee.ress.engine.video.VideoProcessingPipeline
import com.zidee.ress.presentation.dashboard.DashboardViewModel
import com.zidee.ress.presentation.quickshare.QuickShareViewModel
import com.zidee.ress.presentation.share.AndroidPackageAvailabilityChecker
import com.zidee.ress.presentation.share.DirectShareIntentRouter
import com.zidee.ress.presentation.share.PackageAvailabilityChecker
import com.zidee.ress.presentation.studio.StudioEditorViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val coreModule: Module =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider }
    }

val domainModule: Module =
    module {
        factory { CalculateOptimalSpecUseCase() }
        factory {
            OptimizeMediaUseCase(
                calculateOptimalSpec = get(),
                engine = get(),
                storage = get(),
                history = get(),
            )
        }
        factory {
            ObserveOptimizationHistoryUseCase(
                history = get(),
                storage = get(),
            )
        }
    }

val engineModule: Module =
    module {
        single { StagingDirectoryCleaner(context = androidContext()) }
        factory { ColorSpaceNormalizer(context = androidContext()) }
        factory {
            PhotoProcessingPipeline(
                context = androidContext(),
                dispatchers = get(),
                normalizer = get(),
                stagingCleaner = get(),
            )
        }
        factory { FfmpegFiltergraphBuilder() }
        factory<FfmpegCommandExecutor> { FFmpegKitCommandExecutor(context = androidContext()) }
        factory {
            VideoProcessingPipeline(
                dispatchers = get(),
                executor = get(),
                stagingCleaner = get(),
                filtergraphBuilder = get(),
            )
        }
        factory<ForegroundExecutionMonitor> { AndroidForegroundExecutionMonitor(context = androidContext()) }
        single<MediaOptimizerEngine> {
            OfflineMediaOptimizerEngine(
                context = androidContext(),
                photoPipeline = get(),
                videoPipeline = get(),
                stagingCleaner = get(),
                foregroundMonitor = get(),
            )
        }
    }

val dataModule: Module =
    module {
        single { ZideeDatabase.create(androidContext()) }
        single { get<ZideeDatabase>().optimizationHistoryDao() }
        single { OptimizationHistoryRepositoryImpl(dao = get(), dispatchers = get()) }
        single<MediaStoreGateway> { AndroidMediaStoreGateway(context = androidContext()) }
        single { ZideeMediaStoreSaver(gateway = get(), dispatchers = get()) }
        single { MediaRepositoryImpl(storageSaver = get(), historyRepository = get()) }
        single<MediaStorageRepository> { get<MediaRepositoryImpl>() }
        single<OptimizationHistoryRepository> { get<MediaRepositoryImpl>() }
        single<MediaMetadataRepository> {
            MediaMetadataReader(
                context = androidContext(),
                dispatchers = get(),
            )
        }
        single<UserPreferencesRepository> { UserPreferencesDataStore(context = androidContext()) }
    }

val presentationModule: Module =
    module {
        factory<PackageAvailabilityChecker> { AndroidPackageAvailabilityChecker(androidContext().packageManager) }
        factory { DirectShareIntentRouter(availabilityChecker = get()) }
        viewModel {
            DashboardViewModel(
                observeOptimizationHistory = get(),
                storageRepository = get(),
                historyRepository = get(),
                preferencesRepository = get(),
            )
        }
        viewModel {
            StudioEditorViewModel(
                calculateOptimalSpec = get(),
                optimizeMedia = get(),
                metadataRepository = get(),
                preferencesRepository = get(),
                savedStateHandle = get(),
            )
        }
        viewModel {
            QuickShareViewModel(
                calculateOptimalSpec = get(),
                optimizeMedia = get(),
                metadataRepository = get(),
                preferencesRepository = get(),
                savedStateHandle = get(),
            )
        }
    }

val zideeAppModules: List<Module> =
    listOf(
        coreModule,
        domainModule,
        engineModule,
        dataModule,
        presentationModule,
    )
