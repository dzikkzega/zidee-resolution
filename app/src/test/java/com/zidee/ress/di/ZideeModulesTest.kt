package com.zidee.ress.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.zidee.ress.domain.usecase.OptimizeMediaUseCase
import com.zidee.ress.engine.StagingDirectoryCleaner
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import java.time.ZoneId

class ZideeModulesTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `verify entire Koin dependency graph across all Zidee modules`() {
        module {
            includes(zideeAppModules)
        }.verify(
            extraTypes =
                listOf(
                    Context::class,
                    Application::class,
                    SavedStateHandle::class,
                ),
            injections =
                injectedParameters(
                    definition<OptimizeMediaUseCase>(Function0::class, ZoneId::class),
                    definition<StagingDirectoryCleaner>(Function0::class),
                ),
        )
    }
}
