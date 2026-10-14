package com.hafeziquran.app.di

import android.content.Context
import com.shockwave.pdfium.PdfiumCore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * One PdfiumCore for the process. It loads the `pdfium` native library on
     * construction, so a second instance would only waste memory.
     */
    @Provides
    @Singleton
    fun providePdfiumCore(@ApplicationContext context: Context): PdfiumCore =
        PdfiumCore(context)
}
