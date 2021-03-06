/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.security.sessionEncryption.holder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ul.ims.gmdl.cbordata.response.Response
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionData
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager
import com.ul.ims.gmdl.security.sessionencryption.verifier.VerifierSessionManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class HolderSessionManagerTest {

    companion object {
        const val CREDENTIAL_NAME = "mycredentialtest"
        const val VERIFIER_MSG_TO_HOLDER = "HELLO FROM VERIFIER"
    }

    lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun generateHolderCoseKeyTest() {
        val sessionManager = HolderSessionManager.getInstance(appContext, CREDENTIAL_NAME)
        sessionManager.initializeHolderSession()

        val coseKey = sessionManager.generateHolderCoseKey()
        Assert.assertNotNull(coseKey)
        Assert.assertNotNull(coseKey?.curve?.yCoordinate)
        Assert.assertNotNull(coseKey?.curve?.xCoordinate)
    }

    @Test
    fun decryptSessionEstablishmentTest() {
        val holderSessionManager = HolderSessionManager.getInstance(appContext, CREDENTIAL_NAME)
        holderSessionManager.initializeHolderSession()

        val coseKey = holderSessionManager.generateHolderCoseKey()
        coseKey?.let {
            val verifierSessionManager = VerifierSessionManager(it)
            val rCoseKey = verifierSessionManager.getReaderCoseKey()
            Assert.assertNotNull(rCoseKey)

            rCoseKey?.let {ck ->
                holderSessionManager.setVerifierEphemeralPublicKey(ck)

                val encryptedMsg = verifierSessionManager.encryptData(
                    VERIFIER_MSG_TO_HOLDER.toByteArray())
                encryptedMsg?.let {msg ->
                    val sessionEstablishment = verifierSessionManager.
                        createSessionEstablishment(msg)

                    val decryptedSessionEstablishment = holderSessionManager.
                        decryptSessionEstablishment(sessionEstablishment)
                    Assert.assertNotNull(decryptedSessionEstablishment)
                    Assert.assertArrayEquals(VERIFIER_MSG_TO_HOLDER.toByteArray(),
                        decryptedSessionEstablishment)
                }
            }
        }
    }

    @Test
    fun generateResponseTest() {
        val holderSessionManager = HolderSessionManager.getInstance(appContext, CREDENTIAL_NAME)
        holderSessionManager.initializeHolderSession()

        // Error Response
        var response = holderSessionManager.generateResponse(null)
        Assert.assertNotNull(response)
        var sessionData = SessionData.Builder()
            .decode(response)
            .build()

        Assert.assertNotNull(sessionData)
        Assert.assertEquals(10, sessionData.errorCode)
        Assert.assertNull(sessionData.encryptedData)

        // Correct Response
        val coseKey = holderSessionManager.generateHolderCoseKey()
        coseKey?.let {
            val verifierSessionManager = VerifierSessionManager(it)
            val coseKey = verifierSessionManager.getReaderCoseKey()
            coseKey?.let {ck->
                holderSessionManager.setVerifierEphemeralPublicKey(ck)
                val correctResponse = Response.Builder().isError().build().encode()
                response = holderSessionManager.generateResponse(correctResponse)
                Assert.assertNotNull(response)
                sessionData = SessionData.Builder()
                    .decode(response)
                    .build()

                Assert.assertNotNull(sessionData)
                Assert.assertEquals(0, sessionData.errorCode)
                Assert.assertNotNull(sessionData.encryptedData)
            }
        }
    }
}