package com.ss.medrecord.di

import javax.inject.Qualifier

/**
 * A CoroutineScope that lives as long as the process. For work that must outlive
 * any single screen - session state, sync triggers - never for work a ViewModel
 * could own.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
