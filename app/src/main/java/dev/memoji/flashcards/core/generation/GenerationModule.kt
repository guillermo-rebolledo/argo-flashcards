package dev.memoji.flashcards.core.generation

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.memoji.flashcards.core.data.ApiKeyRepository
import java.time.Duration
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal object GenerationModule {

    /**
     * The app's only HTTP client, for its only request. The timeouts are what turns a request
     * that is never coming back into a message the user can act on: a Generation the user is
     * sat waiting for must give up long before the platform's own timeouts would.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(60))
        .callTimeout(Duration.ofSeconds(90))
        .build()

    @Provides
    @Singleton
    fun provideCardGenerator(
        apiKeyRepository: ApiKeyRepository,
        client: OkHttpClient,
    ): CardGenerator = AnthropicCardGenerator(apiKeyRepository, client, MESSAGES_ENDPOINT)

    private const val MESSAGES_ENDPOINT = "https://api.anthropic.com/v1/messages"
}
