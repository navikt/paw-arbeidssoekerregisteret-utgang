package no.nav.paw.kafkakeygenerator.auth

import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.cache.CaffeineTokenCache
import no.nav.common.token_client.client.AzureAdMachineToMachineTokenClient
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

fun azureAdM2MTokenClient(naisEnv: NaisEnv, azureProviderConfig: AuthProvider): AzureAdMachineToMachineTokenClient =
    when (naisEnv) {
        NaisEnv.Local -> AzureAdTokenClientBuilder.builder()
            .withClientId(azureProviderConfig.clientId)
            .withPrivateJwk(createMockRSAKey("azure"))
            .withTokenEndpointUrl(azureProviderConfig.tokenEndpointUrl)
            .buildMachineToMachineTokenClient()

        else -> AzureAdTokenClientBuilder.builder()
            .withNaisDefaults()
            .withCache(CaffeineTokenCache())
            .buildMachineToMachineTokenClient()
    }

fun createMockRSAKey(keyID: String): String? = KeyPairGenerator
    .getInstance("RSA").let {
        it.initialize(2048)
        it.generateKeyPair()
    }.let {
        RSAKey.Builder(it.public as RSAPublicKey)
            .privateKey(it.private as RSAPrivateKey)
            .keyUse(KeyUse.SIGNATURE)
            .keyID(keyID)
            .build()
            .toJSONString()
    }