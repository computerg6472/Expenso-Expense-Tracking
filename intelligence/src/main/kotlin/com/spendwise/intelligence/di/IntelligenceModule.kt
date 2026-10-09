package com.spendwise.intelligence.di

import com.spendwise.intelligence.anomaly.AnomalyDetectorImpl
import com.spendwise.intelligence.capability.CapabilityProvider
import com.spendwise.intelligence.capability.DefaultCapabilityProvider
import com.spendwise.intelligence.categorizer.SmartCategorizerImpl
import com.spendwise.intelligence.domain.feature.*
import com.spendwise.intelligence.forecast.ForecastEngineImpl
import com.spendwise.intelligence.goal.GoalAdvisorImpl
import com.spendwise.intelligence.health.HealthScoreCalculatorImpl
import com.spendwise.intelligence.health.WhatIfSimulatorImpl
import com.spendwise.intelligence.insight.InsightEngineImpl
import com.spendwise.intelligence.nlp.NlpExpenseParserImpl
import com.spendwise.intelligence.query.NlQueryEngineImpl
import com.spendwise.intelligence.receipt.ReceiptScannerImpl
import com.spendwise.intelligence.recurring.RecurringDetectorImpl
import com.spendwise.intelligence.voice.VoiceCaptureEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IntelligenceModule {

    @Binds
    @Singleton
    abstract fun bindCapabilityProvider(
        impl: DefaultCapabilityProvider
    ): CapabilityProvider

    @Binds
    @Singleton
    abstract fun bindNlpExpenseParser(
        impl: NlpExpenseParserImpl
    ): NlpExpenseParser

    @Binds
    @Singleton
    abstract fun bindSmartCategorizer(
        impl: SmartCategorizerImpl
    ): SmartCategorizer

    @Binds
    @Singleton
    abstract fun bindRecurringDetector(
        impl: RecurringDetectorImpl
    ): RecurringDetector

    @Binds
    @Singleton
    abstract fun bindForecastEngine(
        impl: ForecastEngineImpl
    ): ForecastEngine

    @Binds
    @Singleton
    abstract fun bindAnomalyDetector(
        impl: AnomalyDetectorImpl
    ): AnomalyDetector

    @Binds
    @Singleton
    abstract fun bindHealthScoreCalculator(
        impl: HealthScoreCalculatorImpl
    ): HealthScoreCalculator

    @Binds
    @Singleton
    abstract fun bindWhatIfSimulator(
        impl: WhatIfSimulatorImpl
    ): WhatIfSimulator

    @Binds
    @Singleton
    abstract fun bindInsightEngine(
        impl: InsightEngineImpl
    ): InsightEngine

    @Binds
    @Singleton
    abstract fun bindReceiptScanner(
        impl: ReceiptScannerImpl
    ): ReceiptScanner

    @Binds
    @Singleton
    abstract fun bindVoiceCaptureEngine(
        impl: VoiceCaptureEngineImpl
    ): VoiceCaptureEngine

    @Binds
    @Singleton
    abstract fun bindNlQueryEngine(
        impl: NlQueryEngineImpl
    ): NlQueryEngine

    @Binds
    @Singleton
    abstract fun bindGoalAdvisor(
        impl: GoalAdvisorImpl
    ): GoalAdvisor
}
